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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.spacegoose.carlauncher.settings.MediaSourcePreference
import com.spacegoose.carlauncher.settings.ThemeMode
import com.spacegoose.carlauncher.vehicle.SpeedUnit

@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
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
                Section("Map")
                Choice("Map app", MapApp.entries, settings.mapApp, { it.label }) { v -> onChange { it.copy(mapApp = v) } }
                Choice("Open the map as", MapLaunchMode.entries, settings.mapLaunchMode, { it.label }) { v ->
                    onChange { it.copy(mapLaunchMode = v) }
                }
                Toggle("Open the map when the launcher starts", settings.autoLaunchMap) { v ->
                    onChange { it.copy(autoLaunchMap = v) }
                }
                if (Build.VERSION.SDK_INT < 32) {
                    val running = SplitScreenAccessibilityService.isRunning
                    Hint(
                        if (running) "Split-screen helper is on: the map can open beside the dashboard."
                        else "This Android version can't start split-screen on its own. Turn on the " +
                            "\"Car Launcher split-screen helper\" in Accessibility to allow it, or use a floating window."
                    )
                    if (!running) Action("Open Accessibility settings") { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                }

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
                Choice("Theme", ThemeMode.entries, settings.themeMode, { it.label }) { v -> onChange { it.copy(themeMode = v) } }
                AccentPicker(settings.accent) { v -> onChange { it.copy(accent = v) } }
                Toggle("Hide status and navigation bars", settings.hideSystemBars) { v ->
                    onChange { it.copy(hideSystemBars = v) }
                }

                Section("Launcher")
                Action("Choose default home app") { open(Settings.ACTION_HOME_SETTINGS) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
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
