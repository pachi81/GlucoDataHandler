package de.michelinside.glucodatahandler.widget

import android.content.Context
import android.util.Log


/**
 * Implementation of App Widget functionality.
 */
class GlucoseTrendWidget : GlucoseBaseWidget(GlucoseTrendWidget::class.java, false) {

    companion object {
        private val LOG_ID = "GlucoDataHandler.GlucoseTrendDeltaWidget"
        fun create(context: Context) {
            Log.d(LOG_ID, "create called")
            triggerUpdate(context, GlucoseTrendWidget::class.java)
        }
    }
}
