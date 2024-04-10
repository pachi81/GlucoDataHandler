package de.michelinside.glucodatahandler.common.notification

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import java.io.FileOutputStream
import java.math.RoundingMode
import de.michelinside.glucodatahandler.common.R as CR


abstract class AlarmNotificationBase: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    protected val LOG_ID = "GDH.AlarmNotification"
    private var enabled: Boolean = false
    private var addSnooze: Boolean = false
    val ALARM_GROUP_ID = "alarm_group"
    private val VERY_LOW_NOTIFICATION_ID = 801
    private val LOW_NOTIFICATION_ID = 802
    private val HIGH_NOTIFICATION_ID = 803
    private val VERY_HIGH_NOTIFICATION_ID = 804
    private val OBSOLETE_NOTIFICATION_ID = 805
    lateinit var audioManager:AudioManager
    protected var curNotification = 0
    private var curAlarmTime = 0L
    private var forceSound = false
    private var forceVibration = false
    private var lastRingerMode = -1
    private var lastDndMode = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    private var retriggerTime = 0
    private var retriggerCount = 0
    private var curTestAlarmType = AlarmType.NONE
    private var retriggerOnDestroy = false
    //private var soundLevel = -1
    //private var lastSoundLevel = -1

    companion object {
        private var classInstance: AlarmNotificationBase? = null
        val instance: AlarmNotificationBase? get() = classInstance

    }

    private val elapsedMinute: Long get() {
        return Utils.round((System.currentTimeMillis()- curAlarmTime).toFloat()/60000, 0, RoundingMode.DOWN).toLong()
    }

    abstract val active: Boolean

    fun getAlarmState(context: Context): AlarmState {
        var state = AlarmState.currentState(context)
        if(state != AlarmState.DISABLED && !active) {
            state = AlarmState.INACTIVE
        }
        Log.i(LOG_ID, "Current alarm state: $state")
        return state
    }

    fun isAlarmActive(context: Context): Boolean {
        return getAlarmState(context) == AlarmState.ACTIVE
    }

    fun initNotifications(context: Context) {
        try {
            Log.v(LOG_ID, "initNotifications called")
            classInstance = this
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            createNotificationChannels(context)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref, null)
            updateNotifier()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initNotifications exception: " + exc.toString() )
        }
    }

    fun getEnabled(): Boolean = enabled

    private fun setEnabled(newEnabled: Boolean) {
        try {
            Log.v(LOG_ID, "setEnabled called: current=$enabled - new=$newEnabled")
            if (enabled != newEnabled) {
                enabled = newEnabled
                Log.i(LOG_ID, "enable alarm notifications: $newEnabled")
                updateNotifier()
                if(!enabled) {
                    stopCurrentNotification()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setEnabled exception: " + exc.toString() )
        }
    }

    fun getAddSnooze(): Boolean = addSnooze

    private fun setAddSnooze(snooze: Boolean) {
        try {
            Log.v(LOG_ID, "setAddSnooze called: current=$addSnooze - new=$snooze")
            addSnooze = snooze
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setAddSnooze exception: " + exc.toString() )
        }
    }

    fun destroy(context: Context) {
        try {
            Log.v(LOG_ID, "destroy called")
            stopCurrentNotification(context)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.toString() )
        }
    }

    fun stopCurrentNotification(context: Context? = null, cancelOnlySound: Boolean = false, fromClient: Boolean = false) {
        Log.v(LOG_ID, "stopCurrentNotification cancelOnlySound=$cancelOnlySound - fromClient=$fromClient - curNotification=$curNotification")
        if (curNotification > 0) {
            stopNotification(curNotification, context, cancelOnlySound = cancelOnlySound, fromClient = fromClient)
        } else {
            onNotificationStopped(0, context, true)
        }
    }

    open fun onNotificationStopped(noticationId: Int, context: Context? = null, reset: Boolean) {

    }

    fun stopNotification(noticationId: Int, context: Context? = null, reset: Boolean = true, cancelOnlySound: Boolean = false, fromClient: Boolean = false) {
        try {
            Log.v(LOG_ID, "stopNotification called for $noticationId - current=$curNotification")
            if(noticationId == curNotification) {
                if (reset)
                    checkRecreateSound()
                if (noticationId > 0) {
                    Channels.getNotificationManager(context).cancel(noticationId)
                    if (!cancelOnlySound)  // only stop the sound -> used after snooze is unlocked
                        onNotificationStopped(noticationId, context, reset)
                    if (reset) {
                        curNotification = 0
                        curAlarmTime = 0
                        curTestAlarmType = AlarmType.NONE
                        updateNotifier(context)
                        if(!fromClient)
                            GlucoDataService.sendCommand(Command.STOP_ALARM)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "stopNotification exception: " + exc.toString() )
        }
    }

    fun triggerNotification(alarmType: AlarmType, context: Context, forTest: Boolean = false) {
        try {
            Log.v(LOG_ID, "triggerNotification called for $alarmType - active=$active - forTest=$forTest")
            if (active || forTest) {
                stopCurrentNotification(context)
                curNotification = getNotificationId(alarmType)
                retriggerCount = 0
                retriggerOnDestroy = false
                retriggerTime = getTriggerTime(alarmType, context)
                curAlarmTime = ReceiveData.time
                curTestAlarmType = if(forTest)
                    alarmType
                else
                    AlarmType.NONE
                updateNotifier(context)
                Log.d(LOG_ID, "Create notification for $alarmType with ID=$curNotification - triggerTime=$retriggerTime")
                checkCreateSound(alarmType)
                showNotification(alarmType, context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    open fun executeTest(alarmType: AlarmType, context: Context) {
        Log.v(LOG_ID, "executeTest called for $alarmType")
        triggerNotification(alarmType, context, true)
    }

    fun triggerTest(alarmType: AlarmType, context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var hasExactAlarmPermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(!alarmManager.canScheduleExactAlarms()) {
                Log.d(LOG_ID, "Need permission to set exact alarm!")
                hasExactAlarmPermission = false
            }
        }
        val intent = Intent(context, TestAlarmReceiver::class.java)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE, alarmType.ordinal)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            800,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val alarmTime = System.currentTimeMillis() + 3000
        if (hasExactAlarmPermission) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime,
                pendingIntent!!
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime,
                pendingIntent!!
            )
        }
    }

    private fun showNotification(alarmType: AlarmType, context: Context) {
        Channels.getNotificationManager(context).notify(
            curNotification,
            createNotification(context, alarmType)
        )
    }

    private fun createNotificationChannels(context: Context) {
        Log.v(LOG_ID, "createNotificationChannels called")
        val groupName = context.resources.getString(CR.string.alarm_notification_group_name)
        Channels.getNotificationManager(context).createNotificationChannelGroup(
            NotificationChannelGroup(ALARM_GROUP_ID, groupName))
        createNotificationChannel(context, AlarmType.VERY_LOW, true)
        createNotificationChannel(context, AlarmType.LOW, false)
        createNotificationChannel(context, AlarmType.HIGH, false)
        createNotificationChannel(context, AlarmType.VERY_HIGH, true)
        createNotificationChannel(context, AlarmType.OBSOLETE, false)
    }

    abstract fun createNotificationChannel(context: Context, alarmType: AlarmType, byPassDnd: Boolean)

    protected fun createSnoozeIntent(context: Context, snoozeTime: Long, noticationId: Int): PendingIntent {
        val intent = Intent(Constants.ALARM_SNOOZE_ACTION)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_TIME, snoozeTime)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, noticationId)
        intent.setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, snoozeTime.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    protected fun createStopIntent(context: Context, noticationId: Int): PendingIntent {
        val intent = Intent(Constants.ALARM_STOP_NOTIFICATION_ACTION)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, noticationId)
        intent.setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, 888, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    protected fun createAction(context: Context, title: String, snoozeTime: Long, noticationId: Int): Notification.Action {
        return Notification.Action.Builder(null, title, createSnoozeIntent(context, snoozeTime, noticationId)).build()

    }

    abstract fun buildNotification(notificationBuilder: Notification.Builder, context: Context, alarmType: AlarmType)

    private fun createNotification(context: Context, alarmType: AlarmType): Notification? {
        Log.v(LOG_ID, "createNotification called for $alarmType")
        val channelId = getChannelId(alarmType)
        val resId = getAlarmTextRes(alarmType)
        if (channelId.isNullOrEmpty() || resId == null)
            return null

        val notificationBuilder = Notification.Builder(context, channelId)
            .setSmallIcon(CR.mipmap.ic_launcher)
            .setDeleteIntent(createStopIntent(context, getNotificationId(alarmType)))
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(ReceiveData.time)
            .setColorized(false)
            .setGroup("alarm")
            .setLocalOnly(false)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentTitle(context.getString(resId))
            .setContentText(ReceiveData.getClucoseAsString()  + " (Δ " + ReceiveData.getDeltaAsString() + ")")

            /*.setLargeIcon(BitmapUtils.getRateAsIcon())
            .addAction(createAction(context, context.getString(CR.string.snooze) + ": 60", 60L, getNotificationId(alarmType)))
            .addAction(createAction(context, "90", 90L, getNotificationId(alarmType)))
            .addAction(createAction(context, "120", 120L, getNotificationId(alarmType)))*/

        buildNotification(notificationBuilder, context, alarmType)
        return notificationBuilder.build()
    }

    fun forceDnd(): Boolean {
        if(!Channels.getNotificationManager().isNotificationPolicyAccessGranted &&
            ( Channels.getNotificationManager().currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL ||
                    audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) ) {
            return true
        }
        return false
    }

    protected fun getRingerMode(): Int {
        if(forceDnd())
            return AudioManager.RINGER_MODE_NORMAL  // DnD is on and can not be changed, so do not change ringer mode
        if(Channels.getNotificationManager().currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) {
            lastDndMode = Channels.getNotificationManager().currentInterruptionFilter
            Log.d(LOG_ID, "Disable DnD in level $lastDndMode")
            Channels.getNotificationManager()
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            Thread.sleep(100)
        }
        Log.d(LOG_ID, "Current ringer mode ${audioManager.ringerMode}")
        return audioManager.ringerMode
    }

    protected fun checkCreateSound(alarmType: AlarmType) {
        try {
            Log.v(LOG_ID, "checkCreateSound called for force sound=$forceSound - vibration=$forceVibration - DnD=${Channels.getNotificationManager().currentInterruptionFilter} - ringmode=${audioManager.ringerMode}")
            lastRingerMode = -1
            lastDndMode = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            if (forceSound || forceVibration) {
                if (getRingerMode() < AudioManager.RINGER_MODE_NORMAL) {
                    val channelId = getChannelId(alarmType)
                    val channel = Channels.getNotificationManager().getNotificationChannel(channelId)
                    Log.d(LOG_ID, "Channel prio=${channel.importance}")
                    if(channel.importance >= NotificationManager.IMPORTANCE_DEFAULT) { // notification supports sound
                        val soundMode = getSoundMode(alarmType)
                        var targetRingerMode = AudioManager.RINGER_MODE_SILENT
                        if(soundMode>SoundMode.SILENT) {
                            if (!forceSound && forceVibration) {
                                targetRingerMode = AudioManager.RINGER_MODE_VIBRATE
                            } else if (forceSound) {
                                targetRingerMode = soundMode.ringerMode
                            }
                        }
                        Log.d(LOG_ID, "Check force sound for soundMode=$soundMode - targetRinger=$targetRingerMode - currentRinger=${audioManager.ringerMode}")
                        if (targetRingerMode > audioManager.ringerMode ) {
                            lastRingerMode = audioManager.ringerMode
                            Log.d(LOG_ID, "Set cur ringer mode $lastRingerMode to $targetRingerMode")
                            audioManager.ringerMode = targetRingerMode
                        }
                    }
                }
            }
            /*
            if (soundLevel >= 0) {
                lastSoundLevel = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val level = minOf(soundLevel, getMaxSoundLevel())
                Log.d(LOG_ID, "Set cur sound level $lastSoundLevel to $level")
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    level,
                    0
                )
            }*/
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkCreateSound exception: " + exc.message.toString() )
        }
    }

    open fun getSoundMode(alarmType: AlarmType): SoundMode {
        val channelId = getChannelId(alarmType)
        val channel = Channels.getNotificationManager().getNotificationChannel(channelId)
        Log.d(LOG_ID, "Channel: prio=${channel.importance} - sound=${channel.sound} - vibration=${channel.shouldVibrate()}")
        if(channel.importance >= NotificationManager.IMPORTANCE_DEFAULT) {
            if (channel.sound != null) {
                return SoundMode.NORMAL
            } else if (channel.shouldVibrate()) {
                return SoundMode.VIBRATE
            }
        } else if(channel.importance == NotificationManager.IMPORTANCE_NONE)
            return SoundMode.OFF
        return SoundMode.SILENT
    }

    protected fun checkRecreateSound() {
        try {
            if(lastRingerMode >= 0 ) {
                Log.d(LOG_ID, "Reset ringer mode to $lastRingerMode")
                audioManager.ringerMode = lastRingerMode
                lastRingerMode = -1
            }
            if(lastDndMode != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                Log.d(LOG_ID, "Reset DnD mode to $lastDndMode")
                Channels.getNotificationManager().setInterruptionFilter(lastDndMode)
                lastDndMode = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }
            /*
            if(lastSoundLevel >= 0) {
                Log.d(LOG_ID, "Reset sound level to $lastSoundLevel")
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    lastSoundLevel,
                    0
                )
                lastSoundLevel = -1
            }*/
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkCreateSound exception: " + exc.message.toString() )
        }
    }

    protected fun getNotificationId(alarmType: AlarmType): Int {
        return when(alarmType) {
            AlarmType.VERY_LOW -> VERY_LOW_NOTIFICATION_ID
            AlarmType.LOW -> LOW_NOTIFICATION_ID
            AlarmType.HIGH -> HIGH_NOTIFICATION_ID
            AlarmType.VERY_HIGH -> VERY_HIGH_NOTIFICATION_ID
            AlarmType.OBSOLETE -> OBSOLETE_NOTIFICATION_ID
            else -> -1
        }
    }

    abstract fun getChannelType(alarmType: AlarmType): ChannelType?

    fun getChannelId(alarmType: AlarmType): String? {
        val channel = getChannelType(alarmType)
        if (channel != null)
            return channel.channelId
        return null
    }

    fun getAlarmSoundRes(alarmType: AlarmType): Int? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> CR.raw.gdh_very_low_alarm
            AlarmType.LOW -> CR.raw.gdh_low_alarm
            AlarmType.HIGH -> CR.raw.gdh_high_alarm
            AlarmType.VERY_HIGH -> CR.raw.gdh_very_high_alarm
            AlarmType.OBSOLETE -> CR.raw.gdh_obsolete_alarm
            else -> null
        }
    }

    fun getAlarmTextRes(alarmType: AlarmType): Int? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> CR.string.very_low_alarm_text
            AlarmType.LOW -> CR.string.very_low_text
            AlarmType.HIGH -> CR.string.very_high_text
            AlarmType.VERY_HIGH -> CR.string.very_high_alarm_text
            AlarmType.OBSOLETE -> CR.string.obsolete_alarm_text
            else -> null
        }
    }

    protected fun getDefaultAlarm(alarmType: AlarmType, context: Context): Uri? {
        val res = getAlarmSoundRes(alarmType)
        if (res != null) {
            return getUri(res, context)
        }
        return null
    }

    private fun getUri(resId: Int, context: Context): Uri {
        val uri = "android.resource://" + context.packageName + "/" + resId
        return Uri.parse(uri)
    }

    fun getVibrationPattern(alarmType: AlarmType): LongArray? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000)
            AlarmType.LOW -> longArrayOf(0, 700, 500, 700, 500, 700, 500, 700)
            AlarmType.HIGH -> longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)
            AlarmType.VERY_HIGH -> longArrayOf(0, 800, 500, 800, 800, 600, 800, 800, 500, 800, 800, 600, 800)
            AlarmType.OBSOLETE -> longArrayOf(0, 600, 500, 500, 500, 600, 500, 500)
            else -> null
        }
    }


    private fun getTriggerTime(alarmType: AlarmType, context: Context): Int {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        return when(alarmType) {
            AlarmType.VERY_LOW -> sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_RETRIGGER, 0)
            AlarmType.LOW -> sharedPref.getInt(Constants.SHARED_PREF_ALARM_LOW_RETRIGGER, 0)
            AlarmType.HIGH -> sharedPref.getInt(Constants.SHARED_PREF_ALARM_HIGH_RETRIGGER, 0)
            AlarmType.VERY_HIGH -> sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_RETRIGGER, 0)
            AlarmType.OBSOLETE -> sharedPref.getInt(Constants.SHARED_PREF_ALARM_OBSOLETE_RETRIGGER, 0)
            else -> 0
        }
    }

    fun saveAlarm(context: Context, alarmType: AlarmType, uri: Uri) {
        try {
            Log.v(LOG_ID, "saveAlarm called for $alarmType to $uri")
            val resId = getAlarmSoundRes(alarmType)
            if (resId != null) {
                Thread {
                    context.contentResolver.openFileDescriptor(uri, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use { outputStream ->
                            val inputStream = context.resources.openRawResource(resId)
                            val buffer = ByteArray(4 * 1024) // or other buffer size
                            var read: Int
                            while (inputStream.read(buffer).also { rb -> read = rb } != -1) {
                                outputStream.write(buffer, 0, read)
                            }
                            Log.v(LOG_ID, "flush")
                            outputStream.flush()
                            outputStream.close()
                        }
                    }
                    val text = context.resources.getText(CR.string.alarm_saved)
                    Handler(GlucoDataService.context!!.mainLooper).post {
                        Toast.makeText(GlucoDataService.context!!, text, Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving alarm to file exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FORCE_SOUND)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FORCE_VIBRATION)
            } else {
                when(key) {
                    Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED -> setEnabled(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, enabled))
                    Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION -> setAddSnooze(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION, addSnooze))
                    Constants.SHARED_PREF_ALARM_FORCE_SOUND -> forceSound = sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_SOUND, forceSound)
                    Constants.SHARED_PREF_ALARM_FORCE_VIBRATION -> forceVibration = sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_VIBRATION, forceVibration)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun canRetrigger(): Boolean {
        if(isTriggerActive()) {
            Log.v(LOG_ID, "canRetrigger called for count=$retriggerCount - time=$retriggerTime - elapsed=${elapsedMinute} - notification=$curNotification")
            if(elapsedMinute != 0L)
                return elapsedMinute.mod(retriggerTime) == 0
        }
        return false
    }

    open fun stopNotificationForRetrigger(): Boolean = false

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for $dataSource")
            when(dataSource) {
                NotifySource.ALARM_TRIGGER -> {
                    if (ReceiveData.forceAlarm)
                        triggerNotification(ReceiveData.getAlarmType(), context)
                }
                NotifySource.OBSOLETE_ALARM_TRIGGER -> {
                    triggerNotification(AlarmType.OBSOLETE, context)
                }
                NotifySource.ALARM_STATE_CHANGED -> {
                    updateNotifier(context)
                }
                NotifySource.TIME_VALUE,
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT -> {
                    // check for retrigger notification
                    if (canRetrigger()) {
                        Log.d(LOG_ID, "Retrigger notification")
                        val stop = stopNotificationForRetrigger()
                        if (stop) {
                            retriggerOnDestroy = true
                            stopNotification(curNotification, context, reset = false)
                        } else {
                            retriggerOnDestroy = false
                            showNotification(getAlarmType(), context)
                        }
                        retriggerCount++
                    }
                    if (!isTriggerActive()) {
                        updateNotifier(context)
                    }
                }
                else -> Log.w(LOG_ID, "Unsupported source $dataSource")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }

    fun checkRetrigger(alarmType: AlarmType, context: Context) {
        if(retriggerOnDestroy) {
            Log.d(LOG_ID, "Retrigger on destroy!")
            retriggerOnDestroy = false
            Thread {
                Thread.sleep(100)
                Handler(context.mainLooper).post {
                    showNotification(alarmType, context)
                }
            }.start()
        }
    }

    private fun isTriggerActive(): Boolean {
        return retriggerCount < 3 && retriggerTime > 0 && curAlarmTime > 0L && curNotification == getNotificationId(getAlarmType())
    }

    private fun getAlarmType(): AlarmType {
        if(curTestAlarmType != AlarmType.NONE)
            return curTestAlarmType
        return ReceiveData.getAlarmType()
    }

    private fun getNotifierFilter() : MutableSet<NotifySource> {
        val filter = mutableSetOf(NotifySource.ALARM_TRIGGER, NotifySource.OBSOLETE_ALARM_TRIGGER, NotifySource.ALARM_STATE_CHANGED)
        if(isTriggerActive()) {
            // add triggers for time changing
            filter.add(NotifySource.TIME_VALUE)
            filter.add(NotifySource.BROADCAST)
            filter.add(NotifySource.MESSAGECLIENT)
        }
        return filter
    }

    fun updateNotifier(context: Context? = null) {
        val requireConext = context ?: GlucoDataService.context!!
        Log.d(LOG_ID, "updateNotifier called - active=${isAlarmActive(requireConext)}")
        if(isAlarmActive(requireConext)) {
            InternalNotifier.addNotifier(requireConext, this, getNotifierFilter() )
        } else {
            InternalNotifier.addNotifier(requireConext, this, mutableSetOf(NotifySource.ALARM_STATE_CHANGED))
        }
    }



    fun hasFullscreenPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return Channels.getNotificationManager().canUseFullScreenIntent()
        return true
    }
}


class TestAlarmReceiver: BroadcastReceiver() {
    private val LOG_ID = "GDH.AlarmTestReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        Log.v(LOG_ID, "onReceive called for ${intent.action} with extras: ${Utils.dumpBundle(intent.extras)}")
        if(intent.extras?.containsKey(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE) == true && AlarmNotificationBase.instance != null) {
            val alarmType = AlarmType.fromIndex(intent.extras!!.getInt(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE, ReceiveData.getAlarmType().ordinal))
            AlarmNotificationBase.instance!!.executeTest(alarmType, context)
            GlucoDataService.sendCommand(Command.TEST_ALARM, intent.extras)
        }
    }
}