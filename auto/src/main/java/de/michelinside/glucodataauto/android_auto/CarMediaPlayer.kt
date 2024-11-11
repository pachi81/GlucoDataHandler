package de.michelinside.glucodataauto.android_auto

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import java.io.File

abstract class CarMediaPlayerCallback {
    abstract fun onPlay()
    abstract fun onStop()
}

object CarMediaPlayer: NotifierInterface {
    private val LOG_ID = "GDH.AA.CarMediaPlayer"
    private lateinit var  sharedPref: SharedPreferences
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusTransientRequest: AudioFocusRequest
    private val player = MediaPlayer()
    private var file: File? = null
    private var last_speak_time = 0L
    private var enabled = false
    private var curAudioFocus: AudioFocusRequest? = null
    private var callback: CarMediaPlayerCallback? = null
    private var forceNextNotify = false

    fun enable(context: Context) {
        if(enabled) {
           return
        }
        Log.i(LOG_ID, "enable called")

        try {
            sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioFocusTransientRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.i(LOG_ID, "Audio focus change: $focusChange")
                }
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()

            TextToSpeechUtils.initTextToSpeech(context)
            InternalNotifier.addNotifier(context, this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.OBSOLETE_ALARM_TRIGGER))
            enabled = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    fun disable(context: Context) {
        if(enabled) {
            Log.i(LOG_ID, "disable called")
            try {
                stopMedia()
                enabled = false
                InternalNotifier.remNotifier(context, this)
                callback = null
            } catch (exc: Exception) {
                Log.e(LOG_ID, "onDestroy exception: " + exc.message.toString() )
            }
        }
    }

    fun play(context: Context, playSilent: Boolean = false): Boolean {
        if(enabled) {
            Log.d(LOG_ID, "play called")
            playMedia(context, playSilent)
            return true
        }
        return false
    }

    fun stop() {
        if(enabled) {
            Log.d(LOG_ID, "stop called")
            stopMedia()
        }
    }

    fun setCallback(callback: CarMediaPlayerCallback?) {
        this.callback = callback
    }

    val currentPosition: Int get() {
        if(enabled) {
            return player.currentPosition
        }
        return 0
    }

    val isPlaying: Boolean get() {
        if(enabled) {
            return player.isPlaying
        }
        return false
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for source $dataSource")
        try {
            if(checkSpeakNewValue(dataSource)) {
                Log.i(LOG_ID, "speak new value")
                play(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    private fun playMedia(context: Context, playSilent: Boolean = false) {
        Log.i(LOG_ID, "playMedia called - playSilent=$playSilent")
        try {
            stopMedia()
            // Current song is ready, but paused, so start playing the music.
            var uri: String? = null
            var requestAudioFocus = false
            if(!playSilent) {
                file = TextToSpeechUtils.getAsFile(ReceiveData.getAsText(context, false, false))
                if(file != null) {
                    uri = file!!.absolutePath
                    last_speak_time = ReceiveData.time
                    requestAudioFocus = true
                }
            } else {  // play silent
                uri = "android.resource://" + context.packageName + "/" + R.raw.silence
                requestAudioFocus = false
            }
            if(!uri.isNullOrEmpty()) {
                Log.d(LOG_ID, "onPlay uri: $uri")
                player.reset()
                player.setDataSource(context, Uri.parse(uri))
                player.prepare()
                player.setOnCompletionListener {
                    Log.d(LOG_ID, "setOnCompletionListener called")
                    stopMedia()
                }
                if(requestAudioFocus) {
                    if(audioManager.requestAudioFocus(audioFocusTransientRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        Log.d(LOG_ID, "requestAudioFocus transient")
                        curAudioFocus = audioFocusTransientRequest
                    } else {
                        Log.w(LOG_ID, "requestAudioFocus transient failed")
                    }
                }
                player.start()
                if(callback != null)
                    callback!!.onPlay()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPlay exception: " + exc.message.toString() )
        }
    }

    private fun stopMedia() {
        Log.i(LOG_ID, "stopMedia called playing: ${player.isPlaying}")
        try {
            if(player.isPlaying) {
                player.stop()
            }
            if(callback != null)
                callback!!.onStop()
            if(file != null) {
                Log.d(LOG_ID, "delete file ${file!!.absolutePath}")
                file?.delete()
                file = null
            }
            if(curAudioFocus!=null) {
                Log.d(LOG_ID, "abandonAudioFocusRequest")
                audioManager.abandonAudioFocusRequest(curAudioFocus!!)
                curAudioFocus = null
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStop exception: " + exc.message.toString() )
        }
    }

    private fun getTimeDiffMinute(): Long {
        return Utils.round((ReceiveData.time-last_speak_time).toFloat()/60000, 0).toLong()
    }

    private fun checkSpeakNewValue(dataSource: NotifySource): Boolean {
        if(!sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false)) {
            Log.d(LOG_ID, "Check speak new value: disabled")
            return false
        }
        if(ReceiveData.getAlarmType() == AlarmType.VERY_LOW) {
            Log.d(LOG_ID, "Check speak new value: alarm == VERY_LOW")
            forceNextNotify = true
            return true
        }
        if(forceNextNotify) {
            Log.d(LOG_ID, "Check speak new value: forceNextNotify")
            forceNextNotify = false
            return true
        }

        if (dataSource == NotifySource.OBSOLETE_ALARM_TRIGGER) {
            Log.d(LOG_ID, "Obsolete alarm triggered")
            forceNextNotify = true
            return true
        }

        val interval = sharedPref.getInt(Constants.AA_MEDIA_PLAYER_SPEAK_INTERVAL, 1).toLong()
        Log.d(LOG_ID, "Check speak new value:"
                + "\nglucose-alarm:  " + ReceiveData.getAlarmType()
                + "\ndelta-alarm:    " + ReceiveData.getDeltaAlarmType()
                + "\nforce-alarm:    " + ReceiveData.forceAlarm
                + "\nspeak-elapsed:  " + getTimeDiffMinute()
                + "\nspeak-interval: " + interval
                + "\ndata-elapsed:   " + ReceiveData.getElapsedTimeMinute())
        if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_ALARM_ONLY, true) && !ReceiveData.forceAlarm)
            return false
        if(ReceiveData.forceAlarm)
            return true
        if (interval == 1L)
            return true
        if (interval > 1L && getTimeDiffMinute() >= interval && ReceiveData.getElapsedTimeMinute() == 0L)
            return true
        return false
    }

}