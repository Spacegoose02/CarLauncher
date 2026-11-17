package com.spacegoose.carlauncher.maps

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.spacegoose.carlauncher.HomeActivity

data class LaunchOutcome(val strategy: LaunchStrategy?, val message: String)

/**
 * Opens Waze (or Google Maps) next to the dashboard. Third-party apps can't embed Waze, so the map is
 * Waze's own window: split-screen where the device allows it, a floating window over the map pane where
 * freeform windows are enabled, and plain full screen otherwise.
 */
class MapLauncher {

    fun capabilities(activity: Activity) = WindowCapabilities(
        sdkInt = Build.VERSION.SDK_INT,
        supportsSplitScreen = systemBool("config_supportsSplitScreenMultiWindow", default = true) &&
            systemBool("config_supportsMultiWindow", default = true),
        inMultiWindow = activity.isInMultiWindowMode,
        freeformSupported = freeformSupported(activity),
        splitHelperEnabled = SplitScreenAccessibilityService.isRunning,
    )

    /** [paneBounds] is the dashboard's map pane in screen coordinates, used for the floating window. */
    fun launch(activity: Activity, app: MapApp, mode: MapLaunchMode, paneBounds: Rect?): LaunchOutcome {
        val intent = activity.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return openStore(activity, app)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return when (val strategy = MapLaunchPlanner.plan(mode, capabilities(activity))) {
            LaunchStrategy.LAUNCH_ADJACENT -> {
                start(activity, intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
                LaunchOutcome(strategy, "Opened ${app.label} in split-screen.")
            }

            LaunchStrategy.ENTER_SPLIT_THEN_ADJACENT -> {
                HomeActivity.suppressForwardUntil = SystemClock.elapsedRealtime() + SPLIT_SETTLE_GUARD_MS
                if (SplitScreenAccessibilityService.toggleSplitScreen()) {
                    activity.window.decorView.postDelayed({
                        start(activity, intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
                    }, SPLIT_SETTLE_DELAY_MS)
                    LaunchOutcome(strategy, "Entering split-screen, then opening ${app.label} beside the dashboard.")
                } else {
                    HomeActivity.suppressForwardUntil = 0L
                    start(activity, intent)
                    LaunchOutcome(LaunchStrategy.FULLSCREEN, "Couldn't enter split-screen, so ${app.label} opened full screen.")
                }
            }

            LaunchStrategy.FREEFORM -> {
                val bounds = paneBounds ?: defaultPaneBounds(activity)
                val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
                setFreeformWindowingMode(options)
                start(activity, intent, options.toBundle())
                LaunchOutcome(strategy, "Opened ${app.label} in a floating window over the map area.")
            }

            LaunchStrategy.FULLSCREEN -> {
                start(activity, intent)
                val why = if (mode == MapLaunchMode.FULLSCREEN) "" else
                    " This device didn't offer split-screen or floating windows; press Home to come back."
                LaunchOutcome(strategy, "Opened ${app.label} full screen.$why")
            }
        }
    }

    private fun start(activity: Activity, intent: Intent, options: android.os.Bundle? = null) {
        try {
            activity.startActivity(intent, options)
        } catch (e: RuntimeException) {
            // Some head units reject launch bounds or adjacent flags outright: retry as a normal launch.
            val plain = Intent(intent).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(plain)
        }
    }

    private fun openStore(activity: Activity, app: MapApp): LaunchOutcome {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.startActivity(market)
            LaunchOutcome(null, "${app.label} isn't installed. Opening the Play Store.")
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome(null, "${app.label} isn't installed, and there's no app store to get it from.")
        }
    }

    private fun defaultPaneBounds(activity: Activity): Rect {
        val metrics = activity.resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        return Rect((w * 0.42f).toInt(), 0, w, h)
    }

    private fun freeformSupported(activity: Activity): Boolean {
        val feature = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        val devOption = try {
            Settings.Global.getInt(activity.contentResolver, "enable_freeform_support", 0) != 0
        } catch (e: Exception) {
            false
        }
        return feature || devOption
    }

    /** ActivityOptions.setLaunchWindowingMode is hidden; it's needed on Android 10/11 head units with freeform. */
    private fun setFreeformWindowingMode(options: ActivityOptions) {
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        } catch (t: Throwable) {
            // Not available: launch bounds alone still work where freeform is the default mode.
        }
    }

    private fun systemBool(name: String, default: Boolean): Boolean {
        val res = Resources.getSystem()
        val id = res.getIdentifier(name, "bool", "android")
        return if (id == 0) default else try {
            res.getBoolean(id)
        } catch (e: Resources.NotFoundException) {
            default
        }
    }

    private companion object {
        const val WINDOWING_MODE_FREEFORM = 5
        const val SPLIT_SETTLE_DELAY_MS = 700L
        const val SPLIT_SETTLE_GUARD_MS = 4000L
    }
}
