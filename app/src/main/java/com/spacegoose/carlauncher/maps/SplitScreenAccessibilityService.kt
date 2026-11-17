package com.spacegoose.carlauncher.maps

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional helper for Android 7-11, where an app can't start split-screen by itself: the only public way
 * is the accessibility "toggle split screen" global action. It reads nothing on screen.
 */
class SplitScreenAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: SplitScreenAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        fun toggleSplitScreen(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false
    }
}
