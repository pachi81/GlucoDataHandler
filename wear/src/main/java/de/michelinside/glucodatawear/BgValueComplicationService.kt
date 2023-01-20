package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import de.michelinside.glucodatahandler.common.ReceiveDataSource


abstract class BgValueComplicationService(type: ComplicationType) : SuspendingComplicationDataSourceService() {
    protected val LOG_ID = "GlucoDataHandler.Complication." + type.toString()
    val complicationTpe = type
    var descriptionResId: Int = R.string.app_name

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_ID, "onCreate called")
        descriptionResId = this.applicationInfo.labelRes
    }

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        Log.d(LOG_ID, "onComplicationActivated called for id " + complicationInstanceId + " (" + type + ")" )
        val serviceIntent = Intent(this, GlucoDataService::class.java)
        this.startService(serviceIntent)
        ActiveComplicationHandler.addInstance(complicationInstanceId, ComponentName(this, javaClass))
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(LOG_ID, "onComplicationDeactivated called for id " + complicationInstanceId )
        super.onComplicationDeactivated(complicationInstanceId)
        ActiveComplicationHandler.remInstance(complicationInstanceId)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "onComplicationRequest called for " + request.complicationType.toString())
        if (!isTypeSupported(request.complicationType)) {
            Log.w(LOG_ID, "invalid complication type: " + request.complicationType.toString())
            return null
        }
        return getComplicationData(request)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(ComplicationRequest(0, type))!!
    }

    open fun isTypeSupported(type: ComplicationType): Boolean =
        type == complicationTpe

    abstract fun getComplicationData(request: ComplicationRequest): ComplicationData?

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
    open fun getTitle(): PlainComplicationText? = null

    fun plainText(text: CharSequence): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    fun glucoseText(): PlainComplicationText =
        plainText(ReceiveData.getClucoseAsString())

    fun deltaWithIconText(): PlainComplicationText =
        plainText("Δ: " + ReceiveData.getDeltaAsString())

    fun deltaText(): PlainComplicationText =
        plainText(ReceiveData.getDeltaAsString())

    fun glucoseAndDeltaText(): PlainComplicationText =
        plainText(ReceiveData.getClucoseAsString() + "  Δ: " + ReceiveData.getDeltaAsString())

    fun resText(resId: Int): PlainComplicationText =
        plainText(getText(resId))

    fun descriptionText(): PlainComplicationText =
        resText(descriptionResId)

    fun arrowIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, getArrowIcon())
        ).build()

    fun glucoseIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.glucose)
        ).build()

    fun ambientArrowIcon(): Icon {
        val icon = Icon.createWithResource(this, getArrowIcon())
        icon.setTint(Color.GRAY)
        icon.setTintMode(PorterDuff.Mode.SRC_IN)
        return icon
    }

    fun deltaIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_delta)
        ).build()

    fun arrowImage(): SmallImage {
        val icon = Icon.createWithResource(this, getArrowIcon())
        icon.setTint(ReceiveData.getClucoseColor())
        icon.setTintMode(PorterDuff.Mode.SRC_IN)
        return  SmallImage.Builder(
            image = icon,
            type = SmallImageType.ICON
        )
            .setAmbientImage(ambientArrowIcon())
            .build()
    }

}

object ActiveComplicationHandler: ReceiveDataInterface {
    private val LOG_ID = "GlucoDataHandler.ActiveComplicationHandler"
    private var instanceContextMap = mutableMapOf<Int, ComponentName> ()

    fun addInstance(instanceId: Int, providerComponent: ComponentName)
    {
        if(instanceContextMap.isEmpty())
            ReceiveData.addNotifier(this)
        instanceContextMap[instanceId] = providerComponent
    }

    fun remInstance(instanceId: Int)
    {
        instanceContextMap.remove(instanceId)
        if(instanceContextMap.isEmpty())
            ReceiveData.remNotifier(this)
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "Update " + instanceContextMap.size.toString() + " active complication(s)")
        instanceContextMap.forEach {
            Log.d(LOG_ID, "Update " + it.value.toString() + " id: " + it.key.toString())
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = it.value
                )
                .requestUpdate(it.key)
        }
    }

}