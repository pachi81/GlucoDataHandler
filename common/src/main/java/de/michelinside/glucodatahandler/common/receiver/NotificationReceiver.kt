package de.michelinside.glucodatahandler.common.receiver

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils

class NotificationReceiver : NotificationListenerService(), NamedReceiver {
    private val LOG_ID = "GDH.NotificationReceiver"

    override fun getName(): String {
        return LOG_ID
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        if (isRegistered()) {
            statusBarNotification?.let { sbn ->
                if (sbn.packageName == "com.dexcom.g7") {
                    Log.i(LOG_ID, "handling dexcom g7 notification")
                    val notification = sbn.notification
                    val contentView = notification.contentView
                    val bigContentView = notification.bigContentView

                    // Try processing different RemoteViews
                    if (processRemoteViews(contentView)) return
                    if (processRemoteViews(bigContentView)) return

                    // If we get here, we couldn't find the glucose value
                    Log.d(LOG_ID, "Could not find glucose value in notification")
                }
                if (sbn.packageName == "com.insulet.myblue.pdm") {
                    Log.i(LOG_ID, "handling omnipod 5 notification")
                    // omnipod 5 app notificaiton
                    sbn.notification.extras.getString("android.title")?.let {
                        Log.i(LOG_ID, "extracted title `$it`")
                        // format like `Automated Mode (IOB: 6.35 U)`
                        // parses out the number and puts it into capture group 1, if it exists
                        val regex = """Automated Mode \(IOB: (\d*\.?\d+) U\)""".toRegex()

                       regex.find(it)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                           handleIobValue(it)
                       }
                    }
                }
            }
        }
    }


    // chatgpted off of xDrip+ (under GPL)
    // https://github.com/NightscoutFoundation/xDrip/blob/eb920d2258300966db22f1051f29468d386c0929/app/src/main/java/com/eveningoutpost/dexdrip/services/UiBasedCollector.java#L294
    private fun processRemoteViews(remoteViews: RemoteViews?): Boolean {
        if (remoteViews == null) return false

        try {
            // Apply the RemoteViews to get the actual View hierarchy
            val context = applicationContext
            val applied = remoteViews.apply(context, null)
            val root = applied.rootView as ViewGroup

            // Collect all TextViews
            val textViews = ArrayList<TextView>()
            findTextViews(root, textViews)

            Log.d(LOG_ID, "Found ${textViews.size} text views")

            var matches = 0
            var glucoseValue = 0.0f

            // Examine each TextView
            for (textView in textViews) {
                try {
                    textView.text?.toString()?.toFloatOrNull()?.let {
                        glucoseValue = it
                        matches++
                    }
                } catch (e: Exception) {
                    Log.e(LOG_ID, "Error processing TextView: ${e.message}")
                }
            }

            textViews.clear()

            when {
                matches == 0 -> {
                    Log.d(LOG_ID, "Did not find any matches")
                    return false
                }

                matches > 1 -> {
                    Log.e(LOG_ID, "Found too many matches: $matches")
                    return false
                }

                else -> {
                    handleGlucoseValue(glucoseValue)
                    return true
                }
            }

        } catch (e: Exception) {
            Log.e(LOG_ID, "Error processing RemoteViews: ${e.message}")
            return false
        }
    }

    private fun findTextViews(view: View, textViews: MutableList<TextView>) {
        when (view) {
            is TextView -> textViews.add(view)
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    findTextViews(view.getChildAt(i), textViews)
                }
            }
        }
    }

    private fun handleGlucoseValue(glucoseValue: Float) {
        Log.d(LOG_ID, "Extracted glucose value: $glucoseValue")
        // TODO: g7 notifications update weirdly often. deduplication is needed
        if (GlucoDataUtils.isGlucoseValid(glucoseValue)) {
            val glucoExtras = Bundle()
            glucoExtras.putLong(ReceiveData.TIME, System.currentTimeMillis())
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

    private fun handleIobValue(iobValue: Float) {
        Log.d(LOG_ID, "Extracted iob value: $iobValue")
        val glucoExtras = Bundle()
        glucoExtras.putFloat(ReceiveData.IOB, iobValue)

        ReceiveData.handleIobCob(applicationContext, DataSource.NOTIFICATION, glucoExtras)
        SourceStateData.setState(DataSource.NOTIFICATION, SourceState.NONE)
    }
}