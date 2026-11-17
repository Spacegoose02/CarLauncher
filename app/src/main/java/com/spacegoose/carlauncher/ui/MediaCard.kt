package com.spacegoose.carlauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenApp),
                contentAlignment = Alignment.Center,
            ) {
                val art = nowPlaying?.art
                if (art != null) {
                    Image(
                        art.asImageBitmap(),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    SourceLabel(nowPlaying, wavelengthInstalled)
                    Text(
                        nowPlaying?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        nowPlaying?.artist?.ifBlank { null } ?: when {
                            nowPlaying != null -> ""
                            wavelengthInstalled -> "Tap play to open Wavelength"
                            else -> "Start music in any app"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, enabled = nowPlaying != null, modifier = Modifier.size(TouchTarget)) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(40.dp))
                    }
                    FilledIconButton(
                        onClick = { if (nowPlaying == null) onOpenApp() else onPlayPause() },
                        modifier = Modifier.size(TouchTarget + 8.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            if (nowPlaying?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (nowPlaying?.isPlaying == true) "Pause" else "Play",
                            Modifier.size(44.dp),
                        )
                    }
                    IconButton(onClick = onNext, enabled = nowPlaying != null, modifier = Modifier.size(TouchTarget)) {
                        Icon(Icons.Filled.SkipNext, "Next", Modifier.size(40.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpenApp, modifier = Modifier.size(TouchTarget)) {
                        Icon(Icons.Filled.OpenInNew, "Open music app", Modifier.size(30.dp))
                    }
                }
                if (!hasNotificationAccess) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Also follow other music apps") }
                }
            }
        }
    }
}

@Composable
private fun SourceLabel(nowPlaying: NowPlaying?, wavelengthInstalled: Boolean) {
    val label = when {
        nowPlaying != null -> nowPlaying.appLabel
        wavelengthInstalled -> "Wavelength"
        else -> "Media"
    }
    val isWavelength = nowPlaying?.isWavelength ?: wavelengthInstalled
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (isWavelength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp),
        maxLines = 1,
    )
}
