package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class ChartWidget : GlucoseBaseWidget(WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB, true, true, true, true, false, true) {
    override fun getLayout(): Int = R.layout.chart_widget
    override fun getShortLayout(): Int = R.layout.chart_widget_short
    override fun getLongLayout(): Int = R.layout.chart_widget_long

    override fun isShortWidget(width: Int, height: Int): Boolean {
        return (width < 110 || height < 120)
    }

    override fun isLongWidget(width: Int, height: Int): Boolean {
        val ratio = width.toFloat() / height.toFloat()
        return ratio > 1F
    }
}
