package de.michelinside.glucodatahandler.common.notification

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import de.michelinside.glucodatahandler.common.GlucoDataService

object Vibrator {
    private val LOG_ID = "GDH.Vibrator"
    private var vibratorInstance: Vibrator? = null
    val vibrator: Vibrator
        get() {
            if(vibratorInstance == null) {
                vibratorInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = GlucoDataService.context!!.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    manager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    GlucoDataService.context!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            }
            return vibratorInstance!!
        }

    fun vibrate(pattern: LongArray, repeat: Int = -1, amplitude: Int = -1): Int {
        cancel()
        val duration = if(repeat == -1) pattern.sum().toInt() else -1
        val effect = if (vibrator.hasAmplitudeControl() && amplitude > 0) {
            Log.d(LOG_ID, "Vibrate for $duration ms with amplitude $amplitude")
            val amplitudePatterns = IntArray(pattern.size)
            amplitudePatterns[0] = 0
            for(i in 1 until pattern.size) {
                if(i.mod(2) == 1)
                    amplitudePatterns[i] = minOf(amplitude, 255)
                else
                    amplitudePatterns[i] = 0
            }
            VibrationEffect.createWaveform(pattern, amplitudePatterns, repeat)
        } else {
            Log.d(LOG_ID, "Vibrate for $duration ms")
            VibrationEffect.createWaveform(pattern, repeat)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Use new API for Android 12+
                val vibratorManager = GlucoDataService.context!!.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.vibrate(CombinedVibration.createParallel(effect))
            } else {
                vibrator.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.e(LOG_ID, "Vibration failed", e)
        }

        return duration
    }

    fun cancel() {
        Log.d(LOG_ID, "Stop vibration")
        vibrator.cancel()
    }
}