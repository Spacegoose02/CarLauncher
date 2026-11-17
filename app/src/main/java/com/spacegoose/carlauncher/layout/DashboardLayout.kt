package com.spacegoose.carlauncher.layout

/** The cards the dashboard can show. New widgets (OBD2 gauges, weather, ...) get a new entry here. */
enum class WidgetType { CLOCK, SPEEDOMETER, MEDIA, WAVELENGTH_WIDGET }

data class WidgetRow(val widgets: List<WidgetType>, val weight: Float)

/**
 * What goes in the widget column beside the map pane. Fixed for now; the planned layout editor will
 * edit and persist one of these instead of the default.
 */
data class DashboardLayout(val rows: List<WidgetRow>) {
    companion object {
        val Default = DashboardLayout(
            listOf(
                WidgetRow(listOf(WidgetType.CLOCK, WidgetType.SPEEDOMETER), weight = 1f),
                WidgetRow(listOf(WidgetType.MEDIA), weight = 1.4f),
                WidgetRow(listOf(WidgetType.WAVELENGTH_WIDGET), weight = 0.55f),
            )
        )
    }
}
