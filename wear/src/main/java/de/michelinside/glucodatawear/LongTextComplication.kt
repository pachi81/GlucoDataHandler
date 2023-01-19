package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.ReceiveData

open class LongTextComplication(descrResId: Int = R.string.long_bg_value_content_description): BgValueComplicationService(ComplicationType.LONG_TEXT) {
    private val descriptionResourceId = descrResId

    open fun getText(): PlainComplicationText {
        return glucoseAndDeltaText()
    }

    open fun getTitle(): PlainComplicationText? {
        return null
    }

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())

        return LongTextComplicationData.Builder(
            getText(),
            resText(descriptionResourceId)
        )
            .setTitle(getTitle())
            .setSmallImage(arrowImage())
            .setTapAction(getTapAction())
            .build()
    }
}

class LongText2LinesComplication: LongTextComplication(R.string.long_2_lines_bg_value_content_description) {

    override fun getText(): PlainComplicationText =
        glucoseText()

    override fun getTitle(): PlainComplicationText? =
        deltaText()
}