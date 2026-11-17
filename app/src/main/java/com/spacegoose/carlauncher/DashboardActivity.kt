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
import kotlinx.coroutines.delay
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
            CarLauncherTheme(settings) {
                DashboardScreen(vm)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.collect { applySystemBars(it.hideSystemBars) }
            }
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val settings = vm.loadedSettings()
                if (settings.autoLaunchMap) {
                    delay(AUTO_LAUNCH_DELAY_MS)
                    vm.openMap(this@DashboardActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FROM_HOME, false)) vm.onHomePressed(this)
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
        private const val AUTO_LAUNCH_DELAY_MS = 1200L
    }
}
