package de.michelinside.glucodatahandler.common.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import android.util.TypedValue
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import java.math.RoundingMode
import kotlin.random.Random


object Utils {
    private val LOG_ID = "GDH.Utils"
    fun round(value: Float, scale: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): Float {
        return value.toBigDecimal().setScale( scale, roundingMode).toFloat()
    }

    fun rangeValue(value: Float, min: Float, max: Float): Float {
        if (value > max)
            return max
        if (value < min)
            return min
        return value
    }

    fun isMmolValue(value: Float): Boolean = value < Constants.GLUCOSE_MIN_VALUE.toFloat()

    fun mgToMmol(value: Float, scale: Int = 1): Float {
        return round(value / Constants.GLUCOSE_CONVERSION_FACTOR, scale)
    }

    fun mmolToMg(value: Float): Float {
        return round(value * Constants.GLUCOSE_CONVERSION_FACTOR, 0)
    }

    fun dpToPx(dp: Float, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun spToPx(sp: Float, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun bytesToBundle(bytes: ByteArray): Bundle? {
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val bundle = parcel.readBundle(GlucoDataService::class.java.getClassLoader())
        parcel.recycle()
        return bundle
    }

    fun bundleToBytes(bundle: Bundle?): ByteArray? {
        if (bundle==null)
            return null
        val parcel = Parcel.obtain()
        parcel.writeBundle(bundle)
        val bytes = parcel.marshall()
        parcel.recycle()
        return bytes
    }

    fun dumpBundle(bundle: Bundle?): String {
        if (bundle == null) {
            return "NULL"
        }
        var string = "Bundle{"
        for (key in bundle.keySet()) {
            string += " " + key + " => " + (if (bundle[key] != null) bundle[key].toString() else "NULL") + ";"
        }
        string += " }Bundle"
        return string
    }

    @SuppressLint("ObsoleteSdkInt")
    fun checkPermission(context: Context, permission: String, minSdk: Int = Build.VERSION_CODES.O): Boolean {
        if (Build.VERSION.SDK_INT >= minSdk) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(LOG_ID, "Permission " + permission + " not granted!")
                return false
            }
        }
        Log.d(LOG_ID, "Permission " + permission + " granted!")
        return true
    }

    fun getAppIntent(context: Context, activityClass: Class<*>, requestCode: Int, useExternalApp: Boolean = false): PendingIntent {
        var launchIntent: Intent? = null
        if (useExternalApp) {
            launchIntent = context.packageManager.getLaunchIntentForPackage("tk.glucodata")
        }
        if (launchIntent == null) {
            launchIntent = Intent(context, activityClass)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun isPackageAvailable(context: Context, packageName: String): Boolean {
        return context.packageManager.getInstalledApplications(0).find { info -> info.packageName.startsWith(packageName) } != null
    }

    fun getBackgroundColor(transparancyFactor: Int) : Int {
        if( transparancyFactor <= 0 )
            return 0
        val transparency = 255 * minOf(10, transparancyFactor) / 10
        return transparency shl (4*6)
    }

    private var rateDelta = 0.1F
    private var rawDelta = 5
    fun getDummyGlucodataIntent(random: Boolean = true) : Intent {
        val useMmol = ReceiveData.isMmol
        val first = ReceiveData.time == 0L
        ReceiveData.time = System.currentTimeMillis()-60000
        val time =  System.currentTimeMillis()
        val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
        var raw: Int
        var glucose: Float
        var rate: Float
        if (random) {
            raw = Random.nextInt(40, 400)
            glucose = if(useMmol) mgToMmol(raw.toFloat()) else raw.toFloat()
            rate = Random.nextFloat() + Random.nextInt(-4, 4).toFloat()
        } else {
            if ((ReceiveData.rawValue >= 200 && rawDelta > 0) || (ReceiveData.rawValue <= 40 && rawDelta < 0)) {
                rawDelta *= -1
            }
            raw =
                if (first || ReceiveData.rawValue == 400) Constants.GLUCOSE_MIN_VALUE else ReceiveData.rawValue + rawDelta
            glucose = if (useMmol) mgToMmol(raw.toFloat()) else raw.toFloat()
            if (useMmol && glucose == ReceiveData.glucose) {
                raw += 1
                glucose = mgToMmol(raw.toFloat())
            }
            if (ReceiveData.rate >= 3.5F) {
                rateDelta = -0.1F
                rate = 2F
            } else if (ReceiveData.rate <= -3.5F) {
                rateDelta = 0.1F
                rate = -2F
            } else {
                rate = if (first) -3.5F else ReceiveData.rate + rateDelta
            }
            if (rate > 2F && rateDelta > 0)
                rate = 3.5F
            else if (rate < -2F && rateDelta < 0)
                rate = -3.5F

        }
        intent.putExtra(ReceiveData.SERIAL, "WUSEL_DUSEL")
        intent.putExtra(ReceiveData.MGDL, raw)
        intent.putExtra(ReceiveData.GLUCOSECUSTOM, glucose)
        intent.putExtra(ReceiveData.RATE, round(rate, 2))
        intent.putExtra(ReceiveData.TIME, time)
        intent.putExtra(ReceiveData.ALARM, if (raw <= 70) 7 else if (raw >= 250) 6 else 0)
        return intent
    }
}