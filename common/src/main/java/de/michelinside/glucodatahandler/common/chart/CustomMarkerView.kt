package de.michelinside.glucodatahandler.common.chart

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import de.michelinside.glucodatahandler.common.R
import java.text.DateFormat
import java.util.Date


@SuppressLint("ViewConstructor")
class CustomMarkerView(context: Context): MarkerView(context, R.layout.custom_marker_view_layout) {
    private val LOG_ID = "GDH.Chart.MarkerView"
    // this markerview only displays a textview
    private val time = findViewById<View>(R.id.time) as TextView
    private val glucose = findViewById<View>(R.id.glucose) as TextView

    // callbacks everytime the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        Log.v(LOG_ID, "refreshContent for $e")
        super.refreshContent(e, highlight)
        time.text = DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(TimeValueFormatter.from_chart_x(e!!.x)))
        glucose.text = GlucoseFormatter.getValueAsString(e.y)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }

}