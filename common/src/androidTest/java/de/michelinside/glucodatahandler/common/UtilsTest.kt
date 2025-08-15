package de.michelinside.glucodatahandler.common

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.michelinside.glucodatahandler.common.utils.JsonUtils
import de.michelinside.glucodatahandler.common.utils.Utils

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class UtilsTest {
    // Helper for float arrays (NaN, Infinity, tolerance)
    private fun assertFloatArrayEqualsTolerance(
        expected: FloatArray?, actual: FloatArray?, delta: Float = 0.0001f
    ) {
        if (expected == null) {
            assertNull("Expected null float array, actual not null", actual); return
        }
        assertNotNull("Expected non-null float array, actual null", actual)
        assertEquals("Float array lengths differ", expected.size, actual!!.size)
        for (i in expected.indices) {
            when {
                expected[i].isNaN() ->
                    assertTrue("Idx $i: expected NaN", actual[i].isNaN())
                expected[i].isInfinite() && expected[i] > 0 ->
                    assertTrue("Idx $i: expected PosInf", actual[i].isInfinite() && actual[i] > 0)
                expected[i].isInfinite() && expected[i] < 0 ->
                    assertTrue("Idx $i: expected NegInf", actual[i].isInfinite() && actual[i] < 0)
                else -> assertEquals("Float idx $i differs", expected[i], actual[i], delta)
            }
        }
    }

    // Helper for double arrays (NaN, Infinity, tolerance)
    private fun assertDoubleArrayEqualsTolerance(
        expected: DoubleArray?, actual: DoubleArray?, delta: Double = 0.0000001
    ) {
        if (expected == null) {
            assertNull("Expected null double array, actual not null", actual); return
        }
        assertNotNull("Expected non-null double array, actual null", actual)
        assertEquals("Double array lengths differ", expected.size, actual!!.size)
        for (i in expected.indices) {
            when {
                expected[i].isNaN() ->
                    assertTrue("Idx $i: expected NaN", actual[i].isNaN())
                expected[i].isInfinite() && expected[i] > 0 ->
                    assertTrue("Idx $i: expected PosInf", actual[i].isInfinite() && actual[i] > 0)
                expected[i].isInfinite() && expected[i] < 0 ->
                    assertTrue("Idx $i: expected NegInf", actual[i].isInfinite() && actual[i] < 0)
                else -> assertEquals("Double idx $i differs", expected[i], actual[i], delta)
            }
        }
    }

    private fun performBundleConversionTest(useJson: Boolean) {
        val originalBundle = Bundle()
        val testType = if(useJson) "JSON" else "Parcel"
        println("Performing Bundle conversion test using $testType")

        // --- Basic Primitive Types ---
        originalBundle.putBoolean("key_boolean", true)
        originalBundle.putByte("key_byte", 123.toByte())
        originalBundle.putChar("key_char", '€')
        originalBundle.putShort("key_short", 12345.toShort())
        originalBundle.putInt("key_int", 1234567890)
        originalBundle.putLong("key_long", 1234567890123456789L)
        originalBundle.putLong("key_long_max_int", JsonUtils.MAX_SAFE_INTEGER)
        originalBundle.putLong("key_long_min_int", JsonUtils.MIN_SAFE_INTEGER)
        originalBundle.putFloat("key_float", 123.456f)
        originalBundle.putDouble("key_double", 12345.67890)
        originalBundle.putString("key_string", "Test: \n\t\"'\\@#\$%^&*()_+[]{};':,./<>?`~ éüöäàç€和平")
        originalBundle.putString("key_string_empty", "")
        originalBundle.putString("key_string_null", null)

        // --- Special Float/Double Values ---
        originalBundle.putFloat("key_float_nan", Float.NaN)
        originalBundle.putFloat("key_float_pos_inf", Float.POSITIVE_INFINITY)
        originalBundle.putFloat("key_float_neg_inf", Float.NEGATIVE_INFINITY)
        originalBundle.putFloat("key_float_max", Float.MAX_VALUE)
        originalBundle.putFloat("key_float_min_val", Float.MIN_VALUE) // Corrected

        originalBundle.putDouble("key_double_nan", Double.NaN)
        originalBundle.putDouble("key_double_pos_inf", Double.POSITIVE_INFINITY)
        originalBundle.putDouble("key_double_neg_inf", Double.NEGATIVE_INFINITY)
        originalBundle.putDouble("key_double_max", Double.MAX_VALUE)
        originalBundle.putDouble("key_double_min_val", Double.MIN_VALUE) // Corrected
        originalBundle.putBooleanArray(
            "key_boolean_array",
            booleanArrayOf(true, false, true, false)
        )
        originalBundle.putByteArray(
            "key_byte_array",
            byteArrayOf(0, 10, -10, Byte.MAX_VALUE, Byte.MIN_VALUE)
        )
        originalBundle.putCharArray(
            "key_char_array",
            charArrayOf('H', '€', '\u0000', 'Ā', '글')
        ) // Diverse chars
        originalBundle.putShortArray(
            "key_short_array",
            shortArrayOf(0, 100, -100, Short.MAX_VALUE, Short.MIN_VALUE)
        )
        originalBundle.putIntArray(
            "key_int_array",
            intArrayOf(0, 1000, -1000, Int.MAX_VALUE, Int.MIN_VALUE)
        )
        originalBundle.putLongArray(
            "key_long_array",
            longArrayOf(0L, 10000L, -10000L, Long.MAX_VALUE, Long.MIN_VALUE)
        )
        originalBundle.putFloatArray(
            "key_float_array",
            floatArrayOf(
                0.0f,
                1.23f,
                -4.56f,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.MAX_VALUE,
                Float.MIN_VALUE
            )
        )
        originalBundle.putDoubleArray(
            "key_double_array",
            doubleArrayOf(
                0.0,
                12.34,
                -56.78,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE,
                Double.MIN_VALUE
            )
        )
        originalBundle.putStringArray(
            "key_string_array",
            arrayOf("alpha", "beta", null, "delta", "", "epsilon with space & €")
        )
        originalBundle.putStringArrayList(
            "key_string_arraylist",
            arrayListOf("list_one", null, "list_three", "", "list five with €")
        )

        // --- CharSequence Types ---
        // Note: Complex CharSequence (like SpannableString) might lose styling with JSON, but parceling should be fine.
        originalBundle.putCharSequence(
            "key_charsequence_string",
            "This is a CharSequence (from String with €)"
        )
        originalBundle.putCharSequenceArray(
            "key_charsequence_array",
            arrayOf("cs_one" as CharSequence, null, "cs_three_again with €" as CharSequence, "" as CharSequence)
        )

        // --- ArrayLists of other directly supported types ---
        originalBundle.putIntegerArrayList(
            "key_integer_arraylist",
            arrayListOf(10, 20, null, 40, Int.MAX_VALUE)
        )
        // Bundle also supports putParcelableArrayList, but we're avoiding Parcelable for this version.

        // --- Nested Bundle ---
        val nestedBundle = Bundle()
        nestedBundle.putString("nested_key_string", "Hello from nested bundle! €")
        nestedBundle.putInt("nested_key_int", 999)
        nestedBundle.putBoolean("nested_key_boolean", false)
        nestedBundle.putDouble("nested_key_double_nan", Double.NaN)
        nestedBundle.putStringArray("nested_key_string_array", arrayOf("n_one", null, "n_three"))

        originalBundle.putBundle("key_nested_bundle", nestedBundle)
        originalBundle.putBundle("key_nested_bundle_null", null) // Test null bundle

        // --- Empty Arrays (Edge Cases) ---
        originalBundle.putBooleanArray("key_empty_boolean_array", booleanArrayOf())
        originalBundle.putByteArray("key_empty_byte_array", byteArrayOf())
        originalBundle.putCharArray("key_empty_char_array", charArrayOf())
        originalBundle.putShortArray("key_empty_short_array", shortArrayOf())
        originalBundle.putIntArray("key_empty_int_array", intArrayOf())
        originalBundle.putLongArray("key_empty_long_array", longArrayOf())
        originalBundle.putFloatArray("key_empty_float_array", floatArrayOf())
        originalBundle.putDoubleArray("key_empty_double_array", doubleArrayOf())
        originalBundle.putStringArray("key_empty_string_array", arrayOf())
        originalBundle.putCharSequenceArray("key_empty_charsequence_array", arrayOf())
        println("Original Bundle content (${if (useJson) "target: JSON" else "target: Parcel"}):")
        println(Utils.dumpBundle(originalBundle)) // Assuming Utils.dumpBundle exists for logging

        val byteArray = Utils.bundleToBytes(originalBundle, useJson)
        assertNotNull("Byte array should not be null after conversion ($testType)", byteArray)
        assertTrue("Byte array should not be empty ($testType)", byteArray!!.isNotEmpty())

        val restoredBundle = Utils.bytesToBundle(byteArray, useJson)
        assertNotNull("Restored bundle should not be null ($testType)", restoredBundle)

        println("Restored Bundle content (${if (useJson) "source: JSON" else "source: Parcel"}):")
        println(Utils.dumpBundle(restoredBundle))

        // --- Assertions ---
        // Basic Primitive Types
        assertEquals(
            "Boolean value differs ($testType)",
            originalBundle.getBoolean("key_boolean"),
            restoredBundle!!.getBoolean("key_boolean")
        )
        assertEquals(
            "Byte value differs ($testType)",
            originalBundle.getByte("key_byte"),
            restoredBundle.getByte("key_byte")
        )
        assertEquals(
            "Char value differs ($testType)",
            originalBundle.getChar("key_char"),
            restoredBundle.getChar("key_char")
        )
        assertEquals(
            "Short value differs ($testType)",
            originalBundle.getShort("key_short"),
            restoredBundle.getShort("key_short")
        )
        assertEquals(
            "Int value differs ($testType)",
            originalBundle.getInt("key_int"),
            restoredBundle.getInt("key_int")
        )
        assertEquals(
            "Long value differs ($testType)",
            originalBundle.getLong("key_long"),
            restoredBundle.getLong("key_long")
        )
        assertEquals(
            "Long max int value differs ($testType)",
            originalBundle.getLong("key_long_max_int"),
            restoredBundle.getLong("key_long_max_int")
        )
        assertEquals(
            "Long min int value differs ($testType)",
            originalBundle.getLong("key_long_min_int"),
            restoredBundle.getLong("key_long_min_int")
        )
        assertEquals(
            "Float value differs ($testType)",
            originalBundle.getFloat("key_float"),
            restoredBundle.getFloat("key_float"),
            0.0001f
        )
        assertEquals(
            "Double value differs ($testType)",
            originalBundle.getDouble("key_double"),
            restoredBundle.getDouble("key_double"),
            0.0000001
        )
        assertEquals(
            "String value differs ($testType)",
            originalBundle.getString("key_string"),
            restoredBundle.getString("key_string")
        )
        assertEquals(
            "Empty String value differs ($testType)",
            originalBundle.getString("key_string_empty"),
            restoredBundle.getString("key_string_empty")
        )
        assertNull(
            "Null String should be null ($testType)",
            restoredBundle.getString("key_string_null")
        )

        // Special Float/Double Values
        assertTrue(
            "Float NaN differs ($testType)",
            restoredBundle.getFloat("key_float_nan").isNaN()
        )
        assertEquals(
            "Float POSITIVE_INFINITY differs ($testType)",
            Float.POSITIVE_INFINITY,
            restoredBundle.getFloat("key_float_pos_inf"),
            0.0f
        )
        assertEquals(
            "Float NEGATIVE_INFINITY differs ($testType)",
            Float.NEGATIVE_INFINITY,
            restoredBundle.getFloat("key_float_neg_inf"),
            0.0f
        )
        assertEquals(
            "Float MAX_VALUE differs ($testType)",
            originalBundle.getFloat("key_float_max"),
            restoredBundle.getFloat("key_float_max"),
            0.0f
        )
        assertEquals(
            "Float MIN_VALUE differs ($testType)",
            originalBundle.getFloat("key_float_min_val"),
            restoredBundle.getFloat("key_float_min_val"),
            0.0f
        )

        assertTrue(
            "Double NaN differs ($testType)",
            restoredBundle.getDouble("key_double_nan").isNaN()
        )
        assertEquals(
            "Double POSITIVE_INFINITY differs ($testType)",
            Double.POSITIVE_INFINITY,
            restoredBundle.getDouble("key_double_pos_inf"),
            0.0
        )
        assertEquals(
            "Double NEGATIVE_INFINITY differs ($testType)",
            Double.NEGATIVE_INFINITY,
            restoredBundle.getDouble("key_double_neg_inf"),
            0.0
        )
        assertEquals(
            "Double MAX_VALUE differs ($testType)",
            originalBundle.getDouble("key_double_max"),
            restoredBundle.getDouble("key_double_max"),
            0.0
        )
        assertEquals(
            "Double MIN_VALUE differs ($testType)",
            originalBundle.getDouble("key_double_min_val"),
            restoredBundle.getDouble("key_double_min_val"),
            0.0
        )
        assertArrayEquals(
            "Boolean array differs ($testType)",
            originalBundle.getBooleanArray("key_boolean_array"),
            restoredBundle.getBooleanArray("key_boolean_array")
        )
        assertArrayEquals(
            "Byte array differs ($testType)",
            originalBundle.getByteArray("key_byte_array"),
            restoredBundle.getByteArray("key_byte_array")
        )
        assertArrayEquals(
            "Char array differs ($testType)",
            originalBundle.getCharArray("key_char_array"),
            restoredBundle.getCharArray("key_char_array")
        )
        assertArrayEquals(
            "Short array differs ($testType)",
            originalBundle.getShortArray("key_short_array"),
            restoredBundle.getShortArray("key_short_array")
        )
        assertArrayEquals(
            "Int array differs ($testType)",
            originalBundle.getIntArray("key_int_array"),
            restoredBundle.getIntArray("key_int_array")
        )
        assertArrayEquals(
            "Long array differs ($testType)",
            originalBundle.getLongArray("key_long_array"),
            restoredBundle.getLongArray("key_long_array")
        )

        assertFloatArrayEqualsTolerance(
            originalBundle.getFloatArray("key_float_array"),
            restoredBundle.getFloatArray("key_float_array")
        )
        assertDoubleArrayEqualsTolerance(
            originalBundle.getDoubleArray("key_double_array"),
            restoredBundle.getDoubleArray("key_double_array")
        )

        // String Array & ArrayList
        assertArrayEquals(
            "String array differs ($testType)",
            originalBundle.getStringArray("key_string_array"),
            restoredBundle.getStringArray("key_string_array")
        )
        assertEquals(
            "String ArrayList differs ($testType)",
            originalBundle.getStringArrayList("key_string_arraylist"),
            restoredBundle.getStringArrayList("key_string_arraylist")
        )

        // CharSequence Types
        // For CharSequence, direct equality check works if they are Strings. If they were complex (e.g. Spannable),
        // you might need to convert to String for comparison if styling is lost, especially with JSON.
        assertEquals(
            "CharSequence (String) differs ($testType)",
            originalBundle.getCharSequence("key_charsequence_string")?.toString(),
            restoredBundle.getCharSequence("key_charsequence_string")?.toString()
        )

        val originalCSArray = originalBundle.getCharSequenceArray("key_charsequence_array")
        val restoredCSArray = restoredBundle.getCharSequenceArray("key_charsequence_array")
        assertNotNull("Restored CS Array should not be null ($testType)", restoredCSArray)
        if (originalCSArray != null && restoredCSArray != null) {
            assertEquals(
                "CS Array length differs ($testType)",
                originalCSArray.size,
                restoredCSArray.size
            )
            for (i in originalCSArray.indices) {
                assertEquals(
                    "CS Array element $i differs ($testType)",
                    originalCSArray[i]?.toString(),
                    restoredCSArray[i]?.toString()
                )
            }
        } else if (originalCSArray != null || restoredCSArray != null) {
            fail("One CS array is null and the other isn't ($testType)")
        }

        // ArrayLists of other supported types
        assertEquals("Integer ArrayList differs ($testType)", originalBundle.getIntegerArrayList("key_integer_arraylist"), restoredBundle.getIntegerArrayList("key_integer_arraylist"))

        // Nested Bundle
        val originalNestedBundle = originalBundle.getBundle("key_nested_bundle")
        val restoredNestedBundle = restoredBundle.getBundle("key_nested_bundle")
        assertNotNull("Restored nested bundle should not be null ($testType)", restoredNestedBundle)
        if (originalNestedBundle != null && restoredNestedBundle != null) {
            assertEquals("Nested String differs ($testType)", originalNestedBundle.getString("nested_key_string"), restoredNestedBundle.getString("nested_key_string"))
            assertEquals("Nested Int differs ($testType)", originalNestedBundle.getInt("nested_key_int"), restoredNestedBundle.getInt("nested_key_int"))
            assertEquals("Nested Boolean differs ($testType)", originalNestedBundle.getBoolean("nested_key_boolean"), restoredNestedBundle.getBoolean("nested_key_boolean"))
            assertTrue("Nested Double NaN differs ($testType)", restoredNestedBundle.getDouble("nested_key_double_nan").isNaN())
            assertArrayEquals("Nested String array differs ($testType)", originalNestedBundle.getStringArray("nested_key_string_array"), restoredNestedBundle.getStringArray("nested_key_string_array"))
        } else if (originalNestedBundle != null || restoredNestedBundle != null) {
            fail("One nested bundle is null and the other isn't ($testType)")
        }
        assertNull("Null nested bundle should be null ($testType)", restoredBundle.getBundle("key_nested_bundle_null"))

        // Empty Arrays
        assertArrayEquals("Empty boolean array differs ($testType)", originalBundle.getBooleanArray("key_empty_boolean_array"), restoredBundle.getBooleanArray("key_empty_boolean_array"))
        assertArrayEquals("Empty byte array differs ($testType)", originalBundle.getByteArray("key_empty_byte_array"), restoredBundle.getByteArray("key_empty_byte_array"))
        assertArrayEquals("Empty char array differs ($testType)", originalBundle.getCharArray("key_empty_char_array"), restoredBundle.getCharArray("key_empty_char_array"))
        assertArrayEquals("Empty short array differs ($testType)", originalBundle.getShortArray("key_empty_short_array"), restoredBundle.getShortArray("key_empty_short_array"))
        assertArrayEquals("Empty int array differs ($testType)", originalBundle.getIntArray("key_empty_int_array"), restoredBundle.getIntArray("key_empty_int_array"))
        assertArrayEquals("Empty long array differs ($testType)", originalBundle.getLongArray("key_empty_long_array"), restoredBundle.getLongArray("key_empty_long_array"))
        assertFloatArrayEqualsTolerance(originalBundle.getFloatArray("key_empty_float_array"), restoredBundle.getFloatArray("key_empty_float_array"))
        assertDoubleArrayEqualsTolerance(originalBundle.getDoubleArray("key_empty_double_array"), restoredBundle.getDoubleArray("key_empty_double_array"))
        assertArrayEquals("Empty string array differs ($testType)", originalBundle.getStringArray("key_empty_string_array"), restoredBundle.getStringArray("key_empty_string_array"))
        assertArrayEquals("Empty charsequence array differs ($testType)", originalBundle.getCharSequenceArray("key_empty_charsequence_array"), restoredBundle.getCharSequenceArray("key_empty_charsequence_array"))

        println("Bundle conversion test using $testType completed successfully.")
    } // End of performBundleConversionTest

    @Test
    fun testBundleToByteArray() {
        performBundleConversionTest(false)
    }
    @Test
    fun testBundleToByteArrayWithJson() {
        performBundleConversionTest(true)
    }
}