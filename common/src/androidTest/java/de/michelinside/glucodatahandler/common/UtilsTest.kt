package de.michelinside.glucodatahandler.common

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext


    @Test
    fun testBundleToByteArray() {
        println("Start Test")
        val bundle = Bundle()
        bundle.putString("key1", "value1")
        bundle.putInt("key2", 42)
        bundle.putBoolean("key3", true)
        bundle.putFloat("key4", 3.14f)
        val subBundle = Bundle()
        subBundle.putString("subKey1", "subValue1")
        subBundle.putInt("subKey2", 123)
        subBundle.putBoolean("subKey3", false)
        subBundle.putFloat("subKey4", 2.71f)
        bundle.putBundle("key5", subBundle)
        println(Utils.dumpBundle(bundle))
        val byteArray = Utils.bundleToBytes(bundle)
        val bundle2 = Utils.bytesToBundle(byteArray!!)
        println(Utils.dumpBundle(bundle2))
        assertEquals(bundle.getString("key1"), bundle2?.getString("key1"))
        assertEquals(bundle.getInt("key2"), bundle2?.getInt("key2"))
        assertEquals(bundle.getBoolean("key3"), bundle2?.getBoolean("key3"))
        assertEquals(bundle.getFloat("key4"), bundle2?.getFloat("key4"))
        assertEquals(bundle.getBundle("key5")?.getString("subKey1"), bundle2?.getBundle("key5")?.getString("subKey1"))
        assertEquals(bundle.getBundle("key5")?.getInt("subKey2"), bundle2?.getBundle("key5")?.getInt("subKey2"))
        assertEquals(bundle.getBundle("key5")?.getBoolean("subKey3"), bundle2?.getBundle("key5")?.getBoolean("subKey3"))
        assertEquals(bundle.getBundle("key5")?.getFloat("subKey4"), bundle2?.getBundle("key5")?.getFloat("subKey4"))

        println("End Test")
    }
}