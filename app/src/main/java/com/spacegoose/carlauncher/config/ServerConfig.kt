package com.spacegoose.carlauncher.config

import com.spacegoose.carlauncher.layout.DashboardLayout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A theme's colours as "#RRGGBB", in the same JSON the config server stores. */
@Serializable
data class ThemeColors(
    val accent: String,
    val onAccent: String = "#000000",
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val text: String,
    val textSecondary: String,
)

@Serializable
data class ThemeDefinition(val id: String, val name: String, val dark: Boolean = true, val colors: ThemeColors)

/** What the launcher got from the config server: the layouts and themes it offers, plus what it had to skip. */
@Serializable
data class ServerConfig(
    val layouts: List<DashboardLayout> = emptyList(),
    val themes: List<ThemeDefinition> = emptyList(),
    val skipped: Int = 0,
    /** When it was fetched, in epoch millis; 0 when never. */
    val fetchedAt: Long = 0,
) {
    fun theme(id: String): ThemeDefinition? = themes.firstOrNull { it.id == id }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * Reads the server's `/api/config` answer. Each layout and theme is read on its own, so one the
         * launcher doesn't understand (say, a module from a newer server) is skipped instead of failing all.
         */
        fun parseServerResponse(text: String, fetchedAt: Long = 0): ServerConfig {
            val root = json.parseToJsonElement(text).jsonObject
            var skipped = 0
            fun <T> each(key: String, read: (JsonObject) -> T?): List<T> =
                (root[key] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { e ->
                    runCatching { read(e.jsonObject) }.getOrNull().also { if (it == null) skipped++ }
                }
            val layouts = each("layouts") { o ->
                json.decodeFromJsonElement(DashboardLayout.serializer(), o).takeIf { it.isUsable() }
            }
            val themes = each("themes") { o ->
                json.decodeFromJsonElement(ThemeDefinition.serializer(), o).takeIf { it.isUsable() }
            }
            return ServerConfig(layouts.distinctBy { it.id }, themes.distinctBy { it.id }, skipped, fetchedAt)
        }

        /** The saved copy written by [toJson]. */
        fun fromCache(text: String): ServerConfig = json.decodeFromString(serializer(), text)

        /**
         * Turns what the user typed into a base URL: "192.168.1.20:8080" → "http://192.168.1.20:8080".
         * Returns null when it can't be a URL.
         */
        fun normalizeUrl(input: String): String? {
            val s = input.trim()
            if (s.isEmpty()) return null
            val scheme = if (s.contains("://")) s.substringBefore("://").lowercase() else "http"
            if (scheme != "http" && scheme != "https") return null
            val rest = (if (s.contains("://")) s.substringAfter("://") else s).trimEnd('/')
            val host = rest.substringBefore('/').substringBefore(':')
            if (host.isBlank() || rest.any { it.isWhitespace() }) return null
            return "$scheme://$rest"
        }

        /** Parses "#RRGGBB" (or "#AARRGGBB") to an ARGB long, or null. */
        fun parseColor(hex: String): Long? {
            val h = hex.removePrefix("#")
            val v = h.toLongOrNull(16) ?: return null
            return when (h.length) {
                6 -> 0xFF000000L or v
                8 -> v
                else -> null
            }
        }
    }
}

private fun DashboardLayout.isUsable(): Boolean =
    id.isNotBlank() && columns.isNotEmpty() &&
        columns.all { c -> c.weight > 0 && c.rows.isNotEmpty() && c.rows.all { r -> r.weight > 0 && r.widgets.isNotEmpty() } }

private fun ThemeDefinition.isUsable(): Boolean = id.isNotBlank() && with(colors) {
    listOf(accent, onAccent, background, surface, surfaceVariant, text, textSecondary).all { ServerConfig.parseColor(it) != null }
}
