package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
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
import java.math.RoundingMode
import kotlin.math.abs

class NotificationReceiver : NotificationListenerService(), NamedReceiver {
    private val LOG_ID = "GDH.NotificationReceiver"
    private var parsedTextViews = mutableListOf<String>()
    private var lastDexcomForegroundTime = 0L
    private var lastValueNotificationTime = 0L
    private var updateOnlyChangedValue = false
    private var lastValue = Float.NaN
    private var waitForAdditionalNotification: Thread? = null
    private var multiValueNotificationPackage: String? = null  // notification updates several times within 0s for new values

    override fun getName(): String {
        return LOG_ID
    }

    private fun isWaitThreadActive(): Boolean {
        return waitForAdditionalNotification != null && waitForAdditionalNotification!!.isAlive
    }

    private fun startWaitThread(sbn: StatusBarNotification, waitTime: Long = 3000) {
        stopWaitThread()
        Log.i(LOG_ID, "Start wait thread for $waitTime ms")
        waitForAdditionalNotification = Thread {
            try {
                Thread.sleep(waitTime)
                // no additional notification, parse this one
                Handler(applicationContext.mainLooper).post {
                    val diffTime = (sbn.postTime - ReceiveData.time)/1000 // in seconds
                    Log.i(LOG_ID, "Handle wait value notification - diff: $diffTime")
                    if(diffTime > 50) {
                        if(multiValueNotificationPackage == null)
                            multiValueNotificationPackage = ""  // set to not receiving any additional notification for not trigger delay thread each time!
                        updateOnlyChangedValue = diffTime < 250
                        parseValue(sbn)
                    }
                }
            } catch (exc: InterruptedException) {
                Log.d(LOG_ID, "Wait thread interrupted")
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Exception in delay thread: " + exc.toString())
            }
        }
        waitForAdditionalNotification!!.start()
    }

    private fun stopWaitThread() {
        Log.v(LOG_ID, "Stop wait thread for $waitForAdditionalNotification")
        if (isWaitThreadActive() && waitForAdditionalNotification!!.id != Thread.currentThread().id )
        {
            Log.d(LOG_ID, "Stop running wait thread!")
            waitForAdditionalNotification!!.interrupt()
            while(waitForAdditionalNotification!!.isAlive)
                Thread.sleep(1)
            Log.i(LOG_ID, "Wait thread stopped!")
            waitForAdditionalNotification = null
        }
    }

    private fun hasOngoingNotification(packageName: String): Boolean {
        if(PackageUtils.isDexcomG7App(packageName)) // G7 foreground has disabled ongoing flag
            return false
        if(packageName.lowercase().startsWith("com.senseonics."))  // Eversense 365 CGM has no ongoing
            return false

        // apps supporting ongoing notifications
        if(PackageUtils.isDexcomApp(packageName))  // Dexcom G6 has ongoing
            return true
        if(packageName.lowercase().startsWith("com.camdiab."))  // CamAPS FX
            return true

        // default (unknown app): also allow no ongoing notifications
        return false
    }

    private fun hasBigContentViewData(packageName: String): Boolean {
        if(PackageUtils.receiverFilterContains(packageName))
            return false  // all these apps has the value in the normal view...
        return true
    }

    private fun validGlucoseNotification(sbn: StatusBarNotification, sharedPref: SharedPreferences): Boolean {
        if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP, "")) {
            updateOnlyChangedValue = false
            var minDiff = 50
            val diffTime = (sbn.postTime - ReceiveData.time)/1000 // in seconds
            Log.i(LOG_ID, "New notification from ${sbn.packageName} - ongoing: ${sbn.isOngoing} (flags: ${sbn.notification?.flags}, prio: ${sbn.notification?.priority}) - posted: ${Utils.getUiTimeStamp(sbn.postTime)} (${sbn.postTime}) - when ${Utils.getUiTimeStamp(sbn.notification.`when`)} (${sbn.notification.`when`}) - diff notify: ${(sbn.postTime - lastValueNotificationTime)/1000}, diff recv value: $diffTime")
            if(!sbn.isOngoing && hasOngoingNotification(sbn.packageName))
                return false
            if(PackageUtils.isDexcomApp(sbn.packageName)) {
                /* special Dexcom handling (only for G7!)
                   - value notification is updated quite often and all must be ignored, until the "Dexcom app is running" notification is received
                   - this can be detected using the title of the notification -> null: value -> not null: foreground
                   - value notifications with an update of 30s must be ignored
                   - value notification with an update of 0s should be used, even with 50s diff as after a long disconnect, a new value can come within this time frame
                   - foreground notifications will normally updated direct before value notification (less 1s diff), but can also update in the mean time, for example during connection lost (keep 250s diff)
                 */
                val extras = sbn.notification?.extras
                val title: String? = if(!sbn.isOngoing && PackageUtils.isDexcomG7App(sbn.packageName)) extras?.getCharSequence("android.title")?.toString() else null
                if(title != null) {
                    val text = extras?.getCharSequence("android.text")?.toString()
                    if(text != null) {
                        Log.i(LOG_ID, "Dexcom notification with title '$title' and text '$text' - ignore it!")
                        return false
                    }
                    Log.i(LOG_ID, "Dexcom foreground notification updated with title '${title}' and text '${text}' at ${Utils.getUiTimeStamp(sbn.postTime)} -> ignore and wait for next value notification")
                    lastDexcomForegroundTime = sbn.postTime
                    return false
                } else {
                    if(!sbn.isOngoing) {
                        Log.i(LOG_ID, "Ignoring Dexcom notification as it is not ongoing and no foreground")
                        return false // ignore this notification as it is not ongoing and no foreground
                    }
                    val diffForegroundTime = (sbn.postTime - lastDexcomForegroundTime)/1000 // in seconds
                    val diffValueTime = (sbn.postTime - lastValueNotificationTime)/1000 // in seconds
                    lastValueNotificationTime = sbn.postTime
                    Log.i(LOG_ID, "Dexcom value notification updated at ${Utils.getUiTimeStamp(sbn.postTime)} - diff foreground: $diffForegroundTime, diff value notify: $diffValueTime, diff recv value: $diffTime")

                    if(diffValueTime > 0 && diffValueTime == 30L) {
                        Log.i(LOG_ID, "Ignoring Dexcom value notification with fix 30s update")
                        return false
                    }
                    if(lastDexcomForegroundTime == 0L || diffForegroundTime > 900) {   // no foreground notification
                        Log.w(LOG_ID, "Dexcom foreground notification is disabled!")
                        lastDexcomForegroundTime = 0L
                        if(diffValueTime == 0L || isWaitThreadActive()) {
                            stopWaitThread()
                            Log.i(LOG_ID, "Check for new value for 0s update")
                            updateOnlyChangedValue = diffTime < 250
                        } else if(diffTime in 297..303) {
                            startWaitThread(sbn, 10000)  // wait for a new notification with a value update otherwise use this one
                            return false
                        } else {
                            Log.i(LOG_ID, "New value without foreground notification -> check for 5 min interval")
                            minDiff = 250
                        }
                    } else if(diffForegroundTime <= 3) {   // new value after update of foreground notification
                        if(diffValueTime == 0L) {
                            Log.i(LOG_ID, "Check for new value for 0s update")
                            updateOnlyChangedValue = diffTime < 250
                        } else {
                            Log.i(LOG_ID, "New value after foreground notification -> check for 5 min interval")
                            minDiff = 250  // ignore other updates of the foreground notification -> wait for the next one for value (~300s)
                        }
                    } else {
                        Log.i(LOG_ID, "Ignoring Dexcom value notification -> wait for foreground notification")
                        return false
                    }
                }
            } else if(sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_5_MINUTE_INTERVAl, true)) {
                val diffValueTime = (sbn.postTime - lastValueNotificationTime)/1000 // in seconds
                lastValueNotificationTime = sbn.postTime
                if(diffValueTime <= 1L || isWaitThreadActive()) {
                    stopWaitThread()
                    Log.i(LOG_ID, "Check for new value for ${diffValueTime}s update")
                    if(diffValueTime == 0L && (multiValueNotificationPackage.isNullOrEmpty() || multiValueNotificationPackage != sbn.packageName)) {
                        Log.i(LOG_ID, "Multi value package discovered: ${sbn.packageName}")
                        multiValueNotificationPackage = sbn.packageName
                    }
                    updateOnlyChangedValue = diffTime < 250
                } else if(multiValueNotificationPackage == sbn.packageName) {
                    Log.i(LOG_ID, "Check for changed value or wait for multi value package with 0s interval")
                    minDiff = 250  // ignore values not in 5 minute interval
                    updateOnlyChangedValue = true  // check for new values only
                } else if(diffTime >= 250) {
                    if(multiValueNotificationPackage != "") {
                        startWaitThread(sbn, 20000)  // wait for a new notification with a value update otherwise use this one
                        return false
                    }
                } else {
                    Log.i(LOG_ID, "New value notification -> check for changed value within 5 min interval")
                    updateOnlyChangedValue = true
                }
            } else {
                lastValueNotificationTime = sbn.postTime
            }

            if(diffTime < minDiff) {
                Log.i(LOG_ID, "Ignoring notification out of interval - diff: $diffTime < $minDiff")
                return false
            }
            return true
        }
        return false
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        if (isRegistered()) {
            statusBarNotification?.let { sbn ->
                Log.d(LOG_ID, "New notification posted from ${sbn.packageName} - ongoing: ${sbn.isOngoing} (flags: ${sbn.notification?.flags}, prio: ${sbn.notification?.priority}) - posted: ${Utils.getUiTimeStamp(sbn.postTime)} (${sbn.postTime}) - when ${Utils.getUiTimeStamp(sbn.notification.`when`)} (${sbn.notification.`when`})")
                val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                if (validGlucoseNotification(sbn, sharedPref)) {
                    parseValue(sbn)
                }
                if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP, "")) {
                    Log.i(LOG_ID, "New IOB notification from ${sbn.packageName} - posted: ${Utils.getUiTimeStamp(sbn.postTime)} (${sbn.postTime}) - when ${Utils.getUiTimeStamp(sbn.notification.`when`)} (${sbn.notification.`when`})")
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

    private fun parseValue(sbn: StatusBarNotification) {
        val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX, "(\\d*\\.?\\d+)")!!.toRegex()
        Log.i(LOG_ID, "using regex $regex")
        val value = parseValueFromNotification(sbn, false, regex)
        if(!value.isNaN()) {
            handleGlucoseValue(value, sbn.postTime)
        } else if(parsedTextViews.size > 0) {
            SourceStateData.setError(DataSource.NOTIFICATION, "Could not parse from $parsedTextViews")
        } else {
            SourceStateData.setError(DataSource.NOTIFICATION, "No text found for parsing!")
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
        if(!glucoseValue.isNaN() || !hasBigContentViewData(sbn.packageName))
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
                            Log.i(LOG_ID, "Found value: $it using regex in $textView")
                            return it
                        }
                    }
                } else {
                    text.toFloatOrNull()?.let {
                        if(isValidValue(it, isIobCob)) {
                            Log.i(LOG_ID, "Found value: $it in $textView")
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
            if(!glucoseValue.isNaN() || isIobCob)  // IOB only with regex
                return glucoseValue

            Log.i(LOG_ID, "Found ${textViews.size} text views: $textViews and ${derivedTextViews.size} derived text views: $derivedTextViews")

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

    private fun validGlucoseValue(value: Float): Boolean {
        if(updateOnlyChangedValue && value == lastValue) {
            Log.i(LOG_ID, "Ignoring value notification with same value: $value")
            return false
        }
        val mgVal = if (GlucoDataUtils.isMmolValue(value)) GlucoDataUtils.mmolToMg(value).toInt() else value.toInt()
        if(mgVal >= Constants.GLUCOSE_MIN_VALUE && mgVal <= Constants.GLUCOSE_MAX_NOTIFICATION_VALUE) {
            if(ReceiveData.getElapsedTimeMinute(RoundingMode.HALF_UP) > 0) {
                val delta = (mgVal - ReceiveData.rawValue).toFloat() / ReceiveData.getElapsedTimeMinute(RoundingMode.HALF_UP)
                val maxDelta = if(updateOnlyChangedValue) 10F else 40F
                if(abs(delta) > maxDelta) {
                    Log.i(LOG_ID, "Ignoring value notification with delta: $delta (max: $maxDelta)")
                    return false
                }
            }
            return GlucoDataUtils.isGlucoseValid(value)
        }
        return false
    }

    private fun handleGlucoseValue(glucoseValue: Float, time: Long) {
        Log.i(LOG_ID, "Extracted glucose value: $glucoseValue from time: ${Utils.getUiTimeStamp(time)}")
        if (validGlucoseValue(glucoseValue)) {
            lastValue = glucoseValue
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
        Log.i(LOG_ID, "Extracted iob value: $iobValue from time: ${Utils.getUiTimeStamp(time)}")
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.IOBCOB_TIME, time)
        glucoExtras.putFloat(ReceiveData.IOB, iobValue)

        ReceiveData.handleIobCob(applicationContext, DataSource.NOTIFICATION, glucoExtras)
        SourceStateData.setState(DataSource.NOTIFICATION, SourceState.NONE)
    }
}