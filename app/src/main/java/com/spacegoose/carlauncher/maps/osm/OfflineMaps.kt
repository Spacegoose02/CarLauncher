package com.spacegoose.carlauncher.maps.osm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A downloaded offline map (a Mapsforge .map file, drawn on the tablet with no internet). */
data class OfflineMap(val file: File) {
    val name: String
        get() = file.nameWithoutExtension.split('-', '_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    val sizeLabel: String get() = formatBytes(file.length())
}

/** A row in the map download list: a folder (continent, country) or a map file. */
data class MapRegion(val name: String, val path: String, val isFolder: Boolean, val size: String? = null)

data class MapDownload(val id: Long, val name: String, val downloaded: Long, val total: Long) {
    val label: String
        get() = if (total > 0) "$name: ${downloaded * 100 / total}% of ${formatBytes(total)}" else "$name: starting…"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    else -> "${(bytes / 1000).coerceAtLeast(1)} KB"
}

/**
 * Offline maps for no-signal areas. Maps come from the free Mapsforge download server, one region per file
 * (a US state, a country, ...), and the launcher draws them itself, so they need no internet at all.
 */
class OfflineMaps(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val root = app.getExternalFilesDir(null) ?: app.filesDir
    private val mapsDir = File(root, "maps").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("offline_maps", Context.MODE_PRIVATE)
    private val downloadManager = app.getSystemService(DownloadManager::class.java)

    private val _maps = MutableStateFlow(scan())
    val maps: StateFlow<List<OfflineMap>> = _maps.asStateFlow()
    private val _downloads = MutableStateFlow<List<MapDownload>>(emptyList())
    val downloads: StateFlow<List<MapDownload>> = _downloads.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var watcher: Job? = null

    /** Downloads only work into shared app storage; internal-only devices can still add a file by hand. */
    val canDownload: Boolean get() = app.getExternalFilesDir(null) != null && downloadManager != null

    init {
        if (pendingIds().isNotEmpty()) watchDownloads()
    }

    private fun scan(): List<OfflineMap> =
        mapsDir.listFiles { f -> f.isFile && f.name.endsWith(".map") }.orEmpty().sortedBy { it.name }.map(::OfflineMap)

    fun refresh() {
        _maps.value = scan()
    }

    fun delete(map: OfflineMap) {
        map.file.delete()
        refresh()
    }

    /** Lists a folder of the download server, e.g. "" (continents) or "north-america/us/". */
    suspend fun browse(path: String): List<MapRegion> = withContext(Dispatchers.IO) {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", app.packageName)
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            MapRegionListing.parse(conn.inputStream.bufferedReader().use { it.readText() }, path)
        } finally {
            conn.disconnect()
        }
    }

    fun download(region: MapRegion) {
        val dm = downloadManager ?: return
        val fileName = region.path.substringAfterLast('/')
        val partName = "$fileName.part"
        File(root, "maps-downloading/$partName").delete()
        val request = DownloadManager.Request(Uri.parse(BASE_URL + region.path))
            .setTitle("Offline map: ${region.name}")
            .setDescription("Car Launcher")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, "maps-downloading", partName)
            .addRequestHeader("User-Agent", app.packageName)
        val id = dm.enqueue(request)
        prefs.edit().putString(id.toString(), fileName).apply()
        _message.value = null
        watchDownloads()
    }

    /** Copies a .map file the user picked (from a USB stick, Downloads, ...) into the offline maps. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val name = displayName(uri)?.takeIf { it.endsWith(".map", ignoreCase = true) }
        if (name == null) {
            _message.value = "That isn't a .map file. Offline maps come from download.mapsforge.org."
            return@withContext false
        }
        val target = File(mapsDir, name.lowercase())
        val temp = File(mapsDir, "$name.importing")
        val ok = runCatching {
            app.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
            temp.renameTo(target)
        }.getOrDefault(false)
        temp.delete()
        _message.value = if (ok) "Added ${OfflineMap(target).name}." else "Couldn't copy that file."
        refresh()
        ok
    }

    private fun displayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun pendingIds(): Map<Long, String> =
        prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? String)?.let { id to it } } }.toMap()

    private fun watchDownloads() {
        if (watcher?.isActive == true) return
        watcher = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val pending = pendingIds()
                if (pending.isEmpty()) break
                _downloads.value = pending.mapNotNull { (id, fileName) -> check(id, fileName) }
                delay(2000)
            }
            _downloads.value = emptyList()
        }
    }

    /** Checks one download; moves it into place when finished. Returns its progress while it is running. */
    private fun check(id: Long, fileName: String): MapDownload? {
        val dm = downloadManager ?: return null
        val name = OfflineMap(File(fileName)).name
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
        cursor.use { c ->
            if (c == null || !c.moveToFirst()) {
                forget(id)
                return null
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val part = File(root, "maps-downloading/$fileName.part")
                    val target = File(mapsDir, fileName)
                    target.delete()
                    val moved = part.renameTo(target) || runCatching { part.copyTo(target, overwrite = true); part.delete() }.isSuccess
                    _message.value = if (moved) "$name is ready to use offline." else "Couldn't save $name."
                    forget(id)
                    refresh()
                    null
                }
                DownloadManager.STATUS_FAILED -> {
                    Log.w(TAG, "Download of $fileName failed")
                    _message.value = "Downloading $name failed. Check the connection and try again."
                    dm.remove(id)
                    forget(id)
                    null
                }
                else -> MapDownload(id, name, done, total)
            }
        }
    }

    fun cancel(download: MapDownload) {
        downloadManager?.remove(download.id)
        forget(download.id)
        _downloads.value = _downloads.value.filter { it.id != download.id }
    }

    private fun forget(id: Long) {
        prefs.edit().remove(id.toString()).apply()
    }

    companion object {
        private const val TAG = "OfflineMaps"
        const val BASE_URL = "https://download.mapsforge.org/maps/v5/"
    }
}

/** Reads the download server's folder listing (a plain web server index page). */
object MapRegionListing {
    private val link = Regex("""<a\s+href="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
    private val size = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\s?([KMGT])B?(?![\w])""", RegexOption.IGNORE_CASE)

    fun parse(html: String, path: String): List<MapRegion> {
        val matches = link.findAll(html).toList()
        val seen = mutableSetOf<String>()
        return matches.mapIndexedNotNull { i, m ->
            val href = m.groupValues[1]
            if (href.startsWith("?") || href.startsWith("/") || href.startsWith("..") || href.contains("://")) return@mapIndexedNotNull null
            val isFolder = href.endsWith("/")
            if (!isFolder && !href.endsWith(".map")) return@mapIndexedNotNull null
            if (!seen.add(href)) return@mapIndexedNotNull null
            // The text between this link and the next holds the date and the size.
            val tailEnd = matches.getOrNull(i + 1)?.range?.first ?: html.length
            val tail = html.substring(m.range.last + 1, tailEnd).replace(Regex("<[^>]*>"), " ")
            val sizeText = if (isFolder) null else size.findAll(tail).lastOrNull()?.let { it.groupValues[1] + " " + it.groupValues[2].uppercase() + "B" }
            val slug = decode(href.removeSuffix("/").removeSuffix(".map"))
            MapRegion(
                // Short names are country codes ("us", "uk").
                name = if (slug.length <= 2) slug.uppercase()
                else slug.split('-', '_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
                path = path + href,
                isFolder = isFolder,
                size = sizeText,
            )
        }
    }

    private fun decode(s: String): String = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
