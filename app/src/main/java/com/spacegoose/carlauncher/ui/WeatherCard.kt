package com.spacegoose.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.integrations.weather.WeatherState

/** The weather module: Weather Scout's current conditions, rain in the next two hours, and the top alert. */
@Composable
fun WeatherCard(state: WeatherState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    DashCard(modifier.clickable(onClick = onRefresh)) {
        val w = state.now
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = fitText(maxWidth, maxHeight, chars = 6, heightFraction = 0.34f)
            val mid = fitText(maxWidth, maxHeight, chars = 16, heightFraction = 0.11f)
            val small = fitText(maxWidth, maxHeight, chars = 22, heightFraction = 0.09f)
            if (w == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌡️", fontSize = big, lineHeight = big)
                    Text(
                        state.problem ?: if (state.loading) "Getting the weather..." else "Weather",
                        fontSize = small, lineHeight = small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
                return@BoxWithConstraints
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.emoji, fontSize = big * 0.8f, lineHeight = big)
                    Text(
                        w.temp, fontSize = big, lineHeight = big, fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    listOf(w.label, w.place).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = mid, lineHeight = mid, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val line = w.rainSoon.ifBlank { w.hiLo }
                if (line.isNotBlank()) {
                    Text(
                        line, fontSize = small, lineHeight = small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (w.rainSoon.isNotBlank() && w.hiLo.isNotBlank()) {
                    Text(
                        w.hiLo, fontSize = small, lineHeight = small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    w.hasAlert && w.alert.isNotBlank() -> Text(
                        w.alert, fontSize = small, lineHeight = small, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    state.problem != null -> Text(
                        "Not updated: tap to try again", fontSize = small, lineHeight = small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
