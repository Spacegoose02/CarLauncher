package com.spacegoose.carlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.apps.AppEntry

@Composable
fun Dock(
    apps: List<AppEntry>,
    onOpenDrawer: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = glassSurface(), border = glassEdge()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            // Size the icons so the whole dock fits the space it has (the dock sits under the widget
            // column next to the map box). Below 48dp the apps scroll instead of shrinking further.
            val slots = apps.size + 3
            val gap = 8.dp
            val fit = (maxWidth - gap * (slots - 1)) / slots
            val size = minOf(fit, maxHeight - 16.dp, TouchTarget + 8.dp).coerceAtLeast(48.dp)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                DockButton(Icons.Filled.Apps, "All apps", size, onOpenDrawer)
                DockButton(Icons.Filled.Navigation, "Map", size, onOpenMap)
                LazyRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(apps, key = { it.key }) { app -> DockApp(app, size, onLaunch, onRemove) }
                }
                DockButton(Icons.Filled.Settings, "Launcher settings", size, onOpenSettings)
            }
        }
    }
}

@Composable
private fun DockButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, description, Modifier.size(size * 0.5f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockApp(app: AppEntry, size: Dp, onLaunch: (AppEntry) -> Unit, onRemove: (AppEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Image(
            app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true })
                .padding(4.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Remove ${app.label} from dock") },
                onClick = {
                    menu = false
                    onRemove(app)
                },
            )
        }
    }
}
