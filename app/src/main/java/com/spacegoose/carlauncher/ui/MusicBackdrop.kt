package com.spacegoose.carlauncher.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

private const val FadeMs = 900

/**
 * The dashboard's background. While music plays it glows with [tint] (the album art's colour) from two corners,
 * and on Android 12 and later the art itself sits behind everything, heavily blurred. Both fade in and out
 * slowly, and are dimmer on a dark theme so the screen never dazzles at night.
 */
@Composable
fun MusicBackdrop(tint: Color?, art: Bitmap?, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val darkTheme = background.luminance() < 0.2f
    val strength by animateFloatAsState(if (tint != null) 1f else 0f, tween(FadeMs), label = "glow strength")
    val glow by animateColorAsState(tint ?: background, tween(FadeMs), label = "glow colour")
    val glowAlpha = if (darkTheme) 0.28f else 0.22f

    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                if (strength <= 0f) return@drawBehind
                val a = glowAlpha * strength
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = a), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.maxDimension * 0.8f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = a * 0.6f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.maxDimension * 0.7f,
                    )
                )
            }
    ) {
        // Blur needs Android 12. Older head units get the glow alone, which still looks right.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Crossfade(art, animationSpec = tween(FadeMs), label = "backdrop art") { picture ->
                if (picture != null) {
                    Image(
                        picture.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha((if (darkTheme) 0.22f else 0.18f) * strength)
                            .blur(80.dp),
                    )
                }
            }
        }
    }
}
