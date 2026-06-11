package de.michelinside.glucodatahandler.common.chart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.format.DateUtils
import android.widget.ImageView
import de.michelinside.glucodatahandler.common.utils.Log
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.utils.MPPointF
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import java.text.DateFormat
import java.time.Duration
import java.util.Date
import android.view.MotionEvent
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.LineDataSet
import androidx.core.graphics.withSave

@SuppressLint("ViewConstructor")
class CustomBubbleMarker(context: Context, private val showDate: Boolean, private val showDelta: Boolean) : MarkerView(context, R.layout.marker_layout), OnChartGestureListener {
    private val LOG_ID = "GDH.Chart.BubbleMarker"
    private val arrowSize = 35
    private val arrowCircleOffset = 0f
    private var isGlucose = true
    private var currentHighlight: Highlight? = null
    private var isMarkerVisible = false
    private var markerScreenX: Float? = null
    private var lastHighlightedIndex: Int = -1
    private var stationaryX: Float? = null
    private val markerBounds = RectF()

    override fun refreshContent(e: Entry?, highlight: Highlight) {
        Log.v(LOG_ID, "refreshContent - index ${highlight.dataSetIndex} - x ${e?.x}")
        try {
            isGlucose = highlight.dataSetIndex == 0
            e?.let {
                val timeValue = TimeValueFormatter.from_chart_x(e.x)
                val dateValue = Date(timeValue)
                val date: TextView = this.findViewById(R.id.date)
                date.visibility = if(showDate && !DateUtils.isToday(timeValue)) VISIBLE else GONE
                val time: TextView = this.findViewById(R.id.time)
                val glucose: TextView = this.findViewById(R.id.glucose)
                val delta: TextView = this.findViewById(R.id.delta)
                val rate: ImageView = this.findViewById(R.id.trendImage)
                val layout: LinearLayoutCompat = this.findViewById(R.id.marker_layout)
                if(isGlucose) {
                    date.text = DateFormat.getDateInstance(DateFormat.SHORT).format(dateValue)
                    time.text = DateFormat.getTimeInstance(DateFormat.DEFAULT).format(dateValue)
                    glucose.text = GlucoseFormatter.getValueAsString(e.y)
                    val timeDiff = Duration.ofMillis(ReceiveData.time - timeValue)
                    if(showDelta && timeDiff.toMinutes() > 0) {
                        delta.visibility = VISIBLE
                        "Δ ${GlucoseFormatter.getValueAsString(ReceiveData.rawValue - e.y)} (${resources.getString(R.string.elapsed_time, timeDiff.toMinutes())})".also { delta.text = it }
                    } else {
                        delta.visibility = GONE
                    }
                    layout.visibility = VISIBLE
                    val dbValue = dbAccess.getGlucoseValue(timeValue)
                    if(dbValue != null && dbValue.rate != null && !dbValue.rate!!.isNaN()) {
                        rate.visibility = VISIBLE
                        Log.v(LOG_ID, "Trend found - ${dbValue.rate}")
                        rate.setImageBitmap(BitmapUtils.rateToBitmap(dbValue.rate!!, Color.WHITE, 50, 50))
                    } else {
                        Log.v(LOG_ID, "No trend found")
                        rate.visibility = GONE
                    }
                } else {
                    date.text = ""
                    time.text = ""
                    glucose.text = ""
                    delta.text = ""
                    layout.visibility = GONE
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in refreshContent", exc)
        }
        super.refreshContent(e, highlight)
        isMarkerVisible = true
        currentHighlight = highlight
        // Store the index to track which data point is highlighted
        lastHighlightedIndex = highlight.let {
            val chart = chartView as? BarLineChartBase ?: return@let -1
            val dataSet = chart.data?.dataSets?.get(0) as? LineDataSet ?: return@let -1
            val entries = dataSet.values
            entries.indexOfFirst { entry -> entry.x == highlight.x }
        }
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
            markerScreenX = posX
            val left = posX + offset.x
            val top = posY + offset.y
            markerBounds.set(left, top, left + width, top + height)
            canvas.withSave {
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
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in getOffsetForDrawingAtPoint", exc)
        }
    }

    private fun findNearestIndex(chart: BarLineChartBase<*>, dataSet: LineDataSet, xVal: Float): Int {
        val entries = dataSet.values
        if (entries.isEmpty()) return -1
        
        // Clamp xVal to the visible range to ensure we don't pick points off-screen
        val visibleMinX = chart.lowestVisibleX
        val visibleMaxX = chart.highestVisibleX
        val clampedXVal = Math.max(visibleMinX, Math.min(xVal, visibleMaxX))

        var low = 0
        var high = entries.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val midX = entries[mid].getX()
            if (midX < clampedXVal) {
                low = mid + 1
            } else if (midX > clampedXVal) {
                high = mid - 1
            } else {
                return mid
            }
        }
        // Find closest
        val left = if (high >= 0) high else 0
        val right = if (low < entries.size) low else entries.size - 1
        
        val leftEntry = entries[left]
        val rightEntry = entries[right]
        
        // Further filter: if the closest points are outside the visible range, 
        // find the closest one that IS visible.
        val leftVisible = leftEntry.x >= visibleMinX && leftEntry.x <= visibleMaxX
        val rightVisible = rightEntry.x >= visibleMinX && rightEntry.x <= visibleMaxX
        
        if (leftVisible && rightVisible) {
            val leftDiff = Math.abs(leftEntry.x - clampedXVal)
            val rightDiff = Math.abs(rightEntry.x - clampedXVal)
            return if (leftDiff <= rightDiff) left else right
        } else if (leftVisible) {
            return left
        } else if (rightVisible) {
            return right
        }
        
        // If neither are "visible" by strict range (rounding issues), return the clamped one
        return if (Math.abs(leftEntry.x - clampedXVal) <= Math.abs(rightEntry.x - clampedXVal)) left else right
    }

    fun setMarkerVisible(visible: Boolean) {
        isMarkerVisible = visible
        if (!visible) {
            currentHighlight = null
            markerScreenX = null
            lastHighlightedIndex = -1
            stationaryX = null
            markerBounds.setEmpty()
        }
    }

    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
        if (isMarkerVisible && markerScreenX != null) {
            stationaryX = markerScreenX
            Log.v(LOG_ID, "onChartGestureStart - Lock stationaryX to $stationaryX")
        }
    }

    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
        stationaryX = null
        Log.v(LOG_ID, "onChartGestureEnd - Unlock stationaryX")
    }


    override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
        // Handle scale
    }

    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
        val xToUse = stationaryX ?: markerScreenX
        Log.v(LOG_ID, "onChartTranslate: visible: $isMarkerVisible, dX: $dX, dY: $dY, xToUse: $xToUse, lastIndex: $lastHighlightedIndex")
        if (isMarkerVisible && xToUse != null) {
            try {
                val chart = chartView as? BarLineChartBase ?: return
                val transformer = chart.getTransformer(YAxis.AxisDependency.LEFT)
                val values = transformer.getValuesByTouchPoint(xToUse, 0f)
                val xVal = values.x.toFloat()

                // Clamp xVal to the visible range
                val visibleMinX = chart.lowestVisibleX
                val visibleMaxX = chart.highestVisibleX

                val dataSet = chart.data?.dataSets?.get(0) as? LineDataSet ?: return
                val nearestIndex = findNearestIndex(chart, dataSet, xVal)
                Log.v(LOG_ID, "Nearest: $nearestIndex for xVal: $xVal, visible range: [$visibleMinX, $visibleMaxX]")

                if (nearestIndex >= 0) {
                    val entry = dataSet.getEntryForIndex(nearestIndex)
                    
                    // Final safety check: if the nearest found entry is still outside visible range, don't update
                    if (entry.x < visibleMinX || entry.x > visibleMaxX) {
                        Log.v(LOG_ID, "Nearest entry x=${entry.x} is outside visible range, ignoring.")
                        return
                    }

                    // Check for borders before updating
                    if(nearestIndex > lastHighlightedIndex && visibleMaxX.toInt() == chart.xChartMax.toInt()) {
                        // right border reached, don't move marker further
                        return
                    } else if(nearestIndex < lastHighlightedIndex && visibleMinX.toInt() == chart.xChartMin.toInt()) {
                        // left border reached, don't move marker further
                        return
                    }

                    val newHighlight = Highlight(entry.x, entry.y, 0)
                    Log.v(LOG_ID, "New highlighted at x=${entry.x}, visible range: [$visibleMinX, $visibleMaxX]")

                    // Always update to keep marker on the data point under the finger
                    // Only skip logging if it's the same highlight to reduce spam
                    if (nearestIndex != lastHighlightedIndex) {
                        Log.v(LOG_ID, "Set highlight to $newHighlight")
                    }
                    chart.highlightValue(newHighlight, false)
                    lastHighlightedIndex = nearestIndex
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Exception in onChartTranslate", exc)
            }
        }
    }

    override fun onChartLongPressed(me: MotionEvent) {
        // Handle long press
    }

    override fun onChartDoubleTapped(me: MotionEvent) {
        // Handle double tap
    }

    override fun onChartSingleTapped(me: MotionEvent) {
        Log.v(LOG_ID, "tapped ${me.x} - ${me.y}")
        if (isMarkerVisible && markerBounds.contains(me.x, me.y)) {
            Log.v(LOG_ID, "Marker tapped - hiding")
            val chart = chartView as? BarLineChartBase ?: return
            chart.highlightValue(null)
            setMarkerVisible(false)
            // Prevent immediate re-highlighting by the chart's internal touch handler
            chart.isHighlightPerTapEnabled = false
            chart.postDelayed({
                chart.isHighlightPerTapEnabled = true
            }, 100)
        }
    }

    override fun onChartFling(me1: MotionEvent, me2: MotionEvent, velocityX: Float, velocityY: Float) {
        // Handle fling
    }
}