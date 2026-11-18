package com.spacegoose.carlauncher.integrations.weather

import android.content.Context
import android.util.Log
import com.spacegoose.carlauncher.config.ServerConfig
import com.spacegoose.carlauncher.integrations.Http
import com.spacegoose.carlauncher.integrations.NeedsKeyException
import com.spacegoose.carlauncher.vehicle.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class WeatherState(
    val now: WeatherNow? = null,
    /** When [now] was fetched (System.currentTimeMillis), or 0 for the cached copy from last time. */
    val fetchedAt: Long = 0L,
    /** A short line when the last refresh didn't work; the last good reading stays on the card. */
    val problem: String? = null,
    val loading: Boolean = false,
)

/** Weather from a Weather Scout server (github.com/Spacegoose02/weather-scout). */
class WeatherRepository(context: Context) {
    private val cacheFile = File(context.applicationContext.filesDir, "weather.json")
    private val _state = MutableStateFlow(WeatherState(now = loadCache()))
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    suspend fun refresh(serverUrl: String, key: String, metric: Boolean, at: GeoPoint?) {
        val base = ServerConfig.normalizeUrl(serverUrl)
        if (base == null) {
            _state.value = _state.value.copy(problem = "Add your Weather Scout address in settings.", loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true)
        try {
            val text = withContext(Dispatchers.IO) { Http.getText(WeatherNow.url(base, metric, at?.lat, at?.lon), key) }
            val now = WeatherNow.parse(text)
            withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(WeatherNow.toJson(now)) } }
            _state.value = WeatherState(now = now, fetchedAt = System.currentTimeMillis())
        } catch (e: NeedsKeyException) {
            _state.value = _state.value.copy(
                problem = "Weather Scout needs an app key. Make one in Weather Scout (Family & settings, Connected apps) and add it in settings.",
                loading = false,
            )
        } catch (e: Exception) {
            Log.w(TAG, "weather refresh failed", e)
            _state.value = _state.value.copy(problem = "Couldn't update the weather. ${Http.describe(e)}", loading = false)
        }
    }

    private fun loadCache(): WeatherNow? = runCatching {
        if (cacheFile.exists()) WeatherNow.parse(cacheFile.readText()) else null
    }.getOrNull()

    private companion object {
        const val TAG = "WeatherRepository"
    }
}
