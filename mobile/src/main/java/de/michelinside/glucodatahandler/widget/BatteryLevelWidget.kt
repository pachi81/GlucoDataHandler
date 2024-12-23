package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class BatteryLevelWidget : GlucoseBaseWidget(WidgetType.BATTERY_LEVEL, false, false, false, false, false, true) {
    override fun getLayout(): Int = R.layout.battery_level_widget
}
