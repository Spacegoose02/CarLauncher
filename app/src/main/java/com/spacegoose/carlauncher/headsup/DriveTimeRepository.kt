package com.spacegoose.carlauncher.headsup

import com.spacegoose.carlauncher.integrations.Http
import com.spacegoose.carlauncher.vehicle.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Drive times to the saved places, refreshed by the dashboard every few minutes while it's showing. */
class DriveTimeRepository {
    private val _times = MutableStateFlow<List<DriveTime>>(emptyList())
    val times: StateFlow<List<DriveTime>> = _times.asStateFlow()

    /**
     * Asks the router for each place in [places] the car isn't already at. A failed request keeps that
     * place's last time (it ages out of the strip on its own) rather than blanking the strip.
     */
    suspend fun refresh(from: GeoPoint, places: Map<Place, GeoPoint>) {
        val now = System.currentTimeMillis()
        val previous = _times.value.associateBy { it.place }
        val updated = places.mapNotNull { (place, to) ->
            if (DriveTimes.distanceMeters(from, to) < DriveTimes.ARRIVED_METERS) return@mapNotNull null
            val got = try {
                withContext(Dispatchers.IO) { DriveTimes.parse(Http.getText(DriveTimes.url(from, to))) }
            } catch (e: Exception) {
                null
            }
            got?.let { (seconds, meters) -> DriveTime(place, seconds, meters, now) } ?: previous[place]
        }
        _times.value = updated
    }

    fun clear() {
        _times.value = emptyList()
    }
}
