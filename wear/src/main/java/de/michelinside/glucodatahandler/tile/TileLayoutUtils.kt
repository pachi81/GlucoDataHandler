package de.michelinside.glucodatahandler.tile

import android.graphics.Color
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import java.time.Duration
import de.michelinside.glucodatahandler.common.R as CR
import java.time.Instant

// Shared protolayout building blocks for the wear tiles (GlucoseValueTileService, GlucoseGraphTileService).

internal fun <T> immediate(value: T): ListenableFuture<T> =
    CallbackToFutureAdapter.getFuture { completer ->
        completer.set(value)
        "immediate"
    }

internal fun immediateVoid(): ListenableFuture<Void> =
    CallbackToFutureAdapter.getFuture { completer ->
        completer.set(null)
        "immediateVoid"
    }

internal fun formatSensorAge(duration: Duration): String {
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    return if (days > 0) "${days}d ${hours}h" else "${hours}h"
}

internal fun spacer(heightDp: Float): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

internal fun horizontalSpacer(widthDp: Float): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder().setWidth(dp(widthDp)).build()

internal fun expandSpacer(): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder().setHeight(expand()).build()

internal fun deltaLine(text: String, sizeSp: Float = 14f): LayoutElementBuilders.Text =
    LayoutElementBuilders.Text.Builder()
        .setText(text)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(sizeSp))
                .setColor(argb(Color.LTGRAY))
                .build()
        )
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .build()

internal fun deltaStr(value: Float): String =
    if (ReceiveData.isObsoleteShort() || value.isNaN()) "--"
    else GlucoDataUtils.deltaToString(value)

internal fun iobLineText(): String =
    if (ReceiveData.isIobCobObsolete() || ReceiveData.iob.isNaN()) ""
    else "💉 " + ReceiveData.getIobAsString()

internal fun cobLineText(): String =
    if (ReceiveData.isIobCobObsolete() || ReceiveData.cob.isNaN()) ""
    else "🍔 " + ReceiveData.getCobAsString()

private fun updatedAgoFontStyle(sizeSp: Float): LayoutElementBuilders.FontStyle =
    LayoutElementBuilders.FontStyle.Builder()
        .setSize(sp(sizeSp))
        .setColor(argb(Color.LTGRAY))
        .build()

// "🕒 X min", bound to a ProtoLayout dynamic expression so the tile renderer keeps ticking
// it up off the platform clock by itself - no onTileRequest call, no app wake-up needed. Renderers
// that don't support dynamic values fall back to the static text computed at render time.
internal fun TileService.updatedAgoText(sizeSp: Float = 14f): LayoutElementBuilders.Text {
    if (ReceiveData.time == 0L) {
        return LayoutElementBuilders.Text.Builder()
            .setText("🕒 --")
            .setFontStyle(updatedAgoFontStyle(sizeSp))
            .build()
    }
    val elapsed = DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(ReceiveData.time))
        .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
    val secondsText = DynamicString.constant("🕒 ")
        .concat(elapsed.toIntSeconds().format())
        .concat(DynamicString.constant(" " + getString(CR.string.unit_second_short)))
    val minutesText = DynamicString.constant("🕒 ")
        .concat(elapsed.toIntMinutes().format())
        .concat(DynamicString.constant(" " + getString(CR.string.unit_minute_short)))
    val liveText = DynamicString.onCondition(elapsed.toIntSeconds().lt(60))
        .use(secondsText)
        .elseUse(minutesText)
    val textProp = TypeBuilders.StringProp.Builder("🕒 " + ((System.currentTimeMillis() - ReceiveData.time) / 60000) + getString(CR.string.unit_minute_short))
        .setDynamicValue(liveText)
        .build()
    return LayoutElementBuilders.Text.Builder()
        .setText(textProp)
        .setLayoutConstraintsForDynamicText(
            // Only used to size the text box (widest realistic value); never displayed.
            TypeBuilders.StringLayoutConstraint.Builder("🕒 59 " + getString(CR.string.unit_minute_short)).build()
        )
        .setFontStyle(updatedAgoFontStyle(sizeSp))
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .build()
}
