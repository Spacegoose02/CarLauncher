package com.spacegoose.carlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.integrations.sports.SportsState
import com.spacegoose.carlauncher.integrations.sports.TeamPick
import com.spacegoose.carlauncher.integrations.weather.WeatherState
import com.spacegoose.carlauncher.settings.LauncherSettings
import kotlinx.coroutines.launch

@Composable
internal fun WeatherSettings(
    settings: LauncherSettings,
    state: WeatherState,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
) {
    Hint("The Weather module shows current conditions, rain in the next two hours and alerts from your Weather Scout. Add it with the \"Map with weather and scores\" layout, or to your own layouts on the config server.")
    SavedField("Weather Scout address", settings.weatherServerUrl, KeyboardType.Uri) { v ->
        onChange { it.copy(weatherServerUrl = v.ifBlank { LauncherSettings.DEFAULT_WEATHER_SERVER }) }
    }
    SavedField("App key (only if Weather Scout needs sign-in)", settings.weatherKey, KeyboardType.Password) { v ->
        onChange { it.copy(weatherKey = v) }
    }
    Toggle("Weather where the car is (otherwise Weather Scout's home location)", settings.weatherFollowsCar) { v ->
        onChange { it.copy(weatherFollowsCar = v) }
    }
    state.problem?.let { Hint(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SportsSettings(
    settings: LauncherSettings,
    state: SportsState,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onSearch: suspend (String) -> Result<List<TeamPick>>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Hint("The Sports module shows live scores, next games and results from Stat Scraps.")
    SavedField("Stat Scraps address", settings.sportsServerUrl, KeyboardType.Uri) { v ->
        onChange { it.copy(sportsServerUrl = v.ifBlank { LauncherSettings.DEFAULT_SPORTS_SERVER }) }
    }
    SavedField("Account key (optional)", settings.sportsKey, KeyboardType.Password) { v -> onChange { it.copy(sportsKey = v) } }
    if (settings.sportsKey.isNotBlank()) {
        Hint("Showing the teams you follow on Stat Scraps. Clear the key to pick teams here instead.")
        state.problem?.let { Hint(it) }
        return
    }
    Hint("With your key (Stat Scraps, Account, Connect other apps) it shows the teams you follow there. Or pick teams here:")

    // Picked teams. Names come from the last scores, since only "sport:id" is saved.
    val names = state.rows.associate { it.key to it.name }
    var added by remember { mutableStateOf(mapOf<String, String>()) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        settings.sportsTeams.forEach { key ->
            InputChip(
                selected = false,
                onClick = { onRemove(key) },
                label = { Text(added[key] ?: names[key] ?: key, style = MaterialTheme.typography.bodyLarge) },
                trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(20.dp)) },
                modifier = Modifier.heightIn(min = 52.dp),
            )
        }
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TeamPick>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val search = {
        focus.clearFocus()
        scope.launch {
            message = "Searching..."
            onSearch(query).fold(
                onSuccess = { found ->
                    results = found
                    message = if (found.isEmpty()) "No teams found." else null
                },
                onFailure = { message = "Couldn't search Stat Scraps." },
            )
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Find a team, e.g. Hokies or Commanders") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
    if (query.trim().length >= 2) Action("Search") { search() }
    message?.let { Hint(it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        results.filter { it.key !in settings.sportsTeams }.forEach { pick ->
            AssistChip(
                onClick = {
                    added = added + (pick.key to pick.name)
                    onAdd(pick.key)
                },
                label = { Text("${pick.name} (${pick.sport.uppercase()})", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)) },
                modifier = Modifier.heightIn(min = 52.dp),
            )
        }
    }
    state.problem?.let { Hint(it) }
}

/** A text setting that saves on Done or the Save button, so half-typed addresses aren't used. */
@Composable
private fun SavedField(label: String, saved: String, type: KeyboardType, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    val focus = LocalFocusManager.current
    val save = {
        focus.clearFocus()
        onSave(text.trim())
    }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        if (text.trim() != saved) Row { Action("Save") { save() } }
    }
}
