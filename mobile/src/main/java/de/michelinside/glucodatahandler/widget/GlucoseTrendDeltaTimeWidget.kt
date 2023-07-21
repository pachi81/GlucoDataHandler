package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class GlucoseTrendDeltaTimeWidget : GlucoseBaseWidget(WidgetType.GLUCOSE_TREND_DELTA_TIME, true, true, true) {
    override fun getLayout(): Int = R.layout.glucose_trend_delta_time_widget
    override fun getShortLayout(): Int = R.layout.glucose_trend_delta_time_widget_short
    override fun getLongLayout(): Int = R.layout.glucose_trend_delta_time_widget_long
}
