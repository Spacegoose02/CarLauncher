package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.automation.CarStartRules
import com.spacegoose.carlauncher.vehicle.DrivingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingStateTest {
    private fun kmh(v: Float) = v / 3.6f

    @Test fun startsMovingAboveThreshold() {
        val d = DrivingState()
        assertFalse(d.update(kmh(8f), 0))
        assertTrue(d.update(kmh(20f), 1_000))
    }

    @Test fun staysOnAtATrafficLight() {
        val d = DrivingState()
        d.update(kmh(50f), 0)
        assertTrue(d.update(0f, 10_000))
        assertTrue(d.update(0f, 40_000))
        assertTrue(d.update(kmh(30f), 50_000))
        assertTrue(d.update(0f, 60_000))
        assertTrue(d.update(0f, 100_000))
    }

    @Test fun switchesOffAfterAMinuteStopped() {
        val d = DrivingState()
        d.update(kmh(50f), 0)
        d.update(0f, 10_000)
        assertFalse(d.update(null, 10_000 + DrivingState.STOP_HOLD_MS))
    }

    @Test fun carStartTiming() {
        assertFalse(CarStartRules.longEnough(0, 10_000_000))
        assertFalse(CarStartRules.longEnough(1_000_000, 1_000_000 + 30_000))
        assertTrue(CarStartRules.longEnough(1_000_000, 1_000_000 + CarStartRules.MIN_OFF_MS))
        assertTrue(CarStartRules.notTooSoon(0, 5))
        assertFalse(CarStartRules.notTooSoon(1_000_000, 1_000_000 + 10_000))
        assertTrue(CarStartRules.notTooSoon(5_000_000, 1_000)) // the clock went back (no RTC on some head units)
    }
}
