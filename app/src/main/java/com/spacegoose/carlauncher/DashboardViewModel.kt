package com.spacegoose.carlauncher

import android.app.Activity
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

    val dockApps: StateFlow<List<AppEntry>> = combine(settings, apps) { s, list -> resolveDock(s, list) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var drawerOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var mapStatus by mutableStateOf<String?>(null)

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

    fun onStart() {
        graph.media.start()
        graph.vehicle.start()
        graph.wavelengthWidget.startListening()
    }

    fun onStop() {
        graph.media.stop()
        graph.vehicle.stop()
        graph.wavelengthWidget.stopListening()
    }

    fun onLocationPermissionResult() {
        graph.vehicle.stop()
        graph.vehicle.start()
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

    fun openMap(activity: Activity) {
        val s = settings.value
        val outcome = graph.maps.launch(activity, s.mapApp, s.mapLaunchMode, mapPaneBounds)
        mapStatus = outcome.message
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

    private fun resolveDock(s: LauncherSettings, list: List<AppEntry>): List<AppEntry> {
        val keys = s.dockApps
        if (keys != null) return keys.mapNotNull { k -> list.firstOrNull { it.key == k } }
        return AppRepository.DEFAULT_DOCK_PACKAGES
            .mapNotNull { pkg -> list.firstOrNull { it.packageName == pkg } }
            .distinctBy { it.packageName }
            .take(6)
    }
}
