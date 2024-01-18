package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.io.Closeable

class WakeLockHelper(val context: Context) : Closeable {
    private val WAKE_LOCK_TIMEOUT = 10000L // 10 seconds

    private val LOG_ID = "GDH.Utils.WakeLockHelper"
    private var wakeLock: PowerManager.WakeLock? = null
    init {
        Log.v(LOG_ID, "init called")
        wakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlucoDataHandler::WakeLockHelperTag").apply {
                acquire(WAKE_LOCK_TIMEOUT)
            }
        }
        Log.d(LOG_ID, "wakelock acquired: " + active())
    }

    fun active(): Boolean {
        if (wakeLock != null) {
            return wakeLock!!.isHeld
        }
        return false
    }

    fun release() {
        Log.v(LOG_ID, "release called - active: " + active())
        if(active()) {
            Log.d(LOG_ID, "wakelock release")
            wakeLock?.release()
            wakeLock = null
        }
    }
    override fun close() {
        Log.v(LOG_ID, "close called")
        release()
    }
}