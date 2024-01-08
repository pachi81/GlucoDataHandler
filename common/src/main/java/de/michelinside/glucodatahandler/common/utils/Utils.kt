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
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import de.michelinside.glucodatahandler.common.GlucoDataService
import java.math.RoundingMode


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
        var string = "{"
        for (key in bundle.keySet()) {
            string += " " + key + " => " + (if (bundle[key] != null) bundle[key].toString() else "NULL") + "\r\n"
        }
        string += " }"
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

    fun isHighContrastTextEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        /*if (context != null) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            var m: Method? = null
            try {
                m = am.javaClass.getMethod("isHighTextContrastEnabled", null)
            } catch (e: NoSuchMethodException) {
                Log.i(LOG_ID, "isHighTextContrastEnabled not found in AccessibilityManager")
            }
            val result: Any
            if (m != null) {
                try {
                    result = m.invoke(am, null)!!
                    if (result is Boolean) {
                        return result
                    }
                } catch (e: Exception) {
                    Log.i(LOG_ID, "isHighTextContrastEnabled invoked with an exception" + e.message)
                }
            }
        }
        return false*/
    }

}