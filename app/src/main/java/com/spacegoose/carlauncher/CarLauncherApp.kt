package com.spacegoose.carlauncher

import android.app.Application
import android.content.Context
import com.spacegoose.carlauncher.apps.AppRepository
import com.spacegoose.carlauncher.maps.MapLauncher
import com.spacegoose.carlauncher.media.MediaRepository
import com.spacegoose.carlauncher.media.WavelengthWidgetHost
import com.spacegoose.carlauncher.settings.SettingsRepository
import com.spacegoose.carlauncher.vehicle.GpsSpeedSource
import com.spacegoose.carlauncher.vehicle.VehicleDataSource

class CarLauncherApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/**
 * Hand-wired dependencies. Small on purpose: later features plug in here
 * (an OBD2 [VehicleDataSource], an automation engine for car-start / Bluetooth triggers, profile sync).
 */
class AppGraph(context: Context) {
    val settings = SettingsRepository(context)
    val apps = AppRepository(context)
    val media = MediaRepository(context)
    val vehicle: VehicleDataSource = GpsSpeedSource(context)
    val maps = MapLauncher()
    val wavelengthWidget = WavelengthWidgetHost(context)
}
