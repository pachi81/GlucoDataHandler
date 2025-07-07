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
import android.text.format.DateUtils
import android.util.Log
import android.util.TypedValue
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import java.util.Calendar
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

    fun bytesToBundle(bytes: ByteArray?, isJson: Boolean = true): Bundle? {
        try {
            if(bytes==null)
                return null
            if(isJson)
                return jsonBytesToBundle(bytes)
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val bundle = parcel.readBundle(GlucoDataService::class.java.getClassLoader())
            parcel.recycle()
            dumpBundle(bundle)  // workaround for problems with API 36
            return bundle
        } catch (exc: Exception) {
            Log.e(LOG_ID, "bytesToBundle exception: " + exc.toString())
            return null
        }
    }

    fun bundleToBytes(bundle: Bundle?, useJson: Boolean = true): ByteArray? {
        if (bundle==null)
            return null
        try {
            if(useJson)
                return bundleToJsonBytes(bundle)
            val parcel = Parcel.obtain()
            parcel.writeBundle(bundle)
            val bytes = parcel.marshall()
            parcel.recycle()
            return bytes
        } catch (exc: Exception) {
            Log.e(LOG_ID, "bundleToBytes exception: " + exc.toString())
            return null
        }
    }
    data class TypedJsonValue(val type: String, val value: Any?)

    private fun bundleToTypedMap(bundle: Bundle?): Map<String, TypedJsonValue>? {
        if (bundle == null) return null
        val typedMap = mutableMapOf<String, TypedJsonValue>()
        for (key in bundle.keySet()) {
            val actualValue = bundle.get(key)
            if (actualValue == null) {
                // For null, we don't have a class.simpleName, so define it.
                typedMap[key] = TypedJsonValue("Null", null)
                continue
            }

            // Get the simple class name as the type identifier
            val typeName = actualValue::class.simpleName ?: actualValue.javaClass.simpleName

            when (actualValue) {
                is Bundle -> {
                    // For nested bundles, we recurse. The typeName will be "Bundle".
                    typedMap[key] = TypedJsonValue(typeName, bundleToTypedMap(actualValue))
                }
                is Array<*> -> {
                    // For arrays, typeName will be like "StringArray", "IntArray", or "Array" for Array<Any>
                    // Gson serializes arrays to JSON arrays (lists).
                    typedMap[key] = TypedJsonValue(typeName, actualValue.toList())
                }
                is List<*> -> {
                    // For lists, typeName will be like "ArrayList", "LinkedList"
                    typedMap[key] = TypedJsonValue(typeName, actualValue)
                }
                is Double -> {
                    when {
                        actualValue.isNaN() -> typedMap[key] = TypedJsonValue(typeName, "NaN")
                        actualValue.isInfinite() && actualValue > 0 -> typedMap[key] = TypedJsonValue(typeName, "Infinity")
                        actualValue.isInfinite() && actualValue < 0 -> typedMap[key] = TypedJsonValue(typeName, "-Infinity")
                        else -> typedMap[key] = TypedJsonValue(typeName, actualValue)
                    }
                }
                is Float -> {
                    when {
                        actualValue.isNaN() -> typedMap[key] = TypedJsonValue(typeName, "NaN")
                        actualValue.isInfinite() && actualValue > 0 -> typedMap[key] = TypedJsonValue(typeName, "Infinity")
                        actualValue.isInfinite() && actualValue < 0 -> typedMap[key] = TypedJsonValue(typeName, "-Infinity")
                        else -> typedMap[key] = TypedJsonValue(typeName, actualValue)
                    }
                }
                // For other known primitive wrappers and strings, typeName will be "String", "Integer", "Boolean", etc.
                // Gson can handle these directly.
                is String, is Int, is Long, is Boolean -> {
                    typedMap[key] = TypedJsonValue(typeName, actualValue)
                }
                // Default case for other types
                else -> {
                    // This is for types not explicitly handled above but where typeName is still valid.
                    // Gson will attempt to serialize 'actualValue'. This works for simple POJOs.
                    // For complex objects not directly serializable by Gson, you'd need custom TypeAdapters.
                    Log.w(LOG_ID, "Using generic typeName '$typeName' for key '$key' for value of class ${actualValue.javaClass.name}")
                    typedMap[key] = TypedJsonValue(typeName, actualValue)
                }
            }
        }
        return typedMap
    }

    private fun bundleToJsonBytes(bundle: Bundle?): ByteArray? {
        if (bundle == null) return null
        return try {
            val map = bundleToTypedMap(bundle)
            if (map == null) return null
            val gson = Gson()
            val jsonString = gson.toJson(map)
            Log.v(LOG_ID, "Created jsonString: $jsonString")
            jsonString.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error converting Bundle to JSON bytes: ${e.message}", e)
            null
        }
    }

    // that matches the simpleNames: "String", "Integer", "Bundle", "ArrayList", "StringArray", "Null", etc.
    private fun typedMapToBundle(typedMap: Map<String, Any?>?): Bundle? {
        if (typedMap == null) return null
        val bundle = Bundle()
        for ((key, rawValue) in typedMap) {
            try {
                if (rawValue !is Map<*, *>) {
                    Log.w(LOG_ID, "Expected a map for TypedJsonValue, but got ${rawValue?.javaClass?.name} for key '$key'")
                    continue
                }
                val valueMap = rawValue as Map<String, Any?>
                val type = valueMap["type"] as? String
                val value = valueMap["value"]

                when (type) {
                    "String" -> bundle.putString(key, value as? String)
                    "Integer" -> if (value is Number) bundle.putInt(key, value.toInt())
                    "Int" -> if (value is Number) bundle.putInt(key, value.toInt())
                    "Long" -> if (value is Number) bundle.putLong(key, value.toLong())
                    "Boolean" -> if (value is Boolean) bundle.putBoolean(key, value)
                    "Bundle" -> {
                        @Suppress("UNCHECKED_CAST")
                        bundle.putBundle(key, typedMapToBundle(value as? Map<String, Any?>))
                    }
                    "Null" -> bundle.putString(key, null) // Or handle as you see fit
                    "Float" -> {
                        when (value) {
                            is String -> { // Check if the JSON value is a string (e.g., "NaN", "Infinity")
                                when (value) {
                                    "NaN" -> bundle.putFloat(key, Float.NaN)
                                    "Infinity" -> bundle.putFloat(key, Float.POSITIVE_INFINITY)
                                    "-Infinity" -> bundle.putFloat(key, Float.NEGATIVE_INFINITY)
                                    else -> { // Attempt to parse if it's a string representation of a normal number
                                        try {
                                            bundle.putFloat(key, value.toFloat())
                                        } catch (e: NumberFormatException) {
                                            Log.w(LOG_ID, "Could not parse string '$value' to Float for key '$key'. Defaulting to NaN.")
                                            bundle.putFloat(key, Float.NaN)
                                        }
                                    }
                                }
                            }
                            is Number -> { // If it's already a number (Gson might parse valid numbers directly)
                                bundle.putFloat(key, value.toFloat())
                            }
                            else -> {
                                Log.w(LOG_ID, "Unexpected value type for Float key '$key': ${value?.javaClass?.name}. Defaulting to NaN.")
                                bundle.putFloat(key, Float.NaN)
                            }
                        }
                    }
                    "Double" -> {
                        when (value) {
                            is String -> { // Check if the JSON value is a string
                                when (value) {
                                    "NaN" -> bundle.putDouble(key, Double.NaN)
                                    "Infinity" -> bundle.putDouble(key, Double.POSITIVE_INFINITY)
                                    "-Infinity" -> bundle.putDouble(key, Double.NEGATIVE_INFINITY)
                                    else -> { // Attempt to parse if it's a string representation of a normal number
                                        try {
                                            bundle.putDouble(key, value.toDouble())
                                        } catch (e: NumberFormatException) {
                                            Log.w(LOG_ID, "Could not parse string '$value' to Double for key '$key'. Defaulting to NaN.")
                                            bundle.putDouble(key, Double.NaN)
                                        }
                                    }
                                }
                            }
                            is Number -> { // If it's already a number
                                bundle.putDouble(key, value.toDouble())
                            }
                            else -> {
                                Log.w(LOG_ID, "Unexpected value type for Double key '$key': ${value?.javaClass?.name}. Defaulting to NaN.")
                                bundle.putDouble(key, Double.NaN)
                            }
                        }
                    }
                    // More specific array/list handling would be needed here if you want to restore them
                    // to their exact original array types (e.g., IntArray vs ArrayList<Int>).
                    // Gson typically deserializes JSON arrays to ArrayList.
                    "ArrayList" -> { // Example: if Gson deserialized a JSON array to an ArrayList
                        if (value is List<*>) {
                            // This is tricky. What kind of ArrayList? Bundle needs typed ArrayLists.
                            // e.g., bundle.putStringArrayList(), bundle.putIntegerArrayList()
                            // You'd need more info or make assumptions.
                            // For simplicity, this example doesn't fully rehydrate all list types.
                            Log.d(LOG_ID, "Deserialized ArrayList for key $key. Type of elements: ${value.firstOrNull()?.javaClass?.simpleName}")
                            // Attempt to convert to known parcelable array list types
                            when {
                                value.all { it is String } -> bundle.putStringArrayList(key, value as ArrayList<String>)
                                value.all { it is Int } -> bundle.putIntegerArrayList(key, value as ArrayList<Int>)
                                // Add other types like Parcelable if you have a way to cast them
                                else -> Log.w(LOG_ID, "Cannot directly put generic ArrayList into Bundle for key '$key'")
                            }
                        }
                    }
                    // Add cases for "StringArray", "IntArray" etc. if you serialized them that way
                    // and need to convert List<Number> back to, e.g., IntArray.

                    else -> Log.w(LOG_ID, "Unknown type '$type' in typedMapToBundle for key '$key'")
                }
            } catch (e: ClassCastException) {
                Log.e(LOG_ID, "Type casting error for key '$key' during typedMapToBundle: ${e.message}", e)
            }
        }
        return bundle
    }

    private fun jsonBytesToBundle(bytes: ByteArray?): Bundle? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val jsonString = String(bytes, Charsets.UTF_8)
            Log.v(LOG_ID, "Received jsonString: $jsonString")
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val gson = Gson()
            val map: Map<String, Any?> = gson.fromJson(jsonString, mapType)
            typedMapToBundle(map)
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error converting JSON bytes to Bundle: ${e.message}", e)
            null
        }
    }


    @Suppress("DEPRECATION")
    fun dumpBundle(bundle: Bundle?): String {
        try {
            if (bundle == null) {
                return "NULL"
            }
            var string = "{"
            for (key in bundle.keySet()) {
                string += " " + key + " => " + (if (bundle[key] != null) (bundle[key]!!.javaClass.simpleName + ": " + bundle[key].toString()) else "NULL") + "\r\n"
            }
            string += " }"
            return string.take(2000)
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
                if(pi.requestedPermissions != null && pi.requestedPermissions?.isNotEmpty() == true) {
                    s += "---------------------PERMISSIONS:------------------------------\n"
                    for (i in pi.requestedPermissions!!.indices) {
                        s += "${pi.requestedPermissions!![i]}:"
                        if ((pi.requestedPermissionsFlags!![i] and 1) != 0)
                            s+= " REQUIRED"
                        if ((pi.requestedPermissionsFlags!![i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0)
                            s+= " GRANTED"
                        s+= " (${pi.requestedPermissionsFlags!![i]})\n"
                    }
                }
                if(GlucoDataService.sharedPref != null && GlucoDataService.sharedPref!!.contains(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_MESSAGE)) {
                    s += "---------------------------------------------------------------\n\n"
                    s += "---------------------UNCAUGHT EXCEPTION:-----------------------\n"
                    val excMsg = GlucoDataService.sharedPref!!.getString(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_MESSAGE, "") ?: ""
                    val time = GlucoDataService.sharedPref!!.getLong(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_TIME, 0)
                    s+= getUiTimeStamp(time) + "\n"
                    s+= excMsg + "\n"
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
                try {
                    context.contentResolver.openFileDescriptor(uri, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use { os ->
                            saveLogs(os)
                        }
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Saving logs to stream exception: " + exc.message.toString() )
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
                    try {
                        Log.v(LOG_ID, "read")
                        val buffer = ByteArray(4 * 1024) // or other buffer size
                        var read: Int
                        while (process.inputStream.read(buffer).also { rb -> read = rb } != -1) {
                            Log.v(LOG_ID, "write")
                            outputStream.write(buffer, 0, read)
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "Writing logs exception: " + exc.message.toString() )
                        outputStream.write("Exception while writing logs:".toByteArray())
                        outputStream.write(exc.message.toString().toByteArray())
                        outputStream.write(exc.stackTraceToString().toByteArray())
                    }
                    Log.v(LOG_ID, "flush")
                    outputStream.flush()
                    outputStream.close()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Exception while closing stream: " + exc.message.toString() )
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
            outputStream.write("Exception while reading logcat:".toByteArray())
            outputStream.write(exc.message.toString().toByteArray())
            outputStream.write(exc.stackTraceToString().toByteArray())
            outputStream.flush()
            outputStream.close()
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
        if(DateUtils.isToday(time))
            return getTimeStamp(time)
        return DateFormat.getDateTimeInstance().format(Date(time))
    }

    fun getTimeStamp(time: Long): String {
        return DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(time))
    }

    // returns the current day start time
    fun getDayStartTime(time: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.time = Date(time)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        //calendar.set(2025, 1, 1, 0, 0, 0)
        return calendar.time.time
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