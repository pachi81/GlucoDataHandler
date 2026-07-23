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
import de.michelinside.glucodatahandler.WearActivity
import de.michelinside.glucodatahandler.common.ReceiveData
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
        // Glucose value and trend arrow are composed side by side (inline) into a single bitmap.
        private const val VALUE_IMAGE_HEIGHT_PX = 180
        private const val VALUE_TEXT_PX = 200
        private const val VALUE_ARROW_PX = 180
        private const val VALUE_IMAGE_WIDTH_PX = VALUE_TEXT_PX + VALUE_ARROW_PX
        private const val FRESHNESS_INTERVAL_MS = 60_000L

        private const val TEXT_SIZE = 18f

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
                getUpdater(this).requestUpdate(GlucoseValueTileService::class.java)
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

        // Column flow: centered block with value + arrow on top and details below.
        val frame = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(inlineImage(buildValueBitmap(), VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX))
                    .setWidth(dp(80f * (VALUE_IMAGE_WIDTH_PX.toFloat() / VALUE_IMAGE_HEIGHT_PX.toFloat())))
                    .setHeight(dp(80f))
                    .build()
            )
            .addContent(spacer(12f))

        // details: time + delta row
        frame.addContent(
            LayoutElementBuilders.Row.Builder()
                .addContent(updatedAgoText(TEXT_SIZE))
                .addContent(horizontalSpacer(10f))
                .addContent(deltaLine("Δ $delta", TEXT_SIZE))
                .build()
        )

        if (iobText.isNotEmpty() || cobText.isNotEmpty()) {
            frame.addContent(spacer(4f))
            val iobCobRow = LayoutElementBuilders.Row.Builder()
            if (iobText.isNotEmpty()) {
                iobCobRow.addContent(deltaLine(iobText, TEXT_SIZE))
            }
            if (iobText.isNotEmpty() && cobText.isNotEmpty()) {
                iobCobRow.addContent(horizontalSpacer(10f))
            }
            if (cobText.isNotEmpty()) {
                iobCobRow.addContent(deltaLine(cobText, TEXT_SIZE))
            }
            frame.addContent(iobCobRow.build())
        }

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

    private fun buildValueBitmap(): Bitmap =
        ValueBitmapHandler.getComboBitmap(VALUE_TEXT_PX, VALUE_IMAGE_HEIGHT_PX, VALUE_ARROW_PX, VALUE_ARROW_PX)
}
