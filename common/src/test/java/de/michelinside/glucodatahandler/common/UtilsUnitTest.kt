package de.michelinside.glucodatahandler.common

import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class UtilsUnitTest {

    fun time(time: String): LocalDateTime {
        val dateTime = "30.09.2024 $time"
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        return LocalDateTime.parse(dateTime, formatter)
    }

    fun datetime(dateTime: String): LocalDateTime {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        return LocalDateTime.parse(dateTime, formatter)
    }


    @Test
    fun testIsValidTime() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        assertTrue(Utils.isValidTime("12:00"))
        assertTrue(Utils.isValidTime("00:00"))
        assertTrue(Utils.isValidTime("23:59"))
        assertFalse(Utils.isValidTime("24:01"))
        assertFalse(Utils.isValidTime("12:60"))
        assertFalse(Utils.isValidTime(null))
        assertFalse(Utils.isValidTime(""))
    }

    @Test
    fun testTimeBetweenTimes() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        assertTrue(Utils.timeBetweenTimes(time("12:00"), "10:00", "14:00"))
        assertTrue(Utils.timeBetweenTimes(time("10:00"), "10:00", "14:00"))
        assertTrue(Utils.timeBetweenTimes(time("14:00"), "10:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("15:00"), "10:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("00:00"), "10:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("09:59"), "10:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("14:01"), "10:00", "14:00"))

        // with night shift
        assertTrue(Utils.timeBetweenTimes(time("00:00"), "23:00", "14:00"))
        assertTrue(Utils.timeBetweenTimes(time("23:00"), "23:00", "10:00"))
        assertTrue(Utils.timeBetweenTimes(time("10:00"), "23:00", "10:00"))
        assertFalse(Utils.timeBetweenTimes(time("18:00"), "23:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("22:59"), "23:00", "10:00"))
        assertFalse(Utils.timeBetweenTimes(time("10:01"), "23:00", "10:00"))

        // same time
        assertTrue(Utils.timeBetweenTimes(time("14:00"), "14:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("14:01"), "14:00", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("13:59"), "14:00", "14:00"))

        // invalid times
        assertFalse(Utils.timeBetweenTimes(time("14:00"), "", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("14:00"), "14:00", ""))
        assertFalse(Utils.timeBetweenTimes(time("14:00"), "abc", "14:00"))
        assertFalse(Utils.timeBetweenTimes(time("14:00"), "14:00", "abc"))
    }

    @Test
    fun testTimeBetweenTimesWithDayFilter() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        assertTrue(Utils.timeBetweenTimes(time("23:00"), "23:00", "14:00", mutableSetOf("1")))
        assertTrue(Utils.timeBetweenTimes(time("23:10"), "23:00", "14:00", mutableSetOf("1")))
        assertTrue(Utils.timeBetweenTimes(datetime("01.10.2024 00:00"), "23:00", "14:00", mutableSetOf("1")))
        assertTrue(Utils.timeBetweenTimes(datetime("01.10.2024 10:00"), "23:00", "14:00", mutableSetOf("1")))
        assertTrue(Utils.timeBetweenTimes(datetime("01.10.2024 14:00"), "23:00", "14:00", mutableSetOf("1")))
        assertFalse(Utils.timeBetweenTimes(time("23:10"), "23:00", "14:00", mutableSetOf()))
        assertFalse(Utils.timeBetweenTimes(time("23:10"), "23:00", "14:00", mutableSetOf("2", "3", "4", "5", "6", "7")))
        assertFalse(Utils.timeBetweenTimes(datetime("01.10.2024 00:00"), "23:00", "14:00", mutableSetOf("2", "3", "4", "5", "6", "7")))
        assertFalse(Utils.timeBetweenTimes(datetime("01.10.2024 00:00"), "23:00", "14:00", mutableSetOf("2", "3", "4", "5", "6", "7")))
        assertTrue(Utils.timeBetweenTimes(datetime("01.10.2024 00:00"), "23:00", "14:00", mutableSetOf("1", "2", "3", "4", "5", "6", "7")))
        assertFalse(Utils.timeBetweenTimes(datetime("01.10.2024 23:10"), "23:00", "14:00", mutableSetOf("1")))
        assertFalse(Utils.timeBetweenTimes(datetime("02.10.2024 10:10"), "23:00", "14:00", mutableSetOf("1")))
        assertTrue(Utils.timeBetweenTimes(datetime("02.10.2024 10:10"), "23:00", "14:00", mutableSetOf("2")))
        assertFalse(Utils.timeBetweenTimes(datetime("02.10.2024 14:10"), "23:00", "14:00", mutableSetOf("2")))
        assertTrue(Utils.timeBetweenTimes(time("23:00"), "10:00", "00:00", mutableSetOf("1")))
        assertFalse(Utils.timeBetweenTimes(time("00:00"), "10:00", "00:00", mutableSetOf("1")))
        assertTrue(Utils.timeBetweenTimes(datetime("01.10.2024 00:00"), "10:00", "00:00", mutableSetOf("1")))
        assertFalse(Utils.timeBetweenTimes(datetime("02.10.2024 00:00"), "10:00", "00:00", mutableSetOf("1")))
    }


    @Test
    fun testSets() {
        val set1 = mutableSetOf("a", "b", "c")
        val set2 = mutableSetOf("b", "c", "d")

        assertEquals(mutableSetOf("a", "b", "c", "d"), set1 + set2)
        assertEquals(mutableSetOf("a", "b", "c", "d"), set1.union(set2))
    }
}