package com.spacegoose.carlauncher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacegoose.carlauncher.maps.osm.MapExtras
import com.spacegoose.carlauncher.maps.osm.MapExtrasSettings
import com.spacegoose.carlauncher.maps.osm.MapRegion
import com.spacegoose.carlauncher.maps.osm.OfflineMapUse
import kotlinx.coroutines.launch

/** Settings for the OpenStreetMap map box: camera alerts, offline maps and traffic. Shown in the Map section. */
@Composable
fun MapExtrasSettingsSection() {
    val context = LocalContext.current
    val extras = remember { MapExtras.get(context) }
    val s by extras.settings.settings.collectAsStateWithLifecycle(initialValue = MapExtrasSettings())
    val maps by extras.offline.maps.collectAsStateWithLifecycle()
    val downloads by extras.offline.downloads.collectAsStateWithLifecycle()
    val message by extras.offline.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var browsing by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { extras.offline.import(uri) }
    }

    SubHeading("Speed cameras")
    ExtrasToggle("Warn about speed and red light cameras", s.cameraAlerts) { v -> extras.updateSettings { it.copy(cameraAlerts = v) } }
    if (s.cameraAlerts) {
        ExtrasToggle("Beep when a camera is ahead", s.cameraSound) { v -> extras.updateSettings { it.copy(cameraSound = v) } }
    }
    ExtrasHint("Cameras come from OpenStreetMap, so how many are mapped depends on the area. They are saved for driving with no signal.")

    SubHeading("Offline maps")
    ExtrasChoice("Use offline maps", OfflineMapUse.entries, s.offlineUse, { it.label }) { v ->
        extras.updateSettings { it.copy(offlineUse = v) }
    }
    ExtrasHint(
        "Download the states or countries you drive in, and the map keeps working with no signal. " +
            "Places you've already seen on the map are also saved, up to about 600 MB."
    )
    maps.forEach { map ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${map.name} (${map.sizeLabel})", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { extras.offline.delete(map) }) { Text("Delete") }
        }
    }
    downloads.forEach { d ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { extras.offline.cancel(d) }) { Text("Cancel") }
        }
    }
    message?.let { ExtrasHint(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (extras.offline.canDownload) ExtrasAction("Download a map") { browsing = true }
        ExtrasAction("Add a .map file") { pickFile.launch(arrayOf("*/*")) }
    }

    SubHeading("Traffic")
    ExtrasToggle("Show traffic and incidents on the map", s.traffic) { v -> extras.updateSettings { it.copy(traffic = v) } }
    if (s.traffic) {
        TomTomKeyField(s.tomTomKey) { key -> extras.updateSettings { it.copy(tomTomKey = key) } }
        ExtrasHint(
            "Traffic comes from TomTom and needs a free key: sign up at developer.tomtom.com, copy the key " +
                "from your dashboard, and paste it here. The free plan is plenty for one car."
        )
    }

    if (browsing) RegionBrowser(extras, onDismiss = { browsing = false })
}

@Composable
private fun TomTomKeyField(saved: String, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("TomTom key") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focus.clearFocus()
            onSave(text)
        }),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
    if (text.trim() != saved) ExtrasAction("Save key") {
        focus.clearFocus()
        onSave(text)
    }
}

/** Walks the download server: continents, then countries, then regions. Tapping a map downloads it. */
@Composable
private fun RegionBrowser(extras: MapExtras, onDismiss: () -> Unit) {
    var path by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<MapRegion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<MapRegion?>(null) }
    LaunchedEffect(path) {
        rows = null
        error = null
        runCatching { extras.offline.browse(path) }
            .onSuccess { rows = it }
            .onFailure { error = "Couldn't load the list of maps. Check the connection and try again." }
    }

    val pick = confirm
    if (pick != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Download ${pick.name}?") },
            text = {
                Text(
                    (pick.size?.let { "It's about $it. " } ?: "") +
                        "Wi-Fi is best for big maps. It keeps downloading in the background.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    extras.offline.download(pick)
                    confirm = null
                    onDismiss()
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (path.isEmpty()) "Download an offline map" else path.trimEnd('/').substringAfterLast('/').replace('-', ' ').replaceFirstChar { it.uppercase() }) },
        text = {
            Column {
                when {
                    error != null -> Text(error!!, style = MaterialTheme.typography.bodyLarge)
                    rows == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(rows!!) { r ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                                    if (r.isFolder) path = r.path else confirm = r
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (r.isFolder) "${r.name}  ›" else r.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                r.size?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (path.isNotEmpty()) {
                TextButton(onClick = { path = path.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } }) {
                    Text("Back")
                }
            }
        },
    )
}

@Composable
private fun SubHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun ExtrasHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun ExtrasAction(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(vertical = 6.dp).heightIn(min = 56.dp)) { Text(label) }
}

@Composable
private fun ExtrasToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ExtrasChoice(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(name(option), style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.heightIn(min = 52.dp),
                )
            }
        }
    }
}
