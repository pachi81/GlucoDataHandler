package de.michelinside.glucodatahandler.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import de.michelinside.glucodatahandler.GlucoDataServiceWear
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.chart.ChartBitmapHandler
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.core.graphics.createBitmap

/**
 * Wear OS Tile showing the current glucose value, trend arrow, delta/time and the glucose graph,
 * matching the layout of the wear app main screen.
 *
 * The value + arrow are rendered to a single inline bitmap via [BitmapUtils.getGlucoseTrendBitmap]
 * and the graph is taken from [ChartBitmapHandler] (same source as the chart complication). Tapping
 * the tile opens [WearActivity].
 *
 * The tile is refreshed by [GlucoseGraphTileUpdater] whenever new data or a graph update arrives.
 * Important: the resources version returned here must change whenever the bitmap content changes,
 * otherwise the renderer serves the stale cached image and the tile never repaints.
 */
class GlucoseGraphTileService : TileService() {


    companion object {
        private const val LOG_ID = "GDH.GlucoseGraphTileService"
        private const val GLUCOSE_IMAGE_ID = "glucose_trend"
        private const val GRAPH_IMAGE_ID = "glucose_graph"
        private const val WIDGET_ID = "GDH.GlucoseGraphTile"
        // Keep the inline image payload small: the Tiles IPC channel rejects large parcels
        // (TransactionTooLargeException). Downscaled + RGB_565 (2 bytes/px) keeps us well under it.
        // Glucose value and trend arrow are composed side by side (inline) into a single bitmap.
        private const val VALUE_IMAGE_HEIGHT_PX = 160
        private const val VALUE_TEXT_PX = 170
        private const val VALUE_ARROW_PX = 120
        private const val VALUE_IMAGE_WIDTH_PX = VALUE_TEXT_PX + VALUE_ARROW_PX
        private const val GRAPH_IMAGE_WIDTH_PX = 400
        private const val GRAPH_IMAGE_HEIGHT_PX = 160
        private const val FRESHNESS_INTERVAL_MS = 60_000L

        private fun registerChart(context: Context) {
            if (GlucoDataService.isServiceRunning && !ChartBitmapHandler.isRegistered(WIDGET_ID))
                ChartBitmapHandler.register(context, WIDGET_ID)
        }

        private fun unregisterChart() {
            if (ChartBitmapHandler.isRegistered(WIDGET_ID))
                ChartBitmapHandler.unregister(WIDGET_ID)
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
            unregisterChart()
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
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(buildLayout()))
                .build()
            immediate(tile)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileRequest exception: " + exc.message.toString())
            immediate(TileBuilders.Tile.Builder().setResourcesVersion("0").build())
        }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return try {
            // Echo the requested version so the resources always match the version the tile
            // promised in onTileRequest. Recomputing it here races with GlucoseGraphTileUpdater and makes
            // the renderer reject the resources (tile stops repainting).
            val builder = ResourceBuilders.Resources.Builder()
                .setVersion(requestParams.version)
                .addIdToImageMapping(
                    GLUCOSE_IMAGE_ID,
                    inlineImage(buildValueBitmap(), VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX)
                )
            getGraphBitmap()?.let {
                builder.addIdToImageMapping(
                    GRAPH_IMAGE_ID,
                    inlineImage(it, GRAPH_IMAGE_WIDTH_PX, GRAPH_IMAGE_HEIGHT_PX)
                )
            }
            immediate(builder.build())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileResourcesRequest exception: " + exc.message.toString())
            immediate(
                ResourceBuilders.Resources.Builder().setVersion(requestParams.version).build()
            )
        }
    }

    private fun buildLayout(): LayoutElementBuilders.LayoutElement {
        val delta5 = deltaLineText(CR.string.tile_delta_prefix_5m, ReceiveData.delta5Min)
        val delta15 = deltaLineText(CR.string.tile_delta_prefix_15m, ReceiveData.delta15Min)
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName("de.michelinside.glucodatahandler.WearActivity")
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
            .addContent(spacer(28f))
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(GLUCOSE_IMAGE_ID)
                    // wide aspect to match the inline value+arrow bitmap (290x160)
                    .setWidth(expand())
                    .setHeight(dp(52f))
                    .build()
            )

        if (getGraphBitmap() != null) {
            frame
                .addContent(spacer(3f))
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(GRAPH_IMAGE_ID)
                        .setWidth(expand())     // edge to edge
                        .setHeight(expand())    // fill the gap between value and deltas
                        // FILL_BOUNDS stretches to the full box (default FIT leaves gaps)
                        .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                        .build()
                )
                .addContent(spacer(3f))
        } else {
            frame.addContent(expandSpacer())
        }

        frame
            // bottom stack: 5m change, 15m change, last updated
            .addContent(deltaLine(delta5))
            .addContent(deltaLine(delta15))
            .addContent(spacer(3f))
            .addContent(updatedAgoText())
            .addContent(spacer(8f))

        val box = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .build()
            )
            .addContent(frame.build())

        return box.build()
    }

    // Value text and trend arrow drawn side by side (inline) rather than stacked.
    private fun buildValueBitmap(): Bitmap {
        val value = BitmapUtils.getGlucoseAsBitmap(width = VALUE_TEXT_PX, height = VALUE_IMAGE_HEIGHT_PX)
            ?: createBitmap(VALUE_TEXT_PX, VALUE_IMAGE_HEIGHT_PX)
        val arrow = BitmapUtils.getRateAsBitmap(width = VALUE_ARROW_PX, height = VALUE_ARROW_PX)
            ?: createBitmap(VALUE_ARROW_PX, VALUE_ARROW_PX)
        val combo = createBitmap(VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX)
        val canvas = Canvas(combo)
        canvas.drawBitmap(value, 0f, 0f, null)
        canvas.drawBitmap(arrow, VALUE_TEXT_PX.toFloat(), ((VALUE_IMAGE_HEIGHT_PX - VALUE_ARROW_PX) / 2).toFloat(), null)
        return combo
    }

}
