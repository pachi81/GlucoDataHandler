package de.michelinside.glucodatahandler.common.utils

import android.os.Bundle
import android.os.Parcelable
import de.michelinside.glucodatahandler.common.utils.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

object JsonUtils {
    val LOG_ID = "GDH.Utils.JsonUtils"
    const val MAX_SAFE_INTEGER: Long = 9007199254740991L // 2^53 - 1
    const val MIN_SAFE_INTEGER: Long = -9007199254740991L // -(2^53 - 1)

    private fun isSafeIntegerForLong(value: Long): Boolean {
        return value in MIN_SAFE_INTEGER..MAX_SAFE_INTEGER
    }

    fun getFloat(key: String, jsonObject: JSONObject): Float {
        return Utils.parseFloatString(jsonObject.optString(key))
    }

    fun getParcelableAsJson(parcelable: Parcelable): String {
        return Gson().toJson(parcelable)
    }

    inline fun <reified T : Parcelable?> getParcelableFromJson(json: String?): T? {
        try {
            if(json != null)
                return Gson().fromJson(json, T::class.java)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error parsing json $json: $exc")
        }
        return null
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
                is FloatArray -> {
                    // Convert FloatArray to List<Any> where special values are strings
                    typedMap[key] = TypedJsonValue(typeName, actualValue.map { f ->
                        when {
                            f.isNaN() -> "NaN"
                            f.isInfinite() && f > 0 -> "Infinity"
                            f.isInfinite() && f < 0 -> "-Infinity"
                            else -> f // Keep normal numbers as Floats
                        }
                    })
                }
                is DoubleArray -> {
                    // Convert DoubleArray to List<Any> where special values are strings
                    typedMap[key] = TypedJsonValue(typeName, actualValue.map { d ->
                        when {
                            d.isNaN() -> "NaN"
                            d.isInfinite() && d > 0 -> "Infinity"
                            d.isInfinite() && d < 0 -> "-Infinity"
                            else -> d // Keep normal numbers as Doubles
                        }
                    })
                }

                is BooleanArray, is ByteArray, is CharArray, is ShortArray,
                is IntArray, is LongArray -> {
                    // For arrays, typeName will be like "BooleanArray", "ByteArray", etc.
                    typedMap[key] = TypedJsonValue(typeName, actualValue)
                }
                is Array<*> -> {
                    // For arrays, typeName will be like "StringArray", "IntArray", or "Array" for Array<Any>
                    // Gson serializes arrays to JSON arrays (lists).
                    if(actualValue.isArrayOf<String>()) {
                        typedMap[key] = TypedJsonValue("StringArray", (actualValue).map { it?.toString() }.toTypedArray())
                    } else if (actualValue.isArrayOf<CharSequence>()) {
                        typedMap[key] = TypedJsonValue("CharSequenceArray", (actualValue).map { it?.toString() }.toTypedArray())
                    } else
                        Log.w(LOG_ID, "Using generic typeName '$typeName' for key '$key' for value of class ${actualValue.javaClass.name}")
                }
                is ArrayList<*> -> {
                    // typeName will be "ArrayList". We might need more specific info if contents are mixed
                    // or require special handling not covered by individual element checks.
                    // For CharSequenceArrayList, convert to ArrayList<String> for safer JSON.
                    // For IntegerArrayList, StringArrayList, Gson handles them well.
                    // typeName will be "ArrayList" - `typedMapToBundle` will need to infer or use this.
                    typedMap[key] = TypedJsonValue(typeName, actualValue)
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
                is String, is Int, is Boolean, is Short, is Byte -> {
                    typedMap[key] = TypedJsonValue(typeName, actualValue)
                }
                is Long -> {
                    if (isSafeIntegerForLong(actualValue)) {
                        typedMap[key] = TypedJsonValue(typeName, actualValue)  // needed for downport compatibility
                    } else {
                        typedMap[key] = TypedJsonValue(typeName, actualValue.toString())
                    }
                }
                is CharSequence, is Char -> {
                    // Convert CharSequence to String for reliable JSON serialization.
                    // Styling (SpannableString) will be lost.
                    typedMap[key] = TypedJsonValue(typeName, actualValue.toString())
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

    fun bundleToJsonBytes(bundle: Bundle?): ByteArray? {
        if (bundle == null) return null
        return try {
            val map = bundleToTypedMap(bundle) ?: return null
            val gson = Gson()
            val jsonString = gson.toJson(map)
            if(Log.isLoggable(LOG_ID, android.util.Log.VERBOSE))
                Log.v(LOG_ID, "Created jsonString: ${jsonString.take(4000)}")
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
                val type = rawValue["type"] as? String
                val value = rawValue["value"]

                when (type) {
                    "String" -> bundle.putString(key, value as? String)
                    "Integer" -> if (value is Number) bundle.putInt(key, value.toInt())
                    "Int" -> if (value is Number) bundle.putInt(key, value.toInt())
                    "Long" -> if (value is Number) bundle.putLong(key, value.toLong()) else if (value is String) bundle.putLong(key, value.toLong())
                    "Boolean" -> if (value is Boolean) bundle.putBoolean(key, value)
                    "Short" -> if (value is Number) bundle.putShort(key, value.toShort())
                    "Byte" -> if (value is Number) bundle.putByte(key, value.toByte())
                    "Char" -> if (value is String) bundle.putChar(key, value[0])
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
                                value.all { it is String || it == null } -> bundle.putStringArrayList(key, value as ArrayList<String>)
                                value.all { it is Number || it == null } -> {
                                    val integerList: ArrayList<Int?> = ArrayList(value.map { item ->
                                        when (item) {
                                            null -> null
                                            is Number -> item.toInt()
                                            else -> null // Should not happen if .all { it is Number? } is true
                                        }
                                    })
                                    bundle.putIntegerArrayList(key, integerList)
                                }
                                // Add other types like Parcelable if you have a way to cast them
                                else -> Log.w(LOG_ID, "Cannot directly put generic ArrayList into Bundle for key '$key' with values $value")
                            }
                        }
                    }
                    "FloatArray" -> {
                        if (value is List<*>) {
                            val floatList = value.mapNotNull { item ->
                                when (item) {
                                    is Number -> item.toFloat()
                                    is String -> when (item) {
                                        "NaN" -> Float.NaN
                                        "Infinity" -> Float.POSITIVE_INFINITY
                                        "-Infinity" -> Float.NEGATIVE_INFINITY
                                        else -> try { item.toFloat() } catch (e: NumberFormatException) { null /* Or log error */ }
                                    }
                                    else -> null // Or log error for unexpected item type
                                }
                            }
                            bundle.putFloatArray(key, floatList.toFloatArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for FloatArraySpecialHandled, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }
                    "DoubleArray" -> {
                        if (value is List<*>) {
                            val doubleList = value.mapNotNull { item ->
                                when (item) {
                                    is Number -> item.toDouble()
                                    is String -> when (item) {
                                        "NaN" -> Double.NaN
                                        "Infinity" -> Double.POSITIVE_INFINITY
                                        "-Infinity" -> Double.NEGATIVE_INFINITY
                                        else -> try { item.toDouble() } catch (e: NumberFormatException) { null /* Or log error */ }
                                    }
                                    else -> null // Or log error for unexpected item type
                                }
                            }
                            bundle.putDoubleArray(key, doubleList.toDoubleArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for DoubleArraySpecialHandled, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }
                    // Add cases for "StringArray", "IntArray" etc. if you serialized them that way
                    // and need to convert List<Number> back to, e.g., IntArray.

                    "BooleanArray" -> {
                        if (value is List<*>) {
                            // Gson usually deserializes JSON arrays of booleans to List<Boolean>
                            val booleanList = value.mapNotNull { it as? Boolean }
                            bundle.putBooleanArray(key, booleanList.toBooleanArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for BooleanArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }

                    "ByteArray" -> {
                        if (value is List<*>) {
                            // Gson deserializes JSON arrays of numbers to List<Double> by default.
                            // We need to convert them to Byte.
                            val byteList = value.mapNotNull { (it as? Number)?.toByte() }
                            bundle.putByteArray(key, byteList.toByteArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for ByteArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }
                    "CharArray" -> {
                        // How Gson handles CharArray depends on its configuration.
                        // 1. It might convert chars to their Unicode integer values (List<Double> or List<Long>).
                        // 2. It might convert them to single-character strings (List<String>).
                        // Let's assume List<String> for safer handling, or you might need to check the type.
                        if (value is List<*>) {
                            val charList = value.mapNotNull {
                                when (it) {
                                    is String -> if (it.length == 1) it[0] else null
                                    is Number -> it.toInt().toChar() // If Gson used numeric representation
                                    else -> null
                                }
                            }
                            bundle.putCharArray(key, charList.toCharArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for CharArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }

                    "ShortArray" -> {
                        if (value is List<*>) {
                            // Gson deserializes JSON arrays of numbers to List<Double> by default.
                            val shortList = value.mapNotNull { (it as? Number)?.toShort() }
                            bundle.putShortArray(key, shortList.toShortArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for ShortArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }

                    "IntArray" -> {
                        if (value is List<*>) {
                            // Gson deserializes JSON arrays of numbers to List<Double> by default.
                            val intList = value.mapNotNull { (it as? Number)?.toInt() }
                            bundle.putIntArray(key, intList.toIntArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for IntArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }

                    "LongArray" -> {
                        if (value is List<*>) {
                            // Gson deserializes JSON arrays of numbers to List<Double> or List<Long>
                            // if the numbers are large enough and don't have decimals.
                            // Safest to check for Number.
                            val longList = value.mapNotNull { (it as? Number)?.toLong() }
                            bundle.putLongArray(key, longList.toLongArray())
                        } else {
                            Log.w(LOG_ID, "Expected List for LongArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }
                    "StringArray" -> { // If it was originally a String[] and not CharSequence[]
                        if (value is List<*>) {
                            bundle.putStringArray(key, value.map { it as? String }.toTypedArray())
                        } else if (value is Array<*>) {
                            bundle.putStringArray(key, value.map { it as? String }.toTypedArray())
                        }
                        else {
                            Log.w(LOG_ID, "Expected List or Array for StringArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }

                    "CharSequenceArrayList" -> { // Was ArrayList<CharSequence>
                        if (value is List<*>) {
                            // The list elements should be strings or nulls
                            val stringList = ArrayList<CharSequence?>(value.map { it as? String })
                            bundle.putCharSequenceArrayList(key, stringList)
                        } else {
                            Log.w(LOG_ID, "Expected List for CharSequenceArrayList, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }

                    "CharSequenceArray" -> { // Was CharSequence[]
                        if (value is List<*>) { // Gson turns arrays into Lists
                            // The list elements should be strings or nulls from the serialization step
                            val stringArray = value.map { it as? String }.toTypedArray()
                            bundle.putCharSequenceArray(key, stringArray)
                        } else if (value is Array<*>) { // Less common from Gson default, but good to check
                            bundle.putCharSequenceArray(key, value.map { it as? String }.toTypedArray())
                        }
                        else {
                            Log.w(LOG_ID, "Expected List or Array for CharSequenceArray, got ${value?.javaClass?.name} for key '$key'")
                        }
                    }
                    "CharSequence" -> { // Was an individual CharSequence
                        bundle.putCharSequence(key, value as? String)
                    }
                    else -> Log.w(LOG_ID, "Unknown type '$type' in typedMapToBundle for key '$key'")
                }
                if(!bundle.containsKey(key)) {
                    Log.e(LOG_ID, "Key '$key' not found in bundle after conversion of value '$value'")
                }
            } catch (e: ClassCastException) {
                Log.e(LOG_ID, "Type casting error for key '$key' during typedMapToBundle: ${e.message}", e)
            }
        }
        return bundle
    }

    fun jsonBytesToBundle(bytes: ByteArray?): Bundle? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val jsonString = String(bytes, Charsets.UTF_8)
            if(Log.isLoggable(LOG_ID, android.util.Log.VERBOSE))
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
}