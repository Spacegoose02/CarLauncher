package com.spacegoose.carlauncher

import android.app.Application
import android.content.Context
import com.spacegoose.carlauncher.apps.AppRepository
import com.spacegoose.carlauncher.automation.CarStart
import com.spacegoose.carlauncher.phone.ContactsRepository
import com.spacegoose.carlauncher.vehicle.obd.Elm327Source
import com.spacegoose.carlauncher.config.ConfigRepository
import com.spacegoose.carlauncher.integrations.ImageCache
import com.spacegoose.carlauncher.integrations.sports.SportsRepository
import com.spacegoose.carlauncher.integrations.weather.WeatherRepository
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
        CarStart.register(this)
    }
}

/**
 * Hand-wired dependencies. Small on purpose: later features plug in here
 * (profile sync, more vehicle sources).
 */
class AppGraph(context: Context) {
    val settings = SettingsRepository(context)
    val apps = AppRepository(context)
    val media = MediaRepository(context)
    val vehicle: VehicleDataSource = GpsSpeedSource(context)
    val maps = MapLauncher()
    val wavelengthWidget = WavelengthWidgetHost(context)
    val config = ConfigRepository(context)
    val contacts = ContactsRepository(context)
    /** The Bluetooth OBD-II adapter, for the car data module. */
    val obd = Elm327Source(context)
    val weather = WeatherRepository(context)
    val sports = SportsRepository(context)
    val images = ImageCache()
}
