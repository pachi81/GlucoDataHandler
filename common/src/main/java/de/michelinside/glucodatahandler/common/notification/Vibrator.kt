package de.michelinside.glucodatahandler.common.notification

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresApi
import de.michelinside.glucodatahandler.common.GlucoDataService

object Vibrator {
    private val LOG_ID = "GDH.Vibrator"
    private var vibratorInstance: Vibrator? = null

    @RequiresApi(Build.VERSION_CODES.S)
    private val vibratorManager: VibratorManager = GlucoDataService.context!!.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

    private val vibrator: Vibrator
        get() {
            if(vibratorInstance == null) {
                vibratorInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    GlucoDataService.context!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            }
            return vibratorInstance!!
        }

    fun hasAmplitudeControl(): Boolean {
        return vibrator.hasAmplitudeControl()
    }

    private fun getEffect(pattern: LongArray, repeat: Int = -1, amplitude: Int = -1): VibrationEffect {
        if(hasAmplitudeControl()) {
            val amplitudePatterns = IntArray(pattern.size)
            amplitudePatterns[0] = 0
            for(i in 1 until pattern.size) {
                if(i.mod(2) == 1)
                    amplitudePatterns[i] = minOf(amplitude, 255)
                else
                    amplitudePatterns[i] = 0
            }
            return VibrationEffect.createWaveform(pattern, amplitudePatterns, repeat)
        }
        return VibrationEffect.createWaveform(pattern, repeat)
    }

    fun vibrate(pattern: LongArray, repeat: Int = -1, amplitude: Int = -1): Int {
        cancel()
        val duration = if(repeat == -1) pattern.sum().toInt() else -1
        Log.d(LOG_ID, "Vibrate for $duration ms")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager.vibrate(CombinedVibration.createParallel(getEffect(pattern, repeat, amplitude)))
        } else {
            vibrator.vibrate(getEffect(pattern, repeat, amplitude))
        }
        return duration
    }

    fun cancel() {
        Log.d(LOG_ID, "Stop vibration")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager.cancel()
        } else {
            vibrator.cancel()
        }
    }
}