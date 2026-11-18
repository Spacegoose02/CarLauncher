package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.daynight.SunPosition
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunPositionTest {
    private fun at(iso: String) = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    // Denver, 21 June 2026: sunrise about 05:32, sunset about 20:31 local (MDT).
    private val lat = 39.74
    private val lon = -104.99

    @Test fun noonIsDay() = assertFalse(SunPosition.isNight(lat, lon, at("2026-06-21T12:00:00-06:00")))

    @Test fun midnightIsNight() = assertTrue(SunPosition.isNight(lat, lon, at("2026-06-21T23:59:00-06:00")))

    @Test fun switchesAroundSunset() {
        assertFalse(SunPosition.isNight(lat, lon, at("2026-06-21T20:15:00-06:00")))
        assertTrue(SunPosition.isNight(lat, lon, at("2026-06-21T20:50:00-06:00")))
    }

    @Test fun switchesAroundSunrise() {
        assertTrue(SunPosition.isNight(lat, lon, at("2026-06-21T05:15:00-06:00")))
        assertFalse(SunPosition.isNight(lat, lon, at("2026-06-21T05:50:00-06:00")))
    }

    @Test fun noonElevationMatchesTheSolstice() {
        // At solar noon on the June solstice the sun is 90 - 39.74 + 23.44 = 73.7 degrees up.
        assertEquals(73.7, SunPosition.elevation(lat, lon, at("2026-06-21T13:00:00-06:00")), 0.6)
    }

    @Test fun withoutLocationUsesTheClock() {
        val zone = ZoneId.of("America/Denver")
        assertFalse(SunPosition.isNight(null, null, at("2026-01-10T12:00:00-07:00"), zone))
        assertTrue(SunPosition.isNight(null, null, at("2026-01-10T21:00:00-07:00"), zone))
        assertTrue(SunPosition.isNight(null, null, at("2026-01-10T06:30:00-07:00"), zone))
    }
}
