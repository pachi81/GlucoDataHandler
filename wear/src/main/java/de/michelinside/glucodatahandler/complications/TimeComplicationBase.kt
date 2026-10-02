package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import java.time.Duration
import java.util.*

open class TimeComplicationBase : BgValueComplicationService() {
    fun timeText(short: Boolean = false): PlainComplicationText {
        return plainText(ReceiveData.getElapsedTimeMinuteAsString(this, short))
    }
    override fun getDescription(): String {
        return getDescriptionForContent(time = true)
    }
}

class LongGlucoseWithDeltaAndTrendAndTimeComplication: TimeComplicationBase() {
    override fun getLongTextComplicationData(id: Int): ComplicationData {
        val text: PlainComplicationText
        val title: PlainComplicationText
        if(GlucoDataService.patientName.isNullOrEmpty()) {
            text = plainText("Δ: " + ReceiveData.getDeltaAsString())
            title = timeText()
        } else {
            text = plainText( ReceiveData.getElapsedTimeMinuteAsString(this, false) + " Δ: " + ReceiveData.getDeltaAsString())
            title = plainText(GlucoDataService.patientName!!)
        }

        return LongTextComplicationData.Builder(
            text,
            descriptionText()
        )
            .setTitle(title)
            .setSmallImage(getGlucoseTrendImage(id))
            .setTapAction(getTapAction(id))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, delta = true, trend = true, time = true)
    }
}

class LongGlucoseWithDeltaAndTrendIconAndTimeComplication: TimeComplicationBase() {
    override fun getLongTextComplicationData(id: Int): ComplicationData {
        return LongTextComplicationData.Builder(
            plainText("Δ: " + ReceiveData.getDeltaAsString()),
            descriptionText()
        )
            .setTitle(timeText())
            .setSmallImage(glucoseImage(id))
            .setMonochromaticImage(arrowIcon(id))
            .setTapAction(getTapAction(id))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, delta = true, trend = true, time = true)
    }
}

class TimeStampComplication: TimeComplicationBase() {
    override fun getText(): PlainComplicationText = timeText(true)
    override fun getIcon(id: Int): MonochromaticImage = glucoseIcon()

    override fun getRangeValueComplicationData(id: Int): ComplicationData {
        val time = Date(ReceiveData.time)

        val value = if (ReceiveData.isObsoleteTime(3600)) 0F else (time.minutes * 60 + time.seconds).toFloat()
        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue(value, 0F, 3599F),
            min = 0F,
            max = 3599F,
            contentDescription = descriptionText(),
        )
            .setText(getText())
            .setMonochromaticImage(getIcon(id))
            .setTapAction(PackageUtils.getTapActionIntent(this, this.packageName, id))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(time = true)
    }
}

class ShortDeltaWithTimeComplication: TimeComplicationBase() {
    override fun getTitle(): PlainComplicationText = timeText(true)
    override fun getText(): PlainComplicationText = deltaText()
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true, time = true)
    }
}


class SensorAgeComplication: BgValueComplicationService() {
    private fun ageText(): String {
        if(ReceiveData.sensorStartTime > 0) {
            val duration = Duration.ofMillis(System.currentTimeMillis() - ReceiveData.sensorStartTime)
            val days = duration.toDays()
            val hours = duration.minusDays(days).toHours()
            return this.getString(R.string.sensor_age_value).format(days, hours)
        }
        return "---"
    }

    override fun getText(): PlainComplicationText = plainText(ageText())
    override fun getIcon(id: Int): MonochromaticImage = unicodeIcon(id, "⌛")

    override fun getRangeValueComplicationData(id: Int): ComplicationData {
        val duration = Duration.ofMillis(System.currentTimeMillis() - ReceiveData.sensorStartTime)
        val runtime = sharedPref.getString(Constants.SHARED_PREF_SENSOR_RUNTIME, "14")?.toFloatOrNull()
        val max = if(runtime != null && runtime > 0F) {
            runtime * 24 * 60 // minutes
        } else {
            0F
        }

        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue(duration.toMinutes().toFloat(), 0F, max),
            min = 0F,
            max = max,
            contentDescription = descriptionText(),
        )
            .setText(getText())
            .setMonochromaticImage(getIcon(id))
            .setTapAction(PackageUtils.getTapActionIntent(this, this.packageName, id))
            .build()
    }

    override fun getDescription(): String {
        if(ReceiveData.sensorStartTime > 0) {
            return this.getString(R.string.sensor_age_label) + " " + ageText()
        }
        return this.getString(R.string.sensor_age_label) + " " + this.getString(R.string.not_available)
    }
}