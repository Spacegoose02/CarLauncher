package com.spacegoose.carlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock

/**
 * The HOME activity. Android never puts the home activity into split-screen, so the real dashboard is
 * [DashboardActivity], an ordinary activity in its own task; this one just brings it to the front.
 */
class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forward()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forward()
    }

    private fun forward() {
        // While entering split-screen on Android 7-11 the system may show "home" in the second pane:
        // don't pull the dashboard over it then.
        if (SystemClock.elapsedRealtime() >= suppressForwardUntil) {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(DashboardActivity.EXTRA_FROM_HOME, true)
            )
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        @Volatile
        var suppressForwardUntil = 0L
    }
}
