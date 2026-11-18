package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.vehicle.obd.ObdPid
import com.spacegoose.carlauncher.vehicle.obd.ObdProtocol
import com.spacegoose.carlauncher.vehicle.obd.ObdReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdProtocolTest {
    @Test fun rpm() {
        // 0x1A 0xF8 = 6904 / 4 = 1726 rpm
        assertEquals(1726, ObdProtocol.apply(ObdReading(), ObdPid.RPM, "410C1AF8\r\r>").rpm)
    }

    @Test fun answerWithSpacesAndSearching() {
        val r = ObdProtocol.apply(ObdReading(), ObdPid.COOLANT, "SEARCHING...\r41 05 7B \r\r>")
        assertEquals(0x7B - 40, r.coolantC)
    }

    @Test fun fuelAndSpeed() {
        assertEquals(50, ObdProtocol.apply(ObdReading(), ObdPid.FUEL, "412F80>").fuelPercent)
        assertEquals(88, ObdProtocol.apply(ObdReading(), ObdPid.SPEED, "410D58>").speedKmh)
    }

    @Test fun noDataLeavesTheReadingAlone() {
        val before = ObdReading(rpm = 900)
        assertEquals(before, ObdProtocol.apply(before, ObdPid.RPM, "NO DATA\r\r>"))
        assertNull(ObdProtocol.dataBytes("?\r>", ObdPid.FUEL))
    }

    @Test fun answerToAnotherPidIsIgnored() {
        assertNull(ObdProtocol.dataBytes("410D58>", ObdPid.RPM))
    }

    @Test fun shortAnswerIsIgnored() {
        assertNull(ObdProtocol.dataBytes("410C1A>", ObdPid.RPM))
    }

    @Test fun voltage() {
        assertEquals(12.6f, ObdProtocol.parseVoltage("12.6V\r\r>")!!, 0.001f)
        assertEquals(14f, ObdProtocol.parseVoltage("14V>")!!, 0.001f)
        assertNull(ObdProtocol.parseVoltage("?>"))
    }

    @Test fun noConnection() {
        assertTrue(ObdProtocol.isNoConnection("SEARCHING...\rUNABLE TO CONNECT\r>"))
        assertTrue(ObdProtocol.isNoConnection("BUS INIT: ...ERROR\r>"))
        assertFalse(ObdProtocol.isNoConnection("410C1AF8>"))
    }
}
