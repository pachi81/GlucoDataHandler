package de.michelinside.glucodatahandler.tile

import android.content.Context
import android.graphics.Bitmap
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
import de.michelinside.glucodatahandler.GraphActivity
import de.michelinside.glucodatahandler.WearActivity
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.chart.ChartBitmapHandler
import de.michelinside.glucodatahandler.common.chart.ValueBitmapHandler
import de.michelinside.glucodatahandler.common.utils.Log

/**
 * Wear OS Tile showing the current glucose value, trend arrow, delta/time and the glucose graph,
 * matching the layout of the wear app main screen.
 *
 * The value + arrow bitmaps come from [ValueBitmapHandler] and the graph from [ChartBitmapHandler]
 * (same source as the chart complication) - both cache their rendered bitmaps and only redraw when
 * the underlying data changes, instead of on every tile request. Tapping the tile opens
 * [WearActivity].
 *
 * The tile is refreshed by [GlucoseGraphTileUpdater] whenever new data or a graph update arrives.
 * Images are attached via the request's [ProtoLayoutScope] (see [buildLayout]) - the renderer
 * collects them from the scope itself, so there is no [onTileResourcesRequest] override here.
 */
class GlucoseGraphTileService : TileService() {


    companion object {
        private const val LOG_ID = "GDH.GlucoseGraphTileService"
        private const val WIDGET_ID = "GDH.GlucoseGraphTile"
        // Keep the inline image payload small: the Tiles IPC channel rejects large parcels
        // (TransactionTooLargeException). Downscaled + RGB_565 (2 bytes/px) keeps us well under it.
        // Glucose value and trend arrow are composed side by side (inline) into a single bitmap.
        private const val VALUE_IMAGE_HEIGHT_PX = 160
        private const val VALUE_TEXT_PX = 170
        private const val VALUE_ARROW_PX = 150
        private const val VALUE_IMAGE_WIDTH_PX = VALUE_TEXT_PX + VALUE_ARROW_PX
        private const val GRAPH_IMAGE_WIDTH_PX = 400
        private const val GRAPH_IMAGE_HEIGHT_PX = 160
        private const val FRESHNESS_INTERVAL_MS = 60_000L

        private fun registerChart(context: Context) {
            if (GlucoDataService.isServiceRunning && !ChartBitmapHandler.isRegistered(WIDGET_ID))
                ChartBitmapHandler.register(context, WIDGET_ID)
            if (!ValueBitmapHandler.isRegistered(WIDGET_ID))
                ValueBitmapHandler.register(context, WIDGET_ID)
        }

        private fun unregisterChart(context: Context) {
            if (ChartBitmapHandler.isRegistered(WIDGET_ID))
                ChartBitmapHandler.unregister(WIDGET_ID)
            if (ValueBitmapHandler.isRegistered(WIDGET_ID))
                ValueBitmapHandler.unregister(context, WIDGET_ID)
        }

        private fun getGraphBitmap(): Bitmap? = ChartBitmapHandler.getBitmap()
    }

    override fun onCreate() {
        try {
            super.onCreate()
            GlucoDataServiceWear.start(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString())
        }
    }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        try {
            GlucoDataServiceWear.start(this)
            registerChart(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileAddEvent exception: " + exc.message.toString())
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) {
        try {
            unregisterChart(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileRemoveEvent exception: " + exc.message.toString())
        }
    }

    // Swiping to the tile doesn't guarantee onTileRequest reruns (the system may just show the last
    // cached render up to FRESHNESS_INTERVAL_MS old) - force a fresh one now so the value, graph and
    // "Updated Xs ago" text reflect the moment the tile actually became visible.
    // onTileEnterEvent is deprecated in favor of this batched callback - the system may report several
    // enter/leave events at once, so we only react to the most recent ENTER.
    override fun onRecentInteractionEventsAsync(
        events: MutableList<EventBuilders.TileInteractionEvent>
    ): ListenableFuture<Void> {
        try {
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
            GlucoDataServiceWear.start(this)
            try {
                registerChart(this)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "registerChart exception: " + exc.message.toString())
            }
            val tile = TileBuilders.Tile.Builder()
                .setResourcesVersion(resourcesVersion())
                .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(buildLayout(requestParams.scope)))
                .build()
            immediate(tile)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileRequest exception: " + exc.message.toString())
            immediate(TileBuilders.Tile.Builder().setResourcesVersion("0").build())
        }
    }

    private fun buildLayout(scope: ProtoLayoutScope): LayoutElementBuilders.LayoutElement {
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
                            .setClassName(GraphActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        // Column flow: value (inline value+arrow) on top, the graph fills the gap in the middle,
        // deltas + last-updated pinned at the bottom. The graph expands to take all the height
        // between the value and the deltas, separated by 3dp spacers.
        val frame = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // small top inset so the value isn't clipped by the round bezel
            .addContent(spacer(24f))
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(inlineImage(buildValueBitmap(), VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX))
                    // wide aspect to match the inline value+arrow bitmap
                    .setWidth(expand())
                    .setHeight(dp(64f))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(clickable)
                            .build()
                    )
                    .build()
            )

        val graphBitmap = getGraphBitmap()
        if (graphBitmap != null) {
            frame
                .addContent(spacer(3f))
                .addContent(
                    LayoutElementBuilders.Image.Builder(scope)
                        .setImageResource(inlineImage(graphBitmap, GRAPH_IMAGE_WIDTH_PX, GRAPH_IMAGE_HEIGHT_PX))
                        .setWidth(expand())     // edge to edge
                        .setHeight(expand())    // fill the gap between value and deltas
                        // FILL_BOUNDS stretches to the full box (default FIT leaves gaps)
                        .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(graphClickable)
                                .build()
                        )
                        .build()
                )
                .addContent(spacer(3f))
        } else {
            frame.addContent(expandSpacer())
        }

        // bottom stack: time + delta row, then IOB + COB row
        val bottomStack = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .addContent(updatedAgoText())
                    .addContent(horizontalSpacer(8f))
                    .addContent(deltaLine("Δ $delta"))
                    .build()
            )

        if (iobText.isNotEmpty() || cobText.isNotEmpty()) {
            val iobCobRow = LayoutElementBuilders.Row.Builder()
            if (iobText.isNotEmpty()) {
                iobCobRow.addContent(deltaLine(iobText))
            }
            if (iobText.isNotEmpty() && cobText.isNotEmpty()) {
                iobCobRow.addContent(horizontalSpacer(8f))
            }
            if (cobText.isNotEmpty()) {
                iobCobRow.addContent(deltaLine(cobText))
            }
            bottomStack.addContent(spacer(2f))
            bottomStack.addContent(iobCobRow.build())
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
