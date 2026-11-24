package com.spacegoose.carlauncher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.vehicle.SourceStatus
import com.spacegoose.carlauncher.vehicle.SpeedReading
import com.spacegoose.carlauncher.vehicle.SpeedUnit

@Composable
fun SpeedCard(
    reading: SpeedReading?,
    status: SourceStatus,
    unit: SpeedUnit,
    onPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionResult() }

    DashCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val small = fitText(maxWidth, maxHeight, chars = 12, heightFraction = 0.16f, max = 26.dp)
            val big = fitText(maxWidth, maxHeight, chars = 3, heightFraction = 0.6f)
            val hasFix = status == SourceStatus.ACTIVE && reading != null
            val speed = if (hasFix) unit.fromMetersPerSecond(reading!!.metersPerSecond) else 0
            // The number counts smoothly between readings instead of jumping, and a dial behind it sweeps
            // round with the speed, so it reads at a glance without reading the digits.
            val shown by animateIntAsState(speed, tween(SpeedGlideMs, easing = LinearOutSlowInEasing), label = "speed")
            val sweep by animateFloatAsState(unit.gaugeFraction(speed), tween(SpeedGlideMs * 2), label = "speed dial")
            if (status != SourceStatus.NEEDS_PERMISSION) {
                val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                val fill = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val side = size.minDimension
                    val stroke = side * 0.06f
                    val arcSize = Size(side - stroke, side - stroke)
                    val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
                    drawArc(track, DialStart, DialSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (hasFix && sweep > 0f) {
                        drawArc(fill, DialStart, DialSweep * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            }
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (status == SourceStatus.NEEDS_PERMISSION) {
                    Text(
                        "Speed needs location",
                        fontSize = small,
                        lineHeight = small,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        },
                        modifier = Modifier.heightIn(min = TouchTarget - 8.dp),
                    ) { Text("Allow") }
                } else {
                    Text(
                        if (hasFix) shown.toString() else "--",
                        fontSize = big,
                        lineHeight = big,
                        fontWeight = FontWeight.Light,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = TabularFigures),
                        color = if (hasFix) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        when (status) {
                            SourceStatus.UNAVAILABLE -> "GPS is off"
                            SourceStatus.WAITING -> "No GPS fix yet"
                            else -> unit.label
                        },
                        fontSize = small,
                        lineHeight = small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The dial runs 240 degrees, open at the bottom. */
private const val DialStart = 150f
private const val DialSweep = 240f
private const val SpeedGlideMs = 450
