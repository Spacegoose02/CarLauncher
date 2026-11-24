package com.spacegoose.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.headsup.HeadsUpItem

/** Amber that reads on both light and dark glass, for alerts. */
private val Warning = Color(0xFFFFB020)

/**
 * Chips with what matters right now: rain on the way, a game your team is playing, how long to get home.
 * They live in the dock and fade in and out as things change; with nothing to say, nothing shows.
 */
@Composable
fun HeadsUpChips(items: List<HeadsUpItem>, height: Dp, modifier: Modifier = Modifier) {
    // Keep the last chips while they fade out, so they don't vanish before the fade.
    var shown by remember { mutableStateOf(items) }
    if (items.isNotEmpty()) shown = items
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(tween(300)) + expandHorizontally(tween(300)),
        exit = fadeOut(tween(200)) + shrinkHorizontally(tween(250)),
    ) {
        LazyRow(
            Modifier.height(height),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(shown, key = { it.id }) { item ->
                HeadsUpChip(item, Modifier.animateItem().fillMaxHeight())
            }
        }
    }
}

@Composable
private fun HeadsUpChip(item: HeadsUpItem, modifier: Modifier = Modifier) {
    val accent = if (item.urgent) Warning else MaterialTheme.colorScheme.primary
    Surface(
        modifier,
        shape = RoundedCornerShape(50),
        color = if (item.urgent) Warning.copy(alpha = 0.22f) else glassSurface(),
        border = glassEdge(),
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 18.dp)) {
            val big = fitText(360.dp, maxHeight, chars = 1, heightFraction = 0.36f, max = 22.dp)
            val small = fitText(360.dp, maxHeight, chars = 1, heightFraction = 0.28f, max = 16.dp)
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.emoji, fontSize = big * 1.2f)
                Spacer(Modifier.width(12.dp))
                // Long alert or rain text is cut short instead of one chip filling the strip.
                Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        item.title,
                        fontSize = big,
                        lineHeight = big * 1.1f,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.urgent) Warning else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.detail.isNotBlank()) {
                        Text(
                            item.detail,
                            fontSize = small,
                            lineHeight = small * 1.1f,
                            color = if (item.urgent) MaterialTheme.colorScheme.onSurface else accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
