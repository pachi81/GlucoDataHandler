package de.michelinside.glucodatahandler

import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.widget.SwipeDismissFrameLayout
import de.michelinside.glucodatahandler.common.Constants

class GraphActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Graph"

    private lateinit var chartCreator: WearChartCreator
    private lateinit var chart: WearGlucoseChart
    private lateinit var txtChartDisabled: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_graph)
            txtChartDisabled = findViewById(R.id.txtChartDisabled)
            chart = findViewById(R.id.chart)
            findViewById<SwipeDismissFrameLayout>(R.id.swipe_dismiss_root)?.apply {
                addCallback(object : SwipeDismissFrameLayout.Callback() {
                    override fun onSwipeStarted(layout: SwipeDismissFrameLayout) {
                        Log.d(LOG_ID, "onSwipeStarted")
                        finish()   // finish already here, as finish is called asynchronously and will take some time...
                    }
                    override fun onSwipeCanceled(layout: SwipeDismissFrameLayout) {
                        Log.d(LOG_ID, "onSwipeCanceled")
                    }
                    override fun onDismissed(layout: SwipeDismissFrameLayout) {
                        Log.d(LOG_ID, "onDismissed")
                        layout.visibility = View.GONE
                    }
                })
            }
            findViewById<View>(R.id.btnClose).setOnClickListener {
                Log.d(LOG_ID, "btnClose clicked")
                finish()
            }
            chartCreator = WearChartCreator(chart, this, Constants.SHARED_PREF_GRAPH_BITMAP_DURATION)
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
            Log.d(LOG_ID, "onDestroy called")
            super.onDestroy()
            chartCreator.close()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}