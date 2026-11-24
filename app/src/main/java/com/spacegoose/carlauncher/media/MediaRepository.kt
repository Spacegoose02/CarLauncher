package com.spacegoose.carlauncher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import com.spacegoose.carlauncher.settings.MediaSourcePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val packageName: String,
    val appLabel: String,
    /** Song length, or 0 when the player doesn't say. */
    val durationMs: Long = 0,
    /** Where the song was at [positionAtMs] (SystemClock.elapsedRealtime), and how fast it moves on from there. */
    val positionMs: Long = 0,
    val positionAtMs: Long = 0,
    val speed: Float = 0f,
) {
    val isWavelength: Boolean get() = packageName == Wavelength.PACKAGE

    /** Where the song is at [nowMs] (elapsedRealtime), for a progress bar that moves between player updates. */
    fun positionAt(nowMs: Long): Long {
        val moved = if (isPlaying) ((nowMs - positionAtMs) * speed).toLong() else 0L
        val pos = (positionMs + moved).coerceAtLeast(0L)
        return if (durationMs > 0) pos.coerceAtMost(durationMs) else pos
    }

    /** 0..1 through the song, or null when the length isn't known. */
    fun progressAt(nowMs: Long): Float? =
        if (durationMs > 0) positionAt(nowMs).toFloat() / durationMs else null
}

/**
 * Follows what's playing. Wavelength is reached directly through its MediaBrowserService; every other
 * app through MediaSessionManager, which needs notification access.
 */
class MediaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(appContext, MediaNotificationListener::class.java)
    private val artLoader = ArtLoader(appContext)

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _hasNotificationAccess = MutableStateFlow(false)
    val hasNotificationAccess: StateFlow<Boolean> = _hasNotificationAccess.asStateFlow()

    private val _wavelengthInstalled = MutableStateFlow(false)
    val wavelengthInstalled: StateFlow<Boolean> = _wavelengthInstalled.asStateFlow()

    var preference: MediaSourcePreference = MediaSourcePreference.WAVELENGTH_FIRST
        set(value) {
            field = value
            recompute()
        }

    private class Tracked(val controller: MediaControllerCompat, val callback: MediaControllerCompat.Callback)

    private var started = false
    private var wavelengthBrowser: MediaBrowserCompat? = null
    private var wavelength: Tracked? = null
    private var others: List<Tracked> = emptyList()
    private var listeningForSessions = false
    private var current: MediaControllerCompat? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        setSessions(controllers.orEmpty())
    }

    fun start() {
        started = true
        _wavelengthInstalled.value = Wavelength.isInstalled(appContext)
        connectWavelength()
        refreshSessionAccess()
    }

    fun stop() {
        started = false
        if (listeningForSessions) {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            listeningForSessions = false
        }
        others.forEach { it.controller.unregisterCallback(it.callback) }
        others = emptyList()
        wavelength?.let { it.controller.unregisterCallback(it.callback) }
        wavelength = null
        wavelengthBrowser?.disconnect()
        wavelengthBrowser = null
    }

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    /** Starts playback if something can play and isn't already. Returns false when nothing is connected yet. */
    fun play(): Boolean {
        val c = current ?: return false
        if (c.playbackState?.state != PlaybackStateCompat.STATE_PLAYING) c.transportControls.play()
        return true
    }

    fun next() {
        current?.transportControls?.skipToNext()
    }

    fun previous() {
        current?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        current?.transportControls?.seekTo(positionMs)
    }

    /** Opens the app behind the media card (or Wavelength when nothing is playing). */
    fun openSourceApp(): Boolean {
        val c = current
        val pkg = c?.packageName ?: Wavelength.PACKAGE
        c?.sessionActivity?.let {
            try {
                it.send()
                return true
            } catch (e: Exception) {
                // fall through to the launch intent
            }
        }
        val launch = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun refreshSessionAccess() {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)
        _hasNotificationAccess.value = granted
        if (!granted || listeningForSessions || !started || sessionManager == null) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            listeningForSessions = true
            setSessions(sessionManager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            _hasNotificationAccess.value = false
        }
    }

    private fun connectWavelength() {
        if (!_wavelengthInstalled.value || wavelengthBrowser != null) return
        lateinit var browser: MediaBrowserCompat
        browser = MediaBrowserCompat(appContext, Wavelength.SERVICE, object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                val controller = MediaControllerCompat(appContext, browser.sessionToken)
                val callback = controllerCallback()
                controller.registerCallback(callback, handler)
                wavelength = Tracked(controller, callback)
                // Wavelength's session may also be in the active list: drop the duplicate.
                others.filter { it.controller.packageName == Wavelength.PACKAGE }
                    .forEach { it.controller.unregisterCallback(it.callback) }
                others = others.filter { it.controller.packageName != Wavelength.PACKAGE }
                recompute()
            }

            override fun onConnectionSuspended() {
                wavelength?.let { it.controller.unregisterCallback(it.callback) }
                wavelength = null
                recompute()
            }

            override fun onConnectionFailed() {
                wavelengthBrowser = null
            }
        }, null)
        wavelengthBrowser = browser
        try {
            browser.connect()
        } catch (e: IllegalStateException) {
            wavelengthBrowser = null
        }
    }

    private fun setSessions(controllers: List<MediaController>) {
        others.forEach { it.controller.unregisterCallback(it.callback) }
        others = controllers
            .filter { !(it.packageName == Wavelength.PACKAGE && wavelength != null) }
            .map { platform ->
                val compat = MediaControllerCompat(appContext, MediaSessionCompat.Token.fromToken(platform.sessionToken))
                val callback = controllerCallback()
                compat.registerCallback(callback, handler)
                Tracked(compat, callback)
            }
        recompute()
    }

    private fun controllerCallback() = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) = recompute()
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = recompute()
        override fun onSessionDestroyed() = recompute()
    }

    private fun recompute() {
        val wl = wavelength?.controller?.let { c -> snapshot(c)?.let { c to it } }
        val rest = others.mapNotNull { t -> snapshot(t.controller)?.let { t.controller to it } }
        val chosen = chooseMediaSource(preference, wl, rest) { it.second.isPlaying }
        current = chosen?.first ?: wavelength?.controller
        _nowPlaying.value = chosen?.second
    }

    private fun snapshot(c: MediaControllerCompat): NowPlaying? {
        val m = c.metadata ?: return null
        val title = m.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            ?: m.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        if (title.isBlank()) return null
        val artist = m.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val art = artOf(m)
        val playback = c.playbackState
        val state = playback?.state
        return NowPlaying(
            title = title,
            artist = artist,
            album = m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "",
            art = art,
            isPlaying = state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_BUFFERING,
            packageName = c.packageName,
            appLabel = appLabel(c.packageName),
            durationMs = m.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).coerceAtLeast(0L),
            positionMs = playback?.position?.takeIf { it >= 0 } ?: 0L,
            positionAtMs = playback?.lastPositionUpdateTime ?: 0L,
            speed = playback?.playbackSpeed ?: 0f,
        )
    }

    /**
     * Players hand over artwork in different ways: a picture in one of three keys, or a link
     * (content://, http…) in one of three others. Pictures win; links are loaded in the background
     * and the card updates when one arrives.
     */
    private fun artOf(m: MediaMetadataCompat): Bitmap? {
        val description = m.description
        val picture = m.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
            ?: description?.iconBitmap
        if (picture != null) return picture
        val uris = ArtSources.uris(
            m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI),
            m.getString(MediaMetadataCompat.METADATA_KEY_ART_URI),
            m.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI),
            description?.iconUri?.toString(),
        )
        uris.firstNotNullOfOrNull { artLoader.cached(it) }?.let { return it }
        if (uris.isNotEmpty()) artLoader.load(uris) { recompute() }
        return null
    }

    private fun appLabel(pkg: String): String = try {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
