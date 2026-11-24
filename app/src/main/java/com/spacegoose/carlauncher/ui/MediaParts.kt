package com.spacegoose.carlauncher.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.media.NowPlaying
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far a swipe has to go to skip a song. */
private val SwipeThreshold = 72.dp

/**
 * Swipe left for [onNext], right for [onPrevious]. The content follows the finger a little and springs back,
 * so the swipe feels like it registered.
 */
@Composable
fun Modifier.swipeToSkip(enabled: Boolean, onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    return this
        .graphicsLayer {
            translationX = offset.value * 0.4f
            alpha = 1f - (abs(offset.value) / (threshold * 5)).coerceAtMost(0.35f)
        }
        .pointerInput(enabled, threshold) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragEnd = {
                    val moved = offset.value
                    if (moved <= -threshold) next() else if (moved >= threshold) previous()
                    scope.launch { offset.animateTo(0f, spring()) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
            ) { change, dx ->
                change.consume()
                scope.launch { offset.snapTo(offset.value + dx) }
            }
        }
}

/** Where [nowPlaying] is right now, in ms, ticking along while it plays. */
@Composable
fun rememberSongPosition(nowPlaying: NowPlaying?): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying) {
        now = SystemClock.elapsedRealtime()
        while (nowPlaying?.isPlaying == true) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    return nowPlaying?.positionAt(now) ?: 0L
}

/** A rounded progress bar for the current song. */
@Composable
fun SongProgressBar(nowPlaying: NowPlaying, color: Color, modifier: Modifier = Modifier) {
    val position = rememberSongPosition(nowPlaying)
    val fraction = if (nowPlaying.durationMs > 0) (position.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color, RoundedCornerShape(50)))
    }
}
