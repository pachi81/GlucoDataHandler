package de.michelinside.glucodatahandler.widget

import de.michelinside.glucodatahandler.R

/**
 * Implementation of App Widget functionality.
 */
class OtherUnitWidget : GlucoseBaseWidget(WidgetType.OTHER_UNIT, false, true, false, false, true) {
    override fun getLayout(): Int = R.layout.other_unit_widget
    override fun getShortLayout(): Int = R.layout.other_unit_widget_short
    override fun getLongLayout(): Int = R.layout.other_unit_widget_long
}
