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
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.R as CR


abstract class BgValueComplicationService : SuspendingComplicationDataSourceService() {
    protected val LOG_ID = "GDH.BgValueComplicationService"
    var descriptionResId: Int = CR.string.name
    protected lateinit var sharedPref: SharedPreferences
    protected var instanceId = 0

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

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        try {
            Log.d(LOG_ID, "getPreviewData called for " + type.toString())
            return getComplicationData(ComplicationRequest(0, type, false))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getPreviewData exception: " + exc.message.toString() )
        }
        return null
    }

    private fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        instanceId = request.complicationInstanceId
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
        val value = ReceiveData.rawValue.toFloat()
        val max = 280F
        val colors = intArrayOf(ReceiveData.getGlucoseColor())
        val colorRamp = ColorRamp(colors, false)
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
            .setColorRamp(colorRamp)
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
            photoImage = BitmapUtils.getGlucoseAsIcon(ReceiveData.getGlucoseColor(), true, 500,500),
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
        val action = sharedPref.getString(Constants.SHARED_PREF_COMPLICATION_TAP_ACTION, null)
        if(action != null) {
            if(action == Constants.ACTION_GRAPH) {
                return PackageUtils.getAppIntent(
                    applicationContext,
                    GraphActivity::class.java,
                    instanceId
                )
            }
            return PackageUtils.getTapActionIntent(this, action, instanceId)
        } else if (BuildConfig.DEBUG) {
            // for debug create dummy broadcast (to check in emulator)
            return PendingIntent.getBroadcast(this, instanceId, GlucoDataUtils.getDummyGlucodataIntent(false), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return null
    }


    fun plainText(text: CharSequence): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    fun glucoseText(): PlainComplicationText =
        plainText(ReceiveData.getGlucoseAsString())

    fun deltaWithIconText(): PlainComplicationText =
        plainText("Δ: " + ReceiveData.getDeltaAsString())

    fun deltaText(): PlainComplicationText =
        plainText(ReceiveData.getDeltaAsString())

    fun trendText(): PlainComplicationText =
        plainText(ReceiveData.getRateAsString())

    fun glucoseAndDeltaText(): PlainComplicationText =
        plainText(ReceiveData.getGlucoseAsString() + "  Δ: " + ReceiveData.getDeltaAsString())

    fun resText(resId: Int): PlainComplicationText =
        plainText(getText(resId))

    private fun appendText(text: String, append: String): String {
        var textOut = text
        if(textOut.isNotEmpty())
            textOut += ", "
        textOut += append
        return textOut
    }

    fun getDescriptionForContent(glucose: Boolean = false, delta: Boolean = false, trend: Boolean = false, time: Boolean = false, iob: Boolean = false, cob: Boolean = false): String {
        var text = ""
        if(glucose)
            text = ReceiveData.getGlucoseAsString()
        if(delta)
            text = appendText(text, this.getString(CR.string.info_label_delta) + " " + ReceiveData.getDeltaAsString())
        if(trend)
            text = appendText(text, ReceiveData.getRateAsText(this))
        if(time)
            text = appendText(text, ReceiveData.getElapsedRelativeTimeAsString(this, true))
        if(iob)
            text = appendText(text, this.getString(CR.string.info_label_iob_talkback) + " " + ReceiveData.getIobAsString())
        if(cob)
            text = appendText(text, this.getString(CR.string.info_label_cob_talkback) + " " + ReceiveData.getCobAsString())
        return text
    }

    abstract fun getDescription(): String

    open fun descriptionText(): PlainComplicationText =
        plainText(getDescription())

    fun arrowIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = BitmapUtils.getRateAsIcon(color = Color.WHITE)
        ).setAmbientImage(BitmapUtils.getRateAsIcon(color = Color.WHITE)).build()

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


    fun glucoseImage(small: Boolean = false): SmallImage {
        return SmallImage.Builder(
            image = BitmapUtils.getGlucoseAsIcon(ReceiveData.getGlucoseColor(), true, resizeFactor = if(small) 0.5F else 1.0F ),
            type = SmallImageType.PHOTO
        ).setAmbientImage(ambientGlucoseAsIcon(forImage = true, small = small))
            .build()
    }

    fun ambientGlucoseAsIcon(forImage: Boolean = false, small: Boolean = false): Icon? {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false))
            return null
        return BitmapUtils.getGlucoseAsIcon(color = Color.WHITE, roundTarget = forImage, resizeFactor = if(small) 0.5F else 1.0F)
    }

    fun arrowImage(small: Boolean = false): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getRateAsIcon(
                ReceiveData.getGlucoseColor(),
                resizeFactor = if(small) 0.6F else 1.0F
            ),
            type = SmallImageType.PHOTO
        )
            .setAmbientImage(ambientArrowIcon(small))
            .build()
    }

    fun ambientArrowIcon(small: Boolean = false): Icon? {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false))
            return null
        return BitmapUtils.getRateAsIcon(
            color = Color.WHITE,
            resizeFactor = if(small) 0.6F else 1.0F
        )
    }

    fun getGlucoseTrendImage(small: Boolean = false): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getGlucoseTrendIcon(ReceiveData.getGlucoseColor(), small = small),
            type = SmallImageType.PHOTO
        ).setAmbientImage(ambientGlucoseTrendImage())
            .build()
    }

    fun ambientGlucoseTrendImage(small: Boolean = false): Icon? {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false))
            return null
        return BitmapUtils.getGlucoseTrendIcon(Color.WHITE, small = small)
    }

}
