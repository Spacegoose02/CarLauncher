package com.spacegoose.carlauncher.layout

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The modules the dashboard can show. New modules (weather, ...) get a new entry here. */
@Serializable
enum class WidgetType {
    CLOCK, SPEEDOMETER, MEDIA, WAVELENGTH_WIDGET, MAP,
    /** Favourite contacts to call, and a keypad button. */
    PHONE,
    /** Rpm, coolant, fuel and battery from a Bluetooth OBD-II (ELM327) adapter. Hidden until one is set up. */
    CAR_DATA,
}

@Serializable
data class WidgetRow(val widgets: List<WidgetType>, val weight: Float = 1f)

@Serializable
data class LayoutColumn(val rows: List<WidgetRow>, val weight: Float = 1f)

/**
 * A dashboard layout: columns side by side, each a stack of rows of modules, with the dock along the bottom.
 * It is plain JSON (see [toJson]/[fromJson]) so layouts can be shared as files or built on a config server
 * later; the launcher ships a few built-in ones.
 */
@Serializable
data class DashboardLayout(val id: String, val name: String, val columns: List<LayoutColumn>) {

    val hasMap: Boolean get() = columns.any { c -> c.rows.any { WidgetType.MAP in it.widgets } }

    /**
     * The share of the width (0 to 1) to the left and to the right of the map's column, or null without a map.
     * Overlays (the app drawer, settings) open on the wider side so a floating map never covers them.
     */
    fun widthAroundMap(): Pair<Float, Float>? {
        val i = columns.indexOfFirst { c -> c.rows.any { WidgetType.MAP in it.widgets } }
        if (i < 0) return null
        val total = columns.sumOf { it.weight.toDouble() }.toFloat().takeIf { it > 0f } ?: return null
        val left = columns.take(i).sumOf { it.weight.toDouble() }.toFloat() / total
        val right = columns.drop(i + 1).sumOf { it.weight.toDouble() }.toFloat() / total
        return left to right
    }

    /** True when the map sits in the first (leftmost) column. */
    val mapOnLeft: Boolean get() = columns.firstOrNull()?.rows?.any { WidgetType.MAP in it.widgets } == true

    /** Drop modules that can't show right now (e.g. the map when Waze already has half the screen). */
    fun without(predicate: (WidgetType) -> Boolean): DashboardLayout = copy(
        columns = columns
            .map { c -> c.copy(rows = c.rows.map { r -> r.copy(widgets = r.widgets.filterNot(predicate)) }.filter { it.widgets.isNotEmpty() }) }
            .filter { it.rows.isNotEmpty() },
    )

    /** Everything in one column: for a narrow window (the launcher is one half of split-screen). */
    fun singleColumn(): DashboardLayout = copy(columns = listOf(LayoutColumn(columns.flatMap { it.rows })))

    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

        fun fromJson(text: String): DashboardLayout = json.decodeFromString(serializer(), text)

        private val Widgets = LayoutColumn(
            listOf(
                WidgetRow(listOf(WidgetType.CLOCK, WidgetType.SPEEDOMETER), weight = 1f),
                WidgetRow(listOf(WidgetType.MEDIA), weight = 1.4f),
                WidgetRow(listOf(WidgetType.WAVELENGTH_WIDGET), weight = 0.55f),
            ),
            weight = 0.42f,
        )
        private val Map = LayoutColumn(listOf(WidgetRow(listOf(WidgetType.MAP))), weight = 0.58f)

        val MapRight = DashboardLayout("map_right", "Map on the right", listOf(Widgets, Map))
        val MapLeft = DashboardLayout("map_left", "Map on the left", listOf(Map, Widgets))
        val NoMap = DashboardLayout(
            "no_map", "No map",
            listOf(
                LayoutColumn(
                    listOf(
                        WidgetRow(listOf(WidgetType.CLOCK)),
                        WidgetRow(listOf(WidgetType.SPEEDOMETER)),
                    ),
                    weight = 0.32f,
                ),
                LayoutColumn(
                    listOf(
                        WidgetRow(listOf(WidgetType.MEDIA), weight = 1.6f),
                        WidgetRow(listOf(WidgetType.WAVELENGTH_WIDGET), weight = 0.5f),
                    ),
                    weight = 0.68f,
                ),
            ),
        )

        /**
         * For wide, short screens such as the Atoto S8 (about 1280x720, or 1024x600 on the 7-inch): three
         * columns so nothing is squashed into a short row, with the map in the middle and the big buttons
         * (media, phone) on the driver-friendly right side.
         */
        val WideShort = DashboardLayout(
            "wide_short", "Wide screen (Atoto S8)",
            listOf(
                LayoutColumn(
                    listOf(
                        WidgetRow(listOf(WidgetType.CLOCK)),
                        WidgetRow(listOf(WidgetType.SPEEDOMETER)),
                        WidgetRow(listOf(WidgetType.CAR_DATA)),
                    ),
                    weight = 0.22f,
                ),
                LayoutColumn(listOf(WidgetRow(listOf(WidgetType.MAP))), weight = 0.48f),
                LayoutColumn(
                    listOf(
                        WidgetRow(listOf(WidgetType.MEDIA), weight = 1.3f),
                        WidgetRow(listOf(WidgetType.PHONE), weight = 1f),
                    ),
                    weight = 0.30f,
                ),
            ),
        )

        val BuiltIn = listOf(MapRight, MapLeft, NoMap, WideShort)
        const val DEFAULT_ID = "map_right"

        fun byId(id: String): DashboardLayout = BuiltIn.firstOrNull { it.id == id } ?: MapRight

        /** The layouts to offer: the server's first, then built-ins it doesn't replace (same id wins from the server). */
        fun available(fromServer: List<DashboardLayout>): List<DashboardLayout> =
            fromServer + BuiltIn.filter { b -> fromServer.none { it.id == b.id } }

        fun resolve(id: String, fromServer: List<DashboardLayout>): DashboardLayout =
            available(fromServer).firstOrNull { it.id == id } ?: byId(id)
    }
}
