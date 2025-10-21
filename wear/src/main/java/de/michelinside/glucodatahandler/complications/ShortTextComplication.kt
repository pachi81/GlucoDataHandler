package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.Utils

open class ShortGlucoseComplication:  BgValueComplicationService() {
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
    }
}

class ShortGlucoseWithIconComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = glucoseIcon()
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
    }
}

class ShortGlucoseWithTrendComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
    }
}

class ShortGlucoseWithDeltaComplication: ShortGlucoseComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, delta = true)
    }
}

class ShortGlucoseWithDeltaAndTrendComplication: ShortGlucoseComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
    override fun getIcon(): MonochromaticImage = arrowIcon()
    override fun getLongTextComplicationData(): ComplicationData {
        return LongTextComplicationData.Builder(
            plainText("Δ: " + ReceiveData.getDeltaAsString()),
            descriptionText()
        )
            .setSmallImage(glucoseImage())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, delta = true, trend = true)
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
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
    }
}

open class ShortDeltaComplication: ShortGlucoseComplication() {
    override fun getText(): PlainComplicationText = deltaText()
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true)
    }
}

class ShortDeltaWithTrendComplication: ShortDeltaComplication() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true, trend = true)
    }
}

class ShortDeltaWithIconComplication: ShortDeltaComplication() {
    override fun getIcon(): MonochromaticImage = deltaIcon()
}

class OtherUnitComplication: BgValueComplicationService() {
    override fun getText(): PlainComplicationText = plainText(ReceiveData.getGlucoseAsOtherUnit())
    override fun getTitle(): PlainComplicationText = plainText(ReceiveData.getOtherUnit())
    override fun getDescription(): String {
        return ReceiveData.getGlucoseAsOtherUnit() + " " + ReceiveData.getOtherUnit()
    }
}

class ShortGlucoseWithNameComplication: ShortGlucoseComplication() {
    override fun getTitle(): PlainComplicationText = plainText(GlucoDataService.patientName?:"")
    override fun getDescription(): String {
        return getDescriptionForContent(patientName = true, glucose = true)
    }
}

open class ShortNameComplication:  BgValueComplicationService() {
    override fun getText(): PlainComplicationText = plainText(GlucoDataService.patientName?:"n/a")
    override fun getDescription(): String {
        return getDescriptionForContent(patientName = true)
    }
}