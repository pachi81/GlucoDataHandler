package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.ReceiveData

class LongTextComplication: BgValueComplicationService(ComplicationType.LONG_TEXT) {

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())

        return LongTextComplicationData.Builder(
            plainText(ReceiveData.getClucoseAsString() + " " + ReceiveData.getDeltaAsString()),
            resText(R.string.long_bg_value_content_description)
        )
            .setMonochromaticImage(arrowIcon())
            .setTapAction(getTapAction())
            .build()
    }
}