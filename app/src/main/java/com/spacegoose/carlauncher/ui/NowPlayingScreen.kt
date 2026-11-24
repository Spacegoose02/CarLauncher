package com.spacegoose.carlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.media.NowPlaying
import com.spacegoose.carlauncher.media.formatSongTime

/**
 * The full-screen player: big art, the song, how far through it you are, and big controls. Opened by tapping
 * the media card's art; Back, the close button or a swipe down closes it. Swipe the art to skip songs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    nowPlaying: NowPlaying,
    artTint: Color?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(artTint ?: MaterialTheme.colorScheme.primary, tween(600), label = "player accent")
    val playing = nowPlaying.isPlaying
    val artScale by animateFloatAsState(
        if (playing) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "player art scale",
    )
    val closeDistance = with(LocalDensity.current) { 96.dp.toPx() }
    val sizes = LocalCarSizes.current

    Box(
        modifier
            .fillMaxSize()
            // Swallow taps so nothing behind the player reacts, and close on a firm swipe down.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .pointerInput(closeDistance) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > closeDistance) onClose() },
                ) { change, dy ->
                    change.consume()
                    dragged += dy
                }
            }
    ) {
        MusicBackdrop(accent, nowPlaying.art)
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
            val wide = maxWidth > maxHeight
            val artSize = if (wide) minOf(maxHeight, maxWidth * 0.42f) else minOf(maxWidth, maxHeight * 0.45f)
            val textWidth = if (wide) maxWidth - artSize - 40.dp else maxWidth
            val title = fitText(textWidth, maxHeight, chars = 16, heightFraction = 0.11f, max = 44.dp)
            val subtitle = fitText(textWidth, maxHeight, chars = 22, heightFraction = 0.07f, max = 28.dp)
            val small = fitText(textWidth, maxHeight, chars = 24, heightFraction = 0.05f, max = 20.dp)
            val control = minOf(maxHeight * 0.2f, textWidth / 4f, sizes.mediaControlMax * 1.2f).coerceAtLeast(56.dp)

            val art: @Composable () -> Unit = {
                Box(
                    Modifier
                        .size(artSize)
                        .graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                        }
                        .shadow(if (playing) 32.dp else 8.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .swipeToSkip(true, onNext, onPrevious),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(nowPlaying.art, animationSpec = tween(600), label = "player art") { picture ->
                        if (picture != null) {
                            Image(
                                picture.asImageBitmap(),
                                contentDescription = "Album art. Swipe to change song",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(artSize * 0.4f),
                                )
                            }
                        }
                    }
                }
            }

            val details: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                    Text(
                        nowPlaying.appLabel.uppercase(),
                        fontSize = small,
                        lineHeight = small,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                    )
                    AnimatedContent(
                        nowPlaying.title,
                        transitionSpec = {
                            (slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(350))) togetherWith
                                (slideOutVertically(tween(350)) { -it / 2 } + fadeOut(tween(175)))
                        },
                        label = "player title",
                    ) { songTitle ->
                        Text(
                            songTitle,
                            fontSize = title,
                            lineHeight = title * 1.15f,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                    val by = listOf(nowPlaying.artist, nowPlaying.album).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (by.isNotEmpty()) {
                        Text(
                            by,
                            fontSize = subtitle,
                            lineHeight = subtitle * 1.15f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (nowPlaying.durationMs > 0) {
                        Spacer(Modifier.height(8.dp))
                        SongProgressBar(nowPlaying, accent, Modifier.fillMaxWidth().height(6.dp))
                        val position = rememberSongPosition(nowPlaying)
                        Row(Modifier.fillMaxWidth()) {
                            Text(formatSongTime(position), fontSize = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "-" + formatSongTime(nowPlaying.durationMs - position),
                                fontSize = small,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(control)) {
                            Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(control * 0.6f))
                        }
                        FilledIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(control * 1.3f),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = Color.Black),
                        ) {
                            Crossfade(playing, animationSpec = tween(200), label = "player play pause") { isPlaying ->
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    Modifier.size(control * 0.7f),
                                )
                            }
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(control)) {
                            Icon(Icons.Filled.SkipNext, "Next", Modifier.size(control * 0.6f))
                        }
                    }
                    FilledTonalButton(onClick = onOpenApp, modifier = Modifier.height(TouchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Open ${nowPlaying.appLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    art()
                    Spacer(Modifier.width(40.dp))
                    details(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    art()
                    Spacer(Modifier.height(24.dp))
                    details(Modifier.fillMaxWidth().weight(1f))
                }
            }

            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).size(TouchTarget),
            ) {
                Icon(Icons.Filled.Close, "Close the player", Modifier.size(TouchTarget * 0.45f))
            }
        }
    }
}
