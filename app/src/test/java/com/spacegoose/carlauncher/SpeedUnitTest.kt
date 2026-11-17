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
}
