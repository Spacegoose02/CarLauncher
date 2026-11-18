package com.spacegoose.carlauncher

import com.spacegoose.carlauncher.media.ArtSources
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtSourcesTest {
    @Test
    fun keepsLoadableLinksInOrder() {
        assertEquals(
            listOf("content://com.tonearm.app.art/3", "https://x/cover.jpg"),
            ArtSources.uris(null, "", "content://com.tonearm.app.art/3", "blob:abc", "https://x/cover.jpg"),
        )
    }

    @Test
    fun dropsDuplicatesAndBlanks() {
        assertEquals(listOf("http://a/b.png"), ArtSources.uris(" http://a/b.png ", "http://a/b.png", "   "))
    }

    @Test
    fun nothingUsable() {
        assertEquals(emptyList<String>(), ArtSources.uris(null, "data:image/png;base64,xx", "/relative.png"))
    }
}
