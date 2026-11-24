package com.spacegoose.carlauncher.headsup

import com.spacegoose.carlauncher.integrations.sports.GameState
import com.spacegoose.carlauncher.integrations.sports.TeamScore
import com.spacegoose.carlauncher.integrations.weather.WeatherNow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class HeadsUpKind { WEATHER_ALERT, RAIN, LIVE_SCORE, DRIVE_TIME }

/** One chip on the heads-up strip: a glance-sized fact that matters right now. */
data class HeadsUpItem(
    val kind: HeadsUpKind,
    val emoji: String,
    val title: String,
    val detail: String,
    /** Shown in a warning colour. */
    val urgent: Boolean = false,
    /** Stable id, so chips animate in and out on their own. */
    val id: String = "$kind:$title",
)

/**
 * Picks what the heads-up strip shows, most important first: weather alerts, rain on the way, games
 * your teams are playing right now, then drive times home and to work. Nothing relevant, nothing shown.
 */
object HeadsUp {
    const val MAX_ITEMS = 4

    /** Drive times older than this are dropped rather than shown stale. */
    const val DRIVE_TIME_FRESH_MS = 15 * 60_000L

    fun pick(
        weather: WeatherNow?,
        scores: List<TeamScore>,
        driveTimes: List<DriveTime>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        use24h: Boolean = false,
    ): List<HeadsUpItem> {
        val items = mutableListOf<HeadsUpItem>()
        if (weather != null) {
            if (weather.hasAlert && weather.alert.isNotBlank()) {
                items += HeadsUpItem(HeadsUpKind.WEATHER_ALERT, "⚠️", "Weather alert", weather.alert.trim(), urgent = true)
            }
            val soon = weather.rainSoon.trim()
            if (soon.isNotEmpty()) {
                items += HeadsUpItem(HeadsUpKind.RAIN, rainEmoji(weather.icon), soon, weather.rainText.trim().ifEmpty { weather.place })
            }
        }
        scores.filter { it.state == GameState.LIVE }.forEach { s ->
            items += HeadsUpItem(
                HeadsUpKind.LIVE_SCORE, "🏟️",
                s.scoreline.ifBlank { s.abbreviation ?: s.name },
                s.detail.ifBlank { "Live" },
                id = "${HeadsUpKind.LIVE_SCORE}:${s.key}",
            )
        }
        val clock = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a")
        driveTimes.filter { now - it.fetchedAt < DRIVE_TIME_FRESH_MS }.sortedBy { it.place.ordinal }.forEach { d ->
            val arrive = Instant.ofEpochMilli(now + (d.seconds * 1000).toLong()).atZone(zone).format(clock)
            items += HeadsUpItem(
                HeadsUpKind.DRIVE_TIME, d.place.emoji,
                "${d.place.label} ${d.minutes} min",
                "Arrive $arrive",
                id = "${HeadsUpKind.DRIVE_TIME}:${d.place}",
            )
        }
        return items.take(MAX_ITEMS)
    }

    private fun rainEmoji(icon: String) = when (icon) {
        "snow" -> "❄️"
        "sleet" -> "🌨️"
        "thunder" -> "⛈️"
        else -> "🌧️"
    }
}
