package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import de.michelinside.glucodatahandler.common.ReceiveDataSource

abstract class BgValueComplicationService : SuspendingComplicationDataSourceService(), ReceiveDataInterface {
    private val LOG_ID = "GlucoDataHandler.BgValueComplicationService"
    private var instanceMap = mutableMapOf<Int, ComplicationType> ()

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        Log.d(LOG_ID, "onComplicationActivated called for id " + complicationInstanceId + " (" + type + ")" )
        val serviceIntent = Intent(this, GlucoDataService::class.java)
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
        return getComplicationData(request)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(ComplicationRequest(0, type))!!
    }

    abstract fun getComplicationData(request: ComplicationRequest): ComplicationData?

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

    fun getTapAction(): PendingIntent? {
        var launchIntent: Intent? = packageManager.getLaunchIntentForPackage("tk.glucodata")
        if(launchIntent == null)
        {
            Log.d(LOG_ID, "Juggluco not found, use own one")
            launchIntent = Intent(this, WaerActivity::class.java)
        }
        launchIntent.setAction(Intent.ACTION_MAIN)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return PendingIntent.getActivity(applicationContext, System.currentTimeMillis().toInt(), launchIntent,  PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

}