package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.layout.DashboardLayout
import com.spacegoose.carlauncher.layout.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutTest {
    @Test fun builtInsSurviveJsonRoundTrip() {
        DashboardLayout.BuiltIn.forEach { assertEquals(it, DashboardLayout.fromJson(it.toJson())) }
    }

    @Test fun mapSide() {
        assertTrue(DashboardLayout.MapLeft.mapOnLeft)
        assertFalse(DashboardLayout.MapRight.mapOnLeft)
        assertFalse(DashboardLayout.NoMap.hasMap)
    }

    @Test fun droppingTheMapRemovesItsColumn() {
        val l = DashboardLayout.MapRight.without { it == WidgetType.MAP }
        assertEquals(1, l.columns.size)
        assertFalse(l.hasMap)
    }

    @Test fun unknownIdFallsBack() {
        assertEquals(DashboardLayout.MapRight, DashboardLayout.byId("nope"))
    }
}
