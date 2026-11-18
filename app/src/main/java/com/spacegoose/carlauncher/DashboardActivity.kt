package com.spacegoose.carlauncher

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.spacegoose.carlauncher.ui.DashboardScreen
import com.spacegoose.carlauncher.ui.theme.CarLauncherTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import com.spacegoose.carlauncher.automation.CarStart
import com.spacegoose.carlauncher.ui.CarSizes
import com.spacegoose.carlauncher.ui.LocalCarSizes
import kotlinx.coroutines.launch

class DashboardActivity : ComponentActivity() {
    private val vm: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // A launcher never exits on Back: Back only closes the drawer or settings.
        onBackPressedDispatcher.addCallback(this) { vm.onBack() }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val serverConfig by vm.serverConfig.collectAsStateWithLifecycle()
            val night by vm.isNight.collectAsStateWithLifecycle()
            val driving by vm.drivingMode.collectAsStateWithLifecycle()
            val serverTheme = settings.themeId.takeIf { it.isNotEmpty() }?.let(serverConfig::theme)
            CarLauncherTheme(settings, serverTheme, night) {
                CompositionLocalProvider(LocalCarSizes provides if (driving) CarSizes.Driving else CarSizes.Normal) {
                    DashboardScreen(vm)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.collect { applySystemBars(it.hideSystemBars) }
            }
        }

        // A fresh start (boot, or the car starting) runs the start actions: open the map, resume music.
        if (savedInstanceState == null) {
            val carStart = intent.getBooleanExtra(CarStart.EXTRA_CAR_START, false) ||
                SystemClock.elapsedRealtime() < JUST_BOOTED_MS
            vm.runStartActions(this, carStart)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when {
            intent.getBooleanExtra(CarStart.EXTRA_CAR_START, false) -> {
                vm.drawerOpen = false
                vm.settingsOpen = false
                vm.runStartActions(this, carStart = true)
            }
            intent.getBooleanExtra(EXTRA_FROM_HOME, false) -> vm.onHomePressed(this)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onStart()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the notification shade and permission prompts bring the bars back: hide them again.
        if (hasFocus) applySystemBars(vm.settings.value.hideSystemBars)
    }

    override fun onStop() {
        vm.onStop()
        super.onStop()
    }

    private fun applySystemBars(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        const val EXTRA_FROM_HOME = "from_home"
        /** A dashboard created this soon after boot counts as the car starting. */
        private const val JUST_BOOTED_MS = 5 * 60_000L
    }
}
