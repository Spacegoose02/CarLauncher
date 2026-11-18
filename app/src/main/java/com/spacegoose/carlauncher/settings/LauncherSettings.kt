package com.spacegoose.carlauncher.settings

import com.spacegoose.carlauncher.maps.MapApp
import com.spacegoose.carlauncher.maps.MapLaunchMode
import com.spacegoose.carlauncher.vehicle.SpeedUnit

/** What the map box shows while the map app isn't floating over it. */
enum class MapPaneContent(val label: String) {
    OPEN_MAP("OpenStreetMap"),
    LIVE_MAP("Waze Live Map"),
    BUTTON("Open button only"),
}

/** Which Waze web map the Live Map box loads. */
enum class LiveMapSource(val label: String) {
    IFRAME_PAGE("Waze embed in a page"),
    EMBED("Waze embed"),
    FULL_SITE("waze.com Live Map"),
}

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

enum class AccentColor(val label: String, val argb: Long) {
    SKY("Sky", 0xFF4FC3F7),
    NEON("Neon", 0xFFFF2BD6),
    LIME("Lime", 0xFF9CCC65),
    AMBER("Amber", 0xFFFFB300),
    CORAL("Coral", 0xFFFF7043),
    VIOLET("Violet", 0xFFB388FF),
}

/** Which app the media card follows. */
enum class MediaSourcePreference(val label: String) {
    WAVELENGTH_FIRST("Wavelength first"),
    WAVELENGTH_ONLY("Wavelength only"),
    ANY_APP("Whatever is playing"),
}

data class LauncherSettings(
    val mapApp: MapApp = MapApp.WAZE,
    val mapLaunchMode: MapLaunchMode = MapLaunchMode.AUTO,
    val mapPaneContent: MapPaneContent = MapPaneContent.OPEN_MAP,
    /** Which dashboard layout to use, by [com.spacegoose.carlauncher.layout.DashboardLayout.id]. */
    val layoutId: String = com.spacegoose.carlauncher.layout.DashboardLayout.DEFAULT_ID,
    val liveMapSource: LiveMapSource = LiveMapSource.IFRAME_PAGE,
    val autoLaunchMap: Boolean = false,
    /** Pressing Home puts a floating map back in its box. */
    val reopenMapOnHome: Boolean = true,
    /** Set when "inside the map box" was tried and the map opened full screen: Automatic then uses split-screen. */
    val freeformKnownBroken: Boolean = false,
    /** How much smaller than the box to make the floating map, in percent (some devices add a frame). */
    val floatingMapShrink: Int = 0,
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.SKY,
    /** A theme from the config server, by id; empty uses [themeMode] and [accent]. */
    val themeId: String = "",
    /** The self-hosted config server (layouts and themes), e.g. "http://192.168.1.20:8080"; empty when none. */
    val configServerUrl: String = "",
    val hideSystemBars: Boolean = true,
    val mediaSource: MediaSourcePreference = MediaSourcePreference.WAVELENGTH_FIRST,
    /** The hosted Wavelength "Now playing" widget, or -1 when none is added. */
    val wavelengthWidgetId: Int = NO_WIDGET,
    /** Dock apps as flattened component names; null until the user changes the dock (defaults apply). */
    val dockApps: List<String>? = null,
) {
    companion object {
        const val NO_WIDGET = -1
        const val MAX_DOCK_APPS = 8
    }
}
