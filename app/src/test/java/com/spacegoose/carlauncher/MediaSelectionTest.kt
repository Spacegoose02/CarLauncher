package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.media.chooseMediaSource
import com.spacegoose.carlauncher.settings.MediaSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSelectionTest {
    private data class S(val name: String, val playing: Boolean)

    private fun pick(pref: MediaSourcePreference, wl: S?, vararg others: S) =
        chooseMediaSource(pref, wl, others.toList()) { it.playing }?.name

    private val wlPaused = S("wavelength", false)
    private val wlPlaying = S("wavelength", true)
    private val spotifyPlaying = S("spotify", true)
    private val podcastPaused = S("podcast", false)

    @Test fun `wavelength first prefers wavelength when it plays`() {
        assertEquals("wavelength", pick(MediaSourcePreference.WAVELENGTH_FIRST, wlPlaying, spotifyPlaying))
    }

    @Test fun `wavelength first yields to another app that is playing`() {
        assertEquals("spotify", pick(MediaSourcePreference.WAVELENGTH_FIRST, wlPaused, spotifyPlaying))
    }

    @Test fun `wavelength first keeps paused wavelength over paused others`() {
        assertEquals("wavelength", pick(MediaSourcePreference.WAVELENGTH_FIRST, wlPaused, podcastPaused))
    }

    @Test fun `wavelength only ignores other apps`() {
        assertNull(pick(MediaSourcePreference.WAVELENGTH_ONLY, null, spotifyPlaying))
    }

    @Test fun `any app prefers whatever plays`() {
        assertEquals("spotify", pick(MediaSourcePreference.ANY_APP, wlPaused, podcastPaused, spotifyPlaying))
        assertEquals("wavelength", pick(MediaSourcePreference.ANY_APP, wlPlaying, podcastPaused))
    }
}
