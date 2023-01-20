package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest

open class LongTextComplication: BgValueComplicationService(ComplicationType.LONG_TEXT) {

    open fun getText(): PlainComplicationText {
        return glucoseAndDeltaText()
    }

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())

        return LongTextComplicationData.Builder(
            getText(),
            descriptionText()
        )
            .setTitle(getTitle())
            .setSmallImage(arrowImage())
            .setTapAction(getTapAction())
            .build()
    }
}

class LongText2LinesComplication: LongTextComplication() {

    override fun getText(): PlainComplicationText =
        glucoseText()

    override fun getTitle(): PlainComplicationText? =
        deltaText()
}