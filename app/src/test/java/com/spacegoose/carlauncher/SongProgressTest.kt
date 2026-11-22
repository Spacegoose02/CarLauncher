package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.media.NowPlaying
import com.spacegoose.carlauncher.media.formatSongTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongProgressTest {
    private fun song(playing: Boolean, duration: Long = 200_000, position: Long = 60_000, at: Long = 1_000, speed: Float = 1f) =
        NowPlaying(
            title = "Song", artist = "Artist", album = "", art = null, isPlaying = playing,
            packageName = "app", appLabel = "App",
            durationMs = duration, positionMs = position, positionAtMs = at, speed = speed,
        )

    @Test fun movesOnWhilePlaying() {
        assertEquals(65_000L, song(playing = true).positionAt(6_000))
    }

    @Test fun staysPutWhilePaused() {
        assertEquals(60_000L, song(playing = false).positionAt(60_000))
    }

    @Test fun followsPlaybackSpeed() {
        assertEquals(70_000L, song(playing = true, speed = 2f).positionAt(6_000))
    }

    @Test fun neverRunsPastTheEnd() {
        assertEquals(200_000L, song(playing = true).positionAt(10_000_000))
    }

    @Test fun progressIsAFraction() {
        assertEquals(0.3f, song(playing = false).progressAt(0)!!, 0.0001f)
    }

    @Test fun noProgressWithoutALength() {
        assertNull(song(playing = true, duration = 0).progressAt(5_000))
    }

    @Test fun formatsTimes() {
        assertEquals("0:00", formatSongTime(0))
        assertEquals("3:07", formatSongTime(187_000))
        assertEquals("1:02:45", formatSongTime(3_765_000))
    }
}
