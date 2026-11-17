package com.spacegoose.carlauncher.vehicle

import kotlinx.coroutines.flow.StateFlow

enum class SpeedUnit(val label: String, private val perMeterPerSecond: Double) {
    MPH("mph", 2.2369362920544),
    KMH("km/h", 3.6);

    fun fromMetersPerSecond(metersPerSecond: Float): Int =
        Math.round(metersPerSecond.coerceAtLeast(0f) * perMeterPerSecond).toInt()
}

data class SpeedReading(val metersPerSecond: Float, val timeMillis: Long)

data class GeoPoint(val lat: Double, val lon: Double)

enum class SourceStatus { NEEDS_PERMISSION, UNAVAILABLE, WAITING, ACTIVE }

/**
 * Where vehicle data comes from. GPS now; an OBD2 adapter later can implement the same interface
 * (and add rpm, coolant temperature, ...) without the dashboard changing.
 */
interface VehicleDataSource {
    val speed: StateFlow<SpeedReading?>
    /** Where the car is, for the live map. Sources without a position (OBD2) leave it null. */
    val position: StateFlow<GeoPoint?>
    val status: StateFlow<SourceStatus>
    fun start()
    fun stop()
}
