package com.spacegoose.carlauncher.integrations

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** The server turned the request down (HTTP 401): it needs an app key, or the key is wrong. */
class NeedsKeyException : Exception("needs a key")

/** Small GETs for the self-hosted services the dashboard reads (Weather Scout, Stat Scraps). */
object Http {
    private const val TIMEOUT_MS = 10_000

    /** GETs [url] as text, sending [key] as a Bearer token when it isn't blank. Call off the main thread. */
    fun getText(url: String, key: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (!key.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer ${key.trim()}")
            val code = conn.responseCode
            if (code == 401) throw NeedsKeyException()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun getBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** A short, plain reason for a failed request, for the card and settings. */
    fun describe(e: Exception): String = when (e) {
        is NeedsKeyException -> "The server asked for a key."
        is java.net.UnknownHostException -> "No connection, or the address is wrong."
        is java.net.SocketTimeoutException -> "The server took too long to answer."
        else -> e.message ?: e.javaClass.simpleName
    }
}

/** Team logos and the like, kept in memory so the scores card doesn't download them every refresh. */
class ImageCache {
    private val cache = LruCache<String, ImageBitmap>(64)
    private val failed: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun peek(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        if (url in failed) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Http.getBytes(url)
                // Logos are small on the card: decode at a reduced size.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 128 && bounds.outHeight / (sample * 2) >= 128) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull().also { bmp ->
                if (bmp != null) cache.put(url, bmp) else failed += url
            }
        }
    }
}
