package de.michelinside.glucodatahandler

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.chart.ChartCreator
import de.michelinside.glucodatahandler.common.chart.GlucoseChart

class MainChartCreator(chart: GlucoseChart, context: Context, durationPref: String, transparencyPref: String) : ChartCreator(chart, context, durationPref, transparencyPref) {
    private val LOG_ID = "GDH.Chart.MainCreator"

    override fun init() {
        graphDays = sharedPref.getInt(Constants.SHARED_PREF_GRAPH_DAYS_PHONE_MAIN, 2)
        super.init()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            if(key == Constants.SHARED_PREF_GRAPH_DAYS_PHONE_MAIN) {
                graphDays = sharedPref.getInt(Constants.SHARED_PREF_GRAPH_DAYS_PHONE_MAIN, 2)
                updateGraphStartTime()
            } else {
                super.onSharedPreferenceChanged(sharedPreferences, key)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}