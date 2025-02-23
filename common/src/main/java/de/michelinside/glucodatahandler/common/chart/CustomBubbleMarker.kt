package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.format.DateUtils
import android.util.Log
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import de.michelinside.glucodatahandler.common.R
import java.text.DateFormat
import java.util.Date

class CustomBubbleMarker(context: Context, private val showDate: Boolean = false) : MarkerView(context, R.layout.marker_layout) {
    private val LOG_ID = "GDH.Chart.MarkerView"
    private val arrowSize = 35
    private val arrowCircleOffset = 0f
    private var isGlucose = true

    override fun refreshContent(e: Entry?, highlight: Highlight) {
        Log.v(LOG_ID, "refreshContent - index ${highlight.dataSetIndex}")
        try {
            isGlucose = highlight.dataSetIndex == 0
            e?.let {
                val timeValue = TimeValueFormatter.from_chart_x(e.x)
                val dateValue = Date(timeValue)
                val date: TextView = this.findViewById(R.id.date)
                date.visibility = if(showDate && !DateUtils.isToday(timeValue)) VISIBLE else GONE
                val time: TextView = this.findViewById(R.id.time)
                val glucose: TextView = this.findViewById(R.id.glucose)
                val layout: LinearLayoutCompat = this.findViewById(R.id.marker_layout)
                if(isGlucose) {
                    date.text = DateFormat.getDateInstance(DateFormat.SHORT).format(dateValue)
                    time.text = DateFormat.getTimeInstance(DateFormat.DEFAULT).format(dateValue)
                    glucose.text = GlucoseFormatter.getValueAsString(e.y)
                    layout.visibility = VISIBLE
                } else {
                    date.text = ""
                    time.text = ""
                    glucose.text = ""
                    layout.visibility = GONE
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in refreshContent", exc)
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        Log.v(LOG_ID, "getOffsetForDrawingAtPoint - isGlucose: $isGlucose")
        val offset = offset
        try {
            val chart = chartView
            val width = width.toFloat()
            val height = height.toFloat()

            if (posY <= height + arrowSize) {
                offset.y = arrowSize.toFloat()
            } else {
                offset.y = -height - arrowSize
            }

            if (posX > chart.width - width) {
                offset.x = -width
            } else {
                offset.x = 0f
                if (posX > width / 2) {
                    offset.x = -(width / 2)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in getOffsetForDrawingAtPoint", exc)
        }

        return offset
    }

    override fun draw(canvas: Canvas, posX: Float, posY: Float) {
        Log.v(LOG_ID, "draw - isGlucose: $isGlucose")
        try {
            val paint = Paint().apply {
                style = Paint.Style.FILL
                strokeJoin = Paint.Join.ROUND
                color = if(isGlucose) ContextCompat.getColor(context, R.color.transparent_marker_background) else 0
            }

            val chart = chartView
            val width = width.toFloat()
            val height = height.toFloat()
            val offset = getOffsetForDrawingAtPoint(posX, posY)
            val saveId: Int = canvas.save()
            val path = Path()

            if (posY < height + arrowSize) {
                if (posX > chart.width - width) {
                    path.moveTo(width - (2 * arrowSize), 2f)
                    path.lineTo(width, -arrowSize + arrowCircleOffset)
                    path.lineTo(width - arrowSize, 2f)
                } else {
                    if (posX > width / 2) {
                        path.moveTo(width / 2 - arrowSize / 2, 2f)
                        path.lineTo(width / 2, -arrowSize + arrowCircleOffset)
                        path.lineTo(width / 2 + arrowSize / 2, 2f)
                    } else {
                        path.moveTo(0f, -arrowSize + arrowCircleOffset)
                        path.lineTo(0f + arrowSize, 2f)
                        path.lineTo(0f, 2f)
                        path.lineTo(0f, -arrowSize + arrowCircleOffset)
                    }
                }
                path.offset(posX + offset.x, posY + offset.y)
            } else {
                if (posX > chart.width - width) {
                    path.moveTo(width, (height - 2) + arrowSize - arrowCircleOffset)
                    path.lineTo(width - arrowSize, height - 2)
                    path.lineTo(width - (2 * arrowSize), height - 2)
                } else {
                    if (posX > width / 2) {
                        path.moveTo(width / 2 + arrowSize / 2, height - 2)
                        path.lineTo(width / 2, (height - 2) + arrowSize - arrowCircleOffset)
                        path.lineTo(width / 2 - arrowSize / 2, height - 2)
                        path.lineTo(0f, height - 2)
                    } else {
                        path.moveTo(0f + (arrowSize * 2), height - 2)
                        path.lineTo(0f, (height - 2) + arrowSize - arrowCircleOffset)
                        path.lineTo(0f, height - 2)
                        path.lineTo(0f + arrowSize, height - 2)
                    }
                }
                path.offset(posX + offset.x, posY + offset.y)
            }
            canvas.drawPath(path, paint)
            canvas.translate(posX + offset.x, posY + offset.y)
            draw(canvas)
            canvas.restoreToCount(saveId)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in getOffsetForDrawingAtPoint", exc)
        }
    }
}