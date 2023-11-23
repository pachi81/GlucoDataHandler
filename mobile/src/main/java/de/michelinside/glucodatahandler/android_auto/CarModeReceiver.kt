package de.michelinside.glucodatahandler.android_auto

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.tasker.setAndroidAutoConnectionState
import java.text.DateFormat
import java.util.*


object CarModeReceiver: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.CarModeReceiver"
    private const val CHANNEL_ID = "GlucoDataNotify_Car"
    private const val CHANNEL_NAME = "Notification for Android Auto"
    private const val NOTIFICATION_ID = 789
    private var init = false
    @SuppressLint("StaticFieldLeak")
    private lateinit var notificationMgr: CarNotificationManager
    private var show_notification = false
    private var car_connected = false
    private var last_notification_time = 0L
    private var notification_interval = 1L   // every minute -> always, -1L: only for alarms
    val connected: Boolean get() = car_connected
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
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.setSound(null, null)   // silent
        notificationMgr.createNotificationChannel(notificationChannel.setName(CHANNEL_NAME).build())
    }

    private fun createNofitication(context: Context) {
        createNotificationChannel(context)
        notificationCompat = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(CR.string.app_name))
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
        notification_interval = sharedPref.getString(Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL, "1")!!.toLong()
        if(init && car_connected && cur_enabled != enable_notification) {
            if(enable_notification)
                showNotification(GlucoDataService.context!!, ReceiveData.isObsolete())
            else
                removeNotification()
        }
    }

    fun initNotification(context: Context) {
        try {
            if(!init) {
                Log.d(LOG_ID, "initNotification called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.registerOnSharedPreferenceChangeListener(this)
                updateSettings(sharedPref)
                createNofitication(context)
                CarConnection(context).type.observeForever(CarModeReceiver::onConnectionStateUpdated)
                init = true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.message.toString() )
        }
    }

    fun cleanupNotification(context: Context) {
        try {
            if (init) {
                Log.d(LOG_ID, "remNotification called")
                CarConnection(context).type.removeObserver(CarModeReceiver::onConnectionStateUpdated)
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.unregisterOnSharedPreferenceChangeListener(this)
                init = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.message.toString())
        }
    }

    private fun onConnectionStateUpdated(connectionState: Int) {
        try {
            val message = when(connectionState) {
                CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "Not connected to a head unit"
                CarConnection.CONNECTION_TYPE_NATIVE -> "Connected to Android Automotive OS"
                CarConnection.CONNECTION_TYPE_PROJECTION -> "Connected to Android Auto"
                else -> "Unknown car connection type"
            }
            Log.d(LOG_ID, "onConnectionStateUpdated: " + message + " (" + connectionState.toString() + ")")
            if (connectionState == CarConnection.CONNECTION_TYPE_NOT_CONNECTED)  {
                Log.d(LOG_ID, "Exited Car Mode")
                removeNotification()
                car_connected = false
                GlucoDataService.context?.setAndroidAutoConnectionState(false)
                InternalNotifier.remNotifier(this)
            } else {
                Log.d(LOG_ID, "Entered Car Mode")
                car_connected = true
                GlucoDataService.context?.setAndroidAutoConnectionState(true)
                InternalNotifier.addNotifier(this, mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.MESSAGECLIENT,
                    NotifySource.OBSOLETE_VALUE))
                showNotification(GlucoDataService.context!!, ReceiveData.isObsolete())
            }
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.CAR_CONNECTION, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called")
        try {
            showNotification(context, dataSource == NotifySource.OBSOLETE_VALUE)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    fun removeNotification() {
        notificationMgr.cancel(NOTIFICATION_ID)  // remove notification
    }

    private fun getTimeDiffMinute(): Long {
        return Utils.round((ReceiveData.time-last_notification_time).toFloat()/60000, 0).toLong()
    }

    private fun canShowNotification(dataSource: NotifySource, isObsolete: Boolean): Boolean {
        if (enable_notification && car_connected) {
            if(ReceiveData.forceAlarm || ReceiveData.getAlarmType() == ReceiveData.AlarmType.LOW_ALARM || isObsolete)
                return true
            if (notification_interval > 1L) {
                return getTimeDiffMinute() >= notification_interval
            }
            return true  // always
        }
        return false
    }

    fun showNotification(context: Context, isObsolete: Boolean) {
        try {
            if (canShowNotification(isObsolete)) {
                Log.d(LOG_ID, "showNotification called")
                notificationCompat
                    .setLargeIcon(Utils.getRateAsBitmap(resizeFactor = 0.75F))
                    .setWhen(ReceiveData.time)
                    .setStyle(createMessageStyle(context, isObsolete))
                notificationMgr.notify(NOTIFICATION_ID, notificationCompat)
                last_notification_time = ReceiveData.time
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    private fun createMessageStyle(context: Context, isObsolete: Boolean): NotificationCompat.MessagingStyle {
        val person = Person.Builder()
            .setIcon(IconCompat.createWithBitmap(Utils.getRateAsBitmap(resizeFactor = 0.75F)!!))
            .setName(ReceiveData.getClucoseAsString())
            .setImportant(true)
            .build()
        val messagingStyle = NotificationCompat.MessagingStyle(person)
        if (isObsolete)
            messagingStyle.conversationTitle = context.getString(CR.string.tasker_event_glucodata_obsolete)
        else
            messagingStyle.conversationTitle = ReceiveData.getClucoseAsString()  + " (" + ReceiveData.getDeltaAsString() + ")"
        messagingStyle.isGroupConversation = false
        messagingStyle.addMessage(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time)), System.currentTimeMillis(), person)
        return messagingStyle
    }

    private fun createReplyAction(context: Context): NotificationCompat.Action {
        val remoteInputWear =
            RemoteInput.Builder("extra_voice_reply").setLabel("Reply")
                .build()
        val intent = Intent("DoNothing") //new Intent(this, PopupReplyReceiver.class);
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "Reply", pendingIntent)
            //.setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .addRemoteInput(remoteInputWear)
            .build()
    }

    private fun createDismissAction(context: Context): NotificationCompat.Action {
        val intent = Intent("DoNothing") //new Intent(this, DismissReceiver.class);
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                Constants.SHARED_PREF_CAR_NOTIFICATION,
                Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL -> {
                    updateSettings(sharedPreferences!!)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }
}
