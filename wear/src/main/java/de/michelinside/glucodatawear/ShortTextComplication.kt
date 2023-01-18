package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.ReceiveData

class ShortTextWithTitleComplication: BgValueComplicationService() {
    private val LOG_ID = "GlucoDataHandler.ShortTextWithTitleComplication"
    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            return null
        }
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(
                text = ReceiveData.getRateSymbol().toString()
            ).build(),
            contentDescription = PlainComplicationText.Builder(
                text = getText(R.string.short_bg_value_with_arrow_content_description)
            ).build()
        ).setTitle(
            PlainComplicationText.Builder(
                text = ReceiveData.getClucoseAsString()
            ).build()
        ).setTapAction(getTapAction()).build()
    }
}

class ShortTextOnlyComplication: BgValueComplicationService() {
    private val LOG_ID = "GlucoDataHandler.ShortTextComplication"
    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            return null
        }
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(
                text = ReceiveData.getClucoseAsString()
            ).build(),
            contentDescription = PlainComplicationText.Builder(
                text = getText(R.string.short_bg_value_content_description)
            ).build()
        ).setTapAction(getTapAction()).build()
    }
}