package com.spacegoose.carlauncher.maps

enum class MapApp(val packageName: String, val label: String) {
    WAZE("com.waze", "Waze"),
    GOOGLE_MAPS("com.google.android.apps.maps", "Google Maps"),
}

enum class MapLaunchMode(val label: String) {
    AUTO("Automatic"),
    SPLIT_SCREEN("Split-screen"),
    FREEFORM("Inside the map box"),
    FULLSCREEN("Full screen"),
}

enum class LaunchStrategy {
    /** FLAG_ACTIVITY_LAUNCH_ADJACENT: the map opens in the other half of split-screen. */
    LAUNCH_ADJACENT,
    /** Android 7-11 from full screen: the accessibility helper enters split-screen first, then launch adjacent. */
    ENTER_SPLIT_THEN_ADJACENT,
    /** A freeform (Samsung "pop-up view") window placed exactly over the dashboard's map pane. */
    FREEFORM,
    FULLSCREEN,
}

data class WindowCapabilities(
    val sdkInt: Int,
    val supportsSplitScreen: Boolean,
    val inMultiWindow: Boolean,
    val freeformSupported: Boolean,
    val splitHelperEnabled: Boolean,
    /** A floating window was tried before and Android opened the map full screen instead. */
    val freeformKnownBroken: Boolean = false,
)

/** Picks how to open the map app. Pure so it can be unit tested. */
object MapLaunchPlanner {
    /** Android 12L (API 32): the window manager turns LAUNCH_ADJACENT from a full-screen task into a split. */
    private const val ADJACENT_FROM_FULLSCREEN_SDK = 32

    fun plan(mode: MapLaunchMode, caps: WindowCapabilities): LaunchStrategy {
        val split: LaunchStrategy? = when {
            !caps.supportsSplitScreen -> null
            caps.inMultiWindow -> LaunchStrategy.LAUNCH_ADJACENT
            caps.sdkInt >= ADJACENT_FROM_FULLSCREEN_SDK -> LaunchStrategy.LAUNCH_ADJACENT
            caps.splitHelperEnabled -> LaunchStrategy.ENTER_SPLIT_THEN_ADJACENT
            else -> null
        }
        val freeform = if (caps.freeformSupported) LaunchStrategy.FREEFORM else null
        return when (mode) {
            // Prefer the map inside its box; split-screen when floating windows aren't available or failed before.
            // Already split (the dashboard is half the screen, no box to fill): keep using split-screen.
            MapLaunchMode.AUTO ->
                if (caps.inMultiWindow && split != null) split
                else (if (caps.freeformKnownBroken) null else freeform) ?: split ?: LaunchStrategy.FULLSCREEN
            MapLaunchMode.SPLIT_SCREEN -> split ?: LaunchStrategy.FULLSCREEN
            MapLaunchMode.FREEFORM -> freeform ?: LaunchStrategy.FULLSCREEN
            MapLaunchMode.FULLSCREEN -> LaunchStrategy.FULLSCREEN
        }
    }
}
