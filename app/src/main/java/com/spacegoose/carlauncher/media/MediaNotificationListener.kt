package com.spacegoose.carlauncher.media

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant notification access: with it, MediaSessionManager lists every app's
 * media session (Spotify, YouTube Music, podcasts, ...). Wavelength doesn't need it.
 */
class MediaNotificationListener : NotificationListenerService()
