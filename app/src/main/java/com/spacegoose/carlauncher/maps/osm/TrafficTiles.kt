package com.spacegoose.carlauncher.maps.osm

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * TomTom's traffic map tiles: coloured lines for traffic speed, or icons for crashes, roadworks and closures.
 * They are see-through and drawn over the OpenStreetMap map. TomTom's free plan covers a car's daily use.
 */
class TomTomTrafficTiles(private val layer: Layer, private val key: String) : OnlineTileSourceBase(
    "TomTom-${layer.name}",
    0,
    22,
    256,
    ".png",
    arrayOf("https://api.tomtom.com/traffic/map/4/tile/"),
    "Traffic © TomTom",
) {
    enum class Layer(val path: String) {
        FLOW("flow/relative0"),
        INCIDENTS("incidents/s3"),
    }

    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + layer.path + "/" + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) +
            "/" + MapTileIndex.getY(pMapTileIndex) + ".png?tileSize=256&key=" + key
}
