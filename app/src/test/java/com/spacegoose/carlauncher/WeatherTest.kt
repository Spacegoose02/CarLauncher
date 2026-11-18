package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.integrations.weather.WeatherNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    // Shape of Weather Scout's GET /api/connect/widget (server/src/app.js, widgetSummary).
    private val response = """
        {"place":"Richmond, VA","temp":"52°","feels":"Feels like 47°","label":"Rainy","hiLo":"High 60° · Low 42°",
         "rain":"80% chance of rain","playground":"","tip":"🧥 Rain jacket","hasAlert":true,"alert":"⚠️ Wind Advisory",
         "rockyUrl":"http://x/rocky.png","appUrl":"http://x/","rainSoon":"Rain in 15 min","rainText":null,
         "icon":"rain","isDay":true,"updatedAt":"2026-11-02T18:40:00Z","somethingNew":1}
    """.trimIndent()

    @Test fun readsTheWidgetSummary() {
        val w = WeatherNow.parse(response)
        assertEquals("52°", w.temp)
        assertEquals("Rain in 15 min", w.rainSoon)
        assertEquals("", w.rainText)
        assertTrue(w.hasAlert)
        assertEquals("🌧️", w.emoji)
    }

    @Test fun clearAtNightIsTheMoon() {
        assertEquals("🌙", WeatherNow(icon = "clear", isDay = false).emoji)
    }

    @Test fun urlRoundsTheCarsPosition() {
        assertEquals(
            "https://wx.sgiu.net/api/connect/widget?units=imperial&lat=37.54&lon=-77.44",
            WeatherNow.url("https://wx.sgiu.net/", metric = false, lat = 37.5412, lon = -77.4361),
        )
        assertEquals("http://h:8080/api/connect/widget?units=metric", WeatherNow.url("http://h:8080", metric = true))
    }
}
