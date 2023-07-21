package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class GlucoseTrendWidget : GlucoseBaseWidget(WidgetType.GLUCOSE_TREND, true) {
    override fun getLayout(): Int = R.layout.glucose_trend_delta_widget
    override fun getShortLayout(): Int = R.layout.glucose_trend_delta_time_widget_short
    override fun getLongLayout(): Int = R.layout.glucose_trend_delta_time_widget_long
}
