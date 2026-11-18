package com.spacegoose.carlauncher.vehicle

/**
 * Decides when the car counts as moving, for "bigger buttons while moving". It switches on above
 * [START_KMH] and off only after the car has been below [STOP_KMH] for [STOP_HOLD_MS], so the dashboard
 * doesn't flip back and forth at traffic lights or in slow traffic. Pure, so it can be unit tested.
 */
class DrivingState {
    var moving = false
        private set
    private var slowSince: Long? = null

    /** Feed a reading (or null when there's no speed); returns whether the car counts as moving. */
    fun update(metersPerSecond: Float?, nowMillis: Long): Boolean {
        val kmh = (metersPerSecond ?: 0f) * 3.6f
        when {
            kmh >= START_KMH -> {
                moving = true
                slowSince = null
            }
            moving && kmh < STOP_KMH -> {
                val since = slowSince ?: nowMillis.also { slowSince = it }
                if (nowMillis - since >= STOP_HOLD_MS) {
                    moving = false
                    slowSince = null
                }
            }
            else -> slowSince = null
        }
        return moving
    }

    companion object {
        const val START_KMH = 12f
        const val STOP_KMH = 4f
        const val STOP_HOLD_MS = 60_000L
    }
}
