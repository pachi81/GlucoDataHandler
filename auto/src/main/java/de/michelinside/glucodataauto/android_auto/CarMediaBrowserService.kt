package de.michelinside.glucodataauto.android_auto

import de.michelinside.glucodataauto.R
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media.MediaBrowserServiceCompat
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import android.media.AudioAttributes
import android.support.v4.media.session.MediaControllerCompat
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.utils.Utils
import java.io.File
import de.michelinside.glucodatahandler.common.R as CR
lateinit var audioManager: AudioManager


class CarMediaBrowserService: MediaBrowserServiceCompat(), NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AA.CarMediaBrowserService"
    private val MEDIA_ROOT_ID = "root"
    private val MEDIA_GLUCOSE_ID = "glucose_value"
    private val MEDIA_NOTIFICATION_TOGGLE_ID = "toggle_notification"
    private lateinit var  sharedPref: SharedPreferences
    private lateinit var session: MediaSessionCompat
    private lateinit var mediaController: MediaControllerCompat
    private val player = MediaPlayer()
    private var curMediaItem = MEDIA_GLUCOSE_ID
    private var file: File? = null
    private var create = true
    private var last_speak_time = 0L

    companion object {
        var active = false
    }

    override fun onCreate() {
        Log.d(LOG_ID, "onCreate")
        try {
            super.onCreate()
            active = true
            GlucoDataServiceAuto.init(this)
            GlucoDataServiceAuto.start(this)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            audioManager = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            session = MediaSessionCompat(this, "MyMusicService")
            val audioFocusTransientRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.i(LOG_ID, "Audio focus change: $focusChange")
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            session.isActive = true
                            // Start or resume playback
                        }
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            session.isActive = false
                            // Stop playback
                        }
                        // Handle other focus changes as needed
                    }
                }
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()

            var curAudioFocus: AudioFocusRequest? = null

            // Callbacks to handle events from the user (play, pause, search)
            session.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                    Log.i(LOG_ID, "onPlayFromMediaId: " + mediaId)
                    curMediaItem = mediaId
                    setItem(true)
                }

                override fun onPlay() {
                    Log.i(LOG_ID, "onPlay called for $curMediaItem - create: $create")
                    try {
                        if(curMediaItem == MEDIA_GLUCOSE_ID) {
                            onStop()
                            // Current song is ready, but paused, so start playing the music.
                            var uri: String? = null
                            var requestAudioFocus = false
                            if(!create && sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false)) {
                                file = TextToSpeechUtils.getAsFile(ReceiveData.getAsText(applicationContext, false, false))
                                if(file != null) {
                                    uri = file!!.absolutePath
                                    requestAudioFocus = true
                                    last_speak_time = ReceiveData.time
                                }
                            }
                            if(uri.isNullOrEmpty()) {
                                uri = "android.resource://" + applicationContext.packageName + "/" + CR.raw.silence
                                requestAudioFocus = false
                            }
                            Log.d(LOG_ID, "onPlay uri: $uri")
                            player.reset()
                            player.setDataSource(applicationContext, Uri.parse(uri))
                            player.prepare()
                            player.setOnCompletionListener {
                                Log.d(LOG_ID, "setOnCompletionListener called")
                                onStop()
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
                            // Update the UI to show we are playing.
                            session.setPlaybackState(buildState(PlaybackState.STATE_PLAYING))
                        } else if(curMediaItem == MEDIA_NOTIFICATION_TOGGLE_ID) {
                            Log.d(LOG_ID, "Toggle notification")
                            with(sharedPref.edit()) {
                                putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, !CarNotification.enable_notification)
                                apply()
                            }
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "onPlay exception: " + exc.message.toString() )
                    }
                }

                override fun onStop() {
                    Log.i(LOG_ID, "onStop called playing: ${player.isPlaying}")
                    try {
                        if(player.isPlaying) {
                            player.stop()
                        }
                        if(file != null) {
                            Log.d(LOG_ID, "delete file ${file!!.absolutePath}")
                            file?.delete()
                            file = null
                        }
                        session.setPlaybackState(buildState(PlaybackState.STATE_STOPPED))
                        if(curAudioFocus!=null) {
                            Log.d(LOG_ID, "abandonAudioFocusRequest")
                            audioManager.abandonAudioFocusRequest(curAudioFocus!!)
                            curAudioFocus = null
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "onStop exception: " + exc.message.toString() )
                    }
                }
            })

            sessionToken = session.sessionToken
            mediaController = MediaControllerCompat(this, session.sessionToken)
            TextToSpeechUtils.initTextToSpeech(this)
            InternalNotifier.addNotifier(this, this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.SETTINGS,
                NotifySource.TIME_VALUE))

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onDestroy() {
        Log.d(LOG_ID, "onDestroy")
        try {
            active = false
            InternalNotifier.remNotifier(this, this)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            session.release()
            GlucoDataServiceAuto.stop(this)
            super.onDestroy()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.message.toString() )
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        try {
            Log.d(LOG_ID, "onGetRoot - package: " + clientPackageName + " - UID: " + clientUid.toString())
            return BrowserRoot(MEDIA_ROOT_ID, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onGetRoot exception: " + exc.message.toString() )
        }
        return null
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        try {
            Log.d(LOG_ID, "onLoadChildren for parent: " + parentId)
            if (MEDIA_ROOT_ID == parentId) {
                val items = mutableListOf(createMediaItem())
                if (Channels.notificationChannelActive(this, ChannelType.ANDROID_AUTO)) {
                    items.add(createToggleItem())
                }
                result.sendResult(items)
            } else {
                result.sendResult(null)
            }
            setItem(create)
            create = false
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onLoadChildren exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for source $dataSource")
        try {
            notifyChildrenChanged(MEDIA_ROOT_ID)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
        try {
            when(key) {
                Constants.SHARED_PREF_CAR_NOTIFICATION,
                Constants.SHARED_PREF_CAR_MEDIA,
                Constants.SHARED_PREF_CAR_MEDIA_ICON_STYLE -> {
                    notifyChildrenChanged(MEDIA_ROOT_ID)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }

    private fun getIcon(size: Int = 100): Bitmap? {
        return when(sharedPref.getString(Constants.SHARED_PREF_CAR_MEDIA_ICON_STYLE, Constants.AA_MEDIA_ICON_STYLE_GLUCOSE_TREND)) {
            Constants.AA_MEDIA_ICON_STYLE_TREND -> {
                BitmapUtils.getRateAsBitmap(width = size, height = size)
            }
            Constants.AA_MEDIA_ICON_STYLE_GLUCOSE -> {
                BitmapUtils.getGlucoseAsBitmap(width = size, height = size)
            }
            else -> {
                BitmapUtils.getGlucoseTrendBitmap(width = size, height = size)
            }
        }
    }

    fun setItem(onCreate: Boolean) {
        try {
            Log.d(LOG_ID, "set current media: $curMediaItem")
            when(curMediaItem) {
                MEDIA_GLUCOSE_ID -> {
                    setGlucose(onCreate)
                }
                MEDIA_NOTIFICATION_TOGGLE_ID -> {
                    curMediaItem = MEDIA_GLUCOSE_ID
                    Log.d(LOG_ID, "Toggle notification")
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, !CarNotification.enable_notification)
                        apply()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setItem exception: " + exc.message.toString() )
        }
    }

    private fun setGlucose(onCreate: Boolean = false) {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_CAR_MEDIA,true)) {
            Log.i(LOG_ID, "setGlucose called")
            session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED))
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                        ReceiveData.getGlucoseAsString() + " (Δ " + ReceiveData.getDeltaAsString() + ")"
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                        ReceiveData.getElapsedTimeMinuteAsString(this)
                    )
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, getIcon(400)!!)
                    .build()
            )
            if(!onCreate && checkSpeakNewValue()) {
                Log.i(LOG_ID, "speak new value")
                mediaController.transportControls.play()
            }
        } else {
            session.setPlaybackState(buildState(PlaybackState.STATE_NONE))
        }
    }

    private fun getTimeDiffMinute(): Long {
        return Utils.round((ReceiveData.time-last_speak_time).toFloat()/60000, 0).toLong()
    }

    private fun checkSpeakNewValue(): Boolean {
        if(!sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false))
            return false
        if(!sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false))
            return false
        if(ReceiveData.getAlarmType() == AlarmType.VERY_LOW)
            return true
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

    private fun createMediaItem(): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_GLUCOSE_ID)
            .setTitle(ReceiveData.getGlucoseAsString() + " (Δ " + ReceiveData.getDeltaAsString() + ")\n" + ReceiveData.getElapsedTimeMinuteAsString(this))
            //.setSubtitle(ReceiveData.timeformat.format(Date(ReceiveData.time)))
            .setIconBitmap(getIcon()!!)
        return MediaBrowserCompat.MediaItem(
            mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun getToggleIcon(): Bitmap? {
        if(CarNotification.enable_notification) {
            return ContextCompat.getDrawable(applicationContext, R.drawable.icon_popup_white)?.toBitmap()
        }
        return ContextCompat.getDrawable(applicationContext, R.drawable.icon_popup_off_white)?.toBitmap()
    }

    private fun setToggle() {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_CAR_MEDIA,true)) {
            Log.i(LOG_ID, "setToggle called")
            session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED))
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, resources.getString(if(CarNotification.enable_notification) CR.string.gda_notifications_on else CR.string.gda_notifications_off)
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                        resources.getString(CR.string.gda_media_notification_toggle_action)
                    )
                    //.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, getToggleIcon())
                    .build()
            )
        } else {
            session.setPlaybackState(buildState(PlaybackState.STATE_NONE))
        }
    }

    private fun createToggleItem(): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_NOTIFICATION_TOGGLE_ID)
            .setTitle(resources.getString(CR.string.gda_media_notification_toggle_title))
            .setSubtitle(resources.getString(if(CarNotification.enable_notification) CR.string.gda_notifications_on else CR.string.gda_notifications_off))
            .setIconBitmap(getToggleIcon())
        return MediaBrowserCompat.MediaItem(
            mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun buildState(state: Int): PlaybackStateCompat? {
        Log.d(LOG_ID, "buildState called for state $state - pos: ${player.currentPosition}")
        return PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
            .setState(
                state,
                player.currentPosition.toLong(),
                1f,
                SystemClock.elapsedRealtime()
            )
            .build()
    }

}

