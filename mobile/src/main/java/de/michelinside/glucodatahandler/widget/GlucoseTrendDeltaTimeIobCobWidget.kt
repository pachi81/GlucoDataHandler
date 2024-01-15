package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class GlucoseTrendDeltaTimeIobCobWidget : GlucoseBaseWidget(WidgetType.GLUCOSE_TREND_DELTA_TIME_IOB_COB, true, true, true, true) {
    override fun getLayout(): Int = R.layout.glucose_trend_delta_time_iob_cob_widget
    override fun getShortLayout(): Int = R.layout.glucose_trend_delta_time_iob_cob_widget_short
    override fun getLongLayout(): Int = R.layout.glucose_trend_delta_time_iob_cob_widget_long
}
