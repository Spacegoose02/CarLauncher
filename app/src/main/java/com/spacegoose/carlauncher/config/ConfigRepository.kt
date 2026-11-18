package com.spacegoose.carlauncher.config

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Layouts and themes from the self-hosted config server (see `server/` in the repo). The last good copy is
 * kept in a file, so the launcher looks the same in a car with no network.
 */
class ConfigRepository(context: Context) {
    private val cacheFile = File(context.applicationContext.filesDir, "server_config.json")

    private val _config = MutableStateFlow(loadCache())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    /** A short line for settings: what the last sync did. */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _live = MutableStateFlow(false)
    /** True while [watch] is connected and the server will push changes straight away. */
    val live: StateFlow<Boolean> = _live.asStateFlow()

    /** A cancelled watch can still be blocked in a request for a while; only the newest one may say it's offline. */
    private val watchGeneration = AtomicInteger()

    /** Fetches `<url>/api/config`. On failure the cached copy stays in use. */
    suspend fun sync(serverUrl: String): Boolean {
        val base = ServerConfig.normalizeUrl(serverUrl)
        if (base == null) {
            _status.value = "That doesn't look like a server address."
            return false
        }
        _syncing.value = true
        return try {
            val text = withContext(Dispatchers.IO) { fetch("$base/api/config", null, TIMEOUT_MS) }
            store(text!!)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
            _status.value = "Couldn't reach the server (${e.message ?: e.javaClass.simpleName}). Using the last copy."
            false
        } finally {
            _syncing.value = false
        }
    }

    /**
     * Keeps the layouts and themes current until cancelled: fetches now, then asks the server to hold the
     * request open until something changes (long polling), so a save in the web editor shows up in the car
     * within a second or two. Waiting costs almost nothing; after a failure it retries with a growing delay.
     * A server too old to wait is fetched every few minutes instead.
     */
    suspend fun watch(serverUrl: String) {
        val base = ServerConfig.normalizeUrl(serverUrl) ?: return
        val generation = watchGeneration.incrementAndGet()
        var failures = 0
        var first = true
        try {
            while (currentCoroutineContext().isActive) {
                // The first check answers at once, so settings shows the state straight away; later ones wait.
                val wait = if (first) 0 else WAIT_S
                first = false
                val known = _config.value.revision.takeIf { it.isNotEmpty() && _config.value.fetchedAt > 0 }
                val pause = try {
                    val text = withContext(Dispatchers.IO) {
                        fetch("$base/api/config?wait=$wait", known, (wait + 15) * 1000)
                    }
                    if (failures > 0 || !_live.value) _status.value = "Up to date. Changes on the server show up here straight away."
                    failures = 0
                    if (text != null) {
                        val parsed = store(text)
                        when {
                            parsed.revision.isEmpty() -> OLD_SERVER_INTERVAL_MS // can't wait for changes
                            parsed.revision == known -> 2_000L // shouldn't happen; don't spin
                            else -> 0L
                        }.also { _live.value = parsed.revision.isNotEmpty() }
                    } else {
                        _live.value = true
                        0L
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "watch failed", e)
                    _live.value = false
                    failures++
                    if (failures == 1) {
                        _status.value = "Couldn't reach the server (${e.message ?: e.javaClass.simpleName}). Using the last copy, and trying again."
                    }
                    (5_000L shl (failures - 1).coerceAtMost(4)).coerceAtMost(60_000L)
                }
                if (pause > 0) delay(pause)
            }
        } finally {
            if (watchGeneration.get() == generation) _live.value = false
        }
    }

    /** Reads a full `/api/config` answer, keeps it and caches it. */
    private suspend fun store(text: String): ServerConfig {
        val parsed = ServerConfig.parseServerResponse(text, System.currentTimeMillis())
        withContext(Dispatchers.IO) { cacheFile.writeText(parsed.toJson()) }
        _config.value = parsed
        _status.value = buildString {
            append("Got ${parsed.layouts.size} layout${if (parsed.layouts.size == 1) "" else "s"} and ")
            append("${parsed.themes.size} theme${if (parsed.themes.size == 1) "" else "s"}.")
            if (parsed.skipped > 0) append(" Skipped ${parsed.skipped} this version can't use.")
        }
        return parsed
    }

    /** Forget everything from the server (when the address is cleared). */
    suspend fun clear() {
        withContext(Dispatchers.IO) { cacheFile.delete() }
        _config.value = ServerConfig()
        _status.value = null
    }

    /** GETs [url]; returns null for 304 Not Modified (when [etag] is the server's current revision). */
    private fun fetch(url: String, etag: String?, readTimeoutMs: Int): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = readTimeoutMs
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            if (etag != null) conn.setRequestProperty("If-None-Match", "\"$etag\"")
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return null
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun loadCache(): ServerConfig = runCatching {
        if (cacheFile.exists()) ServerConfig.fromCache(cacheFile.readText()) else ServerConfig()
    }.getOrDefault(ServerConfig())

    private companion object {
        const val TAG = "ConfigRepository"
        const val TIMEOUT_MS = 8000
        /** How long the server may hold a request open waiting for a change. */
        const val WAIT_S = 50
        const val OLD_SERVER_INTERVAL_MS = 5 * 60_000L
    }
}
