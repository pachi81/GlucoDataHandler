package de.michelinside.glucodataauto.android_auto

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.tasks.ElapsedTimeTask
import de.michelinside.glucodatahandler.common.utils.Utils
import java.text.DateFormat
import java.util.*

@SuppressLint("StaticFieldLeak")
object CarNotification: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.AA.CarNotification"
    const val ACTION_REPLY = "de.michelinside.glucodataauto.REPLY"
    const val ACTION_MARK_AS_READ = "de.michelinside.glucodataauto.MARK_AS_READ"
    private const val NOTIFICATION_ID = 789
    private var init = false
    @SuppressLint("StaticFieldLeak")
    private lateinit var notificationMgr: CarNotificationManager
    private var show_notification = false  // default: no notification
    private var notification_interval = 1L   // every minute -> always, -1L: only for alarms
    private var notification_reappear_interval = 5L
    const val LAST_NOTIFCATION_TIME = "last_notification_time"
    private var last_notification_time = 0L
    const val FORCE_NEXT_NOTIFY = "force_next_notify"
    private var forceNextNotify = false
    private var show_iob_cob = false
    @SuppressLint("StaticFieldLeak")
    private lateinit var notificationCompat: NotificationCompat.Builder

    var enable_notification : Boolean get() {
        return show_notification
    }
    set(value) {
        show_notification = value
        Log.d(LOG_ID, "show_notification set to " + show_notification.toString())
    }

    private fun createNotificationChannel(context: Context) {
        notificationMgr = CarNotificationManager.from(context)
        val notificationChannel = NotificationChannelCompat.Builder(
            ChannelType.ANDROID_AUTO.channelId,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.setSound(null, null)   // silent
        notificationChannel.setName(context.getString(ChannelType.ANDROID_AUTO.nameResId))
        notificationChannel.setDescription(context.getString(ChannelType.ANDROID_AUTO.descrResId))
        notificationMgr.createNotificationChannel(notificationChannel.build())
    }

    private fun createNofitication(context: Context) {
        createNotificationChannel(context)
        notificationCompat = NotificationCompat.Builder(context, ChannelType.ANDROID_AUTO.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GlucoDataAuto")
            .setContentText("Android Auto")
            .addInvisibleAction(createReplyAction(context))
            .addInvisibleAction(createDismissAction(context))
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .extend (
                CarAppExtender.Builder()
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .build()
            )
    }

    private fun updateSettings(sharedPref: SharedPreferences) {
        val cur_enabled = enable_notification
        enable_notification = sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, enable_notification)
        val alarmOnly = sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY, true)
        notification_interval = if (alarmOnly) -1 else sharedPref.getInt(Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM, 1).toLong()
        val reappear_active = notification_reappear_interval > 0
        notification_reappear_interval = if (alarmOnly) 0L else sharedPref.getInt(Constants.SHARED_PREF_CAR_NOTIFICATION_REAPPEAR_INTERVAL, 5).toLong()
        show_iob_cob = sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION_SHOW_IOB_COB, show_iob_cob)
        Log.i(LOG_ID, "notification settings changed: active: " + enable_notification + " - interval: " + notification_interval + " - reappear:" + notification_reappear_interval + " - show_iob_cob: " + show_iob_cob)
        if(init && GlucoDataServiceAuto.connected) {
            if (enable_notification)
                ElapsedTimeTask.setInterval(notification_reappear_interval)
            if (cur_enabled != enable_notification) {
                if (enable_notification) {
                    showNotification(GlucoDataService.context!!, NotifySource.BROADCAST)
                } else
                    removeNotification()
            } else if (reappear_active != (notification_reappear_interval > 0) && InternalNotifier.hasNotifier(this) ) {
                Log.d(LOG_ID, "Update internal notification filter for reappear interval: " + notification_reappear_interval)
                InternalNotifier.addNotifier(GlucoDataService.context!!, this, getFilter())
            }
        }
    }

    fun initNotification(context: Context) {
        try {
            if(!init) {
                Log.v(LOG_ID, "initNotification called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                migrateSettings(sharedPref)
                sharedPref.registerOnSharedPreferenceChangeListener(this)
                updateSettings(sharedPref)
                loadExtras(context)
                createNofitication(context)
                init = true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.message.toString() )
        }
    }

    private fun migrateSettings(sharedPref: SharedPreferences) {
        if (sharedPref.contains(Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL)) {
            val old_interval = sharedPref.getString(Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL, "-1")!!.toInt()
            Log.i(LOG_ID, "Migrate old interval " + old_interval + " to new settings")
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY, old_interval==-1)
                if (old_interval > 0) {
                    putInt(Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM, old_interval)
                }
                remove(Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL)
                apply()
            }
        }
    }

    private fun cleanupNotification(context: Context) {
        try {
            if (init) {
                Log.v(LOG_ID, "cleanupNotification called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.unregisterOnSharedPreferenceChangeListener(this)
                init = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "cleanupNotification exception: " + exc.message.toString())
        }
    }

    private fun getFilter(): MutableSet<NotifySource> {
        val filter = mutableSetOf(
            NotifySource.BROADCAST,
            NotifySource.MESSAGECLIENT,
            NotifySource.OBSOLETE_ALARM_TRIGGER)
        if (notification_reappear_interval > 0)
            filter.add(NotifySource.TIME_VALUE)
        return filter
    }

    fun enable(context: Context) {
        Log.d(LOG_ID, "enable called")
        initNotification(context)
        forceNextNotify = false
        InternalNotifier.addNotifier(GlucoDataService.context!!, this, getFilter())
        showNotification(context, NotifySource.BROADCAST)
        if (enable_notification)
            ElapsedTimeTask.setInterval(notification_reappear_interval)
    }

    fun disable(context: Context) {
        Log.d(LOG_ID, "disable called")
        removeNotification()
        InternalNotifier.remNotifier(context, this)
        cleanupNotification(context)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.v(LOG_ID, "OnNotifyData called for source " + dataSource)
        try {
            showNotification(context, dataSource)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun removeNotification() {
        notificationMgr.cancel(NOTIFICATION_ID)  // remove notification
        forceNextNotify = false
        ElapsedTimeTask.setInterval(0L)
    }

    private fun getTimeDiffMinute(): Long {
        return Utils.round((ReceiveData.time-last_notification_time).toFloat()/60000, 0).toLong()
    }

    private fun canShowNotification(dataSource: NotifySource): Boolean {
        if (init && enable_notification && GlucoDataServiceAuto.connected) {
            Log.d(LOG_ID, "Check showing notificiation:"
                    + "\ndataSource:    " + dataSource
                    + "\nglucose-alarm: " + ReceiveData.getAlarmType()
                    + "\ndelta-alarm:   " + ReceiveData.getDeltaAlarmType()
                    + "\nforce-alarm:   " + ReceiveData.forceAlarm
                    + "\nforceNextNotify: " + forceNextNotify
                    + "\nnotify-elapsed:  " + getTimeDiffMinute()
                    + "\ndata-elapsed:    " + ReceiveData.getElapsedTimeMinute()
                    + "\nnotification_interval: " + notification_interval
                    + "\nnotification_reappear_interval: " + notification_reappear_interval
            )
            if (dataSource == NotifySource.OBSOLETE_ALARM_TRIGGER) {
                Log.d(LOG_ID, "Obsolete alarm triggered")
                forceNextNotify = true
                return true
            } else if (dataSource == NotifySource.BROADCAST || dataSource == NotifySource.MESSAGECLIENT) {
                if(notification_interval == 1L || ReceiveData.forceAlarm) {
                    Log.d(LOG_ID, "Notification has forced by interval or alarm")
                    return true
                }
                if (ReceiveData.getAlarmType() == AlarmType.VERY_LOW) {
                    Log.d(LOG_ID, "Notification for very low-alarm")
                    forceNextNotify = true  // if obsolete or VERY_LOW, the next value is important!
                    return true
                }
                if (forceNextNotify) {
                    Log.d(LOG_ID, "Force notification")
                    forceNextNotify = false
                    return true
                }
                if (notification_interval > 1L && getTimeDiffMinute() >= notification_interval && ReceiveData.getElapsedTimeMinute() == 0L) {
                    Log.d(LOG_ID, "Interval for new value elapsed")
                    return true
                }
            } else if(dataSource == NotifySource.TIME_VALUE) {
                if (notification_reappear_interval > 0 && ReceiveData.getElapsedTimeMinute().mod(notification_reappear_interval) == 0L) {
                    Log.d(LOG_ID, "reappear after: " + ReceiveData.getElapsedTimeMinute() + " - interval: " + notification_reappear_interval)
                    return true
                }
            }
            Log.v(LOG_ID, "No notification to show")
            return false
        }
        return false
    }

    fun showNotification(context: Context, dataSource: NotifySource) {
        try {
            if (canShowNotification(dataSource)) {
                Log.v(LOG_ID, "showNotification called")
                notificationCompat
                    .setLargeIcon(BitmapUtils.getRateAsBitmap(resizeFactor = 0.75F))
                    .setWhen(ReceiveData.time)
                    .setStyle(createMessageStyle(context, ReceiveData.isObsoleteShort()))
                notificationMgr.notify(NOTIFICATION_ID, notificationCompat)
                if(dataSource != NotifySource.TIME_VALUE) {
                    last_notification_time = ReceiveData.time
                    saveExtras(context)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    private fun getAlarmText(context: Context): String {
        if(ReceiveData.forceAlarm) {
            val resId: Int? = if(ReceiveData.forceGlucoseAlarm && ReceiveData.getAlarmType() != AlarmType.NONE) {
                AlarmNotificationBase.getAlarmTextRes(ReceiveData.getAlarmType())
            } else if(ReceiveData.forceDeltaAlarm && ReceiveData.getDeltaAlarmType() != AlarmType.NONE) {
                AlarmNotificationBase.getAlarmTextRes(ReceiveData.getDeltaAlarmType())
            } else {
                null
            }
            if (resId != null) {
                return context.getString(resId) + " "
            }
        }
        return ""
    }

    private fun createMessageStyle(context: Context, isObsolete: Boolean): NotificationCompat.MessagingStyle {
        val person = Person.Builder()
            .setIcon(IconCompat.createWithBitmap(BitmapUtils.getRateAsBitmap(resizeFactor = 0.75F)!!))
            .setName(ReceiveData.getGlucoseAsString())
            .setImportant(true)
            .build()
        val messagingStyle = NotificationCompat.MessagingStyle(person)
        var title = ""
        if (isObsolete)
            title = context.getString(CR.string.no_new_value, ReceiveData.getElapsedTimeMinute())
        else
            title = getAlarmText(context) + ReceiveData.getGlucoseAsString()  + " (Δ " + ReceiveData.getDeltaAsString()

        if (show_iob_cob && !ReceiveData.isIobCobObsolete()) {
            if(!ReceiveData.iob.isNaN()) {
                title += " 💉 " + ReceiveData.getIobAsString(true)
            }
            if(!ReceiveData.cob.isNaN()) {
                title += " 🍔 " + ReceiveData.getCobAsString(true)
            }
        }
        title += ")"
        messagingStyle.conversationTitle = title
        messagingStyle.isGroupConversation = false
        var message = ""
        if(!GlucoDataServiceAuto.patientName.isNullOrEmpty()) {
            message += GlucoDataServiceAuto.patientName + " - "
        }
        message += "🕒 " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time))
        messagingStyle.addMessage(message, System.currentTimeMillis(), person)
        return messagingStyle
    }

    private fun createReplyAction(context: Context): NotificationCompat.Action {
        val remoteInputWear =
            RemoteInput.Builder("extra_voice_reply").setLabel("Reply")
                .build()
        val intent = Intent(ACTION_REPLY)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "Reply", pendingIntent)
            //.setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .addRemoteInput(remoteInputWear)
            .build()
    }

    private fun createDismissAction(context: Context): NotificationCompat.Action {
        val intent = Intent(ACTION_MARK_AS_READ)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Mark as read",
            pendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.v(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                Constants.SHARED_PREF_CAR_NOTIFICATION,
                Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY,
                Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM,
                Constants.SHARED_PREF_CAR_NOTIFICATION_REAPPEAR_INTERVAL,
                Constants.SHARED_PREF_CAR_NOTIFICATION_SHOW_IOB_COB -> {
                    updateSettings(sharedPreferences!!)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun saveExtras(context: Context) {
        try {
            Log.d(LOG_ID, "Saving extras")
            // use own tag to prevent trigger onChange event at every time!
            val sharedAutoPref =
                context.getSharedPreferences(Constants.SHARED_PREF_AUTO_TAG, Context.MODE_PRIVATE)
            with(sharedAutoPref.edit()) {
                putLong(LAST_NOTIFCATION_TIME, last_notification_time)
                putBoolean(FORCE_NEXT_NOTIFY, forceNextNotify)
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving extras exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun loadExtras(context: Context) {
        try {
            val sharedAutoPref = context.getSharedPreferences(Constants.SHARED_PREF_AUTO_TAG, Context.MODE_PRIVATE)
            if (sharedAutoPref.contains(LAST_NOTIFCATION_TIME)) {
                Log.i(LOG_ID, "Reading saved values...")
                last_notification_time = sharedAutoPref.getLong(LAST_NOTIFCATION_TIME, last_notification_time)
                forceNextNotify = sharedAutoPref.getBoolean(FORCE_NEXT_NOTIFY, forceNextNotify)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Loading extras exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

}
