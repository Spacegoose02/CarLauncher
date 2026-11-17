package com.spacegoose.carlauncher.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    DashCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Everything scales with the card, so nothing is cut off in split-screen or on small screens.
            val artSize = minOf(maxHeight, maxWidth * 0.38f)
            val showArt = maxWidth >= 300.dp
            val textWidth = if (showArt) maxWidth - artSize - 16.dp else maxWidth
            val controlSize = minOf(maxHeight * 0.36f, textWidth / 3.4f, 76.dp).coerceAtLeast(40.dp)
            val label = fitText(textWidth, maxHeight, chars = 14, heightFraction = 0.11f, max = 18.dp)
            val title = fitText(textWidth, maxHeight, chars = 14, heightFraction = 0.17f, max = 30.dp)
            val subtitle = fitText(textWidth, maxHeight, chars = 18, heightFraction = 0.13f, max = 22.dp)

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (showArt) {
                    Box(
                        Modifier
                            .size(artSize)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onOpenApp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val art = nowPlaying?.art
                        if (art != null) {
                            Image(
                                art.asImageBitmap(),
                                contentDescription = "Album art, opens the music app",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = "Open the music app",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(artSize * 0.4f),
                            )
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
                        color = if (nowPlaying?.isWavelength ?: wavelengthInstalled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        nowPlaying?.title ?: "Nothing playing",
                        fontSize = title,
                        lineHeight = title * 1.15f,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
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
                        ) {
                            val playing = nowPlaying?.isPlaying == true
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (playing) "Pause" else "Play",
                                Modifier.size(controlSize * 0.68f),
                            )
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
        }
    }
}

private fun sourceLabel(nowPlaying: NowPlaying?, wavelengthInstalled: Boolean): String = when {
    nowPlaying != null -> nowPlaying.appLabel
    wavelengthInstalled -> "Wavelength"
    else -> "Media"
}
