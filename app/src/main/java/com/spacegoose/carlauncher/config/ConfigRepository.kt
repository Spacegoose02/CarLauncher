package com.spacegoose.carlauncher.config

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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

    /** Fetches `<url>/api/config`. On failure the cached copy stays in use. */
    suspend fun sync(serverUrl: String): Boolean {
        val base = ServerConfig.normalizeUrl(serverUrl)
        if (base == null) {
            _status.value = "That doesn't look like a server address."
            return false
        }
        _syncing.value = true
        return try {
            val text = withContext(Dispatchers.IO) { fetch("$base/api/config") }
            val parsed = ServerConfig.parseServerResponse(text, System.currentTimeMillis())
            withContext(Dispatchers.IO) { cacheFile.writeText(parsed.toJson()) }
            _config.value = parsed
            _status.value = buildString {
                append("Got ${parsed.layouts.size} layout${if (parsed.layouts.size == 1) "" else "s"} and ")
                append("${parsed.themes.size} theme${if (parsed.themes.size == 1) "" else "s"}.")
                if (parsed.skipped > 0) append(" Skipped ${parsed.skipped} this version can't use.")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
            _status.value = "Couldn't reach the server (${e.message ?: e.javaClass.simpleName}). Using the last copy."
            false
        } finally {
            _syncing.value = false
        }
    }

    /** Forget everything from the server (when the address is cleared). */
    suspend fun clear() {
        withContext(Dispatchers.IO) { cacheFile.delete() }
        _config.value = ServerConfig()
        _status.value = null
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
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
    }
}
