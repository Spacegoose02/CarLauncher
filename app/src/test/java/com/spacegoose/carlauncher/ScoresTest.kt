package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.integrations.sports.GameState
import com.spacegoose.carlauncher.integrations.sports.Scores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class ScoresTest {
    private val now = OffsetDateTime.parse("2026-11-14T21:00:00Z").toInstant().toEpochMilli()

    // Shape of Stat Scraps' GET /api/account/teams (src/calendar.js, myTeamsSummary).
    private val myTeams = """
        {"user":{"displayName":"Sam"},"teams":[
          {"sport":"nfl","id":"28","name":"Washington Commanders","abbreviation":"WSH","logo":"https://a/wsh.png","record":"7-3","rank":null,
           "liveGame":null,
           "nextGame":{"id":"2","date":"2026-11-16T18:00Z","shortName":"WSH @ DAL","broadcast":"FOX",
             "status":{"state":"pre","shortDetail":"11/16 - 1:00 PM EST"},
             "home":{"id":"6","abbreviation":"DAL","score":null},"away":{"id":"28","abbreviation":"WSH","score":null}},
           "lastGame":{"id":"1","date":"2026-11-09T18:00Z","status":{"state":"post","shortDetail":"Final"},
             "home":{"id":"28","abbreviation":"WSH","score":"27","winner":true},"away":{"id":"21","abbreviation":"PHI","score":"20"}}},
          {"sport":"cfb","id":"259","name":"Virginia Tech Hokies","abbreviation":"VT","logo":null,"record":"6-4","rank":18,
           "liveGame":{"id":"3","date":"2026-11-14T20:00:00Z","shortName":"FSU @ VT","status":{"state":"in","shortDetail":"Q2 4:12"},
             "home":{"id":"259","abbreviation":"VT","score":14},"away":{"id":"52","abbreviation":"FSU","score":10}},
           "nextGame":null,"lastGame":null},
          {"sport":"nba","id":"27","name":"Washington Wizards","abbreviation":"WSH","record":"2-9",
           "liveGame":null,"nextGame":null,
           "lastGame":{"id":"4","date":"2026-11-14T00:00:00Z","status":{"state":"post","shortDetail":"Final/OT"},
             "home":{"id":"2","abbreviation":"BOS","score":"120","winner":true},"away":{"id":"27","abbreviation":"WSH","score":"118","winner":false}}}
        ]}
    """.trimIndent()

    @Test fun myTeamsPicksLiveThenRecentFinalThenNext() {
        val rows = Scores.parseMyTeams(myTeams, now)
        assertEquals(3, rows.size)

        val nfl = rows[0]
        assertEquals("nfl:28", nfl.key)
        assertEquals(GameState.UPCOMING, nfl.state) // last game was 5 days ago
        assertEquals("WSH at DAL", nfl.scoreline)
        assertEquals("11/16 - 1:00 PM EST · FOX", nfl.detail)
        assertNull(nfl.rank)

        val vt = rows[1]
        assertEquals(GameState.LIVE, vt.state)
        assertEquals("FSU 10 – 14 VT", vt.scoreline)
        assertEquals("Q2 4:12", vt.detail)
        assertEquals(18, vt.rank)

        val nba = rows[2]
        assertEquals(GameState.FINAL, nba.state)
        assertEquals("WSH 118 – 120 BOS", nba.scoreline)
        assertEquals(false, nba.won)

        assertEquals(listOf("cfb:259", "nfl:28", "nba:27"), Scores.forDisplay(rows).map { it.key })
        assertEquals(45_000L, Scores.refreshDelayMs(rows))
    }

    // Shape of Stat Scraps' GET /api/ha/scorecards (src/scorecard.js).
    private val scorecards = """
        {"updated":"2026-11-14T21:00:00Z","scorecards":{
          "cfb:259":{"kind":"game","sport":"cfb","state":"POST",
            "team":{"id":"259","name":"Virginia Tech Hokies","abbreviation":"VT","logo":"https://a/vt.png","record":"7-4","rank":18,"score":"31","homeAway":"away","winner":true},
            "opponent":{"id":"2390","name":"Miami Hurricanes","abbreviation":"MIA","score":"28","homeAway":"home","winner":false},
            "event":{"shortDetail":"Final","broadcast":"ESPN"}},
          "nhl:23":{"kind":"game","sport":"nhl","state":"PRE",
            "team":{"id":"23","name":"Washington Capitals","abbreviation":"WSH","homeAway":"home"},
            "opponent":{"id":"1","abbreviation":"BOS","homeAway":"away"},
            "event":{"shortDetail":"Sat 7:00 PM","broadcast":"TNT"}},
          "f1":{"kind":"race","sport":"f1","league":"F1","state":"PRE","event":{"shortName":"Las Vegas GP","shortDetail":"Sat 10:00 PM"}},
          "nfl:999":{"error":"Could not build scorecard"}
        }}
    """.trimIndent()

    @Test fun scorecardsKeepTheUsersOrder() {
        val items = listOf("nhl:23", "cfb:259", "nfl:999", "f1")
        val rows = Scores.parseScorecards(scorecards, items)
        assertEquals(listOf("nhl:23", "cfb:259", "f1"), rows.map { it.key })

        assertEquals("WSH vs BOS", rows[0].scoreline)
        assertEquals("Sat 7:00 PM · TNT", rows[0].detail)

        assertEquals("VT 31 – 28 MIA", rows[1].scoreline)
        assertEquals(true, rows[1].won)

        assertEquals("F1", rows[2].name)
        assertEquals("Las Vegas GP", rows[2].scoreline)
        assertEquals(5 * 60_000L, Scores.refreshDelayMs(rows))
    }

    @Test fun searchResultsBecomeSportAndId() {
        val picks = Scores.parseSearch("""{"query":"hok","teams":[{"id":"259","name":"Virginia Tech Hokies","sport":"cfb","logo":null}],"players":[]}""")
        assertEquals("cfb:259", picks.single().key)
    }

    @Test fun urls() {
        assertEquals("https://scores.sgiu.net/api/ha/scorecards?items=cfb%3A259%2Cf1", Scores.scorecardsUrl("https://scores.sgiu.net/", listOf("cfb:259", "f1")))
        assertEquals("http://h/api/account/teams", Scores.myTeamsUrl("http://h"))
    }
}
