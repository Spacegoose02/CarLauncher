package com.spacegoose.carlauncher.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spacegoose.carlauncher.maps.MapApp
import com.spacegoose.carlauncher.maps.MapLaunchMode
import com.spacegoose.carlauncher.vehicle.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore by preferencesDataStore(name = "launcher_settings")

/**
 * Settings live in one DataStore so a later "profile sync" feature can export/import them as a unit.
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.launcherDataStore

    val settings: Flow<LauncherSettings> = store.data.map(::read)

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private object Keys {
        val MAP_APP = stringPreferencesKey("map_app")
        val MAP_MODE = stringPreferencesKey("map_launch_mode")
        val MAP_PANE = stringPreferencesKey("map_pane_content")
        val LIVE_SOURCE = stringPreferencesKey("live_map_source")
        val AUTO_MAP = booleanPreferencesKey("auto_launch_map")
        val REOPEN_MAP = booleanPreferencesKey("reopen_map_on_home")
        val FREEFORM_BROKEN = booleanPreferencesKey("freeform_known_broken")
        val FLOAT_SHRINK = intPreferencesKey("floating_map_shrink")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val HIDE_BARS = booleanPreferencesKey("hide_system_bars")
        val MEDIA_SOURCE = stringPreferencesKey("media_source")
        val WL_WIDGET = intPreferencesKey("wavelength_widget_id")
        val DOCK = stringPreferencesKey("dock_apps")
        val LAYOUT = stringPreferencesKey("layout_id")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CONFIG_URL = stringPreferencesKey("config_server_url")
        val WEATHER_URL = stringPreferencesKey("weather_server_url")
        val WEATHER_KEY = stringPreferencesKey("weather_key")
        val WEATHER_FOLLOWS_CAR = booleanPreferencesKey("weather_follows_car")
        val SPORTS_URL = stringPreferencesKey("sports_server_url")
        val SPORTS_KEY = stringPreferencesKey("sports_key")
        val SPORTS_TEAMS = stringPreferencesKey("sports_teams")
    }

    private fun read(p: Preferences): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            mapApp = enumOr(p[Keys.MAP_APP], d.mapApp),
            mapLaunchMode = enumOr(p[Keys.MAP_MODE], d.mapLaunchMode),
            mapPaneContent = enumOr(p[Keys.MAP_PANE], d.mapPaneContent),
            liveMapSource = enumOr(p[Keys.LIVE_SOURCE], d.liveMapSource),
            autoLaunchMap = p[Keys.AUTO_MAP] ?: d.autoLaunchMap,
            reopenMapOnHome = p[Keys.REOPEN_MAP] ?: d.reopenMapOnHome,
            freeformKnownBroken = p[Keys.FREEFORM_BROKEN] ?: d.freeformKnownBroken,
            floatingMapShrink = p[Keys.FLOAT_SHRINK] ?: d.floatingMapShrink,
            speedUnit = enumOr(p[Keys.SPEED_UNIT], d.speedUnit),
            themeMode = enumOr(p[Keys.THEME], d.themeMode),
            accent = enumOr(p[Keys.ACCENT], d.accent),
            hideSystemBars = p[Keys.HIDE_BARS] ?: d.hideSystemBars,
            mediaSource = enumOr(p[Keys.MEDIA_SOURCE], d.mediaSource),
            wavelengthWidgetId = p[Keys.WL_WIDGET] ?: d.wavelengthWidgetId,
            layoutId = p[Keys.LAYOUT] ?: d.layoutId,
            themeId = p[Keys.THEME_ID] ?: d.themeId,
            configServerUrl = p[Keys.CONFIG_URL] ?: d.configServerUrl,
            dockApps = p[Keys.DOCK]?.split('\n')?.filter { it.isNotBlank() },
            weatherServerUrl = p[Keys.WEATHER_URL] ?: d.weatherServerUrl,
            weatherKey = p[Keys.WEATHER_KEY] ?: d.weatherKey,
            weatherFollowsCar = p[Keys.WEATHER_FOLLOWS_CAR] ?: d.weatherFollowsCar,
            sportsServerUrl = p[Keys.SPORTS_URL] ?: d.sportsServerUrl,
            sportsKey = p[Keys.SPORTS_KEY] ?: d.sportsKey,
            sportsTeams = p[Keys.SPORTS_TEAMS]?.split(',')?.filter { it.isNotBlank() } ?: d.sportsTeams,
        )
    }

    private fun write(p: MutablePreferences, s: LauncherSettings) {
        p[Keys.MAP_APP] = s.mapApp.name
        p[Keys.MAP_MODE] = s.mapLaunchMode.name
        p[Keys.MAP_PANE] = s.mapPaneContent.name
        p[Keys.LIVE_SOURCE] = s.liveMapSource.name
        p[Keys.AUTO_MAP] = s.autoLaunchMap
        p[Keys.REOPEN_MAP] = s.reopenMapOnHome
        p[Keys.FREEFORM_BROKEN] = s.freeformKnownBroken
        p[Keys.FLOAT_SHRINK] = s.floatingMapShrink
        p[Keys.SPEED_UNIT] = s.speedUnit.name
        p[Keys.THEME] = s.themeMode.name
        p[Keys.ACCENT] = s.accent.name
        p[Keys.HIDE_BARS] = s.hideSystemBars
        p[Keys.MEDIA_SOURCE] = s.mediaSource.name
        p[Keys.WL_WIDGET] = s.wavelengthWidgetId
        p[Keys.LAYOUT] = s.layoutId
        p[Keys.THEME_ID] = s.themeId
        p[Keys.CONFIG_URL] = s.configServerUrl
        p[Keys.WEATHER_URL] = s.weatherServerUrl
        p[Keys.WEATHER_KEY] = s.weatherKey
        p[Keys.WEATHER_FOLLOWS_CAR] = s.weatherFollowsCar
        p[Keys.SPORTS_URL] = s.sportsServerUrl
        p[Keys.SPORTS_KEY] = s.sportsKey
        p[Keys.SPORTS_TEAMS] = s.sportsTeams.joinToString(",")
        val dock = s.dockApps
        if (dock == null) p.remove(Keys.DOCK) else p[Keys.DOCK] = dock.joinToString("\n")
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
