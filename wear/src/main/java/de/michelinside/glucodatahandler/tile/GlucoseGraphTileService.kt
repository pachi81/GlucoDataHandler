package de.michelinside.glucodatahandler.tile

import android.content.Context
import android.graphics.Bitmap
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import de.michelinside.glucodatahandler.GlucoDataServiceWear
import de.michelinside.glucodatahandler.WearActivity
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.chart.ChartBitmapHandler
import de.michelinside.glucodatahandler.common.chart.ValueBitmapHandler
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Log
import java.time.Duration
import kotlin.concurrent.thread

/**
 * Wear OS Tile showing the current glucose value, trend arrow, delta/time and the glucose graph,
 * matching the layout of the wear app main screen.
 */
class GlucoseGraphTileService : TileService() {

    companion object {
        private const val LOG_ID = "GDH.tile.graph"
        private const val WIDGET_ID = "GDH.GlucoseGraphTile"
        // Keep the inline image payload small: the Tiles IPC channel rejects large parcels
        // (TransactionTooLargeException). Downscaled + RGB_565 (2 bytes/px) keeps us well under it.
        // Glucose value and trend arrow are composed side by side (inline) into a single bitmap.
        private const val VALUE_IMAGE_HEIGHT_PX = 160
        private const val VALUE_TEXT_PX = 180
        private const val VALUE_ARROW_PX = 150
        private const val VALUE_IMAGE_WIDTH_PX = VALUE_TEXT_PX + VALUE_ARROW_PX
        private const val GRAPH_IMAGE_WIDTH_PX = 400
        private const val GRAPH_IMAGE_HEIGHT_PX = 160
        private const val FRESHNESS_INTERVAL_MS = 60_000L
        private const val TEXT_SIZE = 18f
        private const val IOB_COB_TEXT_SIZE = 16f

        private fun isChartRegistered(): Boolean {
            return ChartBitmapHandler.isRegistered(WIDGET_ID) && ValueBitmapHandler.isRegistered(WIDGET_ID)
        }
        private fun registerChart(context: Context) {
            Log.d(LOG_ID, "registerChart called - is registered: ${isChartRegistered()}")
            if (GlucoDataService.isServiceRunning && !ChartBitmapHandler.isRegistered(WIDGET_ID)) {
                ChartBitmapHandler.register(context, WIDGET_ID)
                //InternalNotifier.addNotifier(context, GlucoseGraphTileUpdater, mutableSetOf(NotifySource.GRAPH_CHANGED))
            }
            if (!ValueBitmapHandler.isRegistered(WIDGET_ID))
                ValueBitmapHandler.register(context, WIDGET_ID)
        }

        private fun unregisterChart(context: Context) {
            Log.d(LOG_ID, "unregisterChart called - is registered: ${isChartRegistered()}")
            if (ChartBitmapHandler.isRegistered(WIDGET_ID)) {
                //InternalNotifier.remNotifier(context, GlucoseGraphTileUpdater)
                ChartBitmapHandler.unregister(WIDGET_ID)
            }
            if (ValueBitmapHandler.isRegistered(WIDGET_ID))
                ValueBitmapHandler.unregister(context, WIDGET_ID)
        }

        private fun getGraphBitmap(): Bitmap? = ChartBitmapHandler.getBitmap()
    }

    override fun onCreate() {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate()
            GlucoDataServiceWear.start(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString())
        }
    }

    override fun onDestroy() {
        Log.v(LOG_ID, "onDestroy called")
        super.onDestroy()
    }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        try {
            Log.i(LOG_ID, "Tile added")
            GlucoDataServiceWear.start(this)
            registerChart(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileAddEvent exception: " + exc.message.toString())
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) {
        try {
            Log.i(LOG_ID, "Tile removed")
            unregisterChart(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileRemoveEvent exception: " + exc.message.toString())
        }
    }

    override fun onRecentInteractionEventsAsync(
        events: MutableList<EventBuilders.TileInteractionEvent>
    ): ListenableFuture<Void> {
        try {
            Log.v(LOG_ID, "Events received")
            if (events.any { it.eventType == EventBuilders.TileInteractionEvent.ENTER }) {
                GlucoDataServiceWear.start(this)
                requestFreshDataIfStale(this)
                getUpdater(this).requestUpdate(GlucoseGraphTileService::class.java)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onRecentInteractionEventsAsync exception: " + exc.message.toString())
        }
        return immediateVoid()
    }

    // Version tied to the content (incl. the updater counter) so the renderer invalidates its cached
    // images on every value or graph change.
    private fun resourcesVersion(): String =
        "${ReceiveData.time}_${ReceiveData.getGlucoseColor()}_${ChartBitmapHandler.chartId}_${GlucoseGraphTileUpdater.updateCount}"

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        return try {
            Log.d(LOG_ID, "onTileRequest called for version ${resourcesVersion()}")
            GlucoDataServiceWear.start(this)
            try {
                registerChart(this)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "registerChart exception: " + exc.message.toString())
            }
            
            val graphBitmap = getGraphBitmap()
            if (graphBitmap == null) {
                Log.d(LOG_ID, "Graph not available yet, delaying request")
                CallbackToFutureAdapter.getFuture { completer ->
                    thread(start = true) {
                        Thread.sleep(100)
                        try {
                            val tile = TileBuilders.Tile.Builder()
                                .setResourcesVersion(resourcesVersion())
                                .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
                                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(buildLayout(requestParams.scope)))
                                .build()
                            completer.set(tile)
                        } catch (exc: Exception) {
                            Log.e(LOG_ID, "Delayed onTileRequest exception: " + exc.message.toString())
                            completer.set(TileBuilders.Tile.Builder().setResourcesVersion("0").build())
                        }
                    }
                    "delayedTileRequest"
                }
            } else {
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(resourcesVersion())
                    .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(buildLayout(requestParams.scope)))
                    .build()
                immediate(tile)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileRequest exception: " + exc.message.toString())
            immediate(TileBuilders.Tile.Builder().setResourcesVersion("0").build())
        }
    }

    private fun buildLayout(scope: ProtoLayoutScope): LayoutElementBuilders.LayoutElement {
        Log.v(LOG_ID, "buildLayout called for version ${resourcesVersion()}")
        val delta = deltaStr(ReceiveData.delta)
        val iobText = iobLineText()
        val cobText = cobLineText()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        val graphClickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_graph")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(de.michelinside.glucodatahandler.GraphActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        val frame = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // small top inset
            .addContent(spacer(22f))
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(inlineImage(buildValueBitmap(), VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX))
                    // wide aspect to match the inline value+arrow bitmap
                    .setWidth(expand())
                    .setHeight(dp(50f))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(clickable)
                            .build()
                    )
                    .build()
            )

        val graphHeight = if (iobText.isNotEmpty() || cobText.isNotEmpty()) 70f else 80f

        val graphBitmap = getGraphBitmap()
        if (graphBitmap != null) {
            frame
                .addContent(spacer(4f))
                .addContent(
                    LayoutElementBuilders.Image.Builder(scope)
                        .setImageResource(inlineImage(graphBitmap, GRAPH_IMAGE_WIDTH_PX, GRAPH_IMAGE_HEIGHT_PX))
                        .setWidth(expand())
                        .setHeight(dp(graphHeight))
                        .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(graphClickable)
                                .build()
                        )
                        .build()
                )
                .addContent(spacer(4f))
        } else {
            Log.w(LOG_ID, "No bitmap available!")
            frame.addContent(expandSpacer())
        }

        // bottom stack: time + delta row, then IOB + COB row
        val bottomStack = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .addContent(updatedAgoText(TEXT_SIZE))
                    .addContent(horizontalSpacer(8f))
                    .addContent(deltaLine("Δ $delta", TEXT_SIZE))
                    .build()
            )

        if (iobText.isNotEmpty() || cobText.isNotEmpty()) {
            val iobCobRow = LayoutElementBuilders.Row.Builder()
            if (iobText.isNotEmpty()) {
                iobCobRow.addContent(deltaLine(iobText, IOB_COB_TEXT_SIZE))
            }
            if (iobText.isNotEmpty() && cobText.isNotEmpty()) {
                iobCobRow.addContent(horizontalSpacer(8f))
            }
            if (cobText.isNotEmpty()) {
                iobCobRow.addContent(deltaLine(cobText, IOB_COB_TEXT_SIZE))
            }
            bottomStack.addContent(spacer(4f))
            bottomStack.addContent(iobCobRow.build())
        } else if (!GlucoDataUtils.isSensorExpired(this)) {
            val duration = Duration.ofMillis(System.currentTimeMillis() - ReceiveData.sensorStartTime)
            val sensorAge = "⌛ " + formatSensorAge(duration)
            bottomStack.addContent(spacer(8f))
            bottomStack.addContent(deltaLine(sensorAge, 14f))
        }

        frame.addContent(
            LayoutElementBuilders.Box.Builder()
                .addContent(bottomStack.build())
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(clickable)
                        .build()
                )
                .build()
        )

        frame.addContent(spacer(20f))

        val box = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(frame.build())

        return box.build()
    }

    // Value text and trend arrow drawn side by side (inline) rather than stacked. The composited
    // combo bitmap is cached in ValueBitmapHandler itself, so this is just a cache read once the
    // underlying value/arrow bitmaps haven't changed.
    private fun buildValueBitmap(): Bitmap =
        ValueBitmapHandler.getComboBitmap(VALUE_TEXT_PX, VALUE_IMAGE_HEIGHT_PX, VALUE_ARROW_PX, VALUE_ARROW_PX)

}
