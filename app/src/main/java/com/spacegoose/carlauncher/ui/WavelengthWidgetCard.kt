package com.spacegoose.carlauncher.ui

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spacegoose.carlauncher.media.WavelengthWidgetHost

/** Wavelength's own "Now playing" home-screen widget, hosted live inside the dashboard. */
@Composable
fun WavelengthWidgetCard(host: WavelengthWidgetHost, widgetId: Int, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = glassSurface(), border = glassEdge()) {
        key(widgetId) {
            AndroidView(
                factory = { ctx -> host.createView(ctx, widgetId) ?: FrameLayout(ctx) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
