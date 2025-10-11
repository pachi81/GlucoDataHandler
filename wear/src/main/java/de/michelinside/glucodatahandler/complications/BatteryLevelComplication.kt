package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils

enum class BatteryLevelType {
    PHONE_WATCH_BATTERY_LEVEL,
    WATCH_BATTERY_LEVEL,
    PHONE_BATTERY_LEVEL
}

open class BatteryLevelComplicationBase(val type: BatteryLevelType): SuspendingComplicationDataSourceService() {
    protected val LOG_ID = "GDH.BatteryLevelComplication"

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        try {
            Log.d(
                LOG_ID,
                "onComplicationRequest called for ID " + request.complicationInstanceId.toString() + " with type " + request.complicationType.toString()
            )
            GlucoDataServiceWear.start(this)
            return getComplicationData(request)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationRequest exception: " + exc.message.toString())
        }
        return null
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(ComplicationRequest(0, type, false))!!
    }

    private fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> getShortTextComplicationData(request.complicationInstanceId)
            ComplicationType.RANGED_VALUE -> getRangeValueComplicationData(request.complicationInstanceId)
            else -> {
                Log.e(
                    LOG_ID,
                    "Unsupported type for battery level: " + request.complicationType.toString()
                )
                null
            }
        }
    }

    protected fun getUnit(): String {
        val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        if(sharedPref.getBoolean(Constants.SHARED_PREF_SHOW_BATTERY_PERCENT, true))
            return "%"
        return ""
    }

    open fun getRangeValue(): Float {
        return Float.NaN
    }

    private fun getRangeValueComplicationData(complicationInstanceId: Int): ComplicationData? {
        val value = getRangeValue()
        if(value.isNaN())
            return null
        val colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN)
        val colorRamp = ColorRamp(colors, true)
        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue(value, 0F, 100F),
            min = 0F,
            max = 100F,
            contentDescription = descriptionText()
        )
            .setTitle(getTitle())
            .setText(getText())
            .setMonochromaticImage(getIcon())
            .setTapAction(
                PackageUtils.getAppIntent(
                    applicationContext,
                    WearActivity::class.java,
                    complicationInstanceId
                )
            )
            .setColorRamp(colorRamp)
            .build()
    }

    private fun getShortTextComplicationData(complicationInstanceId: Int): ComplicationData {
        return ShortTextComplicationData.Builder(
            getText(),
            descriptionText()
        )
            .setTitle(getTitle())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction(complicationInstanceId))
            .build()
    }

    open fun getText(): PlainComplicationText =
        plainText("⌚" + BatteryReceiver.batteryPercentage.toString() + getUnit())
    open fun getIcon(): MonochromaticImage? = null
    open fun getTitle(): PlainComplicationText? = null

    fun getPhoneBatteryDescr(force: Boolean): String {
        val level = getPhoneLevel()
        if (level != null) {
            return resources.getString(CR.string.source_phone) + " " + level.toString() + getUnit()
        } else if (force) {
            return resources.getString(CR.string.source_phone) + " " + resources.getString(CR.string.not_available)
        }
        return ""
    }

    fun getWatchBatteryDescr(): String {
        if(BatteryReceiver.batteryPercentage > 0)
            return resources.getString(CR.string.source_wear) + " " + BatteryReceiver.batteryPercentage.toString() + getUnit()
        return resources.getString(CR.string.source_wear) + " " + resources.getString(CR.string.not_available)
    }

    private fun descriptionText(): PlainComplicationText {
        return plainText(when (type) {
            BatteryLevelType.PHONE_WATCH_BATTERY_LEVEL -> getWatchBatteryDescr() + " " + getPhoneBatteryDescr(false)
            BatteryLevelType.WATCH_BATTERY_LEVEL -> getWatchBatteryDescr()
            BatteryLevelType.PHONE_BATTERY_LEVEL -> getPhoneBatteryDescr(true)
        })
    }

    private fun getTapAction(
        complicationInstanceId: Int
    ): PendingIntent {
        return PackageUtils.getAppIntent(
            applicationContext,
            WearActivity::class.java,
            complicationInstanceId
        )
    }

    protected fun plainText(text: CharSequence): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    protected fun getPhoneLevel(): Int? {
        val levels = WearPhoneConnection.getBatterLevels()
        if (levels.isNotEmpty() && levels[0] > 0) {
            return levels[0]
        }
        return null
    }
}


class BatteryLevelComplication: BatteryLevelComplicationBase(BatteryLevelType.PHONE_WATCH_BATTERY_LEVEL) {

    override fun getTitle(): PlainComplicationText? {
        val level = getPhoneLevel()
        if (level != null) {
            return plainText("\uD83D\uDCF1" + level.toString() + getUnit())
        }
        return null
    }
}

class WatchBatteryLevelComplication: BatteryLevelComplicationBase(BatteryLevelType.WATCH_BATTERY_LEVEL) {
    override fun getRangeValue(): Float = BatteryReceiver.batteryPercentage.toFloat()

    override fun getText(): PlainComplicationText {
        if(BatteryReceiver.batteryPercentage > 0)
            return plainText(BatteryReceiver.batteryPercentage.toString() + getUnit())
        return plainText("--${getUnit()}")
    }

    override fun getIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_watch)
        ).build()
}

class PhoneBatteryLevelComplication: BatteryLevelComplicationBase(BatteryLevelType.PHONE_BATTERY_LEVEL) {
    override fun getText(): PlainComplicationText {
        val level = getPhoneLevel()
        if (level != null) {
            return plainText(level.toString() + getUnit())
        }
        return plainText("---${getUnit()}")
    }

    override fun getRangeValue(): Float {
        val level = getPhoneLevel()
        if (level != null) {
            return level.toFloat()
        }
        return 0F
    }

    override fun getIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_phone)
        ).build()
}

object BatteryLevelComplicationUpdater: NotifierInterface {
    private var complicationClasses = mutableListOf(BatteryLevelComplication::class.java, WatchBatteryLevelComplication::class.java, PhoneBatteryLevelComplication::class.java)
    val LOG_ID = "GDH.BatteryLevelComplicationUpdater"
    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString() )
        complicationClasses.forEach {
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(
                        context,
                        it
                    )
                )
                .requestUpdateAll()
        }
    }
}