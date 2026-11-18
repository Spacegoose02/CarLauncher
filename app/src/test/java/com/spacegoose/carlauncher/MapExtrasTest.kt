package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.maps.osm.CameraAlerts
import com.spacegoose.carlauncher.maps.osm.CameraKind
import com.spacegoose.carlauncher.maps.osm.Geo
import com.spacegoose.carlauncher.maps.osm.HeadingTracker
import com.spacegoose.carlauncher.maps.osm.MapRegionListing
import com.spacegoose.carlauncher.maps.osm.OverpassCameras
import com.spacegoose.carlauncher.maps.osm.SpeedCamera
import com.spacegoose.carlauncher.vehicle.GeoPoint
import com.spacegoose.carlauncher.vehicle.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapExtrasTest {
    private val here = GeoPoint(40.0, -75.0)
    /** About 300 m due north of [here]. */
    private val north = GeoPoint(40.0027, -75.0)

    @Test fun distanceAndBearing() {
        assertEquals(300.0, Geo.distanceMeters(here, north), 5.0)
        assertEquals(0.0, Geo.bearing(here, north), 0.5)
        assertEquals(180.0, Geo.bearing(north, here), 0.5)
        assertEquals(20.0, Geo.angleBetween(350.0, 10.0), 1e-9)
    }

    @Test fun formatsDistances() {
        assertEquals("500 ft", Geo.formatDistance(152.4, SpeedUnit.MPH))
        assertEquals("1.0 mi", Geo.formatDistance(1609.344, SpeedUnit.MPH))
        assertEquals("300 m", Geo.formatDistance(301.0, SpeedUnit.KMH))
    }

    @Test fun warnsOnlyForCamerasAheadAndFacingUs() {
        val cam = SpeedCamera(1, north.lat, north.lon, CameraKind.SPEED)
        // Heading north: the camera is ahead.
        assertNotNull(CameraAlerts.next(here, 0.0, listOf(cam)))
        // Heading south: it's behind.
        assertNull(CameraAlerts.next(here, 180.0, listOf(cam)))
        // Standing still (no heading yet): no alert.
        assertNull(CameraAlerts.next(here, null, listOf(cam)))
        // Camera checks southbound traffic only.
        assertNull(CameraAlerts.next(here, 0.0, listOf(cam.copy(directions = listOf(180.0)))))
        assertNotNull(CameraAlerts.next(here, 0.0, listOf(cam.copy(directions = listOf(0.0, 180.0)))))
        // Too far away.
        val far = SpeedCamera(2, 40.02, -75.0, CameraKind.SPEED)
        assertNull(CameraAlerts.next(here, 0.0, listOf(far)))
    }

    @Test fun picksTheNearestCamera() {
        val near = SpeedCamera(1, 40.001, -75.0, CameraKind.RED_LIGHT)
        val further = SpeedCamera(2, 40.003, -75.0, CameraKind.SPEED)
        assertEquals(1L, CameraAlerts.next(here, 0.0, listOf(further, near))!!.camera.id)
    }

    @Test fun headingIgnoresJitter() {
        val t = HeadingTracker(minMeters = 15.0)
        assertNull(t.update(here))
        assertNull(t.update(GeoPoint(40.00001, -75.0)))
        assertEquals(0.0, t.update(GeoPoint(40.0003, -75.0))!!, 1.0)
    }

    @Test fun parsesOverpassCameras() {
        val body = """
            {"elements":[
              {"type":"node","id":10,"lat":40.1,"lon":-75.1,"tags":{"highway":"speed_camera","maxspeed":"35 mph","direction":"NE"}},
              {"type":"node","id":11,"lat":40.2,"lon":-75.2,"tags":{"enforcement":"traffic_signals"}},
              {"type":"node","id":12,"lat":40.3,"lon":-75.3},
              {"type":"node","id":13,"lat":40.4,"lon":-75.4,"tags":{"amenity":"bench"}},
              {"type":"relation","id":99,"members":[{"type":"node","ref":12,"role":"device"},{"type":"node","ref":13,"role":"from"}],
               "tags":{"type":"enforcement","enforcement":"maxspeed","maxspeed":"50"}}
            ]}
        """.trimIndent()
        val cams = OverpassCameras.parse(body).associateBy { it.id }
        assertEquals(setOf(10L, 11L, 12L), cams.keys)
        assertEquals(CameraKind.SPEED, cams[10]!!.kind)
        assertEquals(listOf(45.0), cams[10]!!.directions)
        assertEquals("35 mph", cams[10]!!.limitLabel())
        assertEquals(CameraKind.RED_LIGHT, cams[11]!!.kind)
        assertEquals("50 km/h", cams[12]!!.limitLabel())
        assertTrue(OverpassCameras.query(here, 1000).contains("around:1000,40.0,-75.0"))
    }

    @Test fun parsesDirections() {
        assertEquals(listOf(90.0, 270.0), OverpassCameras.parseDirections("90;270"))
        assertEquals(listOf(315.0), OverpassCameras.parseDirections("-45"))
        assertEquals(emptyList<Double>(), OverpassCameras.parseDirections("forward"))
    }

    @Test fun parsesServerListing() {
        val html = """
            <pre><a href="?C=N;O=D">Name</a>   <a href="?C=M;O=A">Last modified</a>  <a href="?C=S;O=A">Size</a><hr>
            <a href="/maps/v5/north-america/">Parent Directory</a>                             -
            <a href="us/">us/</a>                     2025-10-28 09:03    -
            <a href="canada.map">canada.map</a>              2025-10-28 09:03  1.2G
            <a href="new-york.map">new-york.map</a>          2025-10-28 09:03  355M
            <a href="new-york.map.md5">new-york.map.md5</a>  2025-10-28 09:03   45
            </pre>
        """.trimIndent()
        val rows = MapRegionListing.parse(html, "north-america/")
        assertEquals(listOf("US", "Canada", "New York"), rows.map { it.name })
        assertTrue(rows[0].isFolder)
        assertEquals("north-america/us/", rows[0].path)
        assertEquals("1.2 GB", rows[1].size)
        assertEquals("355 MB", rows[2].size)
        assertEquals("north-america/new-york.map", rows[2].path)
    }
}
