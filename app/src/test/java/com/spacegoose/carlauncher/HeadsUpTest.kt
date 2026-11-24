package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.headsup.DriveTime
import com.spacegoose.carlauncher.headsup.DriveTimes
import com.spacegoose.carlauncher.headsup.HeadsUp
import com.spacegoose.carlauncher.headsup.HeadsUpKind
import com.spacegoose.carlauncher.headsup.Place
import com.spacegoose.carlauncher.integrations.sports.GameState
import com.spacegoose.carlauncher.integrations.sports.TeamScore
import com.spacegoose.carlauncher.integrations.weather.WeatherNow
import com.spacegoose.carlauncher.vehicle.GeoPoint
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsUpTest {
    private val now = 1_700_000_000_000L // 22:13 UTC

    private fun team(state: GameState, key: String = "cfb:259") = TeamScore(
        key = key, name = "Virginia Tech", abbreviation = "VT", logo = null, record = null, rank = null,
        state = state, scoreline = "FSU 10 – 14 VT", detail = "Q2 4:12",
    )

    private fun pick(weather: WeatherNow? = null, scores: List<TeamScore> = emptyList(), drives: List<DriveTime> = emptyList()) =
        HeadsUp.pick(weather, scores, drives, now, ZoneOffset.UTC, use24h = true)

    @Test fun nothingRelevantShowsNothing() {
        assertTrue(pick(WeatherNow(temp = "52°"), listOf(team(GameState.UPCOMING), team(GameState.FINAL))).isEmpty())
    }

    @Test fun alertComesFirstAndIsUrgent() {
        val items = pick(
            WeatherNow(hasAlert = true, alert = "Flood watch", rainSoon = "Rain in 15 min", icon = "rain"),
            listOf(team(GameState.LIVE)),
        )
        assertEquals(listOf(HeadsUpKind.WEATHER_ALERT, HeadsUpKind.RAIN, HeadsUpKind.LIVE_SCORE), items.map { it.kind })
        assertTrue(items[0].urgent)
        assertEquals("Rain in 15 min", items[1].title)
        assertEquals("FSU 10 – 14 VT", items[2].title)
        assertEquals("Q2 4:12", items[2].detail)
    }

    @Test fun driveTimeShowsMinutesAndArrival() {
        val item = pick(drives = listOf(DriveTime(Place.HOME, 1080.0, 15_000.0, now))).single()
        assertEquals("Home 18 min", item.title)
        assertEquals("Arrive 22:31", item.detail)
    }

    @Test fun staleDriveTimesAreDropped() {
        assertTrue(pick(drives = listOf(DriveTime(Place.WORK, 600.0, 9_000.0, now - HeadsUp.DRIVE_TIME_FRESH_MS))).isEmpty())
    }

    @Test fun neverMoreThanTheLimit() {
        val live = (1..6).map { team(GameState.LIVE, key = "nfl:$it") }
        assertEquals(HeadsUp.MAX_ITEMS, pick(scores = live).size)
    }

    @Test fun routerUrlIsLonLat() {
        val url = DriveTimes.url(GeoPoint(37.2296, -80.4139), GeoPoint(37.5407, -77.4360), base = "https://r.test/")
        assertEquals(
            "https://r.test/route/v1/driving/-80.41390,37.22960;-77.43600,37.54070?overview=false&alternatives=false&steps=false",
            url,
        )
    }

    @Test fun parsesRouterAnswer() {
        val ok = """{"code":"Ok","routes":[{"duration":1234.5,"distance":20500.2,"legs":[]}],"waypoints":[]}"""
        assertEquals(1234.5 to 20500.2, DriveTimes.parse(ok))
        assertNull(DriveTimes.parse("""{"code":"NoRoute","routes":[]}"""))
    }

    @Test fun placesRoundTrip() {
        val p = GeoPoint(37.229612, -80.413940)
        assertEquals(p, DriveTimes.decode(DriveTimes.encode(p)))
        assertNull(DriveTimes.decode(""))
        assertNull(DriveTimes.decode("100,0"))
        assertEquals("", DriveTimes.encode(null))
    }

    @Test fun distanceIsRoughlyRight() {
        // Blacksburg to Richmond is about 263 km in a straight line.
        val d = DriveTimes.distanceMeters(GeoPoint(37.2296, -80.4139), GeoPoint(37.5407, -77.4360))
        assertTrue(d in 255_000.0..270_000.0)
    }
}
