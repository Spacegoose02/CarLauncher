package com.spacegoose.carlauncher.ui

import android.annotation.SuppressLint
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.spacegoose.carlauncher.R
import com.spacegoose.carlauncher.maps.MapApp
import com.spacegoose.carlauncher.vehicle.GeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

private const val FOLLOW_ZOOM = 16.0

/**
 * An OpenStreetMap map drawn by the launcher itself, so it really lives in the box: it follows the car,
 * can be panned and zoomed, and recentres with the button. Turn-by-turn still hands off to Waze or Maps.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OpenMapPane(
    position: GeoPoint?,
    locationAllowed: Boolean,
    mapApp: MapApp,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var follow by remember { mutableStateOf(true) }
    var centred by remember { mutableStateOf(false) }

    val mapView = remember {
        Configuration.getInstance().apply {
            // The OpenStreetMap tile servers ask every app to identify itself.
            userAgentValue = context.packageName
            val base = File(context.cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
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
        Text(
            "© OpenStreetMap contributors",
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
