package com.spacegoose.carlauncher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) { Text("Allow") }
                } else {
                    val hasFix = status == SourceStatus.ACTIVE && reading != null
                    val value = if (hasFix) unit.fromMetersPerSecond(reading!!.metersPerSecond).toString() else "--"
                    Text(
                        value,
                        fontSize = big,
                        lineHeight = big,
                        fontWeight = FontWeight.Light,
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
