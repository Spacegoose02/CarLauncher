package com.spacegoose.carlauncher.integrations.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.Locale

/**
 * Weather Scout's ready-to-show summary (`GET /api/connect/widget`, the same call its own Android widget
 * makes). The strings come pre-formatted from the server ("52°", "High 60° · Low 42°", "Rain in 15 min").
 */
@Serializable
data class WeatherNow(
    val place: String = "",
    val temp: String = "--",
    val feels: String = "",
    val label: String = "",
    val hiLo: String = "",
    val rain: String = "",
    val rainSoon: String = "",
    val rainText: String = "",
    val hasAlert: Boolean = false,
    val alert: String = "",
    /** clear, partly-cloudy, cloudy, fog, drizzle, rain, sleet, snow or thunder. */
    val icon: String = "",
    val isDay: Boolean = true,
    val updatedAt: String = "",
) {
    /** An emoji for [icon], so the card needs no image assets. */
    val emoji: String
        get() = when (icon) {
            "clear" -> if (isDay) "☀️" else "🌙"
            "partly-cloudy" -> if (isDay) "⛅" else "☁️"
            "cloudy" -> "☁️"
            "fog" -> "🌫️"
            "drizzle" -> "🌦️"
            "rain" -> "🌧️"
            "sleet" -> "🌨️"
            "snow" -> "❄️"
            "thunder" -> "⛈️"
            else -> "🌡️"
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

        fun parse(text: String): WeatherNow = json.decodeFromString(serializer(), text)

        fun toJson(w: WeatherNow): String = json.encodeToString(serializer(), w)

        /**
         * The request URL. [lat]/[lon] are rounded to about a kilometre: plenty for a forecast, and it lets
         * the server's cache work while driving. Without them the server uses its home location.
         */
        fun url(base: String, metric: Boolean, lat: Double? = null, lon: Double? = null): String = buildString {
            append(base.trimEnd('/'))
            append("/api/connect/widget?units=")
            append(if (metric) "metric" else "imperial")
            if (lat != null && lon != null) {
                append("&lat=").append(URLEncoder.encode(String.format(Locale.US, "%.2f", lat), "UTF-8"))
                append("&lon=").append(URLEncoder.encode(String.format(Locale.US, "%.2f", lon), "UTF-8"))
            }
        }
    }
}
