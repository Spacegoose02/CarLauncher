package com.spacegoose.carlauncher.layout

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The modules the dashboard can show. New modules (OBD2 gauges, weather, ...) get a new entry here. */
@Serializable
enum class WidgetType { CLOCK, SPEEDOMETER, MEDIA, WAVELENGTH_WIDGET, MAP }

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

        val BuiltIn = listOf(MapRight, MapLeft, NoMap)
        const val DEFAULT_ID = "map_right"

        fun byId(id: String): DashboardLayout = BuiltIn.firstOrNull { it.id == id } ?: MapRight

        /** The layouts to offer: the server's first, then built-ins it doesn't replace (same id wins from the server). */
        fun available(fromServer: List<DashboardLayout>): List<DashboardLayout> =
            fromServer + BuiltIn.filter { b -> fromServer.none { it.id == b.id } }

        fun resolve(id: String, fromServer: List<DashboardLayout>): DashboardLayout =
            available(fromServer).firstOrNull { it.id == id } ?: byId(id)
    }
}
