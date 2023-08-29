package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import java.util.*

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

class ShortGlucoseWithTrendTextComplication: ShortClucoseComplication() {
    override fun getTitle(): PlainComplicationText = trendText()
}

class ShortGlucoseWithDeltaAndTrendComplication: ShortClucoseComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
    override fun getIcon(): MonochromaticImage = arrowIcon()
    override fun getLongTextComplicationData(): ComplicationData {
        return LongTextComplicationData.Builder(
            plainText(" Δ: " + ReceiveData.getDeltaAsString() + "   " + ReceiveData.getRateSymbol().toString() + " (" + ReceiveData.getRateAsString() + ")"),
            descriptionText()
        )
            .setSmallImage(glucoseImage())
            .setTapAction(getTapAction())
            .build()
    }
}

class LongGlucoseWithDeltaAndTrendAndTimeComplication: ShortClucoseComplication() {
    override fun getLongTextComplicationData(): ComplicationData {
        return LongTextComplicationData.Builder(
            plainText(" Δ: " + ReceiveData.getDeltaAsString()),
            descriptionText()
        )
            .setTitle(timeText())
            .setSmallImage(getGlucoseTrendImage())
            .setTapAction(getTapAction())
            .build()
    }
}

class TimeStampComplication: BgValueComplicationService() {
    override fun getText(): PlainComplicationText = timeText(true)
    override fun getIcon(): MonochromaticImage = glucoseIcon()

    override fun getRangeValueComplicationData(): ComplicationData {
        val time = Date(ReceiveData.time)

        val value = if (ReceiveData.isObsolete(3600)) -1F else (time.minutes * 60 + time.seconds).toFloat()
        return RangedValueComplicationData.Builder(
            value = value,
            min = 0F,
            max = 3600F,
            contentDescription = descriptionText()
        )
            .setText(getText())
            .setMonochromaticImage(getIcon())
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

class ShortGlucoseWithDeltaAndTrendRangeComplication: ShortGlucoseWithTrendRangeComplication() {
    override fun getTitle(): PlainComplicationText = deltaText()
}

class ShortDeltaWithTrendArrowAndTrendRangeComplication: ShortGlucoseWithTrendRangeComplication() {
    override fun getText(): PlainComplicationText = deltaText()
    override fun getIcon(): MonochromaticImage = arrowIcon()
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

class ShortDeltaWithTimeComplication: ShortDeltaComplication() {
    override fun getTitle(): PlainComplicationText = timeText(true)
}

open class ShortTrendComplication: ShortClucoseComplication() {
    override fun getText(): PlainComplicationText = trendText()
}

class ShortTrendWithTrendArrowComplication: ShortTrendComplication() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class ShortTrendWithIconComplication: ShortTrendComplication() {
    override fun getIcon(): MonochromaticImage = trendIcon()
}

