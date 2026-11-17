package com.spacegoose.carlauncher.media

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent

/**
 * Hosts Wavelength's own "Now playing" widget inside the dashboard, the same way a home screen does.
 * Android asks the user once to allow the launcher to show it.
 */
class WavelengthWidgetHost(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)

    fun isAvailable(): Boolean = try {
        manager.installedProviders.any { it.provider == Wavelength.WIDGET }
    } catch (e: Exception) {
        false
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    /** True when bound without asking (the user allowed this launcher before, or it's a system app). */
    fun bindIfAllowed(id: Int): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, Wavelength.WIDGET)
    } catch (e: Exception) {
        false
    }

    fun bindRequest(id: Int): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, Wavelength.WIDGET)

    fun isBound(id: Int): Boolean = id >= 0 && manager.getAppWidgetInfo(id) != null

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(context, id, info)
    }

    fun delete(id: Int) {
        if (id >= 0) host.deleteAppWidgetId(id)
    }

    fun startListening() {
        try {
            host.startListening()
        } catch (e: Exception) {
            // Some OEM builds throw if the widget service is still starting; the next onStart retries.
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (e: Exception) {
            // ignore
        }
    }

    private companion object {
        const val HOST_ID = 0x5157
    }
}
