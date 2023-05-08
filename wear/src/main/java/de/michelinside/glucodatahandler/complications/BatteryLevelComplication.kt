package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver

class BatteryLevelComplication: SuspendingComplicationDataSourceService() {
    protected val LOG_ID = "GlucoDataHandler.BatteryLevelComplication"

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        try {
            Log.d(LOG_ID, "onComplicationRequest called for ID " + request.complicationInstanceId.toString() + " with type " + request.complicationType.toString())
            GlucoDataServiceWear.start(this)
            return getComplicationData(request)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationRequest exception: " + exc.message.toString() )
        }
        return null
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(ComplicationRequest(0, type,false))!!
    }

    private fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        if(request.complicationType != ComplicationType.SHORT_TEXT) {
            Log.e(LOG_ID, "Unsupported type for battery level: " + request.complicationType.toString())
            return null
        }
        return ShortTextComplicationData.Builder(
            getText(),
            plainText(getText(this.applicationInfo.labelRes))
        )
            .setTitle(getTitle())
            .setTapAction(getTapAction())
            .build()
    }

    private fun plainText(text: CharSequence): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    private fun getTapAction(): PendingIntent? {
        val launchIntent = Intent(this, WaerActivity::class.java)
        launchIntent.action = Intent.ACTION_MAIN
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return PendingIntent.getActivity(
            applicationContext,
            3,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getTitle(): PlainComplicationText? {
        val levels =
            if(BuildConfig.DEBUG) mutableListOf<Int>(BatteryReceiver.batteryPercentage)
            else WearPhoneConnection.getBatterLevels()
        if (levels.isNotEmpty() && levels[0] > 0) {
            return plainText("\uD83D\uDCF1" + levels[0].toString() + "%")
        }
        return null
    }

    private fun getText(): PlainComplicationText =
        plainText("⌚" + BatteryReceiver.batteryPercentage.toString() + "%")
}

object BatteryLevelComplicationUpdater: NotifierInterface {
    val LOG_ID = "GlucoDataHandler.BatteryLevelComplicationUpdater"
    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "OnReceiveData called for source " + dataSource.toString() )
        ComplicationDataSourceUpdateRequester
            .create(
                context = context,
                complicationDataSourceComponent = ComponentName(context, BatteryLevelComplication::class.java)
            )
            .requestUpdateAll()
    }
}