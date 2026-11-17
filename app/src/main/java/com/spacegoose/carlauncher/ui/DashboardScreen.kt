package com.spacegoose.carlauncher.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacegoose.carlauncher.DashboardViewModel
import com.spacegoose.carlauncher.layout.DashboardLayout
import com.spacegoose.carlauncher.layout.WidgetType
import com.spacegoose.carlauncher.settings.LauncherSettings
import com.spacegoose.carlauncher.settings.MapPaneContent
import com.spacegoose.carlauncher.vehicle.SourceStatus

private val Gap = 16.dp
private const val OverlayWidthFraction = 0.44f

@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dock by vm.dockApps.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val notificationAccess by vm.hasNotificationAccess.collectAsStateWithLifecycle()
    val wavelengthInstalled by vm.wavelengthInstalled.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val speedStatus by vm.speedStatus.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()

    // Wide window: widgets + map pane. Narrow (we're one half of split-screen): widgets only, Waze has the rest.
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    var pendingWidgetId by rememberSaveable { mutableIntStateOf(LauncherSettings.NO_WIDGET) }
    val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.finishAddWavelengthWidget(pendingWidgetId, result.resultCode == Activity.RESULT_OK)
        pendingWidgetId = LauncherSettings.NO_WIDGET
    }
    val addWidget: () -> Unit = {
        val (id, request) = vm.beginAddWavelengthWidget()
        if (request != null) {
            pendingWidgetId = id
            bindWidget.launch(request)
        }
    }
    val openMap: () -> Unit = { (activity as? ComponentActivity)?.let(vm::openMap) }

    @Composable
    fun Widget(type: WidgetType, modifier: Modifier) {
        when (type) {
            WidgetType.CLOCK -> ClockCard(modifier)
            WidgetType.SPEEDOMETER -> SpeedCard(speed, speedStatus, settings.speedUnit, vm::onLocationPermissionResult, modifier)
            WidgetType.MEDIA -> MediaCard(
                nowPlaying = nowPlaying,
                wavelengthInstalled = wavelengthInstalled,
                hasNotificationAccess = notificationAccess,
                onPlayPause = vm.graph.media::playPause,
                onPrevious = vm.graph.media::previous,
                onNext = vm.graph.media::next,
                onOpenApp = { vm.graph.media.openSourceApp() },
                modifier = modifier,
            )
            WidgetType.WAVELENGTH_WIDGET ->
                WavelengthWidgetCard(vm.graph.wavelengthWidget, settings.wavelengthWidgetId, modifier)
        }
    }

    val widgetBound = remember(settings.wavelengthWidgetId) {
        vm.graph.wavelengthWidget.isBound(settings.wavelengthWidgetId)
    }
    val rows = DashboardLayout.Default.rows
        .map { row -> row.copy(widgets = row.widgets.filter { it != WidgetType.WAVELENGTH_WIDGET || widgetBound }) }
        .filter { it.widgets.isNotEmpty() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Wide: widgets and dock on the left, a full-height map box on the right (Samsung won't make a
        // floating window shorter than the app's minimum height, so the box gets all the height there is).
        // Narrow (we're one half of split-screen): widgets and dock only.
        Row(Modifier.fillMaxSize().padding(Gap), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Column(
                Modifier.weight(if (wide) 0.42f else 1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                rows.forEach { row ->
                    Row(
                        Modifier.weight(row.weight).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                    ) {
                        row.widgets.forEach { Widget(it, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
                Dock(
                    apps = dock,
                    onOpenDrawer = { vm.drawerOpen = true },
                    onOpenMap = openMap,
                    onOpenSettings = { vm.settingsOpen = true },
                    onLaunch = { vm.launchApp(context, it) },
                    onRemove = vm::removeFromDock,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                )
            }
            if (wide) {
                val paneModifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        val origin = IntArray(2)
                        activity?.window?.decorView?.getLocationOnScreen(origin)
                        vm.mapPaneBounds = Rect(
                            r.left.toInt() + origin[0], r.top.toInt() + origin[1],
                            r.right.toInt() + origin[0], r.bottom.toInt() + origin[1],
                        )
                    }
                if (settings.mapPaneContent == MapPaneContent.LIVE_MAP) {
                    LiveMapPane(
                        position = position,
                        locationAllowed = speedStatus != SourceStatus.NEEDS_PERMISSION,
                        mapApp = settings.mapApp,
                        source = settings.liveMapSource,
                        onOpenMap = openMap,
                        modifier = paneModifier,
                    )
                } else {
                    MapPane(
                        mapApp = settings.mapApp,
                        mode = settings.mapLaunchMode,
                        status = vm.mapStatus,
                        onOpenMap = openMap,
                        modifier = paneModifier,
                    )
                }
            }
        }

        // With the map floating in its box, overlays stay in the widget column so the map never covers them.
        val overlayModifier = if (wide) Modifier.fillMaxHeight().fillMaxWidth(OverlayWidthFraction) else Modifier.fillMaxSize()
        if (vm.drawerOpen) Box(overlayModifier) {
            AppDrawer(
                apps = apps,
                isDocked = vm::isDocked,
                onLaunch = { vm.launchApp(context, it) },
                onAddToDock = vm::addToDock,
                onRemoveFromDock = vm::removeFromDock,
                onAppInfo = { vm.openAppInfo(context, it) },
                onClose = { vm.drawerOpen = false },
            )
        }
        if (vm.settingsOpen) Box(overlayModifier) {
            SettingsPanel(
                settings = settings,
                onChange = vm::updateSettings,
                wavelengthInstalled = wavelengthInstalled,
                wavelengthWidgetAvailable = remember(wavelengthInstalled) { vm.wavelengthWidgetAvailable() },
                onAddWavelengthWidget = addWidget,
                onRemoveWavelengthWidget = vm::removeWavelengthWidget,
                onClose = { vm.settingsOpen = false },
            )
        }
    }
}
