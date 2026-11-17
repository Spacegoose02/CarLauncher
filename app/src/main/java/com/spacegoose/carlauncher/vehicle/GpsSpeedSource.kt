package com.spacegoose.carlauncher.vehicle

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsSpeedSource(context: Context) : VehicleDataSource, LocationListener {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val _speed = MutableStateFlow<SpeedReading?>(null)
    private val _status = MutableStateFlow(SourceStatus.WAITING)
    override val speed: StateFlow<SpeedReading?> = _speed.asStateFlow()
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()
    private val _position = MutableStateFlow<GeoPoint?>(null)
    override val position: StateFlow<GeoPoint?> = _position.asStateFlow()

    private var running = false
    private var previous: Location? = null

    override fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _status.value = SourceStatus.NEEDS_PERMISSION
            return
        }
        if (locationManager == null || !locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            _status.value = SourceStatus.UNAVAILABLE
            return
        }
        try {
            // Start the live map somewhere sensible before the first GPS fix.
            if (_position.value == null) {
                locationManager.allProviders
                    .mapNotNull { p -> runCatching { locationManager.getLastKnownLocation(p) }.getOrNull() }
                    .maxByOrNull { it.time }
                    ?.let { _position.value = GeoPoint(it.latitude, it.longitude) }
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper())
            running = true
            _status.value =
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) SourceStatus.WAITING
                else SourceStatus.UNAVAILABLE
        } catch (e: SecurityException) {
            _status.value = SourceStatus.NEEDS_PERMISSION
        }
    }

    override fun stop() {
        if (!running) return
        locationManager?.removeUpdates(this)
        running = false
        previous = null
    }

    override fun onLocationChanged(location: Location) {
        val metersPerSecond = if (location.hasSpeed()) {
            location.speed
        } else {
            // Some head-unit GPS modules don't report speed: derive it from the last two fixes.
            val last = previous
            val seconds = last?.let { (location.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1e9f } ?: 0f
            if (last != null && seconds > 0f) last.distanceTo(location) / seconds else 0f
        }
        previous = location
        _position.value = GeoPoint(location.latitude, location.longitude)
        _speed.value = SpeedReading(metersPerSecond, SystemClock.elapsedRealtime())
        _status.value = SourceStatus.ACTIVE
    }

    // Overridden explicitly: before Android 11 these weren't default methods, and a missing one crashes.
    override fun onProviderEnabled(provider: String) {
        if (_status.value == SourceStatus.UNAVAILABLE) _status.value = SourceStatus.WAITING
    }

    override fun onProviderDisabled(provider: String) {
        _status.value = SourceStatus.UNAVAILABLE
        _speed.value = null
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}
