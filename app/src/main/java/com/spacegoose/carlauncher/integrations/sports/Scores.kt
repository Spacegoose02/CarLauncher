package com.spacegoose.carlauncher.integrations.sports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder
import java.time.OffsetDateTime

enum class GameState { LIVE, FINAL, UPCOMING, NONE }

/** One line on the scores card: a team (or a race / fight card) and the game that matters right now. */
data class TeamScore(
    /** "cfb:259"; for races and fight cards just the sport, e.g. "f1". */
    val key: String,
    val name: String,
    val abbreviation: String?,
    val logo: String?,
    val record: String?,
    val rank: Int?,
    val state: GameState,
    /** "FSU 10 – 14 VT", "at Miami", or the race name. */
    val scoreline: String,
    /** "Q2 4:12", "Sat 3:30 PM · ESPN", "Final". */
    val detail: String,
    /** For a finished game: did the team win. */
    val won: Boolean? = null,
)

/** A team found by the search in settings. [key] is what gets saved ("nfl:28"). */
data class TeamPick(val key: String, val name: String, val sport: String, val logo: String?)

/**
 * Reads Stat Scraps (github.com/Spacegoose02/sports-tracker). Two ways in:
 * - `GET /api/account/teams` with the account's feed key: the teams followed on the site, any sport.
 * - `GET /api/ha/scorecards?items=cfb:259,nfl:28,f1`: no key, teams picked in the launcher's settings.
 * Both are reduced to [TeamScore] rows. Everything here is pure so it can be unit tested.
 */
object Scores {
    private val json = Json { ignoreUnknownKeys = true }

    /** A finished game stays on the card this long, like the site's own scorecards. */
    private const val SHOW_FINAL_FOR_MS = 20L * 60 * 60 * 1000

    fun myTeamsUrl(base: String) = "${base.trimEnd('/')}/api/account/teams"

    fun scorecardsUrl(base: String, items: List<String>) =
        "${base.trimEnd('/')}/api/ha/scorecards?items=" + URLEncoder.encode(items.joinToString(","), "UTF-8")

    fun searchUrl(base: String, query: String) =
        "${base.trimEnd('/')}/api/search?q=" + URLEncoder.encode(query.trim(), "UTF-8")

    /** `/api/account/teams` → rows, in the order the site lists them. */
    fun parseMyTeams(text: String, now: Long = System.currentTimeMillis()): List<TeamScore> {
        val teams = json.parseToJsonElement(text).obj()?.get("teams") as? JsonArray ?: return emptyList()
        return teams.mapNotNull { it.obj() }.map { t ->
            val teamId = t.str("id")
            val sport = t.str("sport") ?: ""
            val live = t.obj("liveGame")
            val next = t.obj("nextGame")
            val last = t.obj("lastGame")
            val lastRecent = last != null && (time(last)?.let { now - it < SHOW_FINAL_FOR_MS } ?: false)
            val (game, state) = when {
                live != null -> live to GameState.LIVE
                lastRecent -> last to GameState.FINAL
                next != null -> next to GameState.UPCOMING
                last != null -> last to GameState.FINAL
                else -> null to GameState.NONE
            }
            val base = TeamScore(
                key = if (teamId != null) "$sport:$teamId" else sport,
                name = t.str("name") ?: t.str("abbreviation") ?: "Team",
                abbreviation = t.str("abbreviation"),
                logo = t.str("logo"),
                record = t.str("record"),
                rank = t.int("rank")?.takeIf { it in 1..25 },
                state = state,
                scoreline = "",
                detail = "",
            )
            if (game == null) return@map base.copy(detail = "No games scheduled")
            val home = game.obj("home")
            val away = game.obj("away")
            val teamIsHome = teamId != null && home?.str("id") == teamId
            val us = if (teamIsHome) home else away
            val them = if (teamIsHome) away else home
            val status = game.obj("status")
            when (state) {
                GameState.UPCOMING -> base.copy(
                    scoreline = matchup(us ?: t, teamIsHome, them),
                    detail = listOfNotNull(status?.str("shortDetail"), game.str("broadcast")).joinToString(" · "),
                )
                else -> base.copy(
                    scoreline = scoreline(away, home),
                    detail = status?.str("shortDetail") ?: if (state == GameState.LIVE) "Live" else "Final",
                    won = if (state == GameState.FINAL) us?.bool("winner") else null,
                )
            }
        }
    }

    /** `/api/ha/scorecards` → rows, in the order of [items]. Items the server couldn't build are left out. */
    fun parseScorecards(text: String, items: List<String>): List<TeamScore> {
        val cards = json.parseToJsonElement(text).obj()?.obj("scorecards") ?: return emptyList()
        return items.mapNotNull { item ->
            val c = cards.obj(item) ?: return@mapNotNull null
            if (c.str("error") != null) return@mapNotNull null
            val state = when (c.str("state")) {
                "IN" -> GameState.LIVE
                "POST" -> GameState.FINAL
                "PRE" -> GameState.UPCOMING
                else -> GameState.NONE
            }
            val event = c.obj("event")
            val team = c.obj("team")
            if (team == null) {
                // A race or fight card: no team, just the event.
                return@mapNotNull TeamScore(
                    key = item,
                    name = c.str("league") ?: c.str("sportLabel") ?: item.uppercase(),
                    abbreviation = null, logo = null, record = null, rank = null,
                    state = state,
                    scoreline = event?.str("shortName") ?: event?.str("name") ?: "",
                    detail = event?.let { eventDetail(it, state) } ?: "Nothing scheduled",
                )
            }
            val opp = c.obj("opponent")
            val teamIsHome = team.str("homeAway") == "home"
            val base = TeamScore(
                key = item,
                name = team.str("name") ?: team.str("abbreviation") ?: item,
                abbreviation = team.str("abbreviation"),
                logo = team.str("logo"),
                record = team.str("record"),
                rank = team.int("rank")?.takeIf { it in 1..25 },
                state = state,
                scoreline = "",
                detail = "",
            )
            when (state) {
                GameState.NONE -> base.copy(detail = "No games scheduled")
                GameState.UPCOMING -> base.copy(scoreline = matchup(team, teamIsHome, opp), detail = eventDetail(event, state))
                else -> base.copy(
                    scoreline = if (teamIsHome) scoreline(opp, team) else scoreline(team, opp),
                    detail = eventDetail(event, state),
                    won = if (state == GameState.FINAL) team.bool("winner") else null,
                )
            }
        }
    }

    fun parseSearch(text: String): List<TeamPick> {
        val teams = json.parseToJsonElement(text).obj()?.get("teams") as? JsonArray ?: return emptyList()
        return teams.mapNotNull { it.obj() }.mapNotNull { t ->
            val id = t.str("id") ?: return@mapNotNull null
            val sport = t.str("sport") ?: return@mapNotNull null
            TeamPick("$sport:$id", t.str("name") ?: id, sport, t.str("logo"))
        }
    }

    /** Live games first, then everything else in the user's order. */
    fun forDisplay(rows: List<TeamScore>): List<TeamScore> =
        rows.filter { it.state == GameState.LIVE } + rows.filter { it.state != GameState.LIVE }

    /** Poll often while something is live, gently otherwise. */
    fun refreshDelayMs(rows: List<TeamScore>): Long =
        if (rows.any { it.state == GameState.LIVE }) 45_000L else 5 * 60_000L

    private fun eventDetail(event: JsonObject?, state: GameState): String {
        if (event == null) return ""
        val detail = event.str("shortDetail") ?: event.str("detail") ?: ""
        val tv = event.str("broadcast")
        return if (state == GameState.UPCOMING && tv != null) listOf(detail, tv).filter { it.isNotEmpty() }.joinToString(" · ")
        else detail
    }

    /** "VT vs MIA" at home, "VT at MIA" away. */
    private fun matchup(team: JsonObject, teamIsHome: Boolean, opponent: JsonObject?): String {
        val us = team.str("abbreviation") ?: team.str("shortName") ?: team.str("name") ?: ""
        val them = opponent?.let { it.str("abbreviation") ?: it.str("shortName") ?: it.str("name") } ?: "TBD"
        return "$us ${if (teamIsHome) "vs" else "at"} $them".trim()
    }

    private fun scoreline(away: JsonObject?, home: JsonObject?): String {
        fun side(o: JsonObject?) = o?.let { it.str("abbreviation") ?: it.str("shortName") } ?: "?"
        fun score(o: JsonObject?) = o?.str("score") ?: "0"
        return "${side(away)} ${score(away)} – ${score(home)} ${side(home)}"
    }

    private fun time(game: JsonObject): Long? =
        game.str("date")?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    private fun JsonElement.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.str(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(name: String): Int? =
        (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() }
    private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull
}
