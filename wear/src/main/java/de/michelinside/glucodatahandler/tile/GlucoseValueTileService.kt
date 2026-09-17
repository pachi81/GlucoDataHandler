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
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.chart.ValueBitmapHandler
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Log
import java.time.Duration

/**
 * Wear OS Tile showing just the trend arrow and current glucose value (centered, no graph),
 * with the 5m/15m deltas and last-updated time below - a compact alternative to [GlucoseGraphTileService].
 */
class GlucoseValueTileService : TileService() {

    companion object {
        private const val LOG_ID = "GDH.tile.value"
        private const val WIDGET_ID = "GDH.GlucoseValueTile"
        // Glucose value and trend arrow are composed side by side (inline) into a single bitmap.
        private const val VALUE_IMAGE_HEIGHT_PX = 180
        private const val VALUE_TEXT_PX = 180
        private const val VALUE_ARROW_PX = 150
        private const val VALUE_IMAGE_WIDTH_PX = VALUE_TEXT_PX + VALUE_ARROW_PX
        private const val FRESHNESS_INTERVAL_MS = 60_000L

        private const val TEXT_SIZE = 20f

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
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(spacer(12f))
            .addContent(expandSpacer())


        // Top section: Optional items (Sensor Age, Other Unit)
        val sensorAge = if (!GlucoDataUtils.isSensorExpired(this)) {
            val duration = Duration.ofMillis(System.currentTimeMillis() - ReceiveData.sensorStartTime)
            "⌛ " + formatSensorAge(duration)
        } else ""

        val otherUnit = if (GlucoDataService.sharedPref?.getBoolean(de.michelinside.glucodatahandler.common.Constants.SHARED_PREF_SHOW_OTHER_UNIT, false) == true) {
            ReceiveData.getGlucoseAsOtherUnit() + " " + ReceiveData.getOtherUnit()
        } else ""

        if (sensorAge.isNotEmpty() || otherUnit.isNotEmpty()) {
            if (sensorAge.isNotEmpty()) {
                frame.addContent(deltaLine(sensorAge, 14f))
            }
            if (otherUnit.isNotEmpty()) {
                if (sensorAge.isNotEmpty())
                    frame.addContent(spacer(3f))
                frame.addContent(deltaLine(otherUnit, 16f))
            }
        }

        // Middle: Glucose Value (Text) + Arrow (Image)
        val valueRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(inlineImage(buildValueBitmap(), VALUE_IMAGE_WIDTH_PX, VALUE_IMAGE_HEIGHT_PX))
                    .setWidth(dp(80f * (VALUE_IMAGE_WIDTH_PX.toFloat() / VALUE_IMAGE_HEIGHT_PX.toFloat())))
                    .setHeight(dp(80f))
                    .build()
            )

        frame.addContent(valueRow.build())

        // details: time + delta row
        frame.addContent(
            LayoutElementBuilders.Row.Builder()
                .addContent(updatedAgoText(TEXT_SIZE))
                .addContent(horizontalSpacer(12f))
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
                iobCobRow.addContent(horizontalSpacer(12f))
            }
            if (cobText.isNotEmpty()) {
                iobCobRow.addContent(deltaLine(cobText, TEXT_SIZE))
            }
            frame.addContent(iobCobRow.build())
        }

        frame.addContent(expandSpacer())
        frame.addContent(spacer(18f))

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
