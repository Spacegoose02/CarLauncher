package com.spacegoose.carlauncher

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.spacegoose.carlauncher.maps.LaunchStrategy
import com.spacegoose.carlauncher.maps.MapLaunchMode
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import android.os.SystemClock
import com.spacegoose.carlauncher.daynight.SunPosition
import com.spacegoose.carlauncher.phone.QuickContact
import com.spacegoose.carlauncher.settings.DrivingMode
import com.spacegoose.carlauncher.vehicle.DrivingState
import com.spacegoose.carlauncher.vehicle.obd.PairedDevice

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

    // Phone module
    val contacts = graph.contacts.contacts
    val contactsAllowed = graph.contacts.hasPermission

    // Car data module (Bluetooth OBD-II)
    val obdReading = graph.obd.reading
    val obdStatus = graph.obd.status

    /** Night where the car is (from the GPS position, or the clock until there is one), checked every minute. */
    val isNight: StateFlow<Boolean> = combine(position, minuteTicker()) { p, now ->
        SunPosition.isNight(p?.lat, p?.lon, now)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, SunPosition.isNight(null, null, System.currentTimeMillis()))

    private val drivingState = DrivingState()

    /** Bigger buttons: always, or while the car is moving, depending on the driving mode setting. */
    val drivingMode: StateFlow<Boolean> = combine(settings, speed) { s, reading ->
        val moving = drivingState.update(reading?.metersPerSecond, SystemClock.elapsedRealtime())
        when (s.drivingMode) {
            DrivingMode.OFF -> false
            DrivingMode.ALWAYS -> true
            DrivingMode.WHEN_MOVING -> moving
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var lastStartActionsAt = 0L

    /** Between onStart and onStop: the OBD adapter only connects while the dashboard is showing. */
    private var started = false

    /** Layouts and themes from the config server (the cached copy until a sync finishes). */
    val serverConfig = graph.config.config
    val configStatus = graph.config.status
    val configSyncing = graph.config.syncing

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
        viewModelScope.launch {
            settings.map { it.obdAdapterAddress }.distinctUntilChanged().collect { address ->
                if (started) graph.obd.start(address)
            }
        }
        viewModelScope.launch {
            val url = loadedSettings().configServerUrl
            if (url.isNotBlank()) graph.config.sync(url)
        }
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

    fun onStart() {
        started = true
        graph.media.start()
        graph.vehicle.start()
        graph.wavelengthWidget.startListening()
        graph.contacts.start()
        graph.obd.start(settings.value.obdAdapterAddress)
    }

    fun onStop() {
        started = false
        graph.media.stop()
        graph.vehicle.stop()
        graph.wavelengthWidget.stopListening()
        graph.contacts.stop()
        graph.obd.stop()
    }

    fun onContactsPermissionResult() = graph.contacts.start()

    fun onBluetoothPermissionResult() {
        graph.obd.stop()
        if (started) graph.obd.start(settings.value.obdAdapterAddress)
    }

    fun callContact(context: Context, contact: QuickContact) = graph.contacts.call(context, contact.number)

    fun openPhoneApp(context: Context) {
        if (!graph.contacts.openPhoneApp(context)) mapStatus = "No phone app found on this device."
    }

    fun pairedObdDevices(): List<PairedDevice> = graph.obd.pairedDevices()

    fun setObdAdapter(device: PairedDevice?) = updateSettings {
        it.copy(obdAdapterAddress = device?.address ?: "", obdAdapterName = device?.name ?: "")
    }

    /**
     * The car started (or the launcher came up fresh): open the map and resume the music, as set.
     * Runs at most once a minute, since a boot both creates the dashboard and sends the car-start intent.
     */
    fun runStartActions(activity: ComponentActivity, carStart: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (lastStartActionsAt != 0L && now - lastStartActionsAt < START_ACTIONS_GAP_MS) return
        lastStartActionsAt = now
        viewModelScope.launch {
            val s = loadedSettings()
            if (carStart && s.resumeMusicOnCarStart) resumeMusic()
            if (s.autoLaunchMap) {
                delay(AUTO_LAUNCH_DELAY_MS)
                if (!activity.isDestroyed) openMap(activity)
            }
        }
    }

    /** Waits for the music app's session to show up (Wavelength takes a moment to connect), then plays. */
    private fun resumeMusic() {
        viewModelScope.launch {
            withTimeoutOrNull(MUSIC_WAIT_MS) { nowPlaying.first { it != null } }
            graph.media.play()
        }
    }

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
                graph.config.clear()
                graph.settings.update { it.copy(themeId = "") }
            } else {
                graph.config.sync(trimmed)
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
        const val AUTO_LAUNCH_DELAY_MS = 1200L
        const val START_ACTIONS_GAP_MS = 60_000L
        const val MUSIC_WAIT_MS = 15_000L
    }

    private fun minuteTicker() = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
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
