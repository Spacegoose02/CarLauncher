package com.spacegoose.carlauncher.integrations.sports

import android.content.Context
import android.util.Log
import com.spacegoose.carlauncher.config.ServerConfig
import com.spacegoose.carlauncher.integrations.Http
import com.spacegoose.carlauncher.integrations.NeedsKeyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class SportsState(
    val rows: List<TeamScore> = emptyList(),
    val fetchedAt: Long = 0L,
    val problem: String? = null,
    val loading: Boolean = false,
)

/** Scores from a Stat Scraps server (github.com/Spacegoose02/sports-tracker). */
class SportsRepository(context: Context) {
    private val cacheFile = File(context.applicationContext.filesDir, "scores.json")
    private val _state = MutableStateFlow(SportsState(rows = loadCache()))
    val state: StateFlow<SportsState> = _state.asStateFlow()

    /** With a [key], the teams followed on the Stat Scraps account; without one, the [teams] picked in settings. */
    suspend fun refresh(serverUrl: String, key: String, teams: List<String>) {
        val base = ServerConfig.normalizeUrl(serverUrl)
        if (base == null) {
            _state.value = _state.value.copy(problem = "Add your Stat Scraps address in settings.", loading = false)
            return
        }
        if (key.isBlank() && teams.isEmpty()) {
            _state.value = SportsState(problem = "Pick your teams in settings, or add your Stat Scraps key.")
            return
        }
        _state.value = _state.value.copy(loading = true)
        try {
            val rows = withContext(Dispatchers.IO) {
                if (key.isNotBlank()) {
                    val text = Http.getText(Scores.myTeamsUrl(base), key)
                    Scores.parseMyTeams(text).also { cache(text, null) }
                } else {
                    val text = Http.getText(Scores.scorecardsUrl(base, teams))
                    Scores.parseScorecards(text, teams).also { cache(text, teams) }
                }
            }
            _state.value = SportsState(
                rows = rows,
                fetchedAt = System.currentTimeMillis(),
                problem = if (rows.isEmpty() && key.isNotBlank()) "Follow a team on Stat Scraps to see it here." else null,
            )
        } catch (e: NeedsKeyException) {
            _state.value = _state.value.copy(
                problem = "Stat Scraps didn't accept the key. Copy it again from Account, Connect other apps.",
                loading = false,
            )
        } catch (e: Exception) {
            Log.w(TAG, "scores refresh failed", e)
            _state.value = _state.value.copy(problem = "Couldn't update scores. ${Http.describe(e)}", loading = false)
        }
    }

    /** Team search for the picker in settings. */
    suspend fun search(serverUrl: String, query: String): Result<List<TeamPick>> {
        val base = ServerConfig.normalizeUrl(serverUrl) ?: return Result.failure(IllegalArgumentException("No server address"))
        if (query.trim().length < 2) return Result.success(emptyList())
        return withContext(Dispatchers.IO) {
            runCatching { Scores.parseSearch(Http.getText(Scores.searchUrl(base, query))) }
        }
    }

    // The raw response and how to read it, so the card has something to show before the first refresh.
    private fun cache(text: String, teams: List<String>?) {
        runCatching { cacheFile.writeText((teams?.joinToString(",") ?: MY_TEAMS) + "\n" + text) }
    }

    private fun loadCache(): List<TeamScore> = runCatching {
        if (!cacheFile.exists()) return emptyList()
        val all = cacheFile.readText()
        val header = all.substringBefore('\n')
        val body = all.substringAfter('\n')
        if (header == MY_TEAMS) Scores.parseMyTeams(body) else Scores.parseScorecards(body, header.split(','))
    }.getOrDefault(emptyList())

    private companion object {
        const val TAG = "SportsRepository"
        const val MY_TEAMS = "#account"
    }
}
