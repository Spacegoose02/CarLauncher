package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.config.ServerConfig
import com.spacegoose.carlauncher.layout.DashboardLayout
import com.spacegoose.carlauncher.layout.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConfigTest {
    private val response = """
        {
          "formatVersion": 1,
          "layouts": [
            { "id": "media_wide", "name": "Media wide", "columns": [
                { "rows": [ { "widgets": ["MEDIA", "CLOCK"], "weight": 2 } ], "weight": 1 } ] },
            { "id": "future", "name": "From a newer server", "columns": [
                { "rows": [ { "widgets": ["OBD_GAUGE"] } ] } ] },
            { "id": "map_right", "name": "My map right", "columns": [
                { "rows": [ { "widgets": ["MAP"] } ] } ] }
          ],
          "themes": [
            { "id": "synthwave", "name": "Synthwave", "dark": true, "extra": 1, "colors": {
                "accent": "#FF2BD6", "onAccent": "#000000", "background": "#120724", "surface": "#1E0F3A",
                "surfaceVariant": "#2C1652", "text": "#F8E9FF", "textSecondary": "#B9A2D8" } },
            { "id": "broken", "name": "Broken", "colors": { "accent": "pink" } }
          ]
        }
    """.trimIndent()

    @Test fun readsGoodEntriesAndSkipsOnesItCantUse() {
        val c = ServerConfig.parseServerResponse(response)
        assertEquals(listOf("media_wide", "map_right"), c.layouts.map { it.id })
        assertEquals(listOf(WidgetType.MEDIA, WidgetType.CLOCK), c.layouts[0].columns[0].rows[0].widgets)
        assertEquals(2f, c.layouts[0].columns[0].rows[0].weight)
        assertEquals(listOf("synthwave"), c.themes.map { it.id })
        assertEquals(2, c.skipped)
    }

    @Test fun cacheRoundTrip() {
        val c = ServerConfig.parseServerResponse(response, fetchedAt = 42)
        assertEquals(c, ServerConfig.fromCache(c.toJson()))
    }

    @Test fun serverLayoutsReplaceBuiltInsWithTheSameId() {
        val c = ServerConfig.parseServerResponse(response)
        val all = DashboardLayout.available(c.layouts)
        assertEquals(listOf("media_wide", "map_right", "map_left", "no_map"), all.map { it.id })
        assertEquals("My map right", DashboardLayout.resolve("map_right", c.layouts).name)
        assertEquals(DashboardLayout.MapLeft, DashboardLayout.resolve("map_left", c.layouts))
        assertEquals(DashboardLayout.MapRight, DashboardLayout.resolve("gone", emptyList()))
    }

    @Test fun normalizesServerAddresses() {
        assertEquals("http://192.168.1.20:8080", ServerConfig.normalizeUrl(" 192.168.1.20:8080/ "))
        assertEquals("https://cfg.example.org", ServerConfig.normalizeUrl("https://cfg.example.org"))
        assertNull(ServerConfig.normalizeUrl(""))
        assertNull(ServerConfig.normalizeUrl("ftp://x"))
        assertNull(ServerConfig.normalizeUrl("http://"))
    }

    @Test fun parsesColours() {
        assertEquals(0xFFFF2BD6, ServerConfig.parseColor("#FF2BD6"))
        assertEquals(0x80FF2BD6, ServerConfig.parseColor("#80FF2BD6"))
        assertNull(ServerConfig.parseColor("pink"))
    }
}
