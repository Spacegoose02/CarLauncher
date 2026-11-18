package com.spacegoose.carlauncher.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Which artwork links a player gave, best first, keeping only ones we know how to load. */
object ArtSources {
    private val LOADABLE = listOf("content://", "android.resource://", "file://", "https://", "http://")

    fun uris(vararg candidates: String?): List<String> =
        candidates.mapNotNull { it?.trim()?.ifEmpty { null } }
            .filter { uri -> LOADABLE.any { uri.startsWith(it, ignoreCase = true) } }
            .distinct()
}

/**
 * Loads artwork that a player gives as a link instead of a picture (METADATA_KEY_ART_URI and friends),
 * off the main thread, and remembers the last few so a track change doesn't flash the placeholder.
 */
class ArtLoader(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = mutableMapOf<String, Long>()
    private val inFlight = mutableSetOf<String>()

    /** The cached picture for [uri], if it's already loaded. */
    fun cached(uri: String): Bitmap? = cache.get(uri)

    /** Starts loading [uris] in order until one works; [onLoaded] runs on the main thread when one does. */
    fun load(uris: List<String>, onLoaded: () -> Unit) {
        val now = System.currentTimeMillis()
        val todo = uris.filter { cache.get(it) == null && it !in inFlight && (failed[it]?.let { t -> now - t > RETRY_MS } ?: true) }
        if (todo.isEmpty()) return
        val key = todo.first()
        inFlight += key
        io.execute {
            var got: Pair<String, Bitmap>? = null
            for (uri in todo) {
                val bmp = try { decode(uri) } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
                if (bmp != null) {
                    got = uri to bmp
                    break
                }
            }
            main.post {
                inFlight -= key
                val stamp = System.currentTimeMillis()
                todo.filter { it != got?.first }.forEach { failed[it] = stamp }
                got?.let { (uri, bmp) ->
                    cache.put(uri, bmp)
                    onLoaded()
                }
            }
        }
    }

    private fun decode(uri: String): Bitmap? {
        // Read the size first so a huge cover doesn't blow the memory budget.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = open(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun open(uri: String): InputStream? =
        if (uri.startsWith("http://", true) || uri.startsWith("https://", true)) {
            val c = URL(uri).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 8000
            if (c.responseCode !in 200..299) {
                c.disconnect()
                null
            } else {
                c.inputStream
            }
        } else {
            resolver.openInputStream(Uri.parse(uri))
        }

    private companion object {
        const val MAX_SIZE = 512
        const val RETRY_MS = 30_000L
    }
}
