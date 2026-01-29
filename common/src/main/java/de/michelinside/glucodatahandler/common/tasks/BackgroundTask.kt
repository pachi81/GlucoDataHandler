package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import de.michelinside.glucodatahandler.common.ReceiveData

abstract class BackgroundTask {
    abstract fun getIntervalMinute() : Long
    abstract fun execute(context: Context)
    abstract fun active(elapsetTimeMinute: Long): Boolean
    open fun getDelayMs(): Long = 0L
    open fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context) : Boolean = false
    open fun hasIobCobSupport() : Boolean = false
    open fun getLastIobCobTime(): Long = if(hasIobCobSupport()) ReceiveData.getElapsedIobCobTimeMinute() else 0L
    open fun interrupt() {}
    open fun forceExecution(): Boolean = false
}
