package de.michelinside.glucodatahandler.common.notification

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import de.michelinside.glucodatahandler.common.R as CR


abstract class AlarmNotificationBase: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    protected val LOG_ID = "GDH.AlarmNotification"
    private var enabled: Boolean = false
    private var addSnooze: Boolean = false
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
    protected var vibrateOnly = false
    private var lastRingerMode = -1
    private var lastDndMode = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    private var retriggerTime = 0
    private var retriggerCount = 0
    private var curTestAlarmType = AlarmType.NONE
    private var retriggerOnDestroy = false
    private var ringtone: Ringtone? = null
    private val ringtoneRWLock = ReentrantReadWriteLock()
    private var vibratorInstance: Vibrator? = null
    private var alarmManager: AlarmManager? = null
    private var alarmPendingIntent: PendingIntent? = null
    private var useAlarmSound: Boolean = true
    private var autoCloseNotification: Boolean = false
    private var currentAlarmState: AlarmState = AlarmState.DISABLED
    private var startDelayThread: Thread? = null

    enum class TriggerAction {
        TEST_ALARM,
        START_ALARM_SOUND,
        STOP_VIBRATION,
        RETRIGGER_SOUND,
        CLOSE_NOTIFICATION,
    }

    private var lastSoundLevel = -1

    protected val vibrator: Vibrator get() {
        if(vibratorInstance == null) {
            vibratorInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = GlucoDataService.context!!.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                GlucoDataService.context!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
        return vibratorInstance!!
    }

    companion object {
        private var classInstance: AlarmNotificationBase? = null
        val instance: AlarmNotificationBase? get() = classInstance

    }

    abstract val active: Boolean
    val notificationActive: Boolean get() {
        return curNotification > 0
    }

    fun getAlarmState(context: Context): AlarmState {
        var state = AlarmState.currentState(context)
        if(state == AlarmState.DISABLED || !channelActive(context)) {
            state = AlarmState.DISABLED
        } else if(state == AlarmState.ACTIVE) {
            if(!active) {
                Log.d(
                    LOG_ID,
                    "Inactive causes by active: $active"
                )
                state = AlarmState.INACTIVE
            }
        }
        if(currentAlarmState != state) {
            Log.i(LOG_ID, "Current alarm state: $state - last state: $currentAlarmState")
            currentAlarmState = state
        }
        return state
    }

    fun initNotifications(context: Context) {
        try {
            Log.v(LOG_ID, "initNotifications called")
            classInstance = this
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            createNotificationChannel(context)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref, null)
            initNotifier(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initNotifications exception: " + exc.toString() )
        }
    }

    open fun getNotifierFilter(): MutableSet<NotifySource> {
        return mutableSetOf()
    }

    fun initNotifier(context: Context) {
        Log.v(LOG_ID, "initNotifier called")
        val filter = mutableSetOf(NotifySource.ALARM_STATE_CHANGED)
        filter.add(NotifySource.ALARM_TRIGGER)
        filter.add(NotifySource.OBSOLETE_ALARM_TRIGGER)
        filter.addAll(getNotifierFilter())
        InternalNotifier.addNotifier(context, this, filter )
    }

    fun getEnabled(): Boolean = enabled

    private fun setEnabled(newEnabled: Boolean) {
        try {
            Log.v(LOG_ID, "setEnabled called: current=$enabled - new=$newEnabled")
            if (enabled != newEnabled) {
                enabled = newEnabled
                Log.i(LOG_ID, "enable alarm notifications: $newEnabled")
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

    fun stopCurrentNotification(context: Context? = null, fromClient: Boolean = false) {
        Log.v(LOG_ID, "stopCurrentNotification fromClient=$fromClient - curNotification=$curNotification")
        if (curNotification > 0) {
            stopNotification(curNotification, context, fromClient = fromClient)
        } else {
            onNotificationStopped(0, context)
        }
    }

    open fun onNotificationStopped(noticationId: Int, context: Context? = null) {

    }

    fun stopNotification(noticationId: Int, context: Context? = null, fromClient: Boolean = false) {
        try {
            Log.i(LOG_ID, "stopNotification called for $noticationId - current=$curNotification - fromClient=$fromClient")
            stopTrigger()
            if(noticationId == curNotification) {
                checkRecreateSound()
                if (noticationId > 0) {
                    Channels.getNotificationManager(context).cancel(noticationId)
                    onNotificationStopped(noticationId, context)
                    curNotification = 0
                    curAlarmTime = 0
                    curTestAlarmType = AlarmType.NONE
                    if(!fromClient)
                        GlucoDataService.sendCommand(Command.STOP_ALARM)
                    InternalNotifier.notify(GlucoDataService.context!!, NotifySource.NOTIFICATION_STOPPED, null)
                }
            }
            stopVibrationAndSound()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "stopNotification exception: " + exc.toString() )
        }
    }


    fun stopVibrationAndSound() {
        try {
            Log.d(LOG_ID, "stopVibrationAndSound called")
            vibrator.cancel()
            ringtoneRWLock.write {
                if (ringtone != null) {
                    ringtone!!.stop()
                    ringtone = null
                }
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "stopVibrationAndSound exception: " + ex)
        }
    }

    fun triggerNotification(alarmType: AlarmType, context: Context, forTest: Boolean = false) {
        try {
            Log.d(LOG_ID, "triggerNotification called for $alarmType - active=$active - curNotification=$curNotification - forTest=$forTest")
            if (getAlarmState(context) == AlarmState.ACTIVE || forTest) {
                stopCurrentNotification(context, true)  // do not send stop to client! -> to prevent, that the client will stop the newly created notification!
                curNotification = getNotificationId(alarmType)
                retriggerCount = 0
                retriggerOnDestroy = false
                retriggerTime = getTriggerTime(alarmType)
                curAlarmTime = System.currentTimeMillis()
                curTestAlarmType = if(forTest)
                    alarmType
                else
                    AlarmType.NONE
                Log.i(LOG_ID, "Create notification for $alarmType with ID=$curNotification - triggerTime=$retriggerTime")
                checkCreateSound(alarmType, context)
                if(canShowNotification())
                    showNotification(alarmType, context)
                startVibrationAndSound(alarmType, context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    open fun executeTest(alarmType: AlarmType, context: Context, fromIntern: Boolean = true) {
        Log.v(LOG_ID, "executeTest called for $alarmType")
        triggerNotification(alarmType, context, fromIntern)
    }

    fun triggerDelay(action: TriggerAction, alarmType: AlarmType, context: Context, delaySeconds: Float) {
        stopTrigger()
        Log.i(LOG_ID, "Trigger action $action for $alarmType in $delaySeconds seconds")
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var hasExactAlarmPermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(!alarmManager!!.canScheduleExactAlarms()) {
                Log.d(LOG_ID, "Need permission to set exact alarm!")
                hasExactAlarmPermission = false
            }
        }
        val intent = Intent(context, AlarmIntentReceiver::class.java)
        intent.action = action.toString()
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE, alarmType.ordinal)
        alarmPendingIntent = PendingIntent.getBroadcast(
            context,
            800,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val alarmTime = System.currentTimeMillis() + (delaySeconds*1000).toInt()
        if (hasExactAlarmPermission) {
            alarmManager!!.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime,
                alarmPendingIntent!!
            )
        } else {
            alarmManager!!.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime,
                alarmPendingIntent!!
            )
        }
    }

    private fun stopTrigger() {
        if(alarmManager != null && alarmPendingIntent != null) {
            Log.d(LOG_ID, "Stop trigger")
            alarmManager!!.cancel(alarmPendingIntent!!)
            alarmManager = null
            alarmPendingIntent = null
        }
    }

    fun triggerTest(alarmType: AlarmType, context: Context) {
        triggerDelay(TriggerAction.TEST_ALARM, alarmType, context, 3F)
    }

    private fun showNotification(alarmType: AlarmType, context: Context) {
        Channels.getNotificationManager(context).notify(
            curNotification,
            createNotification(context, alarmType)
        )
    }

    abstract fun adjustNoticiationChannel(context: Context, channel: NotificationChannel)

    private fun createNotificationChannel(context: Context) {
        Log.v(LOG_ID, "createNotificationChannel called")

        val channel = Channels.getNotificationChannel(context, ChannelType.ALARM, false)

        adjustNoticiationChannel(context, channel)

        Channels.getNotificationManager(context).createNotificationChannel(channel)

        // TODO: remove
        Channels.getNotificationManager(context).deleteNotificationChannel("gdh_alarm_notification_sound")
    }

    protected fun createSnoozeIntent(context: Context, snoozeTime: Long, noticationId: Int): PendingIntent {
        val intent = Intent(Constants.ALARM_SNOOZE_ACTION)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_TIME, snoozeTime)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, noticationId)
        intent.setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, snoozeTime.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    protected fun createStopIntent(context: Context, noticationId: Int, startApp: Boolean = false): PendingIntent {
        val intent = Intent(Constants.ALARM_STOP_NOTIFICATION_ACTION)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, noticationId)
        if(startApp)
            intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_START_APP, true)
        intent.setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, 888, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    protected fun createSnoozeAction(context: Context, title: String, snoozeTime: Long, notificationId: Int): Notification.Action {
        return Notification.Action.Builder(null, title, createSnoozeIntent(context, snoozeTime, notificationId)).build()
    }

    protected fun createStopAction(context: Context, title: String, notificationId: Int): Notification.Action {
        return Notification.Action.Builder(null, title, createStopIntent(context, notificationId)).build()
    }

    open fun buildNotification(notificationBuilder: Notification.Builder, context: Context, alarmType: AlarmType) {}

    private fun createNotification(context: Context, alarmType: AlarmType): Notification? {
        Log.v(LOG_ID, "createNotification called for $alarmType")
        val channelId = getChannelId()
        val resId = getAlarmTextRes(alarmType)
        if (resId == null)
            return null

        val notificationBuilder = Notification.Builder(context, channelId)
            .setSmallIcon(CR.mipmap.ic_launcher)
            //.setContentIntent(createStopIntent(context, getNotificationId(alarmType), true))
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
            .setContentText(ReceiveData.getGlucoseAsString()  + " (Δ " + ReceiveData.getDeltaAsString() + ")")

        val extender = Notification.WearableExtender()
        extender.addAction(createStopAction(context, context.resources.getString(CR.string.btn_dismiss), getNotificationId(alarmType)))
        if (getAddSnooze()) {
            extender
                .addAction(createSnoozeAction(context, context.getString(CR.string.snooze) + ": 60", 60L, getNotificationId(alarmType)))
                .addAction(createSnoozeAction(context, context.getString(CR.string.snooze) + ": 90", 90L, getNotificationId(alarmType)))
                .addAction(createSnoozeAction(context, context.getString(CR.string.snooze) + ": 120", 120L, getNotificationId(alarmType)))
        }
        notificationBuilder.extend(extender)

        buildNotification(notificationBuilder, context, alarmType)
        return notificationBuilder.build()
    }

    private fun startVibrationAndSound(alarmType: AlarmType, context: Context, reTrigger: Boolean = false) {
        if(!reTrigger) {
            val soundDelay = getSoundDelay(alarmType)
            Log.i(LOG_ID, "Start vibration and sound with $soundDelay seconds delay")
            if(soundDelay > 0) {
                vibrate(alarmType, context, true)
                if(getSound(alarmType, context) != null) {
                    triggerDelay(TriggerAction.START_ALARM_SOUND, alarmType, context, soundDelay.toFloat())
                    return
                }
                triggerDelay(TriggerAction.STOP_VIBRATION, alarmType, context, soundDelay.toFloat())
                return
            }
        }

        if(startDelayThread!=null && startDelayThread!!.isAlive) {
            Log.w(LOG_ID, "Start sound thread is already running!")
            return
        }

        if(curNotification > 0) {
            // else
            startDelayThread = Thread {
                val startDelay = getStartDelayMs(context)
                Log.i(LOG_ID, "Start sound and vibration with a delay of $startDelay ms")
                if (startDelay > 0)
                    Thread.sleep(startDelay.toLong())
                if (curNotification > 0) {
                    Log.d(LOG_ID, "Start sound and vibration")
                    vibrate(alarmType, context, false)
                    val duration = startSound(alarmType, context, false)
                    checkRetriggerAndAutoClose(context, duration)
                }
            }
            startDelayThread!!.start()
        }
    }


    open fun vibrate(alarmType: AlarmType, context: Context, repeat: Boolean = false, vibrateOnly: Boolean = false) : Int {
        try {
            if (getRingerMode() >= AudioManager.RINGER_MODE_VIBRATE && curNotification > 0) {
                val vibratePattern = getVibrationPattern(alarmType) ?: return 0
                val duration = if(repeat) -1 else vibratePattern.sum().toInt()
                Log.i(LOG_ID, "start vibration for $alarmType - repeat: $repeat - duration: $duration ms")
                vibrator.cancel()
                vibrator.vibrate(VibrationEffect.createWaveform(vibratePattern, if(repeat) 1 else -1))
                return duration
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "vibrate exception: " + ex)
        }
        return 0
    }

    fun startSound(alarmType: AlarmType, context: Context, restartVibration: Boolean, forTest: Boolean = false): Int {
        try {
            var soundDuration = 0
            if (getRingerMode() >= AudioManager.RINGER_MODE_NORMAL && (curNotification > 0 || forTest)) {
                val soundUri = getSound(alarmType, context, forTest)
                if (soundUri != null && !isRingtonePlaying()) {
                    ringtoneRWLock.write {
                        if (ringtone != null && ringtone!!.isPlaying) {
                            Log.w(LOG_ID,"Ringtone still playing!")
                            return 0
                        }
                        val player = MediaPlayer.create(context, soundUri)
                        soundDuration = player.duration
                        player.release()
                        Log.i(LOG_ID, "Play ringtone $soundUri - use alarm: $useAlarmSound - length: {${soundDuration} ms}")
                        ringtone = RingtoneManager.getRingtone(context, soundUri)
                        val aa = AudioAttributes.Builder()
                            .setUsage(if (useAlarmSound) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        ringtone!!.setAudioAttributes(aa)
                        ringtone!!.play()
                    }
                }
            }
            var vibrateDuration = 0
            if (restartVibration) {
                vibrateDuration = vibrate(alarmType, context, false)
            }
            return maxOf(soundDuration, vibrateDuration)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception while starting sound for alarm $alarmType - restartVibration=$restartVibration - forTest=${forTest}: ${exc.message}")
        }
        return 0
    }

    fun isRingtonePlaying(): Boolean {
        ringtoneRWLock.read {
            if (ringtone != null)
                return ringtone!!.isPlaying
        }
        return false
    }

    fun forceDnd(): Boolean {
        if(!Channels.getNotificationManager().isNotificationPolicyAccessGranted &&
            ( Channels.getNotificationManager().currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL ||
                    audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) ) {
            Log.d(LOG_ID, "Force DnD is active")
            return true
        }
        return false
    }

    protected fun getRingerMode(): Int {
        if(forceDnd())
            return AudioManager.RINGER_MODE_NORMAL  // DnD is on and can not be changed, so do not change ringer mode
        if(Channels.getNotificationManager().currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) {
            lastDndMode = Channels.getNotificationManager().currentInterruptionFilter
            Log.i(LOG_ID, "Disable DnD in level $lastDndMode")
            Channels.getNotificationManager()
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            Thread.sleep(100)
        }
        Log.i(LOG_ID, "Current ringer mode ${audioManager.ringerMode}")
        return audioManager.ringerMode
    }

    protected fun checkCreateSound(alarmType: AlarmType, context: Context) {
        try {
            Log.v(LOG_ID, "checkCreateSound called for force sound=$forceSound - vibration=$forceVibration - DnD=${Channels.getNotificationManager().currentInterruptionFilter} - ringmode=${audioManager.ringerMode}")
            lastRingerMode = -1
            lastDndMode = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            if (forceSound || forceVibration) {
                if (getRingerMode() < AudioManager.RINGER_MODE_NORMAL) {
                    val channelId = getChannelId()
                    val channel = Channels.getNotificationManager().getNotificationChannel(channelId)
                    Log.d(LOG_ID, "Channel prio=${channel.importance}")
                    if(channel.importance >= NotificationManager.IMPORTANCE_DEFAULT) { // notification supports sound
                        val soundMode = getSoundMode(alarmType, context)
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
            if(audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val soundLevel = getSoundLevel(alarmType)
                if (soundLevel >= 0) {
                    lastSoundLevel = getCurrentSoundLevel()
                    Log.d(LOG_ID, "Set cur sound level $lastSoundLevel to $soundLevel")
                    setSoundLevel(soundLevel)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkCreateSound exception: " + exc.message.toString() )
        }
    }

    fun setSoundLevel(level: Int) {
        val soundLevel = minOf(level, getMaxSoundLevel())
        Log.i(LOG_ID, "setSoundLevel: $soundLevel")
        audioManager.setStreamVolume(
            if(useAlarmSound)AudioManager.STREAM_ALARM else AudioManager.STREAM_RING,
            soundLevel,
            0
        )
    }

    fun getMaxSoundLevel(): Int {
        return audioManager.getStreamMaxVolume(if(useAlarmSound)AudioManager.STREAM_ALARM else AudioManager.STREAM_RING)
    }

    fun getCurrentSoundLevel(): Int {
        return audioManager.getStreamVolume(if(useAlarmSound)AudioManager.STREAM_ALARM else AudioManager.STREAM_RING)
    }

    open fun getSoundMode(alarmType: AlarmType, context: Context): SoundMode {
        val channelId = getChannelId()
        val channel = Channels.getNotificationManager().getNotificationChannel(channelId)
        Log.d(LOG_ID, "Channel: prio=${channel.importance}")
        if(channel.importance >= NotificationManager.IMPORTANCE_DEFAULT) {
            if(getSound(alarmType, context) != null)
                return SoundMode.NORMAL
            return SoundMode.VIBRATE
        } else if(channel.importance == NotificationManager.IMPORTANCE_NONE)
            return SoundMode.OFF
        return SoundMode.SILENT
    }

    fun channelActive(context: Context): Boolean {
        return Channels.notificationChannelActive(context, getChannel())
    }

    protected fun checkRecreateSound() {
        try {
            if(lastSoundLevel >= 0) {
                Log.i(LOG_ID, "Reset sound level to $lastSoundLevel")
                setSoundLevel(lastSoundLevel)
                lastSoundLevel = -1
            }
            if(lastRingerMode >= 0 ) {
                Log.i(LOG_ID, "Reset ringer mode to $lastRingerMode")
                audioManager.ringerMode = lastRingerMode
                lastRingerMode = -1
            }
            if(lastDndMode != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                Log.i(LOG_ID, "Reset DnD mode to $lastDndMode")
                Channels.getNotificationManager().setInterruptionFilter(lastDndMode)
                lastDndMode = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }
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

    fun getChannel(): ChannelType {
        return ChannelType.ALARM
    }

    fun getChannelId(): String {
        return getChannel().channelId
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
    private fun getSound(alarmType: AlarmType, context: Context, forTest: Boolean = false): Uri? {
        if(vibrateOnly && !forTest)
            return null
        if (alarmType.setting == null)
            return null
        if(alarmType.setting.useCustomSound) {
            val path = alarmType.setting.customSoundPath
            if(path.isEmpty())
                return null
            return Uri.parse(path)
        }
        return getDefaultAlarm(alarmType, context)
    }

    private fun getSoundLevel(alarmType: AlarmType): Int {
        if (alarmType.setting == null)
            return -1
        return alarmType.setting.soundLevel
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

    protected fun getUri(resId: Int, context: Context): Uri {
        val uri = "android.resource://" + context.packageName + "/" + resId
        return Uri.parse(uri)
    }

    private fun getVibrationPattern(alarmType: AlarmType): LongArray? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000)
            AlarmType.LOW -> longArrayOf(0, 700, 500, 700, 500, 700, 500, 700)
            AlarmType.HIGH -> longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)
            AlarmType.VERY_HIGH -> longArrayOf(0, 800, 500, 800, 800, 600, 800, 800, 500, 800, 800, 600, 800)
            AlarmType.OBSOLETE -> longArrayOf(0, 600, 500, 500, 500, 600, 500, 500)
            else -> null
        }
    }

    private fun getTriggerTime(alarmType: AlarmType): Int {
        if (alarmType.setting!=null) {
            return alarmType.setting.retriggerTime
        }
        return 0
    }

    private fun getSoundDelay(alarmType: AlarmType): Int {
        if (alarmType.setting!=null) {
            return alarmType.setting.soundDelay
        }
        return 0
    }

    abstract fun getStartDelayMs(context: Context): Int

    private val alarmStatePreferences = mutableSetOf(
        Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED,
        Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_WEAR_CONNECTED,
        Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED,
        Constants.SHARED_PREF_NOTIFICATION_VIBRATE
    )

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FORCE_SOUND)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FORCE_VIBRATION)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NOTIFICATION_VIBRATE)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NOTIFICATION_AUTO_CLOSE)
            } else {
                when(key) {
                    Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED -> setEnabled(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, enabled))
                    Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION -> setAddSnooze(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION, addSnooze))
                    Constants.SHARED_PREF_ALARM_FORCE_SOUND -> forceSound = sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_SOUND, forceSound)
                    Constants.SHARED_PREF_ALARM_FORCE_VIBRATION -> forceVibration = sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_VIBRATION, forceVibration)
                    Constants.SHARED_PREF_NOTIFICATION_VIBRATE -> vibrateOnly = sharedPreferences.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, vibrateOnly)
                    Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND -> useAlarmSound = sharedPreferences.getBoolean(Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND, useAlarmSound)
                    Constants.SHARED_PREF_NOTIFICATION_AUTO_CLOSE -> autoCloseNotification = sharedPreferences.getBoolean(Constants.SHARED_PREF_NOTIFICATION_AUTO_CLOSE, autoCloseNotification)
                }
            }
            if(alarmStatePreferences.contains(key))
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.ALARM_STATE_CHANGED, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    open fun stopNotificationForRetrigger(): Boolean = false

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for $dataSource")
            when(dataSource) {
                NotifySource.ALARM_TRIGGER -> {
                    triggerNotification(ReceiveData.getAlarmType(), context)
                }
                NotifySource.OBSOLETE_ALARM_TRIGGER -> {
                    triggerNotification(AlarmType.OBSOLETE, context)
                }
                NotifySource.ALARM_STATE_CHANGED -> {
                    getAlarmState(context)
                }
                else -> Log.w(LOG_ID, "Unsupported source $dataSource")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
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

    private fun checkRetriggerAndAutoClose(context: Context, soundDuration: Int = 0): Boolean {
        if(isTriggerActive()) {
            val elapsedTimeMin = Utils.round((System.currentTimeMillis() - curAlarmTime).toFloat()/60000, 0, RoundingMode.DOWN).toLong()
            val nextTriggerMin = (elapsedTimeMin/retriggerTime)*retriggerTime + retriggerTime
            val nextAlarmTime = curAlarmTime + TimeUnit.MINUTES.toMillis(nextTriggerMin)
            val timeInMillis = nextAlarmTime - System.currentTimeMillis()
            Log.d(LOG_ID,
                "elapsed: $elapsedTimeMin nextTrigger: $nextTriggerMin - retrigger-time: $retriggerTime - in $timeInMillis ms"
            )
            Log.i(LOG_ID, "Retrigger sound after $nextTriggerMin minute(s) at ${DateFormat.getTimeInstance(
                DateFormat.DEFAULT).format(nextAlarmTime)} (alarm from ${DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(curAlarmTime))})")
            retriggerCount++
            triggerDelay(TriggerAction.RETRIGGER_SOUND, getAlarmType(), context, (timeInMillis.toFloat()/1000))
            return true
        } else if(autoCloseNotification && curNotification > 0 && soundDuration >= 0) {
            if(soundDuration==0){
                stopCurrentNotification(context)
            } else {
                val delaySec = maxOf((soundDuration.toFloat()/1000)+3F, 30F)  // at least 30 seconds delay
                Log.i(LOG_ID, "Trigger close notification after $delaySec seconds")
                triggerDelay(TriggerAction.CLOSE_NOTIFICATION, getAlarmType(), context, delaySec)
            }
        }
        return false
    }

    fun hasFullscreenPermission(context: Context? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return Channels.getNotificationManager(context).canUseFullScreenIntent()
        return true
    }

    open fun canReshowNotification(): Boolean = canShowNotification()
    open fun canShowNotification(): Boolean = true

    fun handleTimerAction(context: Context, action: String, extras: Bundle?) {
        Log.d(LOG_ID, "handleTimerAction called for ${action} with extras: ${Utils.dumpBundle(extras)}")
        if(extras?.containsKey(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE) == true && instance != null) {
            val alarmType = AlarmType.fromIndex(extras.getInt(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE, ReceiveData.getAlarmType().ordinal))
            when(TriggerAction.valueOf(action)) {
                TriggerAction.TEST_ALARM -> {
                    executeTest(alarmType, context)
                    GlucoDataService.sendCommand(Command.TEST_ALARM, extras)
                }
                TriggerAction.START_ALARM_SOUND -> {
                    val duration = startSound(alarmType, context, true)
                    checkRetriggerAndAutoClose(context, duration)
                }
                TriggerAction.STOP_VIBRATION -> {
                    stopVibrationAndSound()
                    checkRetriggerAndAutoClose(context, 0)
                }
                TriggerAction.RETRIGGER_SOUND -> {
                    if(canReshowNotification())
                        showNotification(alarmType, context)
                    startVibrationAndSound(alarmType, context, true)
                }
                TriggerAction.CLOSE_NOTIFICATION -> {
                    stopCurrentNotification(context)
                }
            }
        }
    }
}

class AlarmIntentReceiver: BroadcastReceiver() {
    private val LOG_ID = "GDH.AlarmIntentReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(LOG_ID, "onReceive called for ${intent.action} with extras: ${Utils.dumpBundle(intent.extras)}")
            AlarmNotificationBase.instance!!.handleTimerAction(context, intent.action!!, intent.extras)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceive exception: " + exc.toString())
        }
    }
}