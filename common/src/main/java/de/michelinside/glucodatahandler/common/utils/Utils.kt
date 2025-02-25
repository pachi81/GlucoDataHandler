package de.michelinside.glucodatahandler.common.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Parcel
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.max


object Utils {
    private val LOG_ID = "GDH.Utils"
    fun round(value: Float, scale: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): Float {
        if (value.isNaN())
            return value
        return value.toBigDecimal().setScale( scale, roundingMode).toFloat()
    }

    // check for valid values
    fun getCobValue(value: Float): Float {
        if (value < 0)
            return Float.NaN
        return value
    }

    fun rangeValue(value: Float, min: Float, max: Float): Float {
        if (value > max)
            return max
        if (value < min)
            return min
        return value
    }

    fun getVersion(versionString: String?): String {
        if(!versionString.isNullOrEmpty()) {
            val regex = "[0-9]+(\\.[0-9]+)*".toRegex()
            // If there is matching string, then find method returns non-null MatchResult
            val match = regex.find(versionString)
            if (match != null) {
                return match.value
            }
        }
        return ""
    }

    // return -1 if current < new, 1 if current > new
    fun compareVersion(current: String?, new: String?): Int {
        val curVersion = getVersion(current)
        val newVersion = getVersion(new)
        if (curVersion.isEmpty() && newVersion.isEmpty())
            return 0
        if (newVersion.isEmpty()) return 1
        if (curVersion.isEmpty()) return -1
        val curParts = curVersion.split(".")
        val newParts = newVersion.split(".")
        val length = max(curParts.size, newParts.size)
        for (i in 0 until length) {
            val curPart = if (i < curParts.size) curParts[i].toInt() else 0
            val newPart = if (i < newParts.size) newParts[i].toInt() else 0
            if (curPart < newPart) return -1
            if (curPart > newPart) return 1
        }
        return 0
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

    @Suppress("DEPRECATION")
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
                    Log.i(LOG_ID, "isHighTextContrastEnabled invoked with an exception" + entry.message)
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


    fun encryptSHA256(value: String): String {
        if (value.trim().isEmpty())
            return ""
        try {
            val message: ByteArray = value.trim().toByteArray(Charsets.UTF_8)
            val md = MessageDigest.getInstance("SHA-256")
            val digest: ByteArray = md.digest(message)
            return toHexString(digest)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception while encrypt SHA-256: " + ex)
        }
        return ""
    }

    private fun getDeviceInformations(): String {
        var s = ""
        try {
            if(GlucoDataService.context!=null) {
                val pInfo: PackageInfo = GlucoDataService.context!!.packageManager.getPackageInfo(
                    GlucoDataService.context!!.packageName, PackageManager.GET_META_DATA
                )
                s += "APP Package Name: ${GlucoDataService.context!!.packageName}\n"
                s += "APP Version Name: ${pInfo.versionName}\n"
                s += "APP Version Code: ${pInfo.versionCode}\n"
            }
        } catch (e: NameNotFoundException) {
        }
        s += "OS Version: ${System.getProperty("os.version")} (${Build.VERSION.INCREMENTAL})\n"
        s += "OS API Level: ${Build.VERSION.SDK}\n"
        s += "Device: ${Build.DEVICE}\n"
        s += "Model (and Product): ${Build.MODEL} (${Build.PRODUCT})\n"
        s += "Manufacturer: ${Build.MANUFACTURER}\n"
        s += "Other TAGS: ${Build.TAGS}\n"
        try {
            if(GlucoDataService.context != null) {
                val pi: PackageInfo =
                    GlucoDataService.context!!.packageManager.getPackageInfo(GlucoDataService.context!!.packageName, PackageManager.GET_PERMISSIONS)
                if(!pi.requestedPermissions.isEmpty()) {
                    s += "---------------------PERMISSIONS:------------------------------\n"
                    for (i in pi.requestedPermissions.indices) {
                        s += "${pi.requestedPermissions[i]}:"
                        if ((pi.requestedPermissionsFlags[i] and 1) != 0)
                            s+= " REQUIRED"
                        if ((pi.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0)
                            s+= " GRANTED"
                        s+= " (${pi.requestedPermissionsFlags[i]})\n"
                    }
                }
            }
        } catch (e: java.lang.Exception) {
        }
        s += "---------------------------------------------------------------\n\n"
        return s
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
            outputStream.write(getDeviceInformations().toByteArray())
            outputStream.flush()
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

    fun saveSettings(context: Context, uri: Uri) {
        try {
            Thread {
                context.contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { os ->
                        saveSettings(os, context)
                    }
                }
            }.start()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving logs to file exception: " + exc.message.toString() )
        }
    }

    private fun saveSettings(outputStream: OutputStream, context: Context) {
        var success = false
        try {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(outputStream)
            oos.writeObject(sharedPref.getAll())
            oos.close()
            success = true
            Log.i(LOG_ID, "Settings saved")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving settings exception: " + exc.message.toString() )
        }
        val text = if (success) {
            GlucoDataService.context!!.resources.getText(R.string.settings_save_succeeded)
        } else {
            GlucoDataService.context!!.resources.getText(R.string.settings_save_failed)
        }
        Handler(GlucoDataService.context!!.mainLooper).post {
            Toast.makeText(GlucoDataService.context!!, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun readSettings(context: Context, uri: Uri) {
        try {
            Thread {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    FileInputStream(it.fileDescriptor).use { iss ->
                        readSettings(iss, context)
                    }
                }
            }.start()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving logs to file exception: " + exc.message.toString() )
        }
    }

    private fun readSettings(inputStream: InputStream, context: Context) {
        var success = false
        try {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            val ois = ObjectInputStream(inputStream)
            val map = ois.readObject() as HashMap<*, *>
            with(sharedPref.edit()) {
                map.forEach { entry ->
                    putString(entry.key.toString(), entry.value.toString())
                    when (entry.value) {
                        is Boolean -> putBoolean(entry.key.toString(), entry.value as Boolean)
                        is String -> putString(entry.key.toString(), entry.value as String)
                        is Int -> putInt(entry.key.toString(), entry.value as Int)
                        is Float -> putFloat(entry.key.toString(), entry.value as Float)
                        is Long -> putLong(entry.key.toString(), entry.value as Long)
                        is Set<*> -> putStringSet(entry.key.toString(), entry.value as Set<String>)
                        else -> throw IllegalArgumentException(
                            ("Type " + (entry.value?.javaClass?.name ?: "unknown") + " is unknown")
                        )
                    }
                }
                apply()
            }
            success = true
            Log.i(LOG_ID, "Settings red")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Reading settings exception: " + exc.message.toString() )
        }
        val text = if (success) {
            GlucoDataService.context!!.resources.getText(R.string.settings_read_succeeded)
        } else {
            GlucoDataService.context!!.resources.getText(R.string.settings_read_failed)
        }
        Handler(GlucoDataService.context!!.mainLooper).post {
            Toast.makeText(GlucoDataService.context!!, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun timeInDayFilter(currentDateTime: LocalDateTime, startTime: LocalTime, weekDayFilter: MutableSet<String>?): Boolean {
        try {
            if(weekDayFilter == null || weekDayFilter.size == 7)
                return true
            if (weekDayFilter.isEmpty())
                return false
            val currentTime = currentDateTime.toLocalTime()
            val timeDiff = if(currentTime.isBefore(startTime)) startTime.until(currentTime, ChronoUnit.MINUTES) + 1440 else startTime.until(currentTime, ChronoUnit.MINUTES)
            val startDateTime = currentDateTime.minusMinutes(timeDiff)
            if(weekDayFilter.contains(startDateTime.dayOfWeek.value.toString()))
                return true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "timeInDayFilter exception: " + exc.message.toString() )
        }
        return false
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isValidTime(time: String?): Boolean {
        if (time.isNullOrEmpty())
            return false
        try {
            LocalTime.parse(time, timeFormatter)
            return true
        } catch(_: Exception) {
        }
        return false
    }

    @SuppressLint("SimpleDateFormat")
    fun timeBetweenTimes(currentTime: LocalDateTime, startTime: String, endTime: String, weekDayFilter: MutableSet<String>? = null): Boolean {
        try {
            if (!isValidTime(startTime) || !isValidTime(endTime))
                return false
            val timeCur = currentTime.toLocalTime()
            val timeStart = LocalTime.parse(startTime, timeFormatter)
            val timeEnd = LocalTime.parse(endTime, timeFormatter)

            if (timeCur == null || timeStart == null || timeEnd == null)
                return false

            if (timeCur == timeStart || timeCur == timeEnd)
                return timeInDayFilter(currentTime, timeStart, weekDayFilter)

            if (timeStart.isAfter(timeEnd)) {  // night shift
                if (timeCur.isAfter(timeStart) || timeCur.isBefore(timeEnd))
                    return timeInDayFilter(currentTime, timeStart, weekDayFilter)
            } else {
                if (timeCur.isAfter(timeStart) && timeCur.isBefore(timeEnd))
                    return timeInDayFilter(currentTime, timeStart, weekDayFilter)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "timeBetweenTimes exception: " + exc.message.toString() )
        }
        return false
    }

    fun getElapsedTimeMinute(time: Long, roundingMode: RoundingMode = RoundingMode.DOWN): Long {
        return round((System.currentTimeMillis()-time).toFloat()/60000, 0, roundingMode).toLong()
    }

    fun getTimeDiffMinute(time1: Long, time2: Long, roundingMode: RoundingMode = RoundingMode.DOWN): Long {
        return round((time1-time2).toFloat()/60000, 0, roundingMode).toLong()
    }

    fun getUiTimeStamp(time: Long): String {
        if(getElapsedTimeMinute(time) >= (60*24))
            return DateFormat.getDateTimeInstance().format(Date(time))
        return getTimeStamp(time)
    }

    fun getTimeStamp(time: Long): String {
        return DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(time))
    }

    fun Context.isScreenReaderOn():Boolean{
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (am.isEnabled) {
            val serviceInfoList =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            if (serviceInfoList.isNotEmpty())
                return true
        }
        return false
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

}