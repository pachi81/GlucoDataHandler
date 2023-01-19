package de.michelinside.glucodatahandler

import android.R.color
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import de.michelinside.glucodatahandler.common.ReceiveDataSource


abstract class BgValueComplicationService(type: ComplicationType) : SuspendingComplicationDataSourceService(), ReceiveDataInterface {
    protected val LOG_ID = "GlucoDataHandler.Complication." + type.toString()
    private var instanceIds = mutableListOf<Int> ()
    val complicationTpe = type

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        Log.d(LOG_ID, "onComplicationActivated called for id " + complicationInstanceId + " (" + type + ")" )
        val serviceIntent = Intent(this, GlucoDataService::class.java)
        this.startService(serviceIntent)
        if(instanceIds.isEmpty())
            ReceiveData.addNotifier(this)
        instanceIds.add(complicationInstanceId)
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(LOG_ID, "onComplicationDeactivated called for id " + complicationInstanceId )
        super.onComplicationDeactivated(complicationInstanceId)
        instanceIds.remove(complicationInstanceId)
        if(instanceIds.isEmpty())
            ReceiveData.remNotifier(this)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "onComplicationRequest called for " + request.complicationType.toString())
        if (request.complicationType != complicationTpe) {
            Log.w(LOG_ID, "complication type: " + request.complicationType.toString() + " != " + complicationTpe.toString())
            return null
        }
        return getComplicationData(request)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(ComplicationRequest(0, type))!!
    }

    abstract fun getComplicationData(request: ComplicationRequest): ComplicationData?

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "Update " + instanceIds.size.toString() + " active " + complicationTpe.toString() + " complication(s)")
        instanceIds.forEach {
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(this, javaClass)
                )
                .requestUpdate(it)
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

    fun getArrowIcon(): Int {
        if((System.currentTimeMillis()- ReceiveData.time) > (600 * 1000))
            return R.drawable.icon_question
        if (ReceiveData.rate >= 3.5f) return R.drawable.icon_chevron_up
        if (ReceiveData.rate >= 2.0f) return R.drawable.icon_stick_up
        if (ReceiveData.rate >= 1.0f) return R.drawable.icon_stick_up_right
        if (ReceiveData.rate > -1.0f) return R.drawable.icon_stick_right
        if (ReceiveData.rate > -2.0f) return R.drawable.icon_stick_down_right
        if (ReceiveData.rate > -3.5f) return R.drawable.icon_stick_down
        return if (java.lang.Float.isNaN(ReceiveData.rate)) R.drawable.icon_question else R.drawable.icon_chevron_down
    }

    open fun getIcon(): MonochromaticImage? = null

    fun plainText(text: CharSequence): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    fun glucoseText(): PlainComplicationText =
        plainText(ReceiveData.getClucoseAsString())

    fun deltaText(): PlainComplicationText =
        plainText("Δ: " + ReceiveData.getDeltaAsString())

    fun glucoseAndDeltaText(): PlainComplicationText =
        plainText(ReceiveData.getClucoseAsString() + " Δ: " + ReceiveData.getDeltaAsString())

    fun resText(resId: Int): PlainComplicationText =
        plainText(getText(resId))

    fun arrowIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, getArrowIcon())
        ).build()

    fun arrowImage(): SmallImage {
        var icon = Icon.createWithResource(this, getArrowIcon())
        icon.setTint(ReceiveData.getClucoseColor())
        icon.setTintMode(PorterDuff.Mode.SRC_IN)
        return  SmallImage.Builder(
            image = icon,
            type = SmallImageType.ICON
        )
            .setAmbientImage(Icon.createWithResource(this, getArrowIcon()))
            .build()
    }
}