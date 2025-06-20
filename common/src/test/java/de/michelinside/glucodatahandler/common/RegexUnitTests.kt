package de.michelinside.glucodatahandler.common

import de.michelinside.glucodatahandler.common.receiver.NotificationReceiver
import org.junit.Test
import org.junit.Assert

class RegexUnitTests {
    private fun getFloatFromRegex(regex: Regex, input: String): Float? {
        return regex.find(input.replace(',', '.'))?.groupValues?.get(1)?.toFloatOrNull()
    }

    @Test
    fun testGlucoseRegex() {
        val regex = NotificationReceiver.defaultGlucoseRegex.toRegex()
        Assert.assertNull(getFloatFromRegex(regex, ""))
        Assert.assertNull(getFloatFromRegex(regex, "abc"))
        Assert.assertNull(getFloatFromRegex(regex, "12bc"))
        Assert.assertNull(getFloatFromRegex(regex, "bc12"))
        Assert.assertNull(getFloatFromRegex(regex, "21:45"))   // timestamp should be ignored

        Assert.assertEquals(getFloatFromRegex(regex, "123"), 123F)
        Assert.assertEquals(getFloatFromRegex(regex, "123.4"), 123.4F)
        Assert.assertEquals(getFloatFromRegex(regex, "123.456"), 123.456F)

        Assert.assertEquals(getFloatFromRegex(regex, "123 mg/dl"), 123F)
        Assert.assertEquals(getFloatFromRegex(regex, "123 mg/dL"), 123F)
        Assert.assertEquals(getFloatFromRegex(regex, "123.4 mmol/L"), 123.4F)
        Assert.assertEquals(getFloatFromRegex(regex, "123.5 mmol/l"), 123.5F)



        // Valid glucose values
        Assert.assertEquals(123F, getFloatFromRegex(regex, "123"))
        Assert.assertEquals(123F, getFloatFromRegex(regex, "123 mg/dl"))
        Assert.assertEquals(123F, getFloatFromRegex(regex, "123 mg/dL"))
        Assert.assertEquals(12.3F, getFloatFromRegex(regex, "12.3"))
        Assert.assertEquals(12.3F, getFloatFromRegex(regex, "12.3 mmol/L"))
        Assert.assertEquals(12.3F, getFloatFromRegex(regex, "12,3 mmol/l")) // Comma pre-processed
        Assert.assertEquals(45.67F, getFloatFromRegex(regex, "Value: 45.67"))
        Assert.assertEquals(89F, getFloatFromRegex(regex, "89"))
        Assert.assertEquals(5.0F, getFloatFromRegex(regex, "5.0"))
        Assert.assertEquals(5F, getFloatFromRegex(regex, "5")) // Single digit
        Assert.assertNull(getFloatFromRegex(regex, "BG:200")) // Assumes no digit/colon before '200' if 'BG:' is the prefix

        // Values that should be ignored or not fully matched as glucose
        Assert.assertNull(getFloatFromRegex(regex, "Time: 12:30")) // Should not match 12 or 30
        Assert.assertNull(getFloatFromRegex(regex, "12:30"))       // Should not match 12 or 30
        Assert.assertEquals(140F, getFloatFromRegex(regex, "Time: 12:30 Value: 140")) // Should get 140
        Assert.assertNull(getFloatFromRegex(regex, "ID:12345"))      // Avoids longer numbers
        Assert.assertNull(getFloatFromRegex(regex, "Sensor7")) // Might match 7 if no digit/colon before it
        // This depends on how strict you want to be with prefixes.
        // If "Sensor7" should be ignored, the regex might need
        // to be stricter about what can precede it (e.g., whitespace or start of string).

        // Edge cases for the regex
        Assert.assertEquals(123F, getFloatFromRegex(regex, "123.0")) // Matches 123, trailing dot ignored by toFloatOrNull

        // Test with only time (should be null)
        Assert.assertNull(getFloatFromRegex(regex, "08:00"))
        Assert.assertNull(getFloatFromRegex(regex, "Next check at 09:15"))

        // Test with mixed content
        Assert.assertEquals(10.1F, getFloatFromRegex(regex, "Timestamp 10:30 Glucose 10.1 Other 12345"))
        Assert.assertEquals(6.5F, getFloatFromRegex(regex, "Value is 6,5 mmol/l at 14:45")) // Comma pre-processed

        // Test empty and non-matching strings
        Assert.assertNull(getFloatFromRegex(regex, ""))
        Assert.assertNull(getFloatFromRegex(regex, "abc"))
        Assert.assertNull(getFloatFromRegex(regex, "No value here"))
    }

    @Test
    fun testIobRegex() {
        val regex = NotificationReceiver.defaultIobRegex.toRegex()
        Assert.assertNull(getFloatFromRegex(regex, ""))
        Assert.assertNull(getFloatFromRegex(regex, "abc"))
        Assert.assertNull(getFloatFromRegex(regex, "12bc"))
        Assert.assertNull(getFloatFromRegex(regex, "bc12"))
        Assert.assertNull(getFloatFromRegex(regex, "21:45"))   // timestamp should be ignored
        Assert.assertNull(getFloatFromRegex(regex, "423"))
        Assert.assertNull(getFloatFromRegex(regex, "43.4"))
        Assert.assertNull(getFloatFromRegex(regex, "123 mg/dl"))
        Assert.assertNull(getFloatFromRegex(regex, "123.4 mmol/l"))

        Assert.assertEquals(getFloatFromRegex(regex, "IOB: 123 U"), 123F)
        Assert.assertEquals(getFloatFromRegex(regex, "IOB: 123.4 U"), 123.4F)
        Assert.assertEquals(getFloatFromRegex(regex, "123 U"), 123F)
        Assert.assertEquals(getFloatFromRegex(regex, "123.4 U"), 123.4F)

    }

}