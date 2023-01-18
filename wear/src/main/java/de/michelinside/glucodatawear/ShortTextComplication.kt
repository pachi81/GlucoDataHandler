package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest

open class ShortTextComplication(descrResId: Int = R.string.short_bg_value_content_description):  BgValueComplicationService(ComplicationType.SHORT_TEXT) {
    private val descriptionResourceId = descrResId

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())

        return ShortTextComplicationData.Builder(
            glucoseText(),
            resText(descriptionResourceId)
        )
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
}

class ShortTextWithIconComplication: ShortTextComplication(R.string.short_bg_value_with_arrow_content_description) {
    override fun getIcon(): MonochromaticImage? = arrowIcon()
}
