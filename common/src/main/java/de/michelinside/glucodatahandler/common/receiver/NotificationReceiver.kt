package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
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
import de.michelinside.glucodatahandler.common.utils.Utils

class NotificationReceiver : NotificationListenerService(), NamedReceiver {
    private val LOG_ID = "GDH.NotificationReceiver"
    private var parsedTextViews = mutableListOf<String>()

    override fun getName(): String {
        return LOG_ID
    }


    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        if (isRegistered()) {
            statusBarNotification?.let { sbn ->
                val sharedPref = applicationContext.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                Log.v(LOG_ID, "handling notification for ${sbn.packageName}")
                if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP, "")) {
                    val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX, "(\\d*\\.?\\d+)")!!.toRegex()
                    Log.d(LOG_ID, "using regex $regex")
                    val value = parseValueFromNotification(sbn, false, regex)
                    if(!value.isNaN())
                        handleGlucoseValue(value, sbn.postTime)
                    else if(parsedTextViews.size > 0) {
                        SourceStateData.setError(DataSource.NOTIFICATION, "Could not parse from $parsedTextViews")
                    } else {
                        SourceStateData.setError(DataSource.NOTIFICATION, "No text found for parsing!")
                    }
                }
                if (sbn.packageName == sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP, "")) {
                    val regex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX, "IOB: (\\d*\\.?\\d+) U")!!.toRegex()
                    Log.d(LOG_ID, "using regex $regex")
                    val value = parseValueFromNotification(sbn, true, regex)
                    if(!value.isNaN())
                        handleIobValue(value, sbn.postTime)
                    else if(parsedTextViews.size > 0) {
                        SourceStateData.setError(DataSource.NOTIFICATION, "Could not parse IOB from $parsedTextViews")
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

    private fun parseTextViews(textViews: ArrayList<TextView>, isIobCob: Boolean, regex: Regex): Pair<Int, Float> {
        var matches = 0
        var value = Float.NaN

        // Examine each TextView
        for (textView in textViews) {
            try {
                Log.v(LOG_ID, "Processing TextView: ${textView.text} - isIobCob: $isIobCob")
                var found = false
                if(!textView.text.isNullOrEmpty()) {
                    val text = textView.text.toString().replace(",", ".").trim()
                    regex.find(text)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                        if(isValidValue(it, isIobCob)) {
                            value = it
                            matches++
                            found = true
                            Log.d(LOG_ID, "Found value: $value using regex")
                        }
                    }
                    if(!found) {
                        text.toString()?.toFloatOrNull()?.let {
                            if(isValidValue(it, isIobCob)) {
                                value = it
                                matches++
                                found = true
                                Log.d(LOG_ID, "Found value: $value")
                            }
                        }
                    }
                    if(!found) {
                        parsedTextViews.add(text)
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error processing TextView: ${e.message}")
            }
        }
        return Pair(matches, value)
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
            findTextViews(root, textViews, derivedTextViews)

            Log.d(LOG_ID, "Found ${textViews.size} text views and ${derivedTextViews.size} derived text views")

            var (matches, glucoseValue) = parseTextViews(textViews, isIobCob, regex)
            if(matches==0) {
                parseTextViews(derivedTextViews, isIobCob, regex).apply {
                    matches = first
                    glucoseValue = second
                }
            }

            when {
                matches == 0 -> {
                    Log.d(LOG_ID, "Did not find any matches")
                    return Float.NaN
                }

                matches > 1 -> {
                    Log.e(LOG_ID, "Found too many matches: $matches")
                    return Float.NaN
                }

                else -> {
                    return glucoseValue
                }
            }

        } catch (e: Exception) {
            Log.e(LOG_ID, "Error processing RemoteViews: ${e.message}")
        }
        return Float.NaN
    }

    private fun findTextViews(view: View, textViews: MutableList<TextView>, derivedTextViews: MutableList<TextView>) {
        Log.v(LOG_ID, "findTextViews in view $view")
        when (view) {
            is TextView -> {
                if(view.javaClass.name == TextView::class.java.name) {
                    Log.d(LOG_ID, "Found TextView: $view: ${view.text}")
                    textViews.add(view)
                } else {
                    Log.d(LOG_ID, "Found derived TextView: ${view.javaClass.name} with value ${view.text}")
                    derivedTextViews.add(view)
                }
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    findTextViews(view.getChildAt(i), textViews, derivedTextViews)
                }
            }
        }
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