package de.michelinside.glucodatahandler.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Parcel
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.NumberFormatException
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.concurrent.TimeUnit


object Utils {
    private val LOG_ID = "GDH.Utils"
    fun round(value: Float, scale: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): Float {
        if (value.isNaN())
            return value
        return value.toBigDecimal().setScale( scale, roundingMode).toFloat()
    }

    fun rangeValue(value: Float, min: Float, max: Float): Float {
        if (value > max)
            return max
        if (value < min)
            return min
        return value
    }

    fun parseFloatString(floatValue: String?): Float {
        if(!floatValue.isNullOrEmpty()) {
            try {
                return floatValue.replace(',', '.').toFloat()
            } catch (exc: NumberFormatException) {
                Log.e(LOG_ID, "Error parsing float value $floatValue exception: ${exc.message}")
            }
        }
        return Float.NaN
    }

    fun dpToPx(dp: Float, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun spToPx(sp: Float, context: Context): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
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
        try {
            if (bundle == null) {
                return "NULL"
            }
            var string = "{"
            for (key in bundle.keySet()) {
                string += " " + key + " => " + (if (bundle[key] != null) bundle[key].toString() else "NULL") + "\r\n"
            }
            string += " }"
            return string
        } catch (exc: Exception) {
            Log.e(LOG_ID, "dumpBundle exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
        return bundle.toString()
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

    fun byteToHex(num: Byte): String {
        val hexDigits = CharArray(2)
        hexDigits[0] = Character.forDigit(num.toInt() shr 4 and 0xF, 16)
        hexDigits[1] = Character.forDigit(num.toInt() and 0xF, 16)
        return String(hexDigits)
    }
    fun toHexString(bytes: ByteArray): String {
        val hexStringBuffer = StringBuffer()
        for (element in bytes) {
            hexStringBuffer.append(byteToHex(element))
        }
        return hexStringBuffer.toString()
    }

    fun encryptSHA1(value: String): String {
        if (value.trim().isEmpty())
            return ""
        try {
            val message: ByteArray = value.trim().toByteArray(Charsets.UTF_8)
            val md = MessageDigest.getInstance("SHA-1")
            val digest: ByteArray = md.digest(message)
            return toHexString(digest)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception while encrypt SHA-1: " + ex)
        }
        return ""
    }

    fun saveLogs(context: Context, uri: Uri) {
        try {
            Thread {
                context.contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { os ->
                        saveLogs(os)
                    }
                }
            }.start()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving logs to file exception: " + exc.message.toString() )
        }
    }

    fun saveLogs(outputStream: OutputStream) {
        try {
            val cmd = "logcat -t 4000"
            Log.i(LOG_ID, "Getting logcat with command: $cmd")
            val process = Runtime.getRuntime().exec(cmd)
            val thread = Thread {
                try {
                    Log.v(LOG_ID, "read")
                    val buffer = ByteArray(4 * 1024) // or other buffer size
                    var read: Int
                    while (process.inputStream.read(buffer).also { rb -> read = rb } != -1) {
                        Log.v(LOG_ID, "write")
                        outputStream.write(buffer, 0, read)
                    }
                    Log.v(LOG_ID, "flush")
                    outputStream.flush()
                    outputStream.close()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Writing logs exception: " + exc.message.toString() )
                }
            }
            thread.start()
            Log.v(LOG_ID, "Waiting for saving logs")
            process.waitFor(10, TimeUnit.SECONDS)
            Log.v(LOG_ID, "Process alive: ${process.isAlive}")
            var count = 0
            while (process.isAlive && count < 10) {
                Log.w(LOG_ID, "Killing process")
                process.destroy()
                Thread.sleep(1000)
                count++
            }
            Log.v(LOG_ID, "Process exit: ${process.exitValue()}")
            val text = if (process.exitValue() == 0) {
                GlucoDataService.context!!.resources.getText(R.string.logcat_save_succeeded)
            } else {
                GlucoDataService.context!!.resources.getText(R.string.logcat_save_failed)
            }
            Handler(GlucoDataService.context!!.mainLooper).post {
                Toast.makeText(GlucoDataService.context!!, text, Toast.LENGTH_SHORT).show()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving logs exception: " + exc.message.toString() )
        }
    }

}