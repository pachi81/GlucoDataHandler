package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences

abstract class BackgroundTask {
    abstract fun getIntervalMinute() : Long
    abstract fun execute(context: Context)
    abstract fun active(elapsetTimeMinute: Long): Boolean
    open fun getDelayMs(): Long = 0L
    open fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context) : Boolean = false
    open fun hasIobCobSupport() : Boolean = false
    open fun interrupt() {}
}
