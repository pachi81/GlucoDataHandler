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
import de.michelinside.glucodatahandler.common.R as CR
import java.time.Duration

/**
 * Wear OS Tile showing glucose value with detailed information above and below.
 */
class GlucoseDetailsTileService : TileService() {

    companion object {
        private const val LOG_ID = "GDH.tile.details"
        private const val WIDGET_ID = "GDH.GlucoseDetailsTile"
        private const val VALUE_IMAGE_HEIGHT_PX = 100
        private const val VALUE_TEXT_PX = 100
        private const val VALUE_ARROW_PX = 80
        private const val VALUE_IMAGE_WIDTH_PX = VALUE_TEXT_PX + VALUE_ARROW_PX
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

    override fun onRecentInteractionEventsAsync(
        events: MutableList<EventBuilders.TileInteractionEvent>
    ): ListenableFuture<Void> {
        try {
            if (events.any { it.eventType == EventBuilders.TileInteractionEvent.ENTER }) {
                GlucoDataServiceWear.start(this)
                requestFreshDataIfStale(this)
                getUpdater(this).requestUpdate(GlucoseDetailsTileService::class.java)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onRecentInteractionEventsAsync exception: " + exc.message.toString())
        }
        return immediateVoid()
    }

    private fun resourcesVersion(): String =
        "${ReceiveData.time}_${ReceiveData.getGlucoseColor()}_${GlucoseDetailsTileUpdater.updateCount}"

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        return try {
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

        val frame = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(spacer(18f))
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
                    .setImageResource(inlineImage(buildValueBitmap(),
                        VALUE_IMAGE_WIDTH_PX,
                        VALUE_IMAGE_HEIGHT_PX
                    ))
                    .setWidth(dp(80f * (VALUE_IMAGE_WIDTH_PX.toFloat() / VALUE_IMAGE_HEIGHT_PX.toFloat())))
                    .setHeight(dp(50f))
                    .build()
            )

        frame.addContent(valueRow.build())

        val delta1 = deltaStr(ReceiveData.delta1Min)
        val delta5 = deltaStr(ReceiveData.delta5Min)
        val delta15 = deltaStr(ReceiveData.delta15Min)

        val deltaTable = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(createDeltaRow(1, delta1))
            .addContent(createDeltaRow(5, delta5))
            .addContent(createDeltaRow(15, delta15))

        frame.addContent(deltaTable.build())

        frame.addContent(expandSpacer())

        // Bottom section: Time
        frame.addContent(updatedAgoText(15f))

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

    private fun createDeltaRow(minutes: Int, delta: String): LayoutElementBuilders.LayoutElement {
        val label = "Δ " + resources.getQuantityString(CR.plurals.minutes_short, minutes, minutes)
        return LayoutElementBuilders.Row.Builder()
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(90f))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .addContent(deltaLine(label, 16f))
                    .build()
            )
            .addContent(deltaLine(delta, 16f))
            .build()
    }

    private fun formatSensorAge(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        return if (days > 0) "${days}d ${hours}h" else "${hours}h"
    }

    // Value text and trend arrow drawn side by side (inline) rather than stacked. The composited
    // combo bitmap is cached in ValueBitmapHandler itself, so this is just a cache read once the
    // underlying value/arrow bitmaps haven't changed.
    private fun buildValueBitmap(): Bitmap =
        ValueBitmapHandler.getComboBitmap(VALUE_TEXT_PX, VALUE_IMAGE_HEIGHT_PX,
            VALUE_ARROW_PX,
            VALUE_ARROW_PX
        )
}
