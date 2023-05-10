package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.*
import java.util.*


abstract class BgValueComplicationService : SuspendingComplicationDataSourceService() {
    protected val LOG_ID = "GlucoDataHandler.BgValueComplicationService"
    var descriptionResId: Int = R.string.app_name

    override fun onCreate() {
        try {
            super.onCreate()
            Log.d(LOG_ID, "onCreate called")
            descriptionResId = this.applicationInfo.labelRes

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
            photoImage = getGlucoseAsIcon(ReceiveData.getClucoseColor(), true, 500,500),
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

    open fun getTapAction(): PendingIntent? {
        if (BuildConfig.DEBUG) {
            // for debug create dummy broadcast (to check in emulator)
            return PendingIntent.getBroadcast(this, 3, Utils.getDummyGlucodataIntent(false), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
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
            )
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

    fun timeText(): PlainComplicationText =
        plainText(ReceiveData.timeformat.format(Date(ReceiveData.time)))

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
            image = getRateAsIcon()
        ).setAmbientImage(getRateAsIcon()).build()

    fun glucoseIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.glucose)
        ).build()

    fun ambientArrowIcon(): Icon {
        return getRateAsIcon(forImage = true)
    }

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
            image = getGlucoseAsIcon(ReceiveData.getClucoseColor(), true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getGlucoseAsIcon(forImage = true))
            .build()
    }

    fun arrowImage(): SmallImage {
        return  SmallImage.Builder(
            image = getRateAsIcon(ReceiveData.getClucoseColor(), true),
            type = SmallImageType.PHOTO
        )
            .setAmbientImage(ambientArrowIcon())
            .build()
    }

    fun getGlucoseAsIcon(color: Int = Color.WHITE, forImage: Boolean = false, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(Utils.textToBitmap(ReceiveData.getClucoseAsString(), color, forImage, ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete(),width, height))
    }

    fun getRateAsIcon(color: Int = Color.WHITE, forImage: Boolean = false): Icon {
        if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC))
            return Icon.createWithBitmap(Utils.textToBitmap("?", Color.GRAY, forImage))
        return Icon.createWithBitmap(Utils.rateToBitmap(ReceiveData.rate, color))
    }

    fun getGlucoseTrendIcon(color: Int, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(Utils.textRateToBitmap(ReceiveData.getClucoseAsString(), ReceiveData.rate, color, ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC), ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete(),width, height))
    }

    fun getGlucoseTrendImage(): SmallImage {
        return  SmallImage.Builder(
            image = getGlucoseTrendIcon(ReceiveData.getClucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getGlucoseTrendIcon(Color.WHITE))
            .build()
    }

}
