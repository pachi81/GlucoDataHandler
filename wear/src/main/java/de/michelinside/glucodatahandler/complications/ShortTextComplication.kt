package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils

open class ShortClucoseComplication:  BgValueComplicationService() {
}

class ShortGlucoseWithIconComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = glucoseIcon()
}

class ShortGlucoseWithTrendComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class ShortGlucoseWithDeltaComplication: ShortClucoseComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
}

class ShortGlucoseWithDeltaAndTrendComplication: ShortClucoseComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
    override fun getIcon(): MonochromaticImage = arrowIcon()
    override fun getLongTextComplicationData(): ComplicationData {
        return LongTextComplicationData.Builder(
            plainText(" Δ: " + ReceiveData.getDeltaAsString() + "   " + GlucoDataUtils.getRateSymbol(ReceiveData.rate).toString() + " (" + ReceiveData.getRateAsString() + ")"),
            descriptionText()
        )
            .setSmallImage(glucoseImage())
            .setTapAction(getTapAction())
            .build()
    }
}

open class ShortGlucoseWithTrendRangeComplication: BgValueComplicationService() {
    override fun getRangeValueComplicationData(): ComplicationData {
        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue( ReceiveData.rate, -4F, +4F),
            min = -4F,
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

open class ShortDeltaComplication: ShortClucoseComplication() {
    override fun getText(): PlainComplicationText = deltaText()
}

class ShortDeltaWithTrendComplication: ShortDeltaComplication() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class ShortDeltaWithIconComplication: ShortDeltaComplication() {
    override fun getIcon(): MonochromaticImage = deltaIcon()
}

