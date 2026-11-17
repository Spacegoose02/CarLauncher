package com.spacegoose.carlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AppEntry(val component: ComponentName, val label: String, val icon: ImageBitmap) {
    val key: String get() = component.flattenToString()
    val packageName: String get() = component.packageName
}

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    suspend fun refresh() {
        _apps.value = withContext(Dispatchers.IO) {
            val pm = appContext.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != appContext.packageName }
                .map { ri ->
                    AppEntry(
                        component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    fun launch(context: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // App was removed or disabled since the list was built.
        }
    }

    fun openAppInfo(context: Context, entry: AppEntry) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val ICON_PX = 144

        /** First-run dock, in order, using whichever of these are installed. */
        val DEFAULT_DOCK_PACKAGES = listOf(
            "com.waze",
            "com.google.android.apps.maps",
            "com.tonearm.app", // Wavelength
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.settings",
        )
    }
}
