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

    @Test fun widthAroundTheMap() {
        assertEquals(0.42f to 0f, DashboardLayout.MapRight.widthAroundMap())
        assertEquals(0f to 0.42f, DashboardLayout.MapLeft.widthAroundMap())
        assertEquals(null, DashboardLayout.NoMap.widthAroundMap())
        val (left, right) = DashboardLayout.WideShort.widthAroundMap()!!
        assertEquals(0.22f, left, 0.001f)
        assertEquals(0.30f, right, 0.001f)
    }

    @Test fun wideShortDropsCarDataWhenNotSetUp() {
        val l = DashboardLayout.WideShort.without { it == WidgetType.CAR_DATA }
        assertEquals(3, l.columns.size)
        assertEquals(2, l.columns.first().rows.size)
    }

    @Test fun unknownIdFallsBack() {
        assertEquals(DashboardLayout.MapRight, DashboardLayout.byId("nope"))
    }
}
