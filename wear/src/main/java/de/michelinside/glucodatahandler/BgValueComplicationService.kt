package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.*
import kotlin.random.Random


abstract class BgValueComplicationService : SuspendingComplicationDataSourceService() {
    protected val LOG_ID = "GlucoDataHandler.BgValueComplicationService"
    var descriptionResId: Int = R.string.app_name

    override fun onCreate() {
        try {
            super.onCreate()
            Log.d(LOG_ID, "onCreate called")
            descriptionResId = this.applicationInfo.labelRes

            val serviceIntent = Intent(this, GlucoDataServiceWear::class.java)
            val sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, false))
            this.startService(serviceIntent)
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
            super.onComplicationDeactivated(complicationInstanceId)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationDeactivated exception: " + exc.message.toString() )
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        try {
            Log.d(LOG_ID, "onComplicationRequest called for " + request.complicationType.toString())
            return getComplicationData(request)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationRequest exception: " + exc.message.toString() )
        }
        return null
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(ComplicationRequest(0, type))!!
    }

    private fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        return when(request.complicationType) {
            ComplicationType.SHORT_TEXT -> getShortTextComplicationData()
            ComplicationType.RANGED_VALUE -> getRangeValueComplicationData()
            ComplicationType.LONG_TEXT -> getLongTextComplicationData()
            ComplicationType.SMALL_IMAGE -> getSmallImageComplicationData()
            ComplicationType.MONOCHROMATIC_IMAGE -> getIconComplicationData()
            else -> {
                Log.e(LOG_ID, "Unsupported type: " + request.complicationType)
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
        val max = if(ReceiveData.isMmol()) 16F else 280F
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

    private fun getLongTextComplicationData(): ComplicationData {
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

    fun getTapAction(): PendingIntent? {
        if (BuildConfig.DEBUG) {
            // for debug create dummy broadcast (to check in emulator)
            val useMmol = Random.nextBoolean()
            val time = if(ReceiveData.time==0L) System.currentTimeMillis() else ReceiveData.time+60000L
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            val raw = Random.nextInt(40, 400)
            val glucose = if(useMmol) Utils.mgToMmol(raw.toFloat()) else raw.toFloat()
            /*var raw = if(ReceiveData.time==0L || ReceiveData.rawValue == 400) 40 else ReceiveData.rawValue + 1
            var glucose = if(useMmol) Utils.mgToMmol(raw.toFloat()) else raw.toFloat()
            if(useMmol && glucose == ReceiveData.glucose) {
                raw += 1
                glucose = Utils.mgToMmol(raw.toFloat())
            }*/
            val rate = Utils.round(Random.nextFloat() + Random.nextInt(-4, 4).toFloat(), 2)
            intent.putExtra(ReceiveData.SERIAL, "WUSEL_DUSEL")
            intent.putExtra(ReceiveData.MGDL, raw)
            intent.putExtra(ReceiveData.GLUCOSECUSTOM, glucose)
            intent.putExtra(ReceiveData.RATE, rate)
            intent.putExtra(ReceiveData.TIME, time)
            intent.putExtra(ReceiveData.ALARM, 0)
            return PendingIntent.getBroadcast(this, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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

    fun trendText(): PlainComplicationText =
        plainText((if (ReceiveData.rate > 0) "+" else "") + ReceiveData.rate.toString())

    fun glucoseAndDeltaText(): PlainComplicationText =
        plainText(ReceiveData.getClucoseAsString() + "  Δ: " + ReceiveData.getDeltaAsString())

    fun resText(resId: Int): PlainComplicationText =
        plainText(getText(resId))

    fun descriptionText(): PlainComplicationText =
        resText(descriptionResId)

    fun arrowIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = ambientArrowIcon()
        ).setAmbientImage(ambientArrowIcon()).build()

    fun glucoseIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.glucose)
        ).build()

    fun ambientArrowIcon(): Icon {
        val icon = Icon.createWithResource(this, ReceiveData.getArrowIconRes())
        icon.setTint(Color.WHITE)
        icon.setTintMode(PorterDuff.Mode.SRC_IN)
        return icon
    }

    fun deltaIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_delta)
        ).build()


    fun trendIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_rate)
        ).build()

    fun arrowImage(): SmallImage {
        return  SmallImage.Builder(
            image = ReceiveData.getArrowIcon(this),
            type = SmallImageType.PHOTO
        )
            .setAmbientImage(ambientArrowIcon())
            .build()
    }

    fun getGlucoseAsIcon(color: Int = Color.WHITE, forImage: Boolean = false): Icon {
        return Icon.createWithBitmap(Utils.textToBitmap(ReceiveData.getClucoseAsString(), color, forImage))
    }
}

object ActiveComplicationHandler: ReceiveDataInterface {
    private const val LOG_ID = "GlucoDataHandler.ActiveComplicationHandler"
    init {
        Log.d(LOG_ID, "init called")
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun getPackages(context: Context): PackageInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong()))
        } else {
            @Suppress("DEPRECATION") return context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
        }
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        val packageInfo = getPackages(context)
        Log.d(LOG_ID, "Got " + packageInfo.services.size + " services.")
        packageInfo.services.forEach {
            val isComplication =  BgValueComplicationService::class.java.isAssignableFrom(Class.forName(it.name))
            if(isComplication) {
                ComplicationDataSourceUpdateRequester
                    .create(
                        context = context,
                        complicationDataSourceComponent = ComponentName(context, it.name)
                    )
                    .requestUpdateAll()
            }
        }
    }
}
