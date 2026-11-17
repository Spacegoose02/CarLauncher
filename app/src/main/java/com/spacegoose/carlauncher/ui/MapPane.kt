package com.spacegoose.carlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.maps.MapApp
import com.spacegoose.carlauncher.maps.MapLaunchMode

/**
 * The space the map app's window takes. Waze can't be embedded, so this pane opens Waze beside the
 * dashboard (split-screen) or floats it over this area (freeform).
 */
@Composable
fun MapPane(
    mapApp: MapApp,
    mode: MapLaunchMode,
    status: String?,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashCard(modifier) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Navigation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenMap,
                modifier = Modifier.heightIn(min = 80.dp).widthIn(min = 280.dp),
            ) {
                Text("Open ${mapApp.label}", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Mode: ${mode.label}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
