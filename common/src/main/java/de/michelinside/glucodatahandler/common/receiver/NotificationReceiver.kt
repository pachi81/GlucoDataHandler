package de.michelinside.glucodatahandler.common.receiver

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import de.michelinside.glucodatahandler.common.utils.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R
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
    private var lastValueNotificationTime = 0L
    private var lastIobNotificationTime = 0L
    private var updateOnlyChangedValue = false
    private var lastValue = Float.NaN
    private var waitForAdditionalNotification: Thread? = null
    private var multiValueNotificationPackage: String? = null  // notification updates several times within 0s for new values
    private var onGoingNotificationPackage = ""
    private var lastValueChanged = false
    private var waitForAdditionalIobNotification: Thread? = null
    private val trendRegex = "([→↗↑↘↓⇈⇊])".toRegex()

    companion object {
        const val defaultGlucoseRegex = "(?:^|\\s)(\\d*\\.?\\d+)(?=\\s|\\p{S}|\$)" // old: "(?:^|\\s)(\\d*\\.?\\d+)(?=\\s|\$)"  "(?:^|\s)(\d{1,3}(?:\.\d{1,3})?)(?=\s|${'$'})"
        const val defaultIobRegex = "(\\d*\\.?\\d+)\\s?[Uu]"
        const val defaultCobRegex = "(\\d+)\\s?[Gg]"
    }

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
            } catch (_: InterruptedException) {
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

    private fun isIobWaitThreadActive(): Boolean {
        return waitForAdditionalIobNotification != null && waitForAdditionalIobNotification!!.isAlive
    }

    private fun startIobWaitThread(sbn: StatusBarNotification, waitTime: Long = 1000) {
        stopIobWaitThread()
        Log.i(LOG_ID, "Start IOB wait thread for $waitTime ms")
        waitForAdditionalIobNotification = Thread {
            try {
                Thread.sleep(waitTime)
                // no additional notification, parse this one
                Handler(applicationContext.mainLooper).post {
                    val diffTime = (sbn.postTime - ReceiveData.iobCobTime)/1000 // in seconds
                    Log.i(LOG_ID, "Handle wait IOB notification - diff: $diffTime")
                    parseIobCobValue(sbn)
                }
            } catch (_: InterruptedException) {
                Log.d(LOG_ID, "IOB wait thread interrupted")
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Exception in delay IOB thread: " + exc.toString())
            }
        }
        waitForAdditionalIobNotification!!.start()
    }

    private fun stopIobWaitThread() {
        Log.v(LOG_ID, "Stop IOB wait thread for $waitForAdditionalIobNotification")
        if (isIobWaitThreadActive() && waitForAdditionalIobNotification!!.id != Thread.currentThread().id )
        {
            Log.d(LOG_ID, "Stop running IOB wait thread!")
            waitForAdditionalIobNotification!!.interrupt()
            while(waitForAdditionalIobNotification!!.isAlive)
                Thread.sleep(1)
            Log.i(LOG_ID, "IOB wait thread stopped!")
            waitForAdditionalIobNotification = null
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
        if(packageName.lowercase().startsWith("com.medtronic."))  // MiniMed
            return true
        if(packageName.lowercase().startsWith("com.signos."))  // Signos (uses Dexcom Sensor)
            return true
        if(packageName.lowercase().startsWith("com.gluroo."))  // Gluroo
            return true

        if(onGoingNotificationPackage == packageName)
            return true
        // default (unknown app): also allow no ongoing notifications
        return false
    }

    // returns true, if the notification is only updated by a new value, so no special handing is needed
    private fun hasRegularNotification(packageName: String): Boolean {
        if(packageName.lowercase().startsWith("com.camdiab."))  // CamAPS FX
            return true
        if(packageName.lowercase().startsWith("com.signos."))  // Signos (uses Dexcom Sensor)
            return true
        return false
    }

    private fun hasBigContentViewData(packageName: String): Boolean {
        if(PackageUtils.receiverFilterContains(packageName))
            return false  // all these apps has the value in the normal view...
        return true
    }

    private fun has5MinuteInterval(packageName: String, sharedPref: SharedPreferences): Boolean {
        if(hasRegularNotification(packageName))
            return false
        if(PackageUtils.isDexcomApp(packageName))
            return true
        if(packageName.lowercase().startsWith("com.senseonics."))  // Eversense 365 CGM has 5 min interval
            return true
        if(packageName.lowercase().startsWith("com.medtronic."))  // MiniMed has 5 min interval
            return true
        if(packageName.lowercase().startsWith("com.microtechmd.cgms")) // Aidex has 5 minute interval
            return true
        // else
        return sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_5_MINUTE_INTERVAl, true)
    }


    // returns true, if there are several IOB notifications
    private fun hasIrregularIobNotification(packageName: String): Boolean {
        if(packageName.lowercase().startsWith("com.gluroo."))  // Gluroo
            return true
        return false
    }

    private fun isSpecialNotification(sbn: StatusBarNotification): Boolean {
        // some apps also provides foreground notifications, which should be ignored...
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
                    return true
                }
                Log.i(LOG_ID, "Dexcom foreground notification updated with title '${title}' and text '${text}' at ${Utils.getUiTimeStamp(sbn.postTime)} -> ignore and wait for next value notification")
                //lastDexcomForegroundTime = sbn.postTime
                return true
            }
        }
        if(sbn.packageName.lowercase().startsWith("com.signos.")) {
            /* special Signos handling
               - Signos has 2 notification, one foreground showing "Reconnecting ..." in text and the when time is and older timestamp
                  GDH.NotificationReceiver: New notification from com.signos.core - ongoing: true (flags: 106, prio: 0) - posted: 12:47:43 PM (1758991663788) - when 3:03:41 AM (1758956621129) - diff notify: 326, diff recv value: 327
                  GDH.NotificationReceiver: extracted title `Signos` and text `Reconnecting ...`
               - the value notification contains the value in the text element and the posted and when time are nearly the same
                   GDH.NotificationReceiver: New notification from com.signos.core - ongoing: true (flags: 98, prio: 0) - posted: 12:47:43 PM (1758991663822) - when 12:47:43 PM (1758991663821) - diff notify: 326, diff recv value: 327
                   GDH.NotificationReceiver: extracted title `Signos` and text `114 mg/dL`
             */
            val extras = sbn.notification?.extras
            val text = extras?.getCharSequence("android.text")?.toString()
            if(text != null && text.contains(" ...")) {
                Log.i(LOG_ID, "Signos notification with text '$text' - ignore it!")
                return true
            }
        }

        return false
    }

    private fun validIobCobNotification(sbn: StatusBarNotification, sharedPref: SharedPreferences): Boolean {
        if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP, "") &&
            (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_ENABLED, true) || sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_ENABLED, false))) {
            Log.i(LOG_ID, "New IOB notification from ${sbn.packageName} - ongoing: ${sbn.isOngoing} (flags: ${sbn.notification?.flags}, prio: ${sbn.notification?.priority}) - posted: ${Utils.getUiTimeStamp(sbn.postTime)} (${sbn.postTime}) - when ${Utils.getUiTimeStamp(sbn.notification.`when`)} (${sbn.notification.`when`})")
            if(sbn.isOngoing || !hasOngoingNotification(sbn.packageName)) {
                stopIobWaitThread()
                if(hasIrregularIobNotification(sbn.packageName)) {
                    startIobWaitThread(sbn)
                    return false
                }
                return true
            }
        }
        return false
    }

    private fun validGlucoseNotification(sbn: StatusBarNotification, sharedPref: SharedPreferences): Boolean {
        if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP, "")) {
            updateOnlyChangedValue = false
            val minDiff = 50
            val diffValueTime = (sbn.postTime - ReceiveData.time)/1000 // in seconds
            val diffNotifyTime = (sbn.postTime - lastValueNotificationTime)/1000 // in seconds
            Log.i(LOG_ID, "New notification from ${sbn.packageName} - ongoing: ${sbn.isOngoing} (flags: ${sbn.notification?.flags}, prio: ${sbn.notification?.priority}) - posted: ${Utils.getUiTimeStamp(sbn.postTime)} (${sbn.postTime}) - when ${Utils.getUiTimeStamp(sbn.notification.`when`)} (${sbn.notification.`when`}) - diff notify: $diffNotifyTime, diff recv value: $diffValueTime")
            if(diffNotifyTime == 0L)
                return false
            if(!sbn.isOngoing && hasOngoingNotification(sbn.packageName))
                return false
            if(isSpecialNotification(sbn))
                return false
            if(has5MinuteInterval(sbn.packageName, sharedPref)) {
                Log.i(LOG_ID, "Handle 5 min notification - lastValueChanged: $lastValueChanged - diff: $diffValueTime")
                stopWaitThread()
                /*
                    - time to ignore notifications: 60s if the last value was not a new one and 180s if the last value was a new one
                    - until 250s only update for changed values
                    - after 250s wait until 315s for a newer notification if the value has not changed - if there is no new one, use this one
                    - after 310s use the value
                 */
                val ignoreTime = if(lastValueChanged) 60 else 180
                if(diffValueTime<=ignoreTime) {
                    Log.i(LOG_ID, "Ignoring notification")
                    return false
                }
                if(diffValueTime<=250) {
                    Log.i(LOG_ID, "Check for changed value only")
                    updateOnlyChangedValue = true
                    return true
                }
                if(diffValueTime>=310)  // use this value
                    return true
                Log.i(LOG_ID, "Check for changed value and wait for newer notification until 315s")
                startWaitThread(sbn, (315-diffValueTime)*1000)   // wait for the case, the value is the same and there is no newer notification
                updateOnlyChangedValue=true
                return true
            }

            if(diffValueTime < minDiff) {
                Log.i(LOG_ID, "Ignoring notification out of interval - diff: $diffValueTime < $minDiff")
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
                val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
                if (validGlucoseNotification(sbn, sharedPref)) {
                    parseValue(sbn)
                }
                if (validIobCobNotification(sbn, sharedPref)) {
                    parseIobCobValue(sbn)
                }
            }
        }
    }

    private fun extractTrendValue(value: String?): Float {
        if(!value.isNullOrEmpty()) {
            trendRegex.find(value.trim())?.groupValues?.get(1)?.let { symbol ->
                val rate = GlucoDataUtils.getRateFromSymbol(symbol)
                if(!rate.isNaN()) {
                    Log.i(LOG_ID, "Found trend symbol '$symbol' with value: $rate")
                    return rate
                } else {
                    Log.w(LOG_ID, "Unknown trend symbol '$symbol'")
                }
            }
        }
        return Float.NaN
    }


    private fun parseTrendValue(sbn: StatusBarNotification): Float {
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString()
        val text = extras?.getCharSequence("android.text")?.toString()
        Log.d(LOG_ID, "Check title '$title' and text '$text' for trend value")
        val rate = extractTrendValue(title)
        if(!rate.isNaN())
            return rate
        return extractTrendValue(text)
    }

    private fun parseValue(sbn: StatusBarNotification) {
        val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
        val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX, defaultGlucoseRegex)!!.toRegex()
        Log.i(LOG_ID, "using regex $regex")
        val value = parseValueFromNotification(sbn, false, regex)
        if(!value.isNaN()) {
            lastValueNotificationTime = sbn.postTime
            val rate = parseTrendValue(sbn)
            handleGlucoseValue(value, rate, sbn)
        } else if(parsedTextViews.isNotEmpty()) {
            SourceStateData.setError(DataSource.NOTIFICATION, applicationContext.resources.getString(R.string.source_no_valid_value) + "\n${parsedTextViews.distinct()}")
        } else {
            SourceStateData.setError(DataSource.NOTIFICATION, applicationContext.resources.getString(R.string.missing_data))
        }
    }

    private fun parseIobCobValue(sbn: StatusBarNotification) {
        val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
        val iobValue = parseIobValue(sbn, sharedPref)
        val cobValue = parseCobValue(sbn, sharedPref)
        if(!iobValue.isNaN() || !cobValue.isNaN())
            handleIobCobValue(iobValue, cobValue, sbn.postTime)
        else if(parsedTextViews.isNotEmpty()) {
            SourceStateData.setError(DataSource.NOTIFICATION_IOB, applicationContext.resources.getString(R.string.invalid_iob_value) + "\n${parsedTextViews.distinct()}")
        } else {
            SourceStateData.setError(DataSource.NOTIFICATION_IOB, applicationContext.resources.getString(R.string.missing_data))
        }
    }

    private fun parseIobValue(sbn: StatusBarNotification, sharedPref: SharedPreferences): Float {
        if(sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_ENABLED, true)) {
            val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX, defaultIobRegex)!!.toRegex()
            Log.i(LOG_ID, "using IOB regex $regex")
            return parseValueFromNotification(sbn, true, regex)

        }
        return Float.NaN
    }

    private fun parseCobValue(sbn: StatusBarNotification, sharedPref: SharedPreferences): Float {
        if(sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_ENABLED, true)) {
            val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_APP_REGEX, defaultCobRegex)!!.toRegex()
            Log.i(LOG_ID, "using COB regex $regex")
            return parseValueFromNotification(sbn, true, regex)
        }
        return Float.NaN
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
        if (remoteViews == null) {
            Log.i(LOG_ID, "No RemoteViews found")
            return Float.NaN
        }

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
        val mgVal = if (GlucoDataUtils.isMmolValue(value)) GlucoDataUtils.mmolToMg(value).toInt() else value.toInt()
        if(mgVal >= Constants.GLUCOSE_MIN_VALUE && mgVal <= Constants.GLUCOSE_MAX_VALUE) {
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

    private fun handleGlucoseValue(glucoseValue: Float, rate: Float, sbn: StatusBarNotification) {
        Log.i(LOG_ID, "Extracted glucose value: $glucoseValue and rate $rate from time: ${Utils.getUiTimeStamp(sbn.postTime)}")
        if(updateOnlyChangedValue && glucoseValue == lastValue) {
            Log.i(LOG_ID, "Ignoring value notification with same value: $glucoseValue")
        } else if (validGlucoseValue(glucoseValue)) {
            stopWaitThread()
            lastValueChanged = lastValue != glucoseValue
            lastValue = glucoseValue
            val glucoExtras = Bundle()
            glucoExtras.putString(ReceiveData.SERIAL, PackageUtils.getAppName(applicationContext, sbn.packageName))
            glucoExtras.putLong(ReceiveData.TIME, sbn.postTime)
            if (GlucoDataUtils.isMmolValue(glucoseValue)) {
                glucoExtras.putInt(ReceiveData.MGDL, GlucoDataUtils.mmolToMg(glucoseValue).toInt())
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, glucoseValue)
            } else {
                glucoExtras.putInt(ReceiveData.MGDL, glucoseValue.toInt())
            }
            // getting the trendline is pretty difficult. NaN here just means no trendline
            glucoExtras.putFloat(ReceiveData.RATE, rate)
            glucoExtras.putInt(ReceiveData.ALARM, 0)
            ReceiveData.handleIntent(applicationContext, DataSource.NOTIFICATION, glucoExtras)
            SourceStateData.setState(DataSource.NOTIFICATION, SourceState.NONE)
            if(sbn.isOngoing && !hasOngoingNotification(sbn.packageName)) {
                Log.i(LOG_ID, "Valid glucose value received with ongoing notification from ${sbn.packageName}")
                onGoingNotificationPackage = sbn.packageName
            }
        } else {
            SourceStateData.setError(
                DataSource.NOTIFICATION,
                applicationContext.resources.getString(R.string.invalid_glucose_value, glucoseValue.toString())
            )
        }
    }

    private fun handleIobCobValue(iobValue: Float, cobValue: Float, time: Long) {
        val diffValueTime = (time - lastIobNotificationTime)/1000 //seconds
        if(iobValue.isNaN() && cobValue.isNaN())
            return
        if(iobValue == ReceiveData.iob && cobValue == ReceiveData.cob && diffValueTime <= 250) {
            Log.i(LOG_ID, "Ignoring IOB/COB notification with same value: IOB=$iobValue, COB=$cobValue - diff: $diffValueTime")
            return
        }
        Log.i(LOG_ID, "Extracted iob value: $iobValue, cob value: $cobValue from time: ${Utils.getUiTimeStamp(time)} - diff: $diffValueTime")
        lastIobNotificationTime = time
        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.IOBCOB_TIME, time)
        glucoExtras.putFloat(ReceiveData.IOB, iobValue)
        glucoExtras.putFloat(ReceiveData.COB, cobValue)

        ReceiveData.handleIobCob(applicationContext, DataSource.NOTIFICATION_IOB, glucoExtras)
        SourceStateData.setState(DataSource.NOTIFICATION_IOB, SourceState.NONE)
    }
}