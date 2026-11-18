package com.spacegoose.carlauncher.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacegoose.carlauncher.R
import com.spacegoose.carlauncher.maps.MapApp
import com.spacegoose.carlauncher.maps.osm.CameraAlert
import com.spacegoose.carlauncher.maps.osm.CameraAlerts
import com.spacegoose.carlauncher.maps.osm.Geo
import com.spacegoose.carlauncher.maps.osm.HeadingTracker
import com.spacegoose.carlauncher.maps.osm.MapExtras
import com.spacegoose.carlauncher.maps.osm.MapExtrasSettings
import com.spacegoose.carlauncher.maps.osm.OfflineMapUse
import com.spacegoose.carlauncher.maps.osm.TomTomTrafficTiles
import com.spacegoose.carlauncher.vehicle.GeoPoint
import com.spacegoose.carlauncher.vehicle.SpeedUnit
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

private const val FOLLOW_ZOOM = 16.0
/** How often traffic tiles are fetched again. */
private const val TRAFFIC_REFRESH_MS = 5 * 60 * 1000L
/** Don't beep twice for the same camera within this time. */
private const val BEEP_AGAIN_MS = 3 * 60 * 1000L

/**
 * An OpenStreetMap map drawn by the launcher itself, so it really lives in the box: it follows the car,
 * can be panned and zoomed, and recentres with the button. Turn-by-turn still hands off to Waze or Maps.
 *
 * Extras (see [MapExtras]): downloaded offline maps for no-signal areas, speed and red light camera alerts
 * from OpenStreetMap, and optional TomTom traffic. Map areas already seen stay saved too (up to [TILE_CACHE_BYTES]).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OpenMapPane(
    position: GeoPoint?,
    locationAllowed: Boolean,
    mapApp: MapApp,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    speedUnit: SpeedUnit = SpeedUnit.MPH,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var follow by remember { mutableStateOf(true) }
    var centred by remember { mutableStateOf(false) }
    val extras = remember { MapExtras.get(context) }
    val mapSettings by extras.settings.settings.collectAsStateWithLifecycle(initialValue = MapExtrasSettings())
    val online by extras.online.collectAsStateWithLifecycle()
    val offlineMaps by extras.offline.maps.collectAsStateWithLifecycle()
    val cameras by extras.cameras.cameras.collectAsStateWithLifecycle()

    val mapView = remember {
        Configuration.getInstance().apply {
            // The OpenStreetMap tile servers ask every app to identify itself.
            userAgentValue = context.packageName
            // In app storage rather than the cache, so Android doesn't clear the saved map areas when space is low.
            val base = File(context.filesDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            tileFileSystemCacheMaxBytes = TILE_CACHE_BYTES
            tileFileSystemCacheTrimBytes = TILE_CACHE_BYTES * 4 / 5
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(FOLLOW_ZOOM)
            // Panning by hand stops following the car until the recentre button is pressed.
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_MOVE) follow = false
                false
            }
        }
    }
    val marker = remember {
        Marker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_car_marker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> true }
            setVisible(false)
            mapView.overlays.add(this)
        }
    }
    // Camera icons, drawn under the car.
    val cameraLayer = remember { FolderOverlay().also { mapView.overlays.add(mapView.overlays.indexOf(marker), it) } }
    val cameraIcon = remember { ContextCompat.getDrawable(context, R.drawable.ic_speed_camera) }

    LaunchedEffect(dark) {
        mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        mapView.invalidate()
    }

    LaunchedEffect(position, follow) {
        val p = position ?: return@LaunchedEffect
        val point = OsmPoint(p.lat, p.lon)
        marker.position = point
        marker.setVisible(true)
        if (!centred) {
            mapView.controller.setCenter(point)
            centred = true
        } else if (follow) {
            mapView.controller.animateTo(point)
        }
        mapView.invalidate()
    }

    // Offline maps: drawn over the online map when wanted and the map is looking at a downloaded area.
    val offlineSource = remember(offlineMaps) {
        if (offlineMaps.isEmpty()) null
        else runCatching { MapsForgeTileSource.createFromFiles(offlineMaps.map { it.file }.toTypedArray()) }.getOrNull()
    }
    DisposableEffect(offlineSource) { onDispose { offlineSource?.dispose() } }
    val offlineBounds by rememberUpdatedState(offlineSource?.boundsOsmdroid)
    var viewInOffline by remember { mutableStateOf(false) }
    DisposableEffect(mapView) {
        fun check() {
            val c = mapView.mapCenter
            val inside = offlineBounds?.contains(c.latitude, c.longitude) == true
            if (inside != viewInOffline) viewInOffline = inside
        }
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { check(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { check(); return false }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }
    LaunchedEffect(offlineSource, position) {
        val c = mapView.mapCenter
        viewInOffline = offlineSource?.boundsOsmdroid?.contains(c.latitude, c.longitude) == true
    }
    val wantOffline = when (mapSettings.offlineUse) {
        OfflineMapUse.ALWAYS -> true
        OfflineMapUse.NO_SIGNAL -> !online
        OfflineMapUse.NEVER -> false
    }
    val showingOffline = offlineSource != null && wantOffline && viewInOffline
    DisposableEffect(offlineSource, showingOffline, dark) {
        val source = offlineSource
        if (!showingOffline || source == null) return@DisposableEffect onDispose {}
        val provider = MapsForgeTileProvider(SimpleRegisterReceiver(context), source, null)
        provider.tileRequestCompleteHandlers.add(mapView.tileRequestCompleteHandler)
        val overlay = TilesOverlay(provider, context).apply {
            loadingBackgroundColor = AndroidColor.TRANSPARENT
            loadingLineColor = AndroidColor.TRANSPARENT
            if (dark) setColorFilter(TilesOverlay.INVERT_COLORS)
        }
        mapView.overlays.add(0, overlay)
        mapView.invalidate()
        onDispose {
            mapView.overlays.remove(overlay)
            overlay.onDetach(mapView)
            mapView.invalidate()
        }
    }

    // Traffic: TomTom's live traffic lines and incident icons over the map, never saved (they go stale).
    val trafficOn = mapSettings.traffic && mapSettings.tomTomKey.isNotBlank() && online
    DisposableEffect(trafficOn, mapSettings.tomTomKey) {
        if (!trafficOn) return@DisposableEffect onDispose {}
        val providers = TomTomTrafficTiles.Layer.entries.map { layer ->
            val source = TomTomTrafficTiles(layer, mapSettings.tomTomKey)
            MapTileProviderArray(
                source,
                SimpleRegisterReceiver(context),
                arrayOf<MapTileModuleProviderBase>(MapTileDownloader(source, null, NetworkAvailabliltyCheck(context))),
            ).apply { tileRequestCompleteHandlers.add(mapView.tileRequestCompleteHandler) }
        }
        val overlays = providers.map { provider ->
            TilesOverlay(provider, context).apply {
                loadingBackgroundColor = AndroidColor.TRANSPARENT
                loadingLineColor = AndroidColor.TRANSPARENT
            }
        }
        overlays.forEach { mapView.overlays.add(mapView.overlays.indexOf(cameraLayer).coerceAtLeast(0), it) }
        val handler = Handler(Looper.getMainLooper())
        val refresh = object : Runnable {
            override fun run() {
                providers.forEach { it.clearTileCache() }
                mapView.invalidate()
                handler.postDelayed(this, TRAFFIC_REFRESH_MS)
            }
        }
        handler.postDelayed(refresh, TRAFFIC_REFRESH_MS)
        mapView.invalidate()
        onDispose {
            handler.removeCallbacks(refresh)
            overlays.forEach {
                mapView.overlays.remove(it)
                it.onDetach(mapView)
            }
            mapView.invalidate()
        }
    }

    // Speed and red light cameras: icons on the map, and a warning with a beep when one is ahead.
    LaunchedEffect(cameras, mapSettings.cameraAlerts) {
        cameraLayer.items.clear()
        if (mapSettings.cameraAlerts) {
            cameras.forEach { cam ->
                cameraLayer.add(
                    Marker(mapView).apply {
                        setPosition(OsmPoint(cam.lat, cam.lon))
                        icon = cameraIcon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                )
            }
        }
        mapView.invalidate()
    }
    val headingTracker = remember { HeadingTracker() }
    var alert by remember { mutableStateOf<CameraAlert?>(null) }
    val beeped = remember { mutableMapOf<Long, Long>() }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    LaunchedEffect(position, cameras, mapSettings.cameraAlerts, mapSettings.cameraSound) {
        val p = position ?: return@LaunchedEffect
        val heading = headingTracker.update(p)
        if (!mapSettings.cameraAlerts) {
            alert = null
            return@LaunchedEffect
        }
        extras.cameras.onPosition(p)
        val next = CameraAlerts.next(p, heading, cameras)
        alert = next
        if (next != null && mapSettings.cameraSound) {
            val now = SystemClock.elapsedRealtime()
            val last = beeped[next.camera.id]
            if (last == null || now - last > BEEP_AGAIN_MS) {
                beeped[next.camera.id] = now
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(modifier.clip(CardShape).background(MaterialTheme.colorScheme.surface)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (position == null) {
            Text(
                if (locationAllowed) "Finding your location…" else "Allow location on the speed card to follow the car on the map.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CardShape)
                    .padding(16.dp),
            )
        }
        alert?.let { CameraBanner(it, speedUnit, Modifier.align(Alignment.TopCenter).padding(16.dp)) }
        val credits = buildList {
            add("© OpenStreetMap contributors")
            if (trafficOn) add("Traffic © TomTom")
            if (showingOffline) add("Offline map")
            else if (!online) add("No signal: saved map")
        }
        Text(
            credits.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = {
                    follow = true
                    mapView.controller.setZoom(FOLLOW_ZOOM)
                    position?.let { mapView.controller.animateTo(OsmPoint(it.lat, it.lon)) }
                },
                modifier = Modifier.size(TouchTarget),
            ) { Icon(Icons.Filled.MyLocation, "Follow the car", Modifier.size(32.dp)) }
            Button(onClick = onOpenMap, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text("Navigate in ${mapApp.label}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Map areas already looked at are saved for when there's no signal, up to this size. */
private const val TILE_CACHE_BYTES = 600L * 1024 * 1024

@Composable
private fun CameraBanner(alert: CameraAlert, unit: SpeedUnit, modifier: Modifier = Modifier) {
    val text = buildList {
        add(alert.camera.kind.label)
        add(Geo.formatDistance(alert.meters, unit))
        alert.camera.limitLabel()?.let { add("limit $it") }
    }.joinToString(" · ")
    Row(
        modifier
            .background(Color(0xFFFFA000), CardShape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.PhotoCamera, null, tint = Color.Black, modifier = Modifier.size(32.dp))
        Text(text, color = Color.Black, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
