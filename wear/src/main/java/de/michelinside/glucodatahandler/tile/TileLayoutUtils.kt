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

internal fun spacer(heightDp: Float): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

internal fun expandSpacer(): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder().setHeight(expand()).build()

internal fun deltaLine(text: String): LayoutElementBuilders.Text =
    LayoutElementBuilders.Text.Builder()
        .setText(text)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(14f))
                .setColor(argb(Color.LTGRAY))
                .build()
        )
        .build()

internal fun deltaStr(value: Float): String =
    if (ReceiveData.isObsoleteShort() || value.isNaN()) "--"
    else GlucoDataUtils.deltaToString(value)

// e.g. "5m +3" - resId is a localized "<prefix> %1$s" string, filled in with the formatted delta.
internal fun TileService.deltaLineText(resId: Int, value: Float): String =
    getString(resId, deltaStr(value))

private fun TileService.staticUpdatedAgoText(): String {
    if (ReceiveData.time == 0L) return getString(CR.string.tile_updated_never)
    val secs = (System.currentTimeMillis() - ReceiveData.time) / 1000
    return if (secs < 60) getString(CR.string.tile_updated_seconds_ago, secs)
    else getString(CR.string.tile_updated_minutes_ago, secs / 60)
}

// Resource strings are "...%1$d..." patterns - split around the placeholder so the literal parts
// can be concatenated around a live dynamic value.
private fun TileService.placeholderParts(resId: Int): Pair<String, String> {
    val pattern = getString(resId)
    val index = pattern.indexOf("%1\$d")
    return if (index >= 0) pattern.substring(0, index) to pattern.substring(index + 4) else pattern to ""
}

private val updatedAgoFontStyle: LayoutElementBuilders.FontStyle =
    LayoutElementBuilders.FontStyle.Builder()
        .setSize(sp(12f))
        .setColor(argb(Color.GRAY))
        .build()

// "Updated Xs/Xm ago", bound to a ProtoLayout dynamic expression so the tile renderer keeps ticking
// it up off the platform clock by itself - no onTileRequest call, no app wake-up needed. Renderers
// that don't support dynamic values fall back to the static text computed at render time.
internal fun TileService.updatedAgoText(): LayoutElementBuilders.Text {
    if (ReceiveData.time == 0L) {
        return LayoutElementBuilders.Text.Builder()
            .setText(getString(CR.string.tile_updated_never))
            .setFontStyle(updatedAgoFontStyle)
            .build()
    }
    val (secPrefix, secSuffix) = placeholderParts(CR.string.tile_updated_seconds_ago)
    val (minPrefix, minSuffix) = placeholderParts(CR.string.tile_updated_minutes_ago)
    val elapsed = DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(ReceiveData.time))
        .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
    val secondsText = DynamicString.constant(secPrefix)
        .concat(elapsed.toIntSeconds().format())
        .concat(DynamicString.constant(secSuffix))
    val minutesText = DynamicString.constant(minPrefix)
        .concat(elapsed.toIntMinutes().format())
        .concat(DynamicString.constant(minSuffix))
    val liveText = DynamicString.onCondition(elapsed.toIntSeconds().lt(60))
        .use(secondsText)
        .elseUse(minutesText)
    val textProp = TypeBuilders.StringProp.Builder(staticUpdatedAgoText())
        .setDynamicValue(liveText)
        .build()
    return LayoutElementBuilders.Text.Builder()
        .setText(textProp)
        .setLayoutConstraintsForDynamicText(
            // Only used to size the text box (widest realistic value); never displayed.
            TypeBuilders.StringLayoutConstraint.Builder(getString(CR.string.tile_updated_minutes_ago, 59)).build()
        )
        .setFontStyle(updatedAgoFontStyle)
        .build()
}
