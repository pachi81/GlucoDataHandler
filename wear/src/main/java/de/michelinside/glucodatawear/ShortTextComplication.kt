package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest

open class ShortTextComplication:  BgValueComplicationService(ComplicationType.SHORT_TEXT) {

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())

        return ShortTextComplicationData.Builder(
            glucoseText(),
            descriptionText()
        )
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
}

class ShortTextWithIconComplication: ShortTextComplication() {
    override fun getIcon(): MonochromaticImage? = arrowIcon()
}
