package com.spacegoose.carlauncher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (status == SourceStatus.NEEDS_PERMISSION) {
                Text(
                    "Speed needs location access",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    modifier = Modifier.heightIn(min = TouchTarget),
                ) { Text("Allow") }
            } else {
                val value = if (status == SourceStatus.ACTIVE && reading != null) {
                    unit.fromMetersPerSecond(reading.metersPerSecond).toString()
                } else {
                    "--"
                }
                Text(
                    value,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    when (status) {
                        SourceStatus.UNAVAILABLE -> "GPS off"
                        SourceStatus.WAITING -> "${unit.label} · finding GPS"
                        else -> unit.label
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
