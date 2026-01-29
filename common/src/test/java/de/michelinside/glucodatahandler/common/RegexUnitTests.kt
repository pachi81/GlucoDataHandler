package de.michelinside.glucodatahandler.common

import android.util.Log as AndroidLog
import de.michelinside.glucodatahandler.common.receiver.NotificationReceiver
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class RegexUnitTests {
    private fun getFloatFromRegex(regex: Regex, input: String): Float? {
        return Utils.parseRegexGroupValues(input, regex)?.get(1)?.toFloatOrNull()
    }

    @Before
    fun setup() {
        mockkStatic(AndroidLog::class)
        every { AndroidLog.v(any(), any()) } returns 0
        every { AndroidLog.d(any(), any()) } returns 0
        every { AndroidLog.i(any(), any()) } returns 0
        every { AndroidLog.w(any(), any<String>()) } returns 0
        every { AndroidLog.e(any(), any()) } returns 0

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @Test
    fun testGlucoseRegex() {
        val regex = NotificationReceiver.defaultGlucoseRegex.toRegex(RegexOption.IGNORE_CASE)
        val validValues = mapOf(
            "123" to 123F,
            "123.4" to 123.4F,
            "123 mg/dl" to 123F,
            "123 mg/dL" to 123F,
            "123.4 mmol/L" to 123.4F,
            "12,3 mmol/l" to 12.3F, // Comma pre-processed
            "Value: 45.6" to 45.6F,
            "89" to 89F,
            "5.0" to 5.0F,
            "5" to 5F, // Single digit
            "Time: 12:30 Value: 140" to 140F, // Should get 140
            "123.0" to 123F, // Matches 123, trailing dot ignored by toFloatOrNull
            "Timestamp 10:30 Glucose 10.1 Other 12345" to 10.1F,
            "Value is 6,5 mmol/l at 14:45" to 6.5F, // Comma pre-processed
            "65 mg/dl" to 65F,
            "100 mg" to 100F,
            "5,7" to 5.7F,
            "3.9" to 3.9F,
            "14.1 mmol/l" to 14.1F,
            "23 mmol" to 23F,
            "55 →" to 55F
        )

        val invalidValues = listOf(
            "",
            "abc",
            "12bc",
            "bc12",
            "21:45",   // timestamp should be ignored
            "123.456",
            "Value: 45.67",
            "BG:200", // Assumes no digit/colon before '200' if 'BG:' is the prefix
            "Time: 12:30", // Should not match 12 or 30
            "12:30",       // Should not match 12 or 30
            "ID:12345",      // Avoids longer numbers
            "Sensor7",
            "08:00",
            "Next check at 09:15",
            "No value here",
            "123abc",
            "432mg",
            "high",
            "4.1234 U",
            "4,1U",
            "12.324"
        )

        validValues.forEach { (value, expected) ->
            Assert.assertEquals(
                "Value '$value' with regex '${NotificationReceiver.defaultGlucoseRegex}'",
                expected,
                getFloatFromRegex(regex, value)
            )
        }

        invalidValues.forEach { value ->
            Assert.assertNull(
                "Value '$value' with regex '${NotificationReceiver.defaultGlucoseRegex}'",
                getFloatFromRegex(regex, value)
            )
        }
    }

    @Test
    fun testIobRegex() {
        val regex = NotificationReceiver.defaultIobRegex.toRegex(RegexOption.IGNORE_CASE)
        val validValues = mapOf(
            "IOB: 123 U" to 123F,
            "IOB: 123.4 U" to 123.4F,
            "IOB: 123.45 E" to 123.45F,
            "123 U" to 123F,
            "123.4 j" to 123.4F,
            "IOB: 4.1234 U" to 4.1234F,
            "IOB: 4,1 U" to 4.1F,
            "IOB: 12.0U" to 12.0F,
            "IOB: 0,0U" to 0.0F,
            "4U" to 4F,
            "4,12 U" to 4.12F,
            "4.123 E" to 4.123F,
            "4 j" to 4F
        )

        val invalidValues = listOf(
            "",
            "abc",
            "12bc",
            "bc12",
            "21:45",
            "423",
            "43.4",
            "123 mg/dl",
            "123.4 mmol/l",
            "IOB: 4.1234",
            "IOB: 4,1",
            "IOB: 12.0",
            "IOB: 10:23",
            "4g",
            "5 G",
            "4.123 EU"
        )

        validValues.forEach { (value, expected) ->
            Assert.assertEquals(
                "Value '$value' with regex '${NotificationReceiver.defaultIobRegex}'",
                expected,
                getFloatFromRegex(regex, value)
            )
        }

        invalidValues.forEach { value ->
            Assert.assertNull(
                "Value '$value' with regex '${NotificationReceiver.defaultIobRegex}'",
                getFloatFromRegex(regex, value)
            )
        }
    }


    @Test
    fun testCobRegex() {
        val regex = NotificationReceiver.defaultCobRegex.toRegex(RegexOption.IGNORE_CASE)
        val validValues = mapOf(
            "COB: 12 g" to 12F,
            "COB: 3g" to 3F,
            "COB: 0 g" to 0F,
            "5 G" to 5F,
            "4g" to 4F,
        )

        val invalidValues = listOf(
            "abc",
            "COB: 12",
            "COB: 10:23",
            "4.983 U"
        )

        validValues.forEach { (value, expected) ->
            Assert.assertEquals(
                "Value '$value' with regex '${NotificationReceiver.defaultCobRegex}'",
                expected,
                getFloatFromRegex(regex, value)
            )
        }

        invalidValues.forEach { value ->
            Assert.assertNull(
                "Value '$value' with regex '${NotificationReceiver.defaultCobRegex}'",
                getFloatFromRegex(regex, value)
            )
        }
    }

}