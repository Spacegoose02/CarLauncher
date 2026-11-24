package com.spacegoose.carlauncher.headsup

import com.spacegoose.carlauncher.vehicle.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A saved place the heads-up strip gives a drive time to. */
enum class Place(val label: String, val emoji: String) { HOME("Home", "🏠"), WORK("Work", "💼") }

/** How long to [place] from where the car is, by road with typical traffic. */
data class DriveTime(val place: Place, val seconds: Double, val meters: Double, val fetchedAt: Long) {
    val minutes: Int get() = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
}

/**
 * Drive times from the public OSRM router (router.project-osrm.org): no key, light use only, so the
 * launcher asks every few minutes at most. Pure helpers here; the request itself is in [DriveTimeRepository].
 */
object DriveTimes {
    const val ROUTER = "https://router.project-osrm.org"

    /** Closer than this, you're already there: no drive time is shown for that place. */
    const val ARRIVED_METERS = 400.0

    /** A route request from [from] to [to]. OSRM wants lon,lat. */
    fun url(from: GeoPoint, to: GeoPoint, base: String = ROUTER): String =
        String.format(
            Locale.US,
            "%s/route/v1/driving/%.5f,%.5f;%.5f,%.5f?overview=false&alternatives=false&steps=false",
            base.trimEnd('/'), from.lon, from.lat, to.lon, to.lat,
        )

    /** Seconds and metres of the first route, or null when OSRM found none. */
    fun parse(text: String): Pair<Double, Double>? {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: return null
        if ((root["code"] as? kotlinx.serialization.json.JsonPrimitive)?.content != "Ok") return null
        val route = (root["routes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val seconds = route["duration"]?.jsonPrimitive?.doubleOrNull ?: return null
        val meters = route["distance"]?.jsonPrimitive?.doubleOrNull ?: return null
        return seconds to meters
    }

    /** Straight-line distance in metres. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** "lat,lon" as saved in settings. */
    fun encode(p: GeoPoint?): String = p?.let { String.format(Locale.US, "%.6f,%.6f", it.lat, it.lon) } ?: ""

    fun decode(s: String?): GeoPoint? {
        val parts = s?.split(',')?.map { it.trim().toDoubleOrNull() } ?: return null
        if (parts.size != 2) return null
        val lat = parts[0] ?: return null
        val lon = parts[1] ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoPoint(lat, lon)
    }
}
