package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.vehicle.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUnitTest {
    @Test fun conversions() {
        assertEquals(60, SpeedUnit.MPH.fromMetersPerSecond(26.8224f))
        assertEquals(100, SpeedUnit.KMH.fromMetersPerSecond(27.7778f))
        assertEquals(0, SpeedUnit.MPH.fromMetersPerSecond(-1f))
    }

    @Test fun gaugeFraction() {
        assertEquals(0.5f, SpeedUnit.MPH.gaugeFraction(50), 0.0001f)
        assertEquals(0.5f, SpeedUnit.KMH.gaugeFraction(80), 0.0001f)
        assertEquals(1f, SpeedUnit.MPH.gaugeFraction(140), 0.0001f)
        assertEquals(0f, SpeedUnit.KMH.gaugeFraction(0), 0.0001f)
    }
}
