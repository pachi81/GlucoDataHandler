package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class GlucoseWidget : GlucoseBaseWidget(WidgetType.GLUCOSE) {
    override fun getLayout(): Int = R.layout.glucose_widget
}
