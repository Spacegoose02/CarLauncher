package com.spacegoose.carlauncher.maps.osm

import com.spacegoose.carlauncher.vehicle.GeoPoint
import com.spacegoose.carlauncher.vehicle.SpeedUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class CameraKind(val label: String) {
    SPEED("Speed camera"),
    RED_LIGHT("Red light camera"),
    AVERAGE_SPEED("Average speed check"),
}

/** A traffic enforcement camera mapped in OpenStreetMap. */
@Serializable
data class SpeedCamera(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val kind: CameraKind,
    /** The limit as mapped, e.g. "35 mph" or "50" (km/h when there is no unit). */
    val maxSpeed: String? = null,
    /** Compass directions of the traffic it checks, when mapped. Empty means any direction. */
    val directions: List<Double> = emptyList(),
) {
    val point get() = GeoPoint(lat, lon)

    fun limitLabel(): String? {
        val raw = maxSpeed?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (raw.all { it.isDigit() }) "$raw km/h" else raw
    }
}

/** Cameras around a point, saved so they still work with no signal. */
@Serializable
data class CameraArea(val lat: Double, val lon: Double, val fetchedAt: Long, val cameras: List<SpeedCamera>)

data class CameraAlert(val camera: SpeedCamera, val meters: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Compass bearing from [a] to [b], 0 to 360. */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** The smaller angle between two compass directions, 0 to 180. */
    fun angleBetween(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    /** "500 ft" / "0.3 mi" or "300 m" / "1.2 km", rounded the way road signs are. */
    fun formatDistance(meters: Double, unit: SpeedUnit): String = when (unit) {
        SpeedUnit.MPH -> {
            val feet = meters * 3.28084
            if (feet < 1000) "${((feet / 50).roundToInt() * 50).coerceAtLeast(50)} ft"
            else "%.1f mi".format(meters / 1609.344)
        }
        SpeedUnit.KMH ->
            if (meters < 1000) "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} m"
            else "%.1f km".format(meters / 1000)
    }
}

/**
 * Works out the direction of travel from GPS fixes. Fixes closer together than [minMeters] are ignored,
 * so GPS jitter at a red light doesn't spin the heading around.
 */
class HeadingTracker(private val minMeters: Double = 15.0) {
    private var anchor: GeoPoint? = null
    var heading: Double? = null
        private set

    fun update(p: GeoPoint): Double? {
        val a = anchor
        if (a == null) {
            anchor = p
        } else if (Geo.distanceMeters(a, p) >= minMeters) {
            heading = Geo.bearing(a, p)
            anchor = p
        }
        return heading
    }
}

object CameraAlerts {
    /** How far ahead to warn. */
    const val WARN_METERS = 700.0
    /** How far off straight ahead a camera can be and still count as "ahead" (roads bend). */
    private const val AHEAD_DEGREES = 40.0
    /** How far the camera's mapped direction can differ from ours. */
    private const val FACING_DEGREES = 60.0

    /** The nearest camera ahead of the car that checks traffic going our way, or null. */
    fun next(position: GeoPoint, heading: Double?, cameras: List<SpeedCamera>, warnMeters: Double = WARN_METERS): CameraAlert? {
        heading ?: return null
        return cameras.asSequence()
            .map { CameraAlert(it, Geo.distanceMeters(position, it.point)) }
            .filter { it.meters <= warnMeters }
            .filter { a -> a.meters < 30 || Geo.angleBetween(heading, Geo.bearing(position, a.camera.point)) <= AHEAD_DEGREES }
            .filter { a -> a.camera.directions.isEmpty() || a.camera.directions.any { Geo.angleBetween(it, heading) <= FACING_DEGREES } }
            .minByOrNull { it.meters }
    }
}

/** Turns an Overpass API answer into cameras. */
object OverpassCameras {
    private val json = Json { ignoreUnknownKeys = true }

    fun query(center: GeoPoint, radiusMeters: Int): String {
        val around = "(around:$radiusMeters,${center.lat},${center.lon})"
        return """
            [out:json][timeout:25];
            rel["type"="enforcement"]["enforcement"~"^(maxspeed|traffic_signals|average_speed)$"]$around->.e;
            (
              node["highway"="speed_camera"]$around;
              node(r.e:"device");
            );
            out body;
            .e out body;
        """.trimIndent()
    }

    fun parse(body: String): List<SpeedCamera> {
        val elements = json.parseToJsonElement(body).jsonObject["elements"]?.jsonArray ?: return emptyList()
        val nodes = mutableMapOf<Long, JsonObject>()
        val relations = mutableListOf<JsonObject>()
        for (e in elements) {
            val o = e.jsonObject
            when (o["type"]?.jsonPrimitive?.contentOrNull) {
                "node" -> o["id"]?.jsonPrimitive?.longOrNull?.let { nodes[it] = o }
                "relation" -> relations += o
            }
        }
        // What each enforcement relation says about its camera nodes.
        val fromRelations = mutableMapOf<Long, Pair<CameraKind, String?>>()
        for (r in relations) {
            val tags = r.tags()
            val kind = kindOf(tags["enforcement"]) ?: continue
            val members = r["members"] as? JsonArray ?: continue
            for (m in members) {
                val mo = m.jsonObject
                if (mo["type"]?.jsonPrimitive?.contentOrNull != "node") continue
                if (mo["role"]?.jsonPrimitive?.contentOrNull != "device") continue
                val ref = mo["ref"]?.jsonPrimitive?.longOrNull ?: continue
                fromRelations[ref] = kind to tags["maxspeed"]
            }
        }
        return nodes.mapNotNull { (id, n) ->
            val lat = n["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = n["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val tags = n.tags()
            val rel = fromRelations[id]
            val kind = rel?.first ?: kindOf(tags["enforcement"])
                ?: if (tags["highway"] == "speed_camera") CameraKind.SPEED else return@mapNotNull null
            SpeedCamera(
                id = id,
                lat = lat,
                lon = lon,
                kind = kind,
                maxSpeed = tags["maxspeed"] ?: rel?.second,
                directions = parseDirections(tags["direction"] ?: tags["camera:direction"]),
            )
        }
    }

    private fun kindOf(enforcement: String?): CameraKind? = when (enforcement) {
        "maxspeed" -> CameraKind.SPEED
        "traffic_signals" -> CameraKind.RED_LIGHT
        "average_speed" -> CameraKind.AVERAGE_SPEED
        else -> null
    }

    private fun JsonObject.tags(): Map<String, String> =
        (this["tags"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() } ?: emptyMap()

    private val cardinal = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    ).mapIndexed { i, s -> s to i * 22.5 }.toMap()

    /** OSM "direction": degrees ("90"), compass points ("NE"), or several ("90;270"). "forward"/"both" are unknown here. */
    fun parseDirections(raw: String?): List<Double> =
        raw?.split(';')?.mapNotNull { part ->
            val p = part.trim().uppercase()
            p.toDoubleOrNull()?.let { ((it % 360) + 360) % 360 } ?: cardinal[p]
        } ?: emptyList()
}
