package com.spacegoose.carlauncher.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacegoose.carlauncher.vehicle.SpeedUnit
import com.spacegoose.carlauncher.vehicle.obd.ObdReading
import com.spacegoose.carlauncher.vehicle.obd.ObdStatus

/** Rpm, coolant, fuel and battery from the OBD-II adapter, as a 2x2 grid (one row of four when wide and short). */
@Composable
fun CarDataCard(
    reading: ObdReading,
    status: ObdStatus,
    unit: SpeedUnit,
    onPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionResult() }
    val imperial = unit == SpeedUnit.MPH
    DashCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (status != ObdStatus.CONNECTED) {
                val small = fitText(maxWidth, maxHeight, chars = 16, heightFraction = 0.18f, max = 22.dp)
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(status.message, fontSize = small, lineHeight = small * 1.15f, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (status == ObdStatus.NEEDS_PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(
                            onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                            modifier = Modifier.heightIn(min = TouchTarget - 8.dp),
                        ) { Text("Allow") }
                    }
                }
                return@BoxWithConstraints
            }
            val coolant = reading.coolantC?.let { if (imperial) it * 9 / 5 + 32 else it }
            val gauges = listOf(
                Gauge("RPM", reading.rpm?.toString(), "", warn = (reading.rpm ?: 0) > 6000),
                Gauge("COOLANT", coolant?.toString(), if (imperial) "°F" else "°C", warn = (reading.coolantC ?: 0) >= 110),
                Gauge("FUEL", reading.fuelPercent?.toString(), "%", warn = reading.fuelPercent?.let { it <= 12 } == true),
                Gauge("BATTERY", reading.batteryVolts?.let { "%.1f".format(it) }, "V",
                    warn = reading.batteryVolts?.let { it < 11.8f || it > 15.2f } == true),
            )
            // One row of four when the card is wide and short, otherwise two by two.
            val perRow = if (maxWidth > maxHeight * 2.5f) 4 else 2
            val rows = gauges.chunked(perRow)
            val cellWidth = maxWidth / perRow
            val cellHeight = maxHeight / rows.size
            val big = fitText(cellWidth, cellHeight, chars = 5, heightFraction = 0.5f, max = 44.dp)
            val label = fitText(cellWidth, cellHeight, chars = 9, heightFraction = 0.2f, max = 15.dp)
            Column(Modifier.fillMaxSize()) {
                rows.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { g ->
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(g.label, fontSize = label, lineHeight = label, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text(
                                    (g.value ?: "--") + g.unit,
                                    fontSize = big,
                                    lineHeight = big,
                                    fontWeight = FontWeight.Light,
                                    color = when {
                                        g.value == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        g.warn -> WarnColor
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Gauge(val label: String, val value: String?, val unit: String, val warn: Boolean)

private val WarnColor = Color(0xFFFF5252)
