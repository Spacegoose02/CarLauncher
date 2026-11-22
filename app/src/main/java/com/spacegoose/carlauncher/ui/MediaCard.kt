package com.spacegoose.carlauncher.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.media.NowPlaying

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    nowPlaying: NowPlaying?,
    wavelengthInstalled: Boolean,
    hasNotificationAccess: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    /** The album art's colour (see [rememberArtTint]), or null to use the theme accent. */
    artTint: Color?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The card takes its colour from the album art, and eases into the next song's colour.
    val accent by animateColorAsState(
        artTint ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(ColorFadeMs),
        label = "media accent",
    )
    val playing = nowPlaying?.isPlaying == true
    // Paused art sits back a little, so the state reads from across the car.
    val artScale by animateFloatAsState(
        if (playing || nowPlaying == null) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "art scale",
    )
    DashCard(modifier, glow = if (nowPlaying != null) accent else Color.Unspecified) {
        // Swipe the card left for the next song, right for the previous one: easier than aiming for a button.
        BoxWithConstraints(Modifier.fillMaxSize().swipeToSkip(nowPlaying != null, onNext, onPrevious)) {
            // Everything scales with the card, so nothing is cut off in split-screen or on small screens.
            val artSize = minOf(maxHeight, maxWidth * 0.38f)
            val showArt = maxWidth >= 300.dp
            val textWidth = if (showArt) maxWidth - artSize - 16.dp else maxWidth
            val controlSize = minOf(maxHeight * 0.36f, textWidth / 3.4f, LocalCarSizes.current.mediaControlMax).coerceAtLeast(40.dp)
            val label = fitText(textWidth, maxHeight, chars = 14, heightFraction = 0.11f, max = 18.dp)
            val title = fitText(textWidth, maxHeight, chars = 14, heightFraction = 0.17f, max = 30.dp)
            val subtitle = fitText(textWidth, maxHeight, chars = 18, heightFraction = 0.13f, max = 22.dp)

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (showArt) {
                    Box(
                        Modifier
                            .size(artSize)
                            .graphicsLayer {
                                scaleX = artScale
                                scaleY = artScale
                            }
                            .shadow(if (playing) 18.dp else 4.dp, RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = if (nowPlaying != null) onOpenNowPlaying else onOpenApp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(nowPlaying?.art, animationSpec = tween(ColorFadeMs), label = "album art") { art ->
                            if (art != null) {
                                Image(
                                    art.asImageBitmap(),
                                    contentDescription = "Album art, opens the full-screen player",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = "Open the music app",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(artSize * 0.4f),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        sourceLabel(nowPlaying, wavelengthInstalled).uppercase(),
                        fontSize = label,
                        lineHeight = label,
                        fontWeight = FontWeight.Bold,
                        color = if (nowPlaying != null || wavelengthInstalled) accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    // A new song slides in from below; the old one slides out the top.
                    AnimatedContent(
                        nowPlaying?.title ?: "Nothing playing",
                        transitionSpec = {
                            (slideInVertically(tween(SlideMs)) { it / 2 } + fadeIn(tween(SlideMs))) togetherWith
                                (slideOutVertically(tween(SlideMs)) { -it / 2 } + fadeOut(tween(SlideMs / 2)))
                        },
                        label = "song title",
                    ) { songTitle ->
                        Text(
                            songTitle,
                            fontSize = title,
                            lineHeight = title * 1.15f,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                    val sub = nowPlaying?.artist?.ifBlank { null } ?: when {
                        nowPlaying != null -> null
                        wavelengthInstalled -> "Tap play to open Wavelength"
                        else -> "Start music in any app"
                    }
                    if (sub != null) {
                        Text(
                            sub,
                            fontSize = subtitle,
                            lineHeight = subtitle * 1.15f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrevious, enabled = nowPlaying != null, modifier = Modifier.size(controlSize)) {
                            Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(controlSize * 0.62f))
                        }
                        FilledIconButton(
                            onClick = { if (nowPlaying == null) onOpenApp() else onPlayPause() },
                            modifier = Modifier.size(controlSize * 1.15f),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = Color.Black),
                        ) {
                            Crossfade(playing, animationSpec = tween(200), label = "play pause") { isPlaying ->
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    Modifier.size(controlSize * 0.68f),
                                )
                            }
                        }
                        IconButton(onClick = onNext, enabled = nowPlaying != null, modifier = Modifier.size(controlSize)) {
                            Icon(Icons.Filled.SkipNext, "Next", Modifier.size(controlSize * 0.62f))
                        }
                    }
                    if (nowPlaying == null && !hasNotificationAccess) {
                        Text(
                            "Follow other music apps too",
                            fontSize = subtitle,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                        )
                    }
                }
            }
            // A thin line along the bottom edge shows how far into the song you are.
            if (nowPlaying != null && nowPlaying.durationMs > 0) {
                SongProgressBar(
                    nowPlaying,
                    accent,
                    Modifier.align(Alignment.BottomCenter).offset(y = 12.dp).fillMaxWidth().height(3.dp),
                )
            }
        }
    }
}

private const val ColorFadeMs = 600
private const val SlideMs = 350

private fun sourceLabel(nowPlaying: NowPlaying?, wavelengthInstalled: Boolean): String = when {
    nowPlaying != null -> nowPlaying.appLabel
    wavelengthInstalled -> "Wavelength"
    else -> "Media"
}
