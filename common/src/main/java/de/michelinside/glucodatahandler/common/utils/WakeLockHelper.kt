package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.os.PowerManager
import de.michelinside.glucodatahandler.common.utils.Log
import java.io.Closeable

class WakeLockHelper(val context: Context) : Closeable {
    private val WAKE_LOCK_TIMEOUT = 10000L // 10 seconds
    private val LOG_ID = "GDH.Utils.WakeLockHelper"

    companion object {
        private var wakeLock: PowerManager.WakeLock? = null
        private var wackLockCount = 0
        private val lock = Object()
    }

    init {
        try {
            synchronized(lock) {
                Log.d(LOG_ID, "init called - count: $wackLockCount")
                wackLockCount++
                if(wackLockCount == 1 && wakeLock == null) {
                    wakeLock =
                    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlucoDataHandler::WakeLockHelperTag").apply {
                            acquire(WAKE_LOCK_TIMEOUT)
                        }
                    }
                    Log.i(LOG_ID, "wakelock acquired: " + active())
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.toString())
        }
    }

    private fun active(): Boolean {
        if (wakeLock != null) {
            return wakeLock!!.isHeld
        }
        return false
    }

    private fun release() {
        synchronized(lock) {
            Log.d(LOG_ID, "release called - active: " + active() + " count: $wackLockCount")
            wackLockCount--
            if(wackLockCount == 0 && active()) {
                Log.i(LOG_ID, "wakelock release")
                wakeLock?.release()
                wakeLock = null
            }
        }
    }

    override fun close() {
        try {
            Log.v(LOG_ID, "close called")
            release()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.toString())
        }
    }
}