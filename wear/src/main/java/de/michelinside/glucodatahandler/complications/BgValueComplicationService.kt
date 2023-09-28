package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.*
import java.text.DateFormat


abstract class BgValueComplicationService : SuspendingComplicationDataSourceService() {
    protected val LOG_ID = "GlucoDataHandler.BgValueComplicationService"
    var descriptionResId: Int = R.string.app_name
    val shortTimeformat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    protected lateinit var sharedPref: SharedPreferences

    override fun onCreate() {
        try {
            super.onCreate()
            Log.d(LOG_ID, "onCreate called")
            descriptionResId = this.applicationInfo.labelRes
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            GlucoDataServiceWear.start(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        try {
            super.onComplicationActivated(complicationInstanceId, type)
            Log.d(LOG_ID, "onComplicationActivated called for id " + complicationInstanceId + " (" + type + ")" )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationActivated exception: " + exc.message.toString() )
        }
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        try {
            Log.d(LOG_ID, "onComplicationDeactivated called for id " + complicationInstanceId )
            ActiveComplicationHandler.remComplication(complicationInstanceId)
            super.onComplicationDeactivated(complicationInstanceId)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationDeactivated exception: " + exc.message.toString() )
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        try {
            Log.d(LOG_ID, "onComplicationRequest called for " + javaClass.simpleName + " ID " + request.complicationInstanceId.toString() + " with type " + request.complicationType.toString())
            GlucoDataServiceWear.start(this)
            // add here, because onComplicationActivated is not called after restart...
            ActiveComplicationHandler.addComplication(request.complicationInstanceId, ComponentName(this, javaClass))
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
        return when(request.complicationType) {
            ComplicationType.SHORT_TEXT -> getShortTextComplicationData()
            ComplicationType.RANGED_VALUE -> getRangeValueComplicationData()
            ComplicationType.LONG_TEXT -> getLongTextComplicationData()
            ComplicationType.SMALL_IMAGE -> getSmallImageComplicationData()
            ComplicationType.MONOCHROMATIC_IMAGE -> getIconComplicationData()
            ComplicationType.PHOTO_IMAGE -> getLargeImageComplicationData()
            else -> {
                Log.w(LOG_ID, "Unsupported type: " + request.complicationType)
                null
            }
        }
    }

    private fun getShortTextComplicationData(): ComplicationData {
        return ShortTextComplicationData.Builder(
            getText(),
            descriptionText()
        )
            .setTitle(getTitle())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }

    open fun getRangeValueComplicationData(): ComplicationData {
        val value = ReceiveData.glucose
        val max = if(ReceiveData.isMmol) 16F else 280F
        return RangedValueComplicationData.Builder(
            value = Utils.rangeValue(value, 0F, max),
            min = 0F,
            max = max,
            contentDescription = descriptionText()
        )
            .setTitle(getTitle())
            .setText(getText())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }

    open fun getLongTextComplicationData(): ComplicationData {
        return LongTextComplicationData.Builder(
            getText(),
            descriptionText()
        )
            .setTitle(getTitle())
            .setSmallImage(arrowImage())
            .setTapAction(getTapAction())
            .build()
    }

    private fun getSmallImageComplicationData(): ComplicationData {
        return SmallImageComplicationData.Builder (
            smallImage = getImage()!!,
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }

    open fun getLargeImageComplicationData(): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = Utils.getGlucoseAsIcon(ReceiveData.getClucoseColor(), true, 500,500),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }

    open fun getIconComplicationData(): ComplicationData {
        return MonochromaticImageComplicationData.Builder (
            getIcon()!!,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }

    open fun getIcon(): MonochromaticImage? = null
    open fun getTitle(): PlainComplicationText? = null
    open fun getText(): PlainComplicationText = glucoseText()
    open fun getImage(): SmallImage? = null

    open fun getTapAction(useExternalApp: Boolean = true): PendingIntent? {
        if (BuildConfig.DEBUG) {
            // for debug create dummy broadcast (to check in emulator)
            return PendingIntent.getBroadcast(this, 3, Utils.getDummyGlucodataIntent(false), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            /*
            var launchIntent: Intent? = packageManager.getLaunchIntentForPackage("tk.glucodata")
            if (launchIntent == null) {
                Log.d(LOG_ID, "Juggluco not found, use own one")
                launchIntent = Intent(this, WaerActivity::class.java)
            }
            launchIntent.action = Intent.ACTION_MAIN
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            return PendingIntent.getActivity(
                applicationContext,
                2,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )*/
            return Utils.getAppIntent(applicationContext, WaerActivity::class.java, 2, useExternalApp)
        }
    }


    fun plainText(text: CharSequence): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    fun glucoseText(): PlainComplicationText =
        plainText(ReceiveData.getClucoseAsString())

    fun deltaWithIconText(): PlainComplicationText =
        plainText("Δ: " + ReceiveData.getDeltaAsString())

    fun deltaText(): PlainComplicationText =
        plainText(ReceiveData.getDeltaAsString())

    fun trendText(): PlainComplicationText =
        plainText(ReceiveData.getRateAsString())

    fun glucoseAndDeltaText(): PlainComplicationText =
        plainText(ReceiveData.getClucoseAsString() + "  Δ: " + ReceiveData.getDeltaAsString())

    fun resText(resId: Int): PlainComplicationText =
        plainText(getText(resId))

    fun descriptionText(): PlainComplicationText =
        resText(descriptionResId)

    fun arrowIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Utils.getRateAsIcon(color = Color.WHITE)
        ).setAmbientImage(Utils.getRateAsIcon(color = Color.WHITE)).build()

    fun glucoseIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.glucose)
        ).build()

    fun deltaIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_delta)
        ).build()


    fun trendIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_rate)
        ).build()


    fun glucoseImage(): SmallImage {
        return SmallImage.Builder(
            image = Utils.getGlucoseAsIcon(ReceiveData.getClucoseColor(), true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(ambientGlucoseAsIcon(forImage = true))
            .build()
    }

    fun ambientGlucoseAsIcon(forImage: Boolean = false): Icon? {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false))
            return null
        return Utils.getGlucoseAsIcon(color = Color.WHITE, roundTarget = forImage)
    }

    fun arrowImage(): SmallImage {
        return  SmallImage.Builder(
            image = Utils.getRateAsIcon(ReceiveData.getClucoseColor(), true),
            type = SmallImageType.PHOTO
        )
            .setAmbientImage(ambientArrowIcon())
            .build()
    }

    fun ambientArrowIcon(): Icon? {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false))
            return null
        return Utils.getRateAsIcon(color = Color.WHITE, roundTarget = true)
    }

    fun getGlucoseTrendImage(): SmallImage {
        return  SmallImage.Builder(
            image = Utils.getGlucoseTrendIcon(ReceiveData.getClucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(ambientGlucoseTrendImage())
            .build()
    }

    fun ambientGlucoseTrendImage(): Icon? {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false))
            return null
        return Utils.getGlucoseTrendIcon(Color.WHITE)
    }

}
