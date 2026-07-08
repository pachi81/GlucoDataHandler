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
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.chart.ValueBitmapHandler
import de.michelinside.glucodatahandler.common.utils.Log

/**
 * Wear OS Tile showing just the trend arrow and current glucose value (centered, no graph),
 * with the 5m/15m deltas and last-updated time below - a compact alternative to [GlucoseGraphTileService].
 *
 * The arrow/value bitmaps come from the shared [ValueBitmapHandler] cache, which only redraws
 * them when the underlying data changes rather than on every tile request.
 *
 * Refreshed by [GlucoseValueTileUpdater] whenever new data arrives. Tapping the tile opens
 * [de.michelinside.glucodatahandler.WearActivity]. Images are attached via the request's
 * [ProtoLayoutScope] (see [buildLayout]) - the renderer collects them from the scope itself, so
 * there is no [onTileResourcesRequest] override here.
 */
class GlucoseValueTileService : TileService() {

    companion object {
        private const val LOG_ID = "GDH.GlucoseValueTileService"
        private const val WIDGET_ID = "GDH.GlucoseValueTile"
        private const val ARROW_IMAGE_PX = 120
        private const val VALUE_IMAGE_WIDTH_PX = 220
        private const val VALUE_IMAGE_HEIGHT_PX = 130
        // Pixel->dp scale for the arrow, keeping its own aspect ratio.
        private const val IMAGE_SCALE = 0.4f
        // Bigger display scale just for the value text, so it stands out as the focal point.
        private const val VALUE_IMAGE_SCALE = 0.55f
        private const val FRESHNESS_INTERVAL_MS = 60_000L

        private fun registerValue(context: Context) {
            if (!ValueBitmapHandler.isRegistered(WIDGET_ID))
                ValueBitmapHandler.register(context, WIDGET_ID)
        }

        private fun unregisterValue(context: Context) {
            if (ValueBitmapHandler.isRegistered(WIDGET_ID))
                ValueBitmapHandler.unregister(context, WIDGET_ID)
        }
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
            registerValue(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileAddEvent exception: " + exc.message.toString())
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) {
        try {
            unregisterValue(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTileRemoveEvent exception: " + exc.message.toString())
        }
    }

    // Swiping to the tile doesn't guarantee onTileRequest reruns (the system may just show the last
    // cached render up to FRESHNESS_INTERVAL_MS old) - force a fresh one now so the value and the
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
                TileService.getUpdater(this).requestUpdate(GlucoseValueTileService::class.java)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onRecentInteractionEventsAsync exception: " + exc.message.toString())
        }
        return immediateVoid()
    }

    // Version tied to the content (incl. the updater counter) so the renderer invalidates its cached
    // images on every value change.
    private fun resourcesVersion(): String =
        "${ReceiveData.time}_${ReceiveData.getGlucoseColor()}_${GlucoseValueTileUpdater.updateCount}"

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        return try {
//            GlucoDataServiceWear.start(this)
            try {
                registerValue(this)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "registerValue exception: " + exc.message.toString())
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
        val delta1 = deltaLineText(CR.string.tile_delta_prefix_1m, ReceiveData.delta1Min)
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

        // Column flow: top inset, then arrow + current value near the top. An expanding spacer
        // pushes the deltas/last-updated section down so it sits flush against the bottom edge.
        val frame = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(spacer(28f))
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(inlineImage(buildArrowBitmap(), ARROW_IMAGE_PX, ARROW_IMAGE_PX))
                    .setWidth(dp(ARROW_IMAGE_PX * IMAGE_SCALE))
                    .setHeight(dp(ARROW_IMAGE_PX * IMAGE_SCALE))
                    .build()
            )
            .addContent(spacer(4f))
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(inlineImage(buildValueBitmap(), VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX))
                    .setWidth(dp(VALUE_IMAGE_WIDTH_PX * VALUE_IMAGE_SCALE))
                    .setHeight(dp(VALUE_IMAGE_HEIGHT_PX * VALUE_IMAGE_SCALE))
                    .build()
            )
            .addContent(expandSpacer())
            .addContent(deltaLine(delta1))
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

    private fun buildArrowBitmap(): Bitmap =
        ValueBitmapHandler.getArrowBitmap(ARROW_IMAGE_PX, ARROW_IMAGE_PX)

    private fun buildValueBitmap(): Bitmap =
        ValueBitmapHandler.getValueBitmap(VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX)
}
