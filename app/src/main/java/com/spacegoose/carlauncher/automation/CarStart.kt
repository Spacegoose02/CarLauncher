package com.spacegoose.carlauncher.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.spacegoose.carlauncher.CarLauncherApp
import com.spacegoose.carlauncher.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Notices the car starting and brings the dashboard up. Head units rarely reboot: most sleep with the
 * ignition off and wake when it comes on, so a screen that comes back on after a long time off counts as a
 * car start, as does a reboot, and (for a tablet on a car charger) power coming back after a while.
 */
object CarStart {
    /** Set on the dashboard intent: run the car-start actions (open the map, resume music). */
    const val EXTRA_CAR_START = "car_start"

    private const val PREFS = "car_start"
    private const val KEY_SCREEN_OFF_AT = "screen_off_at"
    private const val KEY_POWER_OFF_AT = "power_off_at"
    private const val KEY_LAST_START_AT = "last_start_at"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Registers for screen and power changes; these can't be declared in the manifest on Android 8+. */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, RuntimeReceiver(), filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private class RuntimeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> prefs.edit().putLong(KEY_SCREEN_OFF_AT, now).apply()
                Intent.ACTION_POWER_DISCONNECTED -> prefs.edit().putLong(KEY_POWER_OFF_AT, now).apply()
                Intent.ACTION_SCREEN_ON -> if (CarStartRules.longEnough(prefs.getLong(KEY_SCREEN_OFF_AT, 0), now)) {
                    trigger(context)
                }
                Intent.ACTION_POWER_CONNECTED -> if (CarStartRules.longEnough(prefs.getLong(KEY_POWER_OFF_AT, 0), now)) {
                    trigger(context)
                }
            }
        }
    }

    /** Boot (and the "quick boot" some head units send instead). */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in BOOT_ACTIONS) trigger(context)
        }
    }

    private val BOOT_ACTIONS = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
    )

    /** Brings the dashboard up with the car-start actions, at most once a minute. */
    fun trigger(context: Context) {
        val app = context.applicationContext as? CarLauncherApp ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!CarStartRules.notTooSoon(prefs.getLong(KEY_LAST_START_AT, 0), now)) return
        prefs.edit().putLong(KEY_LAST_START_AT, now).apply()
        scope.launch {
            if (!app.graph.settings.settings.first().launchOnCarStart) return@launch
            try {
                app.startActivity(
                    Intent(app, DashboardActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_CAR_START, true)
                )
            } catch (e: RuntimeException) {
                // Android may refuse to start an activity from the background; see canComeForward.
            }
        }
    }

    /**
     * Android 10+ only lets an app come to the front by itself with "display over other apps". At boot the
     * system shows the home screen anyway, so this only matters for head units that sleep.
     */
    fun canComeForward(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)
}
