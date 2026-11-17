package com.spacegoose.carlauncher.media

import com.spacegoose.carlauncher.settings.MediaSourcePreference

/**
 * Which session the media card shows. [wavelength] is Wavelength's session (if it has a track loaded);
 * [others] are every other app's sessions with a track. Pure so it can be unit tested.
 */
fun <T> chooseMediaSource(
    preference: MediaSourcePreference,
    wavelength: T?,
    others: List<T>,
    isPlaying: (T) -> Boolean,
): T? {
    val otherPlaying = others.firstOrNull(isPlaying)
    return when (preference) {
        MediaSourcePreference.WAVELENGTH_ONLY -> wavelength
        MediaSourcePreference.WAVELENGTH_FIRST -> when {
            wavelength != null && (isPlaying(wavelength) || otherPlaying == null) -> wavelength
            otherPlaying != null -> otherPlaying
            else -> others.firstOrNull()
        }
        MediaSourcePreference.ANY_APP ->
            otherPlaying ?: wavelength?.takeIf(isPlaying) ?: wavelength ?: others.firstOrNull()
    }
}
