package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.ReceiveData

open class ShortClucoseComplication:  BgValueComplicationService(ComplicationType.SHORT_TEXT) {

    open fun getText(): PlainComplicationText =
        glucoseText()

    override fun isTypeSupported(type: ComplicationType): Boolean {
        return super.isTypeSupported(type) || type == ComplicationType.RANGED_VALUE
    }

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())

        if(request.complicationType == ComplicationType.SHORT_TEXT) {
            return ShortTextComplicationData.Builder(
                getText(),
                descriptionText()
            )
                .setTitle(getTitle())
                .setMonochromaticImage(getIcon())
                .setTapAction(getTapAction())
                .build()
        }
        else if (request.complicationType == ComplicationType.RANGED_VALUE)
        {
            // for any reason, the min value seems to be ignored, so start at zero (which is 40)
            // and substract the 40 from the value
            return RangedValueComplicationData.Builder(
                value = maxOf(0F, ReceiveData.rawValue.toFloat()-40),
                min = 0F,
                max = maxOf(280F, ReceiveData.rawValue.toFloat()-40),
                contentDescription = descriptionText()
            )
                .setTitle(getTitle())
                .setText(getText())
                .setMonochromaticImage(getIcon())
                .setTapAction(getTapAction())
                .build()
        }
        Log.e(LOG_ID, "Unsupported complication type: " + request.complicationType.toString())
        return null
    }
}

class ShortGlucoseWithIconComplication: ShortClucoseComplication() {
    override fun getIcon(): MonochromaticImage = glucoseIcon()
}

class ShortGlucoseWithTrendComplication: ShortClucoseComplication() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class ShortGlucoseWithDeltaComplication: ShortClucoseComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
}

class ShortDeltaComplication: ShortClucoseComplication() {
    override fun getText(): PlainComplicationText = deltaText()
}

class ShortDeltaWithIconComplication: ShortClucoseComplication() {
    override fun getIcon(): MonochromaticImage = deltaIcon()
    override fun getText(): PlainComplicationText = deltaText()
}
