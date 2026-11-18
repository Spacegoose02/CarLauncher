package com.spacegoose.carlauncher.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.spacegoose.carlauncher.config.ServerConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.maps.MapApp
import com.spacegoose.carlauncher.maps.MapLaunchMode
import com.spacegoose.carlauncher.maps.SplitScreenAccessibilityService
import com.spacegoose.carlauncher.settings.AccentColor
import com.spacegoose.carlauncher.settings.LauncherSettings
import com.spacegoose.carlauncher.settings.LiveMapSource
import com.spacegoose.carlauncher.settings.MapPaneContent
import com.spacegoose.carlauncher.layout.DashboardLayout
import com.spacegoose.carlauncher.settings.MediaSourcePreference
import com.spacegoose.carlauncher.settings.ThemeMode
import com.spacegoose.carlauncher.vehicle.SpeedUnit

@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    serverConfig: ServerConfig,
    configStatus: String?,
    configSyncing: Boolean,
    onSetConfigServer: (String) -> Unit,
    onSyncConfig: () -> Unit,
    wavelengthInstalled: Boolean,
    wavelengthWidgetAvailable: Boolean,
    onAddWavelengthWidget: () -> Unit,
    onRemoveWavelengthWidget: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    fun open(action: String) = context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Launcher settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = onClose, modifier = Modifier.size(TouchTarget + 8.dp)) {
                    Icon(Icons.Filled.Close, "Close", Modifier.size(36.dp))
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Section("Layout")
                Choice(
                    "Screen layout",
                    DashboardLayout.available(serverConfig.layouts),
                    DashboardLayout.resolve(settings.layoutId, serverConfig.layouts),
                    { it.name },
                ) { v -> onChange { it.copy(layoutId = v.id) } }
                Section("Map")
                Choice("Map app", MapApp.entries, settings.mapApp, { it.label }) { v -> onChange { it.copy(mapApp = v) } }
                Choice("The map box shows", MapPaneContent.entries, settings.mapPaneContent, { it.label }) { v ->
                    onChange { it.copy(mapPaneContent = v) }
                }
                if (settings.mapPaneContent == MapPaneContent.LIVE_MAP) {
                    Choice("Live Map from", LiveMapSource.entries, settings.liveMapSource, { it.label }) { v ->
                        onChange { it.copy(liveMapSource = v) }
                    }
                }
                Choice("Open the map as", MapLaunchMode.entries, settings.mapLaunchMode, { it.label }) { v ->
                    onChange { it.copy(mapLaunchMode = v) }
                }
                Toggle("Open the map when the launcher starts", settings.autoLaunchMap) { v ->
                    onChange { it.copy(autoLaunchMap = v) }
                }
                Stepper(
                    label = "Floating map size",
                    value = if (settings.floatingMapShrink == 0) "Fills the box" else "${settings.floatingMapShrink}% smaller",
                    onSmaller = { onChange { it.copy(floatingMapShrink = (it.floatingMapShrink + 4).coerceAtMost(40)) } },
                    onBigger = { onChange { it.copy(floatingMapShrink = (it.floatingMapShrink - 4).coerceAtLeast(0)) } },
                )
                Hint("If the floating map spills out of its box, make it smaller here, then close the map and open it again.")
                Toggle("Home puts the map back in its box", settings.reopenMapOnHome) { v ->
                    onChange { it.copy(reopenMapOnHome = v) }
                }
                if (settings.freeformKnownBroken) {
                    Hint("Automatic uses split-screen because the map didn't open inside the box last time.")
                    Action("Try inside the map box again") { onChange { it.copy(freeformKnownBroken = false) } }
                }
                if (Build.VERSION.SDK_INT < 32) {
                    val running = SplitScreenAccessibilityService.isRunning
                    Hint(
                        if (running) "Split-screen helper is on: the map can open beside the dashboard."
                        else "This Android version can't start split-screen on its own. Turn on the " +
                            "\"Car Launcher split-screen helper\" in Accessibility to allow it."
                    )
                    if (!running) Action("Open Accessibility settings") { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                }
                if (settings.mapPaneContent == MapPaneContent.OPEN_MAP) MapExtrasSettingsSection()

                Section("Media")
                Choice("Media card follows", MediaSourcePreference.entries, settings.mediaSource, { it.label }) { v ->
                    onChange { it.copy(mediaSource = v) }
                }
                if (!wavelengthInstalled) Hint("Wavelength isn't installed on this device.")
                if (wavelengthWidgetAvailable) {
                    if (settings.wavelengthWidgetId == LauncherSettings.NO_WIDGET) {
                        Action("Show Wavelength's Now playing widget", onAddWavelengthWidget)
                    } else {
                        Action("Remove Wavelength's widget", onRemoveWavelengthWidget)
                    }
                }
                Action("Let the media card follow other apps") { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

                Section("Speed")
                Choice("Units", SpeedUnit.entries, settings.speedUnit, { it.label }) { v -> onChange { it.copy(speedUnit = v) } }

                Section("Look")
                val serverTheme = settings.themeId.takeIf { it.isNotEmpty() }?.let(serverConfig::theme)
                if (serverConfig.themes.isNotEmpty()) {
                    val options = listOf<String?>(null) + serverConfig.themes.map { it.id }
                    Choice("Colours", options, serverTheme?.id, { id -> id?.let { serverConfig.theme(it)?.name } ?: "Built-in" }) { v ->
                        onChange { it.copy(themeId = v ?: "") }
                    }
                }
                if (serverTheme == null) {
                    Choice("Theme", ThemeMode.entries, settings.themeMode, { it.label }) { v -> onChange { it.copy(themeMode = v) } }
                    AccentPicker(settings.accent) { v -> onChange { it.copy(accent = v) } }
                }
                Toggle("Full screen (hide status bar, navigation bar and taskbar)", settings.hideSystemBars) { v ->
                    onChange { it.copy(hideSystemBars = v) }
                }

                Section("Config server")
                ConfigServer(settings.configServerUrl, configStatus, configSyncing, onSetConfigServer, onSyncConfig)

                Section("Launcher")
                Action("Choose default home app") { open(Settings.ACTION_HOME_SETTINGS) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ConfigServer(
    savedUrl: String,
    status: String?,
    syncing: Boolean,
    onSave: (String) -> Unit,
    onSync: () -> Unit,
) {
    var text by remember(savedUrl) { mutableStateOf(savedUrl) }
    val focus = LocalFocusManager.current
    val save = {
        focus.clearFocus()
        onSave(text)
    }
    Hint("Layouts and themes made on your own config server (the Docker app in the repo's server folder).")
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Server address, e.g. 192.168.1.20:8080") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (text.trim() != savedUrl) {
            Action(if (text.isBlank()) "Stop using a server" else "Save and sync") { save() }
        } else if (savedUrl.isNotEmpty()) {
            Action(if (syncing) "Syncing..." else "Sync now") { if (!syncing) onSync() }
        }
    }
    status?.let { Hint(it) }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(vertical = 6.dp).heightIn(min = 56.dp)) {
        Text(label)
    }
}

@Composable
private fun Stepper(label: String, value: String, onSmaller: () -> Unit, onBigger: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onSmaller, modifier = Modifier.heightIn(min = 56.dp)) { Text("Smaller") }
        FilledTonalButton(onClick = onBigger, modifier = Modifier.heightIn(min = 56.dp)) { Text("Bigger") }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
private fun <T> Choice(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
            AccentColor.entries.forEach { accent ->
                Box(
                    Modifier
                        .size(TouchTarget)
                        .background(Color(accent.argb), CircleShape)
                        .then(
                            if (accent == selected) Modifier.border(4.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            else Modifier
                        )
                        .clickable { onSelect(accent) },
                )
            }
        }
    }
}
