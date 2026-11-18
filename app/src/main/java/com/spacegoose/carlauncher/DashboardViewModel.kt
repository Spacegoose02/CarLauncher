package com.spacegoose.carlauncher

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.spacegoose.carlauncher.maps.LaunchStrategy
import com.spacegoose.carlauncher.maps.MapLaunchMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spacegoose.carlauncher.apps.AppEntry
import com.spacegoose.carlauncher.apps.AppRepository
import com.spacegoose.carlauncher.settings.LauncherSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.spacegoose.carlauncher.integrations.sports.Scores
import com.spacegoose.carlauncher.integrations.sports.TeamPick
import com.spacegoose.carlauncher.layout.DashboardLayout
import com.spacegoose.carlauncher.layout.WidgetType
import com.spacegoose.carlauncher.vehicle.SpeedUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    val graph: AppGraph = (app as CarLauncherApp).graph

    val settings: StateFlow<LauncherSettings> =
        graph.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    val apps: StateFlow<List<AppEntry>> = graph.apps.apps
    val nowPlaying = graph.media.nowPlaying
    val hasNotificationAccess = graph.media.hasNotificationAccess
    val wavelengthInstalled = graph.media.wavelengthInstalled
    val speed = graph.vehicle.speed
    val speedStatus = graph.vehicle.status
    val position = graph.vehicle.position

    /** Layouts and themes from the config server (the cached copy until a sync finishes). */
    val serverConfig = graph.config.config
    val configStatus = graph.config.status
    val configSyncing = graph.config.syncing

    val weather = graph.weather.state
    val sports = graph.sports.state

    val dockApps: StateFlow<List<AppEntry>> = combine(settings, apps) { s, list -> resolveDock(s, list) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var drawerOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var mapStatus by mutableStateOf<String?>(null)

    /** True after the map opened inside its box this session; Home then puts it back there. */
    var mapInBox = false
        private set

    /** The map pane's bounds in screen pixels, for the floating-window fallback. */
    var mapPaneBounds: Rect? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModelScope.launch { graph.apps.refresh() }
        }
    }

    init {
        viewModelScope.launch { settings.collect { graph.media.preference = it.mediaSource } }
        viewModelScope.launch { graph.apps.refresh() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
        super.onCleared()
    }

    suspend fun loadedSettings(): LauncherSettings = graph.settings.settings.first()

    private var started = false
    private var configWatch: Job? = null

    /**
     * While the dashboard is on screen, keep a request open to the config server so edits made in its web
     * editor show up straight away. Stopped when the launcher is hidden; it catches up when it comes back.
     */
    private fun restartConfigWatch() {
        configWatch?.cancel()
        configWatch = null
        if (!started) return
        configWatch = viewModelScope.launch {
            val url = loadedSettings().configServerUrl
            if (url.isNotBlank()) graph.config.watch(url)
        }
    }

    fun onStart() {
        started = true
        restartConfigWatch()
        graph.media.start()
        graph.vehicle.start()
        graph.wavelengthWidget.startListening()
        startIntegrations()
    }

    fun onStop() {
        started = false
        restartConfigWatch()
        graph.media.stop()
        graph.vehicle.stop()
        graph.wavelengthWidget.stopListening()
        integrationJobs.forEach { it.cancel() }
        integrationJobs = emptyList()
    }

    // Weather and scores: polled only while the dashboard is on screen and the layout shows the module.

    private var integrationJobs: List<Job> = emptyList()

    private data class WeatherQuery(val url: String, val key: String, val metric: Boolean, val followCar: Boolean)
    private data class SportsQuery(val url: String, val key: String, val teams: List<String>)

    private fun showing(type: WidgetType) = combine(settings, serverConfig) { s, c ->
        DashboardLayout.resolve(s.layoutId, c.layouts).contains(type)
    }

    private fun startIntegrations() {
        integrationJobs.forEach { it.cancel() }
        val weatherJob = viewModelScope.launch {
            combine(settings, showing(WidgetType.WEATHER)) { s, shown ->
                if (shown) WeatherQuery(s.weatherServerUrl, s.weatherKey, s.speedUnit == SpeedUnit.KMH, s.weatherFollowsCar) else null
            }.distinctUntilChanged().collectLatest { q ->
                if (q == null) return@collectLatest
                // Give GPS a moment for a first fix, so the first reading is where the car is.
                if (q.followCar) withTimeoutOrNull(FIRST_FIX_WAIT_MS) { position.first { it != null } }
                while (true) {
                    refreshWeatherNow(q)
                    delay(WEATHER_EVERY_MS)
                }
            }
        }
        val sportsJob = viewModelScope.launch {
            combine(settings, showing(WidgetType.SPORTS)) { s, shown ->
                if (shown) SportsQuery(s.sportsServerUrl, s.sportsKey, s.sportsTeams) else null
            }.distinctUntilChanged().collectLatest { q ->
                if (q == null) return@collectLatest
                while (true) {
                    graph.sports.refresh(q.url, q.key, q.teams)
                    delay(Scores.refreshDelayMs(graph.sports.state.value.rows))
                }
            }
        }
        integrationJobs = listOf(weatherJob, sportsJob)
    }

    private suspend fun refreshWeatherNow(q: WeatherQuery) {
        val at = if (q.followCar) position.value else null
        graph.weather.refresh(q.url, q.key, q.metric, at)
    }

    /** Tapping the weather card. */
    fun refreshWeather() {
        val s = settings.value
        viewModelScope.launch {
            refreshWeatherNow(WeatherQuery(s.weatherServerUrl, s.weatherKey, s.speedUnit == SpeedUnit.KMH, s.weatherFollowsCar))
        }
    }

    /** Tapping the scores card. */
    fun refreshSports() {
        val s = settings.value
        viewModelScope.launch { graph.sports.refresh(s.sportsServerUrl, s.sportsKey, s.sportsTeams) }
    }

    suspend fun searchTeams(query: String): Result<List<TeamPick>> =
        graph.sports.search(settings.value.sportsServerUrl, query)

    fun addSportsTeam(key: String) = updateSettings { s ->
        s.copy(sportsTeams = (s.sportsTeams + key).distinct().take(MAX_SPORTS_TEAMS))
    }

    fun removeSportsTeam(key: String) = updateSettings { s -> s.copy(sportsTeams = s.sportsTeams - key) }

    fun onLocationPermissionResult() {
        graph.vehicle.stop()
        graph.vehicle.start()
    }

    /** Home was pressed: bring a floating map back into its box (it may have been minimized or covered). */
    fun onHomePressed(activity: ComponentActivity) {
        drawerOpen = false
        settingsOpen = false
        if (mapInBox && settings.value.reopenMapOnHome) openMap(activity)
    }

    fun onBack() {
        when {
            settingsOpen -> settingsOpen = false
            drawerOpen -> drawerOpen = false
        }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { graph.settings.update(transform) }
    }

    fun openMap(activity: ComponentActivity) {
        val s = settings.value
        val outcome = graph.maps.launch(activity, s.mapApp, s.mapLaunchMode, mapPaneBounds, s.freeformKnownBroken, s.floatingMapShrink)
        mapStatus = outcome.message
        val strategy = outcome.strategy ?: return
        viewModelScope.launch {
            delay(VERIFY_DELAY_MS)
            verifyMapLaunch(activity, strategy, s)
        }
    }

    /**
     * Android doesn't say whether a window mode was honoured, so look at the result: a floating window
     * leaves the dashboard visible, split-screen puts it in multi-window, and full screen hides it.
     */
    private fun verifyMapLaunch(activity: ComponentActivity, strategy: LaunchStrategy, s: LauncherSettings) {
        if (activity.isDestroyed) return
        val label = s.mapApp.label
        val dashboardVisible = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        when (strategy) {
            LaunchStrategy.FREEFORM -> if (dashboardVisible) {
                mapInBox = true
            } else {
                mapInBox = false
                if (s.mapLaunchMode == MapLaunchMode.AUTO) {
                    updateSettings { it.copy(freeformKnownBroken = true) }
                    mapStatus = "This device opened $label full screen instead of inside the box, " +
                        "so Automatic will use split-screen from now on."
                } else {
                    mapStatus = "This device opened $label full screen instead of inside the box. " +
                        "Try Split-screen in settings."
                }
            }
            LaunchStrategy.LAUNCH_ADJACENT, LaunchStrategy.ENTER_SPLIT_THEN_ADJACENT ->
                mapStatus = if (activity.isInMultiWindowMode) "$label is open in split-screen."
                else "Split-screen didn't start, so $label opened full screen. " +
                    "Turn on the split-screen helper in settings, or pick another mode."
            LaunchStrategy.FULLSCREEN -> Unit
        }
    }

    /** Saves the config server address and fetches its layouts and themes; a blank address forgets them. */
    fun setConfigServer(url: String) {
        val trimmed = url.trim()
        viewModelScope.launch {
            graph.settings.update { it.copy(configServerUrl = trimmed) }
            if (trimmed.isEmpty()) {
                configWatch?.cancel()
                graph.config.clear()
                graph.settings.update { it.copy(themeId = "") }
            } else {
                graph.config.sync(trimmed)
                restartConfigWatch()
            }
        }
    }

    fun syncConfig() {
        val url = settings.value.configServerUrl
        if (url.isNotBlank()) viewModelScope.launch { graph.config.sync(url) }
    }

    fun launchApp(context: Context, entry: AppEntry) {
        drawerOpen = false
        graph.apps.launch(context, entry)
    }

    fun openAppInfo(context: Context, entry: AppEntry) = graph.apps.openAppInfo(context, entry)

    fun isDocked(entry: AppEntry): Boolean = dockApps.value.any { it.key == entry.key }

    fun addToDock(entry: AppEntry) = updateSettings { s ->
        val current = s.dockApps ?: dockApps.value.map { it.key }
        s.copy(dockApps = (current + entry.key).distinct().take(LauncherSettings.MAX_DOCK_APPS))
    }

    fun removeFromDock(entry: AppEntry) = updateSettings { s ->
        val current = s.dockApps ?: dockApps.value.map { it.key }
        s.copy(dockApps = current - entry.key)
    }

    // Wavelength's home-screen widget, hosted in the dashboard

    fun wavelengthWidgetAvailable(): Boolean = graph.wavelengthWidget.isAvailable()

    /** Returns null when bound right away, or the system "allow widget" intent to launch. */
    fun beginAddWavelengthWidget(): Pair<Int, Intent?> {
        val host = graph.wavelengthWidget
        val id = host.allocateId()
        return if (host.bindIfAllowed(id)) {
            saveWidget(id)
            id to null
        } else {
            id to host.bindRequest(id)
        }
    }

    fun finishAddWavelengthWidget(id: Int, allowed: Boolean) {
        if (allowed && graph.wavelengthWidget.isBound(id)) saveWidget(id) else graph.wavelengthWidget.delete(id)
    }

    fun removeWavelengthWidget() {
        graph.wavelengthWidget.delete(settings.value.wavelengthWidgetId)
        updateSettings { it.copy(wavelengthWidgetId = LauncherSettings.NO_WIDGET) }
    }

    private fun saveWidget(id: Int) {
        val old = settings.value.wavelengthWidgetId
        if (old != id) graph.wavelengthWidget.delete(old)
        updateSettings { it.copy(wavelengthWidgetId = id) }
    }

    private companion object {
        const val VERIFY_DELAY_MS = 3000L
        const val WEATHER_EVERY_MS = 10 * 60_000L
        const val MAX_SPORTS_TEAMS = 20
        const val FIRST_FIX_WAIT_MS = 8000L
    }

    private fun resolveDock(s: LauncherSettings, list: List<AppEntry>): List<AppEntry> {
        val keys = s.dockApps
        if (keys != null) return keys.mapNotNull { k -> list.firstOrNull { it.key == k } }
        return AppRepository.DEFAULT_DOCK_PACKAGES
            .mapNotNull { pkg -> list.firstOrNull { it.packageName == pkg } }
            .distinctBy { it.packageName }
            .take(6)
    }
}
