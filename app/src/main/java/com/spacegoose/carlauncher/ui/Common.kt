package com.spacegoose.carlauncher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

val CardShape = RoundedCornerShape(24.dp)

/**
 * Sizes that grow in driving mode, so buttons are easier to hit with the car moving.
 * [dock] is the dock's height before it is fitted to short screens.
 */
data class CarSizes(val touchTarget: Dp, val dock: Dp, val mediaControlMax: Dp, val drawerCell: Dp, val drawerIcon: Dp) {
    companion object {
        val Normal = CarSizes(touchTarget = 64.dp, dock = 96.dp, mediaControlMax = 76.dp, drawerCell = 128.dp, drawerIcon = 72.dp)
        val Driving = CarSizes(touchTarget = 80.dp, dock = 120.dp, mediaControlMax = 104.dp, drawerCell = 168.dp, drawerIcon = 92.dp)
    }
}

val LocalCarSizes = staticCompositionLocalOf { CarSizes.Normal }

/** Minimum touch target for anything used while driving (bigger in driving mode). */
val TouchTarget: Dp
    @Composable @ReadOnlyComposable get() = LocalCarSizes.current.touchTarget

/**
 * Cards are slightly see-through, so the music glow behind the dashboard shows through them like frosted glass.
 * Opaque enough that text on them keeps its contrast.
 */
@Composable
@ReadOnlyComposable
fun glassSurface(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)

/** A hairline edge that catches the light on the glass cards. */
@Composable
@ReadOnlyComposable
fun glassEdge(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

/**
 * A dashboard card. [glow], when set, washes the card with that colour from the top-left corner (the media
 * card uses the album art's colour), fading into the card's own surface.
 */
@Composable
fun DashCard(
    modifier: Modifier = Modifier,
    glow: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CardShape,
        color = glassSurface(),
        border = glassEdge(),
    ) {
        val wash = if (glow.isSpecified) {
            Modifier.drawBehind {
                drawRect(
                    Brush.linearGradient(
                        0f to glow.copy(alpha = 0.34f),
                        0.55f to glow.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                )
            }
        } else Modifier
        Box(wash.padding(20.dp), content = content)
    }
}

/**
 * A font size that fits [chars] characters in [maxWidth] and takes at most [heightFraction] of [maxHeight].
 * Cards are sized by the screen (and shrink in split-screen), so their big text scales with them.
 */
@androidx.compose.runtime.Composable
fun fitText(
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    chars: Int,
    heightFraction: Float,
    max: androidx.compose.ui.unit.Dp = 400.dp,
): androidx.compose.ui.unit.TextUnit {
    val byHeight = maxHeight * heightFraction
    val byWidth = maxWidth / (chars.coerceAtLeast(1) * 0.62f)
    val size = minOf(byHeight, byWidth, max).coerceAtLeast(10.dp)
    return with(androidx.compose.ui.platform.LocalDensity.current) { size.toSp() }
}
