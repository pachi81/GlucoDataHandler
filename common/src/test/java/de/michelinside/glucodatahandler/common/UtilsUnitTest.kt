package de.michelinside.glucodatahandler.common

import android.util.Log
import de.michelinside.glucodatahandler.common.utils.Utils
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class UtilsUnitTest {
    @Test
    fun testTimeBetweenTimes() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        assertTrue(Utils.timeBetweenTimes("12:00:01", "10:00:00", "14:00:00"))
        assertTrue(Utils.timeBetweenTimes("10:00", "10:00", "14:00"))
        assertTrue(Utils.timeBetweenTimes("14:00", "10:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("15:00", "10:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("00:00", "10:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("09:59:59", "10:00:00", "14:00:00"))
        assertFalse(Utils.timeBetweenTimes("14:01", "10:00", "14:00"))

        // with night shift
        assertTrue(Utils.timeBetweenTimes("00:00", "23:00", "14:00"))
        assertTrue(Utils.timeBetweenTimes("23:00", "23:00", "10:00"))
        assertTrue(Utils.timeBetweenTimes("10:00", "23:00", "10:00"))
        assertFalse(Utils.timeBetweenTimes("18:00", "23:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("22:59", "23:00", "10:00"))
        assertFalse(Utils.timeBetweenTimes("10:01", "23:00", "10:00"))

        // same time
        assertTrue(Utils.timeBetweenTimes("14:00", "14:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("14:01", "14:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("13:59", "14:00", "14:00"))

        // invalid times
        assertFalse(Utils.timeBetweenTimes("", "14:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("14:00", "", "14:00"))
        assertFalse(Utils.timeBetweenTimes("14:00", "14:00", ""))
        assertFalse(Utils.timeBetweenTimes("abc", "14:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes("14:00", "abc", "14:00"))
        assertFalse(Utils.timeBetweenTimes("14:00", "14:00", "abc"))
    }


    @Test
    fun testSets() {
        val set1 = mutableSetOf("a", "b", "c")
        val set2 = mutableSetOf("b", "c", "d")

        assertEquals(mutableSetOf("a", "b", "c", "d"), set1 + set2)
        assertEquals(mutableSetOf("a", "b", "c", "d"), set1.union(set2))
    }
}