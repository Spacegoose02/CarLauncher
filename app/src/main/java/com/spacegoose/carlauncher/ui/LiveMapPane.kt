package com.spacegoose.carlauncher.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spacegoose.carlauncher.maps.MapApp
import com.spacegoose.carlauncher.vehicle.GeoPoint
import java.util.Locale

private const val ZOOM = 15
private const val RECENTER_METERS = 700f
private const val MIN_RELOAD_MS = 20_000L

/** Waze's official embeddable Live Map (developers.google.com/waze/iframe): traffic and reports. */
private fun liveMapUrl(p: GeoPoint) =
    String.format(Locale.US, "https://embed.waze.com/iframe?zoom=%d&lat=%.5f&lon=%.5f&pin=1", ZOOM, p.lat, p.lon)

private fun distanceMeters(a: GeoPoint, b: GeoPoint): Float {
    val out = FloatArray(1)
    Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, out)
    return out[0]
}

/**
 * The map box showing Waze's Live Map in a WebView, so it really lives inside the box. The embed can't
 * pan by itself, so it is reloaded around the car after it has moved a few hundred metres.
 * Navigation still hands off to the Waze app with the button.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveMapPane(
    position: GeoPoint?,
    locationAllowed: Boolean,
    mapApp: MapApp,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf<GeoPoint?>(null) }
    var loadedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(position) {
        val p = position ?: return@LaunchedEffect
        val c = center
        val now = SystemClock.elapsedRealtime()
        if (c == null || (distanceMeters(c, p) > RECENTER_METERS && now - loadedAt > MIN_RELOAD_MS)) {
            center = p
            loadedAt = now
        }
    }

    Surface(modifier.clip(CardShape), shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            val c = center
            if (c == null) {
                Text(
                    if (locationAllowed) "Finding your location for the Waze Live Map…"
                    else "Allow location on the speed card to show the Waze Live Map here.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                val url = liveMapUrl(c)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                // Links out of the embed (search, routing) open in the Waze app or browser.
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (request.url.host == "embed.waze.com") return false
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: Exception) {
                                        // nothing to open it with
                                    }
                                    return true
                                }
                            }
                            tag = url
                            loadUrl(url)
                        }
                    },
                    update = { view ->
                        if (view.tag != url) {
                            view.tag = url
                            view.loadUrl(url)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = {
                        position?.let {
                            center = it
                            loadedAt = SystemClock.elapsedRealtime()
                        }
                    },
                    modifier = Modifier.size(TouchTarget),
                ) { Icon(Icons.Filled.MyLocation, "Centre on my location", Modifier.size(32.dp)) }
                Button(onClick = onOpenMap, modifier = Modifier.heightIn(min = TouchTarget)) {
                    Text("Navigate in ${mapApp.label}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
