package de.michelinside.glucodatahandler.common

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ReceiveDataTest {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testWithOutCustomGlucoseMg() {
        // Context of the app under test.
        assertFalse(ReceiveData.handleIntent(appContext, DataSource.NONE, null, false))
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.TIME, ReceiveData.time + 60000)
        glucoExtras.putInt(ReceiveData.MGDL,180)
        glucoExtras.putFloat(ReceiveData.RATE, 1F)
        glucoExtras.putInt(ReceiveData.ALARM, 0)
        assertTrue(ReceiveData.handleIntent(appContext, DataSource.NONE, glucoExtras))
        assertEquals(180, ReceiveData.rawValue)
        assertEquals(180F, ReceiveData.glucose)
        assertEquals(1F, ReceiveData.rate)
        assertFalse(ReceiveData.isMmol)
    }

    @Test
    fun testWithOutCustomGlucoseMmol() {
        // Context of the app under test.
        ReceiveData.changeIsMmol(true, appContext)
        assertTrue(ReceiveData.isMmol)
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.TIME, ReceiveData.time + 60000)
        glucoExtras.putInt(ReceiveData.MGDL,180)
        glucoExtras.putFloat(ReceiveData.RATE, -1F)
        glucoExtras.putInt(ReceiveData.ALARM, 0)
        assertTrue(ReceiveData.handleIntent(appContext, DataSource.NONE, glucoExtras))
        assertEquals(180, ReceiveData.rawValue)
        assertEquals(10F, ReceiveData.glucose)
        assertEquals(-1F, ReceiveData.rate)
        assertTrue(ReceiveData.isMmol)
    }

    @Test
    fun testChangeToMmol() {
        // Context of the app under test.
        ReceiveData.changeIsMmol(false, appContext)
        ReceiveData.time = 0L // force first value handling
        assertFalse(ReceiveData.isMmol)
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.TIME, ReceiveData.time + 60000)
        glucoExtras.putInt(ReceiveData.MGDL,180)
        glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, 10.1F)
        glucoExtras.putFloat(ReceiveData.RATE, -2F)
        glucoExtras.putInt(ReceiveData.ALARM, 0)
        assertTrue(ReceiveData.handleIntent(appContext, DataSource.NONE, glucoExtras))
        assertEquals(180, ReceiveData.rawValue)
        assertEquals(10.1F, ReceiveData.glucose)
        assertEquals(-2F, ReceiveData.rate)
        assertTrue(ReceiveData.isMmol)
    }


    @Test
    fun testMmolCalculation() {
        ReceiveData.changeIsMmol(true, appContext)
        if(ReceiveData.time == 0L)
            ReceiveData.time = 1L   // prevent changing unit!
        ReceiveData.glucose = 0F    // set to 0 to check, if it gets overwritten
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.TIME, ReceiveData.time + 60000)
        glucoExtras.putInt(ReceiveData.MGDL,180)
        glucoExtras.putFloat(ReceiveData.RATE, -2F)
        assertTrue(ReceiveData.handleIntent(appContext, DataSource.NONE, glucoExtras))
        assertEquals(180, ReceiveData.rawValue)
        assertEquals(GlucoDataUtils.mgToMmol(180F), ReceiveData.glucose)
        assertEquals(-2F, ReceiveData.rate)
        assertTrue(ReceiveData.isMmol)
    }


    @Test
    fun testReceiveInternalGlucoseAlarm() {
        // Context of the app under test.
        ReceiveData.changeIsMmol(false, appContext)
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.TIME, ReceiveData.time + 60000)
        glucoExtras.putInt(ReceiveData.MGDL,280)
        glucoExtras.putFloat(ReceiveData.RATE, 1F)
        glucoExtras.putInt(ReceiveData.ALARM, 14)  // very high + force
        assertTrue(ReceiveData.handleIntent(appContext, DataSource.NONE, glucoExtras, true))
        assertEquals(280, ReceiveData.rawValue)
        assertEquals(280F, ReceiveData.glucose)
        assertEquals(1F, ReceiveData.rate)
        assertEquals(14, ReceiveData.alarm)
        assertTrue(ReceiveData.forceGlucoseAlarm)
        assertTrue(ReceiveData.forceAlarm)
    }
}