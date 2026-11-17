package com.spacegoose.carlauncher.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Wavelength (github.com/Spacegoose02/wavelength), the user's own self-hosted music player. Its Android
 * app exposes a MediaBrowserService (so we can control it without notification access) and a
 * "Now playing" home-screen widget that the dashboard can host.
 */
object Wavelength {
    const val PACKAGE = "com.tonearm.app"
    val SERVICE = ComponentName(PACKAGE, "com.tonearm.app.MediaPlaybackService")
    val WIDGET = ComponentName(PACKAGE, "com.tonearm.app.NowPlayingWidget")

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
