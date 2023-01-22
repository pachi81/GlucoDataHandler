package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils

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
            return getRangeValueComplicationData()
        }
        Log.e(LOG_ID, "Unsupported complication type: " + request.complicationType.toString())
        return null
    }

    open fun getRangeValueComplicationData(): RangedValueComplicationData {
        // for any reason, the min value seems to be ignored, so start at zero (which is 40)
        // and substract the 40 from the value
        val value = ReceiveData.rawValue.toFloat() - Constants.GLUCOSE_MIN_VALUE
        val max = 280F - Constants.GLUCOSE_MIN_VALUE
        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue(value, 0F, max),
            min = 0F,
            max = 240F,
            contentDescription = descriptionText()
        )
            .setTitle(getTitle())
            .setText(getText())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
}

class ShortGlucoseWithIconComplication: ShortClucoseComplication() {
    override fun getIcon(): MonochromaticImage = glucoseIcon()
}

class ShortGlucoseWithTrendComplication: ShortClucoseComplication() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class ShortGlucoseWithTrendRangeComplication: ShortClucoseComplication() {
    override fun getRangeValueComplicationData(): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue( ReceiveData.rate, -3.8F, +3.8F),
            min = 0F,
            max = 4F,
            contentDescription = descriptionText()
        )
            .setTitle(getTitle())
            .setText(getText())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
}

class ShortGlucoseWithDeltaComplication: ShortClucoseComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
}

open class ShortDeltaComplication: ShortClucoseComplication() {
    override fun getText(): PlainComplicationText = deltaText()

    override fun getRangeValueComplicationData(): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue( ReceiveData.rate, -3.8F, +3.8F),
            min = 0F,
            max = 4F,
            contentDescription = descriptionText()
        )
            .setTitle(getTitle())
            .setText(getText())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
}

class ShortDeltaWithTrendComplication: ShortDeltaComplication() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class ShortDeltaWithIconComplication: ShortDeltaComplication() {
    override fun getIcon(): MonochromaticImage = deltaIcon()
}
