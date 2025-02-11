package de.michelinside.glucodatahandler

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.chart.GlucoseChart

class GraphActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Graph"

    private lateinit var chartCreator: WearChartCreator
    private lateinit var chart: GlucoseChart
    private lateinit var txtChartDisabled: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_graph)
            txtChartDisabled = findViewById(R.id.txtChartDisabled)
            chart = findViewById(R.id.chart)
            chartCreator = WearChartCreator(chart, this, Constants.SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION)
            chartCreator.create()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            Log.v(LOG_ID, "onPause called")
            super.onPause()
            chartCreator.pause()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onResume() {
        try {
            Log.v(LOG_ID, "onResume called")
            super.onResume()
            chartCreator.resume()
            if(chart.visibility == View.GONE)
                txtChartDisabled.visibility = View.VISIBLE
            else
                txtChartDisabled.visibility = View.GONE
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onDestroy() {
        try {
            Log.v(LOG_ID, "onDestroy called")
            super.onDestroy()
            chartCreator.close()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}