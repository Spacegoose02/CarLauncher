package com.spacegoose.carlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * True while driving mode is on: decorative motion (cards easing in, scrolling titles) is cut so nothing
 * on the screen pulls the driver's eye. Motion that carries information, like the speed, stays.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Tabular figures keep every digit the same width, so numbers don't jiggle as they change. */
const val TabularFigures = "tnum"

/**
 * Text whose characters roll like an odometer when they change: a digit going up slides in from below,
 * going down from above. Only the characters that changed move.
 */
@Composable
fun RollingText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    val resolved = if (color == Color.Unspecified) LocalContentColor.current else color
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            // Keyed by position from the end, so "9:59" -> "10:00" rolls the minutes rather than shifting them.
            androidx.compose.runtime.key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(tween(RollMs, easing = FastOutSlowInEasing)) { h -> if (up) h else -h } +
                            fadeIn(tween(RollMs))) togetherWith
                            (slideOutVertically(tween(RollMs, easing = FastOutSlowInEasing)) { h -> if (up) -h else h } +
                                fadeOut(tween(RollMs / 2)))
                    },
                    label = "rolling char",
                ) { c ->
                    Text(
                        c.toString(),
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        fontWeight = fontWeight,
                        color = resolved,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = TabularFigures),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private const val RollMs = 450

/**
 * Fades and lifts a dashboard card into place when the launcher opens, [order] steps after the first, so the
 * cards arrive one after another. Skipped in driving mode.
 */
@Composable
fun Modifier.enterOnOpen(order: Int): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(order * StaggerMs)
            progress.animateTo(1f, tween(EnterMs, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
        val scale = 0.96f + 0.04f * p
        scaleX = scale
        scaleY = scale
    }
}

private const val StaggerMs = 70L
private const val EnterMs = 500
