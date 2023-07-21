package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class GlucoseTrendDeltaWidget : GlucoseBaseWidget(WidgetType.GLUCOSE_TREND_DELTA, true, true) {
    override fun getLayout(): Int = R.layout.glucose_trend_delta_widget
    override fun getShortLayout(): Int = R.layout.glucose_trend_delta_widget_short
    override fun getLongLayout(): Int = R.layout.glucose_trend_delta_widget_long
}
