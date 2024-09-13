package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
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
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils

open class BatteryLevelComplicationBase: SuspendingComplicationDataSourceService() {
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

    open fun getRangeValue(): Float {
        return Float.NaN
    }

    private fun getRangeValueComplicationData(complicationInstanceId: Int): ComplicationData? {
        val value = getRangeValue()
        if(value.isNaN())
            return null
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
                    complicationInstanceId,
                    false
                )
            )
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
        plainText("⌚" + BatteryReceiver.batteryPercentage.toString() + "%")
    open fun getIcon(): MonochromaticImage? = null
    open fun getTitle(): PlainComplicationText? = null

    private fun descriptionText(): PlainComplicationText =
        plainText(getText(this.applicationInfo.labelRes))

    private fun getTapAction(
        complicationInstanceId: Int
    ): PendingIntent {
        return PackageUtils.getAppIntent(
            applicationContext,
            WearActivity::class.java,
            complicationInstanceId,
            false
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


class BatteryLevelComplication: BatteryLevelComplicationBase() {

    override fun getTitle(): PlainComplicationText? {
        val level = getPhoneLevel()
        if (level != null) {
            return plainText("\uD83D\uDCF1" + level.toString() + "%")
        }
        return null
    }
}

class WatchBatteryLevelComplication: BatteryLevelComplicationBase() {
    override fun getRangeValue(): Float = BatteryReceiver.batteryPercentage.toFloat()

    override fun getText(): PlainComplicationText =
        plainText(BatteryReceiver.batteryPercentage.toString() + "%")

    override fun getIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_watch)
        ).build()
}

class PhoneBatteryLevelComplication: BatteryLevelComplicationBase() {
    override fun getText(): PlainComplicationText {
        val level = getPhoneLevel()
        if (level != null) {
            return plainText(level.toString() + "%")
        }
        return plainText("--%")
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