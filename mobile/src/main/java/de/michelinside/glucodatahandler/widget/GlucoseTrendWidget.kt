package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class GlucoseTrendWidget : GlucoseBaseWidget(WidgetType.GLUCOSE_TREND, true) {
    override fun getLayout(): Int = R.layout.glucose_trend_widget
    override fun getShortLayout(): Int = R.layout.glucose_trend_widget_short
}
