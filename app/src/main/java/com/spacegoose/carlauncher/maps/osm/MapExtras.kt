package com.spacegoose.carlauncher.maps.osm

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spacegoose.carlauncher.vehicle.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.osmdroid.mapsforge.MapsForgeTileSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** When the map uses downloaded offline maps instead of the online OpenStreetMap tiles. */
enum class OfflineMapUse(val label: String) {
    NO_SIGNAL("When there's no signal"),
    ALWAYS("Always"),
    NEVER("Never"),
}

/** Settings for the OpenStreetMap map box's extras. Kept apart from the launcher settings on purpose. */
data class MapExtrasSettings(
    val cameraAlerts: Boolean = true,
    val cameraSound: Boolean = true,
    val offlineUse: OfflineMapUse = OfflineMapUse.NO_SIGNAL,
    val traffic: Boolean = false,
    /** A free TomTom API key, for traffic and incidents. */
    val tomTomKey: String = "",
)

private val Context.mapExtrasStore by preferencesDataStore(name = "map_extras")

class MapExtrasSettingsRepository(context: Context) {
    private val store = context.applicationContext.mapExtrasStore

    private object Keys {
        val CAMERAS = booleanPreferencesKey("camera_alerts")
        val CAMERA_SOUND = booleanPreferencesKey("camera_sound")
        val OFFLINE_USE = stringPreferencesKey("offline_use")
        val TRAFFIC = booleanPreferencesKey("traffic")
        val TOMTOM_KEY = stringPreferencesKey("tomtom_key")
    }

    private fun read(p: Preferences): MapExtrasSettings {
        val d = MapExtrasSettings()
        return MapExtrasSettings(
            cameraAlerts = p[Keys.CAMERAS] ?: d.cameraAlerts,
            cameraSound = p[Keys.CAMERA_SOUND] ?: d.cameraSound,
            offlineUse = p[Keys.OFFLINE_USE]?.let { n -> OfflineMapUse.entries.firstOrNull { it.name == n } } ?: d.offlineUse,
            traffic = p[Keys.TRAFFIC] ?: d.traffic,
            tomTomKey = p[Keys.TOMTOM_KEY] ?: d.tomTomKey,
        )
    }

    val settings: Flow<MapExtrasSettings> = store.data.map(::read)

    suspend fun update(transform: (MapExtrasSettings) -> MapExtrasSettings) {
        store.edit { p ->
            val now = transform(read(p))
            p[Keys.CAMERAS] = now.cameraAlerts
            p[Keys.CAMERA_SOUND] = now.cameraSound
            p[Keys.OFFLINE_USE] = now.offlineUse.name
            p[Keys.TRAFFIC] = now.traffic
            p[Keys.TOMTOM_KEY] = now.tomTomKey.trim()
        }
    }
}

/** Whether the device has working internet right now. */
private fun connectivityFlow(context: Context): StateFlow<Boolean> {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    fun validated(n: Network?) = cm?.getNetworkCapabilities(n)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } ?: false

    val state = MutableStateFlow(cm == null || validated(cm.activeNetwork))
    cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        override fun onLost(network: Network) {
            state.value = false
        }
    })
    return state.asStateFlow()
}

/**
 * Speed and red light cameras from OpenStreetMap (through the free Overpass API). Cameras within
 * [RADIUS_M] are fetched and saved, and fetched again after the car has driven [REFETCH_M] or a week has passed,
 * so alerts keep working with no signal.
 */
class SpeedCameraRepository(context: Context, private val scope: CoroutineScope, private val online: StateFlow<Boolean>) {
    private val app = context.applicationContext
    private val cacheFile = File(app.filesDir, "speed_cameras.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var area: CameraArea? = runCatching { json.decodeFromString(CameraArea.serializer(), cacheFile.readText()) }.getOrNull()
    private val _cameras = MutableStateFlow(area?.cameras.orEmpty())
    val cameras: StateFlow<List<SpeedCamera>> = _cameras.asStateFlow()

    @Volatile private var fetching = false
    @Volatile private var lastAttempt = 0L

    fun onPosition(p: GeoPoint) {
        val a = area
        val stale = a == null ||
            Geo.distanceMeters(GeoPoint(a.lat, a.lon), p) > REFETCH_M ||
            System.currentTimeMillis() - a.fetchedAt > MAX_AGE_MS
        if (!stale || fetching || !online.value) return
        if (System.currentTimeMillis() - lastAttempt < RETRY_MS) return
        fetching = true
        lastAttempt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                val cams = OverpassCameras.parse(post(OverpassCameras.query(p, RADIUS_M)))
                val fresh = CameraArea(p.lat, p.lon, System.currentTimeMillis(), cams)
                area = fresh
                _cameras.value = cams
                runCatching { cacheFile.writeText(json.encodeToString(CameraArea.serializer(), fresh)) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't fetch speed cameras: ${e.message}")
            } finally {
                fetching = false
            }
        }
    }

    private fun post(query: String): String {
        val conn = URL(OVERPASS_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 40_000
            conn.setRequestProperty("User-Agent", app.packageName)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "SpeedCameras"
        const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        const val RADIUS_M = 40_000
        const val REFETCH_M = 25_000.0
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val RETRY_MS = 5L * 60 * 1000
    }
}

/** Everything the OpenStreetMap map box adds on top of the map: offline maps, cameras, traffic. */
class MapExtras private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = MapExtrasSettingsRepository(context)
    val online: StateFlow<Boolean> = connectivityFlow(context.applicationContext)
    val offline = OfflineMaps(context, scope)
    val cameras = SpeedCameraRepository(context, scope, online)

    fun updateSettings(transform: (MapExtrasSettings) -> MapExtrasSettings) {
        scope.launch { settings.update(transform) }
    }

    companion object {
        @Volatile private var instance: MapExtras? = null

        fun get(context: Context): MapExtras = instance ?: synchronized(this) {
            instance ?: run {
                // Offline maps are drawn with Mapsforge, which needs this once per process.
                (context.applicationContext as? Application)?.let { MapsForgeTileSource.createInstance(it) }
                MapExtras(context.applicationContext).also { instance = it }
            }
        }
    }
}
