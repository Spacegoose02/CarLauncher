package com.spacegoose.carlauncher.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The most striking colour in [art], made bright enough to read on a dark card, or null while there is
 * no art (callers fall back to the theme accent). Worked out off the main thread, once per picture.
 */
@Composable
fun rememberArtTint(art: Bitmap?): Color? {
    var tint by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(art) {
        tint = art?.let { withContext(Dispatchers.Default) { artTint(it) } }
    }
    return tint
}

private fun artTint(art: Bitmap): Color? = try {
    // Palette can't read hardware bitmaps, and a small copy is plenty to find the colours.
    val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && art.config == Bitmap.Config.HARDWARE) {
        art.copy(Bitmap.Config.ARGB_8888, false)
    } else art
    val small = Bitmap.createScaledBitmap(readable, 64, 64, true)
    val palette = Palette.from(small).maximumColorCount(16).generate()
    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
    swatch?.rgb?.let { Color(readableAccent(it)) }
} catch (e: Exception) {
    null
}

/** Keeps the colour's hue but lifts dull or dark picks so text and buttons stay visible at a glance. */
private fun readableAccent(rgb: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceIn(0.35f, 0.85f)
    hsl[2] = hsl[2].coerceIn(0.55f, 0.72f)
    return ColorUtils.HSLToColor(hsl)
}
