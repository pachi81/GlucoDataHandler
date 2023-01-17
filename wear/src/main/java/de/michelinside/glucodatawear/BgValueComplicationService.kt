package de.michelinside.glucodatahandler

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
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import de.michelinside.glucodatahandler.common.ReceiveDataSource

class BgValueComplicationService : SuspendingComplicationDataSourceService(), ReceiveDataInterface {
    private val LOG_ID = "GlucoDataHandler.ShortBgValueComplicationService"
    private var instanceMap = mutableMapOf<Int, ComplicationType> ()

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        Log.d(LOG_ID, "onComplicationActivated called for id " + complicationInstanceId + " (" + type + ")" )
        var serviceIntent = Intent(this, GlucoDataService::class.java)
        this.startService(serviceIntent)
        if(instanceMap.isEmpty())
            ReceiveData.addNotifier(this)
        instanceMap[complicationInstanceId] = type
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(LOG_ID, "onComplicationDeactivated called for id " + complicationInstanceId )
        super.onComplicationDeactivated(complicationInstanceId)
        instanceMap.remove(complicationInstanceId)
        if(instanceMap.isEmpty())
            ReceiveData.remNotifier(this)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "onComplicationRequest called for " + request.complicationType.toString())
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            return null
        }
        return getComplicationData(request.complicationType)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(type)
    }

    private fun getComplicationData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "getComplicationData called for " + type.toString())
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(
                text = ReceiveData.glucose.toString() + ReceiveData.getRateSymbol().toString()
            ).build(),
            contentDescription = PlainComplicationText.Builder(
                text = getText(R.string.short_bg_value_content_description)
            ).build()
        ).build()
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "Update " + instanceMap.size.toString() + " active complication(s)")
        instanceMap.forEach {
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(this, javaClass)
                )
                .requestUpdate(it.key)
        }
    }

}