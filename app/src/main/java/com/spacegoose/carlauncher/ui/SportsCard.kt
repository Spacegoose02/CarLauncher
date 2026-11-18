package com.spacegoose.carlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.spacegoose.carlauncher.integrations.ImageCache
import com.spacegoose.carlauncher.integrations.sports.GameState
import com.spacegoose.carlauncher.integrations.sports.Scores
import com.spacegoose.carlauncher.integrations.sports.SportsState
import com.spacegoose.carlauncher.integrations.sports.TeamScore

private val LiveRed = Color(0xFFE53935)
private val RowGap = 10.dp

/** The sports module: live games first, then each team's next game or latest result, from Stat Scraps. */
@Composable
fun SportsCard(state: SportsState, images: ImageCache, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    DashCard(modifier.clickable(onClick = onRefresh)) {
        val rows = Scores.forDisplay(state.rows)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (rows.isEmpty()) {
                val text = fitText(maxWidth, maxHeight, chars = 22, heightFraction = 0.1f)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏈", fontSize = text * 2.5f, lineHeight = text * 3f)
                    Text(
                        state.problem ?: if (state.loading) "Getting scores..." else "Scores",
                        fontSize = text, lineHeight = text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
                return@BoxWithConstraints
            }
            // As many rows as fit at a readable size; live games come first, so they're never the ones cut.
            val footer = state.problem != null || state.fetchedAt == 0L
            val usable = maxHeight - if (footer) 24.dp else 0.dp
            val rowHeight = min(96.dp, maxOf(56.dp, usable / rows.size.coerceAtLeast(1) - RowGap))
            val fits = ((usable + RowGap) / (rowHeight + RowGap)).toInt().coerceIn(1, rows.size)
            val cardWidth = maxWidth
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                rows.take(fits).forEach { ScoreRow(it, images, rowHeight, cardWidth) }
                if (footer) {
                    Text(
                        if (state.problem != null) "Not updated: tap to try again" else "Saved scores, updating...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(row: TeamScore, images: ImageCache, height: Dp, width: Dp) {
    val logo by produceState<ImageBitmap?>(row.logo?.let(images::peek), row.logo) {
        row.logo?.let { value = images.load(it) }
    }
    val big = fitText(width * 0.62f, height, chars = 14, heightFraction = 0.4f, max = 30.dp)
    val small = fitText(width * 0.62f, height, chars = 22, heightFraction = 0.28f, max = 20.dp)
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(height * 0.7f), contentAlignment = Alignment.Center) {
            val img = logo
            if (img != null) {
                Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    (row.abbreviation ?: row.name).take(4), fontSize = small, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.state == GameState.LIVE) {
                    Box(Modifier.size(10.dp).background(LiveRed, CircleShape))
                    Box(Modifier.width(6.dp))
                }
                Text(
                    row.scoreline.ifBlank { listOfNotNull(row.rank?.let { "#$it" }, row.name).joinToString(" ") },
                    fontSize = big, lineHeight = big,
                    fontWeight = if (row.state == GameState.LIVE) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val result = when (row.won) {
                true -> "W · "
                false -> "L · "
                null -> ""
            }
            Text(
                result + listOfNotNull(row.detail.ifBlank { null }, row.record).joinToString(" · "),
                fontSize = small, lineHeight = small,
                color = if (row.state == GameState.LIVE) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
