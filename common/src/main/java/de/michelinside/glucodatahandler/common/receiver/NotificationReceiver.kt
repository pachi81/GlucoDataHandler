package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils

class NotificationReceiver : NotificationListenerService(), NamedReceiver {
    private val LOG_ID = "GDH.NotificationReceiver"
    private var parsedTextViews = mutableListOf<String>()
    private var lastDexcomForegroundTime = 0L
    private var lastDexcomValueTime = 0L
    private var updateOnlyChangedValue = false
    private var lastValue = Float.NaN

    override fun getName(): String {
        return LOG_ID
    }

    private fun validGlucoseNotification(sbn: StatusBarNotification, sharedPref: SharedPreferences): Boolean {
        if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP, "")) {
            Log.d(LOG_ID, "New notification from ${sbn.packageName} - posted: ${Utils.getUiTimeStamp(sbn.postTime)} (${sbn.postTime}) - when ${Utils.getUiTimeStamp(sbn.notification.`when`)} (${sbn.notification.`when`})")
            updateOnlyChangedValue = false
            var minDiff = 50
            val diffTime = (sbn.postTime - ReceiveData.time)/1000 // in seconds
            if(PackageUtils.isDexcomApp(sbn.packageName)) {
                /* special Dexcom handling (only for G7?)
                   - value notification is updated quite often and all must be ignored, until the "Dexcom app is running" notification is received
                   - this can be detected using the title of the notification -> null: value -> not null: foreground
                   - value notifications with an update of 30s must be ignored
                   - value notification with an update of 0s should be used, even with 50s diff as after a long disconnect, a new value can come within this time frame
                   - foreground notifications will normally updated direct before value notification (less 1s diff), but can also update in the mean time, for example during connection lost (keep 250s diff)
                 */
                val extras = sbn.notification?.extras
                val title = extras?.getCharSequence("android.title")?.toString()
                if(title != null) {
                    Log.d(LOG_ID, "Dexcom foreground notification updated at ${Utils.getUiTimeStamp(sbn.postTime)} -> ignore and wait for next value notification")
                    lastDexcomForegroundTime = sbn.postTime
                    return false
                } else {
                    val diffForegroundTime = (sbn.postTime - lastDexcomForegroundTime)/1000 // in seconds
                    val diffValueTime = (sbn.postTime - lastDexcomValueTime)/1000 // in seconds
                    lastDexcomValueTime = sbn.postTime
                    Log.d(LOG_ID, "Dexcom value notification updated at ${Utils.getUiTimeStamp(sbn.postTime)} - diff foreground: $diffForegroundTime, diff value notify: $diffValueTime, diff recv value: $diffTime ")

                    if(diffValueTime > 0 && diffValueTime.mod(30) == 0) {
                        Log.d(LOG_ID, "Ignoring Dexcom value notification with fix 30s update")
                        return false
                    }
                    if(lastDexcomForegroundTime == 0L || diffForegroundTime >= 600) {   // no foreground notification
                        Log.d(LOG_ID, "Dexcom foreground notification is disabled!")
                        if(diffValueTime == 0L) {
                            Log.d(LOG_ID, "Check for new value for 0s update")
                            updateOnlyChangedValue = diffTime < 250
                        } else {
                            Log.d(LOG_ID, "Ignore new value notification as foreground notification is disabled")
                            return false
                        }
                    } else if(diffForegroundTime <= 3) {   // new value after update of foreground notification
                        if(diffValueTime == 0L) {
                            Log.d(LOG_ID, "Check for new value for 0s update")
                            updateOnlyChangedValue = diffTime < 250
                        } else {
                            Log.d(LOG_ID, "New value after foreground notification -> check for 5 min interval")
                            minDiff = 250  // ignore other updates of the foreground notification -> wait for the next one for value (~300s)
                        }
                    } else {
                        Log.d(LOG_ID, "Ignoring Dexcom value notification -> wait for foreground notification")
                        return false
                    }
                }

            }
            if(diffTime < minDiff) {
                Log.d(LOG_ID, "Ignoring notification out of interval - diff: $diffTime < $minDiff")
                return false
            }
            return true
        }
        return false
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        if (isRegistered()) {
            statusBarNotification?.let { sbn ->
                val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                if (validGlucoseNotification(sbn, sharedPref)) {
                    val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX, "(\\d*\\.?\\d+)")!!.toRegex()
                    Log.i(LOG_ID, "using regex $regex")
                    val value = parseValueFromNotification(sbn, false, regex)
                    if(!value.isNaN()) {
                        if(updateOnlyChangedValue && value == lastValue) {
                            Log.d(LOG_ID, "Ignoring value notification with same value: $value")
                        } else {
                            lastValue = value
                            handleGlucoseValue(value, sbn.postTime)
                        }
                    } else if(parsedTextViews.size > 0) {
                        SourceStateData.setError(DataSource.NOTIFICATION, "Could not parse from $parsedTextViews")
                    } else {
                        SourceStateData.setError(DataSource.NOTIFICATION, "No text found for parsing!")
                    }
                }
                if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP, "")) {
                    Log.d(LOG_ID, "New IOB notification from ${sbn.packageName} - posted: ${Utils.getUiTimeStamp(sbn.postTime)} (${sbn.postTime}) - when ${Utils.getUiTimeStamp(sbn.notification.`when`)} (${sbn.notification.`when`})")
                    val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX, "IOB: (\\d*\\.?\\d+) U")!!.toRegex()
                    Log.i(LOG_ID, "using IOB regex $regex")
                    val value = parseValueFromNotification(sbn, true, regex)
                    if(!value.isNaN())
                        handleIobValue(value, sbn.postTime)
                    else if(parsedTextViews.size > 0) {
                        SourceStateData.setError(DataSource.NOTIFICATION, "Could not parse IOB from ${parsedTextViews.distinct()}")
                    } else {
                        SourceStateData.setError(DataSource.NOTIFICATION, "No text found for parsing IOB!")
                    }
                }
            }
        }
    }

    private fun isValidValue(value: Float, isIobCob: Boolean): Boolean {
        if(isIobCob)
            return !value.isNaN()
        // else check for valid glucose value
        return GlucoDataUtils.isGlucoseValid(value)
    }

    private fun parseValueFromNotification(sbn: StatusBarNotification, isIobCob: Boolean, regex: Regex): Float {
        parsedTextViews.clear()
        val extras = sbn.notification?.extras
        // Extract data from notification extras as needed
        val title = extras?.getCharSequence("android.title")?.toString()
        val text = extras?.getCharSequence("android.text")?.toString()
        Log.i(LOG_ID, "extracted title `$title` and text `$text`")

        if(!title.isNullOrEmpty()) {
            regex.find(title.replace(",", ".").trim())?.groupValues?.get(1)?.toFloatOrNull()?.let {
                if(isValidValue(it, isIobCob)) {
                    return it
                }
            }
            parsedTextViews.add(title)
        }
        if(!text.isNullOrEmpty()) {
            regex.find(text.replace(",", ".").trim())?.groupValues?.get(1)?.toFloatOrNull()?.let {
                if(isValidValue(it, isIobCob)) {
                    return it
                }
            }
            parsedTextViews.add(text)
        }

        // Try processing different RemoteViews
        val glucoseValue = processRemoteViews(sbn.notification.contentView, isIobCob, regex)
        if(!glucoseValue.isNaN())
            return glucoseValue
        Log.i(LOG_ID, "Could not find value in content view - check big content view")
        return processRemoteViews(sbn.notification.bigContentView, isIobCob, regex)
    }

    private fun parseTextView(textView: TextView, isIobCob: Boolean, regex: Regex?): Float {
        try {
            Log.v(LOG_ID, "Processing TextView: ${textView.text} - isIobCob: $isIobCob")
            if(!textView.text.isNullOrEmpty()) {
                val text = textView.text.toString().replace(",", ".").trim()
                if(regex!=null) {
                    regex.find(text)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                        if(isValidValue(it, isIobCob)) {
                            Log.i(LOG_ID, "Found value: $it using regex")
                            return it
                        }
                    }
                } else {
                    text.toFloatOrNull()?.let {
                        if(isValidValue(it, isIobCob)) {
                            Log.i(LOG_ID, "Found value: $it")
                            return it
                        }
                    }
                }
                parsedTextViews.add(text)
            }
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error parsing TextView: ${e.message}")
        }
        return Float.NaN
    }

    private fun parseTextViews(textViews: ArrayList<TextView>, isIobCob: Boolean): Float {
        // Examine each TextView
        for (textView in textViews) {
            try {
                Log.v(LOG_ID, "Processing TextView: ${textView.text} - isIobCob: $isIobCob")
                if(!textView.text.isNullOrEmpty()) {
                    val value = parseTextView(textView, isIobCob, null)  // check everything without regex
                    if(!value.isNaN())
                        return value
                }
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error processing TextView: ${e.message}")
            }
        }
        return Float.NaN
    }

    private fun processRemoteViews(remoteViews: RemoteViews?, isIobCob: Boolean, regex: Regex): Float {
        if (remoteViews == null) return Float.NaN

        try {
            // Apply the RemoteViews to get the actual View hierarchy
            val context = applicationContext
            val applied = remoteViews.apply(context, null)
            val root = applied.rootView

            // Collect all TextViews
            val textViews = ArrayList<TextView>()
            val derivedTextViews = ArrayList<TextView>()
            var glucoseValue = findTextViews(root, textViews, derivedTextViews, isIobCob, regex)
            if(!glucoseValue.isNaN())
                return glucoseValue

            Log.d(LOG_ID, "Found ${textViews.size} text views and ${derivedTextViews.size} derived text views")

            glucoseValue = parseTextViews(textViews, isIobCob)
            if(glucoseValue.isNaN()) {
                glucoseValue = parseTextViews(derivedTextViews, isIobCob)
            }
            return glucoseValue

        } catch (e: Exception) {
            Log.e(LOG_ID, "Error processing RemoteViews: ${e.message}")
        }
        return Float.NaN
    }

    private fun findTextViews(view: View, textViews: MutableList<TextView>, derivedTextViews: MutableList<TextView>, isIobCob: Boolean, regex: Regex): Float {
        Log.v(LOG_ID, "findTextViews in view $view")
        when (view) {
            is TextView -> {
                if(view.javaClass.name == TextView::class.java.name) {
                    Log.d(LOG_ID, "Found TextView: $view: '${view.text}'")
                    val value = parseTextView(view, isIobCob, regex)
                    if(!value.isNaN())  // found
                        return value
                    textViews.add(view)
                } else {
                    Log.d(LOG_ID, "Found derived TextView: ${view.javaClass.name} with value '${view.text}'")
                    val value = parseTextView(view, isIobCob, regex)
                    if(!value.isNaN())  // found
                        return value
                    derivedTextViews.add(view)
                }
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    val value = findTextViews(view.getChildAt(i), textViews, derivedTextViews, isIobCob, regex)
                    if(!value.isNaN())  // found
                        return value
                }
            }
        }
        return Float.NaN
    }

    private fun handleGlucoseValue(glucoseValue: Float, time: Long) {
        Log.d(LOG_ID, "Extracted glucose value: $glucoseValue from time: ${Utils.getUiTimeStamp(time)}")
        if (GlucoDataUtils.isGlucoseValid(glucoseValue)) {
            val glucoExtras = Bundle()
            glucoExtras.putLong(ReceiveData.TIME, time)
            if (GlucoDataUtils.isMmolValue(glucoseValue)) {
                glucoExtras.putInt(ReceiveData.MGDL, GlucoDataUtils.mmolToMg(glucoseValue).toInt())
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, glucoseValue)
            } else {
                glucoExtras.putInt(ReceiveData.MGDL, glucoseValue.toInt())
            }
            // getting the trendline is pretty difficult. NaN here just means no trendline
            glucoExtras.putFloat(ReceiveData.RATE, Float.NaN)
            glucoExtras.putInt(ReceiveData.ALARM, 0)
            ReceiveData.handleIntent(applicationContext, DataSource.NOTIFICATION, glucoExtras)
            SourceStateData.setState(DataSource.NOTIFICATION, SourceState.NONE)
        } else {
            SourceStateData.setError(
                DataSource.NOTIFICATION,
                "Invalid glucose value: $glucoseValue"
            )
        }
    }

    private fun handleIobValue(iobValue: Float, time: Long) {
        Log.d(LOG_ID, "Extracted iob value: $iobValue")
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.IOBCOB_TIME, time)
        glucoExtras.putFloat(ReceiveData.IOB, iobValue)

        ReceiveData.handleIobCob(applicationContext, DataSource.NOTIFICATION, glucoExtras)
        SourceStateData.setState(DataSource.NOTIFICATION, SourceState.NONE)
    }
}