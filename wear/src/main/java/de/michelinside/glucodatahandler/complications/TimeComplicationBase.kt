package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import java.util.*

open class TimeComplicationBase : BgValueComplicationService() {
    fun timeText(short: Boolean = false): PlainComplicationText {
        return plainText(ReceiveData.getElapsedTimeMinuteAsString(this, short))
    }
}

class LongGlucoseWithDeltaAndTrendAndTimeComplication: TimeComplicationBase() {
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

class TimeStampComplication: TimeComplicationBase() {
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

class ShortDeltaWithTimeComplication: TimeComplicationBase() {
    override fun getTitle(): PlainComplicationText = timeText(true)
    override fun getText(): PlainComplicationText = deltaText()
}