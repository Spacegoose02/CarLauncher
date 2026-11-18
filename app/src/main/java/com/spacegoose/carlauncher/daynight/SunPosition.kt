package com.spacegoose.carlauncher.daynight

import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Whether it's day or night where the car is, for the automatic theme. Pure, so it can be unit tested. */
object SunPosition {
    /** The sun's centre is this far below the horizon at sunrise and sunset (refraction plus its radius). */
    private const val SUNSET_ELEVATION = -0.833

    /** Without a location: day from 7:00 to 19:00 local time. */
    private const val FALLBACK_DAY_START_HOUR = 7
    private const val FALLBACK_NIGHT_START_HOUR = 19

    /** The sun's elevation above the horizon in degrees (low-precision NOAA formulas, good to a fraction of a degree). */
    fun elevation(lat: Double, lon: Double, epochMillis: Long): Double {
        val d = epochMillis / 86_400_000.0 + 2440587.5 - 2451545.0 // days since J2000
        val g = Math.toRadians(normalize(357.529 + 0.98560028 * d))
        val q = normalize(280.459 + 0.98564736 * d)
        val l = Math.toRadians(normalize(q + 1.915 * sin(g) + 0.020 * sin(2 * g)))
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = Math.toDegrees(atan2(cos(e) * sin(l), cos(l)))
        val dec = asin(sin(e) * sin(l))
        val gmstDegrees = normalize((18.697374558 + 24.06570982441908 * d) * 15)
        val hourAngle = Math.toRadians(normalize(gmstDegrees + lon - ra))
        val latR = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(latR) * sin(dec) + cos(latR) * cos(dec) * cos(hourAngle)))
    }

    fun isNight(lat: Double?, lon: Double?, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (lat == null || lon == null) {
            val hour = Instant.ofEpochMilli(epochMillis).atZone(zone).hour
            return hour < FALLBACK_DAY_START_HOUR || hour >= FALLBACK_NIGHT_START_HOUR
        }
        return elevation(lat, lon, epochMillis) < SUNSET_ELEVATION
    }

    private fun normalize(degrees: Double): Double = ((degrees % 360) + 360) % 360
}
