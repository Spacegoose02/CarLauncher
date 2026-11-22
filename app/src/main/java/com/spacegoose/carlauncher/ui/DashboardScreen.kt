package com.spacegoose.carlauncher.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacegoose.carlauncher.DashboardViewModel
import com.spacegoose.carlauncher.layout.DashboardLayout
import com.spacegoose.carlauncher.layout.WidgetType
import com.spacegoose.carlauncher.media.NowPlaying
import com.spacegoose.carlauncher.settings.LauncherSettings
import com.spacegoose.carlauncher.settings.MapPaneContent
import com.spacegoose.carlauncher.vehicle.SourceStatus

private val Gap = 16.dp
private const val OverlayWidthFraction = 0.44f
private const val MinOverlayFraction = 0.3f
private const val ShortScreenDp = 640
private const val WideAspect = 1.7f

@Composable
fun DashboardScreen(vm: DashboardViewModel, artTint: Color?) {
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
    val serverConfig by vm.serverConfig.collectAsStateWithLifecycle()
    val configStatus by vm.configStatus.collectAsStateWithLifecycle()
    val configSyncing by vm.configSyncing.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val contactsAllowed by vm.contactsAllowed.collectAsStateWithLifecycle()
    val obdReading by vm.obdReading.collectAsStateWithLifecycle()
    val obdStatus by vm.obdStatus.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val sports by vm.sports.collectAsStateWithLifecycle()
    // The background glows with the album art while a song plays (see [musicAccent]).
    val musicTint = musicAccent(settings, nowPlaying, artTint)

    // Wide window: widgets + map pane. Narrow (we're one half of split-screen): widgets only, Waze has the rest.
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 600
    // Short screens (head units such as the Atoto S8 at 1024x600 or 1280x720): tighter gaps and a dock that
    // takes a fixed share of the height, so the cards above keep enough room.
    val short = configuration.screenHeightDp < ShortScreenDp ||
        configuration.screenWidthDp >= configuration.screenHeightDp * WideAspect
    val gap = if (short) 10.dp else Gap
    val sizes = LocalCarSizes.current
    val dockHeight = if (short) (configuration.screenHeightDp.dp * 0.17f).coerceIn(72.dp, sizes.dock) else sizes.dock

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

    // The map module: an OpenStreetMap map, the Waze Live Map, or the button that opens the map app over it.
    @Composable
    fun MapModule(modifier: Modifier) {
        val paneModifier = modifier.onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            val origin = IntArray(2)
            activity?.window?.decorView?.getLocationOnScreen(origin)
            vm.mapPaneBounds = Rect(
                r.left.toInt() + origin[0], r.top.toInt() + origin[1],
                r.right.toInt() + origin[0], r.bottom.toInt() + origin[1],
            )
        }
        when (settings.mapPaneContent) {
            MapPaneContent.OPEN_MAP -> OpenMapPane(
                position = position,
                locationAllowed = speedStatus != SourceStatus.NEEDS_PERMISSION,
                mapApp = settings.mapApp,
                onOpenMap = openMap,
                modifier = paneModifier,
                speedUnit = settings.speedUnit,
            )
            MapPaneContent.LIVE_MAP -> LiveMapPane(
                position = position,
                locationAllowed = speedStatus != SourceStatus.NEEDS_PERMISSION,
                mapApp = settings.mapApp,
                source = settings.liveMapSource,
                onOpenMap = openMap,
                modifier = paneModifier,
            )
            MapPaneContent.BUTTON -> MapPane(
                mapApp = settings.mapApp,
                mode = settings.mapLaunchMode,
                status = vm.mapStatus,
                onOpenMap = openMap,
                modifier = paneModifier,
            )
        }
    }

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
                onOpenNowPlaying = {
                    vm.drawerOpen = false
                    vm.settingsOpen = false
                    vm.nowPlayingOpen = true
                },
                artTint = artTint,
                modifier = modifier,
            )
            WidgetType.WAVELENGTH_WIDGET ->
                WavelengthWidgetCard(vm.graph.wavelengthWidget, settings.wavelengthWidgetId, modifier)
            WidgetType.MAP -> MapModule(modifier)
            WidgetType.PHONE -> PhoneCard(
                contacts = contacts,
                allowed = contactsAllowed,
                onPermissionResult = vm::onContactsPermissionResult,
                onCall = { vm.callContact(context, it) },
                onOpenKeypad = { vm.openPhoneApp(context) },
                modifier = modifier,
            )
            WidgetType.CAR_DATA -> CarDataCard(obdReading, obdStatus, settings.speedUnit, vm::onBluetoothPermissionResult, modifier)
            WidgetType.WEATHER -> WeatherCard(weather, vm::refreshWeather, modifier)
            WidgetType.SPORTS -> SportsCard(sports, vm.graph.images, vm::refreshSports, modifier)
        }
    }

    val widgetBound = remember(settings.wavelengthWidgetId) {
        vm.graph.wavelengthWidget.isBound(settings.wavelengthWidgetId)
    }
    val obdSetUp = settings.obdAdapterAddress.isNotBlank()
    // The chosen layout, minus modules that can't show: the Wavelength widget until it's added, car data until
    // an OBD-II adapter is picked, and the map when the launcher is only half the screen (Waze has the other half).
    val layout = remember(settings.layoutId, serverConfig, widgetBound, wide, obdSetUp) {
        DashboardLayout.resolve(settings.layoutId, serverConfig.layouts)
            .without {
                (it == WidgetType.WAVELENGTH_WIDGET && !widgetBound) || (it == WidgetType.MAP && !wide) ||
                    (it == WidgetType.CAR_DATA && !obdSetUp)
            }
            .let { if (wide) it else it.singleColumn() }
    }
    LaunchedEffect(layout.hasMap) { if (!layout.hasMap) vm.mapPaneBounds = null }

    // Background, dashboard, then the full-screen player on top.
    Box(Modifier.fillMaxSize()) {
        MusicBackdrop(musicTint, if (musicTint != null) nowPlaying?.art else null)
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(Modifier.fillMaxSize().padding(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    layout.columns.forEach { column ->
                        Column(
                            Modifier.weight(column.weight).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            column.rows.forEach { row ->
                                Row(
                                    Modifier.weight(row.weight).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    row.widgets.forEach { Widget(it, Modifier.weight(1f).fillMaxHeight()) }
                                }
                            }
                        }
                    }
                }
                Dock(
                    apps = dock,
                    onOpenDrawer = { vm.drawerOpen = true },
                    onOpenMap = openMap,
                    onOpenSettings = { vm.settingsOpen = true },
                    onLaunch = { vm.launchApp(context, it) },
                    onRemove = vm::removeFromDock,
                    modifier = Modifier.fillMaxWidth().height(dockHeight),
                )
            }

            // With the map floating in its box, overlays stay beside it so the map never covers them: they open
            // over the widest stretch of columns on one side of the map.
            val around = layout.widthAroundMap()
            val overlayModifier = when {
                !wide -> Modifier.fillMaxSize()
                around == null -> Modifier.fillMaxHeight().fillMaxWidth(OverlayWidthFraction)
                around.second > around.first ->
                    Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(around.second.coerceAtLeast(MinOverlayFraction))
                else -> Modifier.fillMaxHeight().fillMaxWidth(around.first.coerceAtLeast(MinOverlayFraction))
            }
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
                    serverConfig = serverConfig,
                    configStatus = configStatus,
                    configSyncing = configSyncing,
                    onSetConfigServer = vm::setConfigServer,
                    onSyncConfig = vm::syncConfig,
                    wavelengthInstalled = wavelengthInstalled,
                    wavelengthWidgetAvailable = remember(wavelengthInstalled) { vm.wavelengthWidgetAvailable() },
                    onAddWavelengthWidget = addWidget,
                    onRemoveWavelengthWidget = vm::removeWavelengthWidget,
                    pairedObdDevices = vm::pairedObdDevices,
                    onSetObdAdapter = vm::setObdAdapter,
                    onBluetoothPermissionResult = vm::onBluetoothPermissionResult,
                    weather = weather,
                    sports = sports,
                    onSearchTeams = vm::searchTeams,
                    onAddTeam = vm::addSportsTeam,
                    onRemoveTeam = vm::removeSportsTeam,
                    onClose = { vm.settingsOpen = false },
                )
            }
        }

        // The full-screen player covers the dashboard until it's closed.
        val playingNow = nowPlaying
        AnimatedVisibility(
            visible = vm.nowPlayingOpen && playingNow != null,
            enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.96f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.96f),
        ) {
            // Keep showing the last song while the player fades out after the music source goes away.
            var shown by remember { mutableStateOf(playingNow) }
            if (playingNow != null) shown = playingNow
            shown?.let { song ->
                NowPlayingScreen(
                    nowPlaying = song,
                    artTint = artTint,
                    onPlayPause = vm.graph.media::playPause,
                    onPrevious = vm.graph.media::previous,
                    onNext = vm.graph.media::next,
                    onOpenApp = {
                        vm.nowPlayingOpen = false
                        vm.graph.media.openSourceApp()
                    },
                    onClose = { vm.nowPlayingOpen = false },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}

/**
 * The colour the launcher takes from the music: the album art's colour while a song plays and
 * "Colours follow the music" is on, otherwise null (the theme's own accent).
 */
fun musicAccent(settings: LauncherSettings, nowPlaying: NowPlaying?, artTint: Color?): Color? =
    artTint.takeIf { settings.musicColors && nowPlaying?.isPlaying == true }
