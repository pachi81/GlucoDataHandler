package de.michelinside.glucodataauto.android_auto

import de.michelinside.glucodataauto.R
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.session.PlaybackState
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
import android.support.v4.media.session.MediaControllerCompat
import de.michelinside.glucodatahandler.common.R as CR


class CarMediaBrowserService: MediaBrowserServiceCompat(), NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AA.CarMediaBrowserService"
    private val MEDIA_ROOT_ID = "root"
    private val MEDIA_GLUCOSE_ID = "glucose_value"
    private val MEDIA_NOTIFICATION_TOGGLE_ID = "toggle_notification"
    private val MEDIA_SPEAK_TOGGLE_ID = "toggle_speak"
    private lateinit var  sharedPref: SharedPreferences
    private lateinit var session: MediaSessionCompat
    private lateinit var mediaController: MediaControllerCompat
    private var curMediaItem = MEDIA_ROOT_ID
    private var playBackState = PlaybackState.STATE_NONE
    private var lastGlucoseTime = 0L

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
            CarMediaPlayer.enable(this)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)

            session = MediaSessionCompat(this, "MyMusicService")
            // Callbacks to handle events from the user (play, pause, search)
            session.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                    Log.i(LOG_ID, "onPlayFromMediaId: " + mediaId)
                    curMediaItem = mediaId
                    setItem()
                }

                override fun onPlay() {
                    Log.i(LOG_ID, "onPlay called for $curMediaItem")
                    try {
                        if(curMediaItem == MEDIA_GLUCOSE_ID) {
                            // Current song is ready, but paused, so start playing the music.
                            if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false))
                                CarMediaPlayer.play(applicationContext, !sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false))
                            else
                                session.setPlaybackState(buildState(PlaybackState.STATE_PLAYING))
                        } else if(curMediaItem == MEDIA_NOTIFICATION_TOGGLE_ID) {
                            Log.d(LOG_ID, "Toggle notification")
                            with(sharedPref.edit()) {
                                putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, !CarNotification.enable_notification)
                                apply()
                            }
                        } else if(curMediaItem == MEDIA_SPEAK_TOGGLE_ID) {
                            Log.d(LOG_ID, "Toggle speak")
                            with(sharedPref.edit()) {
                                putBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, !sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false))
                                apply()
                            }
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "onPlay exception: " + exc.message.toString() )
                    }
                }

                override fun onStop() {
                    Log.i(LOG_ID, "onStop called playing")
                    try {
                        if(CarMediaPlayer.hasCallback())
                            CarMediaPlayer.stop()
                        else
                            session.setPlaybackState(buildState(PlaybackState.STATE_STOPPED))
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "onStop exception: " + exc.message.toString() )
                    }
                }
            })

            session.setPlaybackState(buildState(PlaybackState.STATE_STOPPED))

            // set callback depending on the current speak value to prevent speaking for values in background as affect on state!
            onSharedPreferenceChanged(sharedPref, Constants.AA_MEDIA_PLAYER_SPEAK_VALUES)

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
            CarMediaPlayer.setCallback(null)
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
                if(curMediaItem == MEDIA_ROOT_ID)
                    curMediaItem = MEDIA_GLUCOSE_ID
                val items = mutableListOf(createMediaItem())
                if (Channels.notificationChannelActive(this, ChannelType.ANDROID_AUTO)) {
                    items.add(createNotificationToggleItem())
                }
                if(TextToSpeechUtils.isAvailable()) {
                    items.add(createSpeakToggleItem())
                }
                result.sendResult(items)
            } else {
                result.sendResult(null)
            }
            setItem()
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
                Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE,
                Constants.SHARED_PREF_CAR_MEDIA,
                Constants.SHARED_PREF_CAR_MEDIA_ICON_STYLE -> {
                    notifyChildrenChanged(MEDIA_ROOT_ID)
                }
                Constants.AA_MEDIA_PLAYER_SPEAK_VALUES -> {
                    if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false)) {
                        CarMediaPlayer.setCallback(object : CarMediaPlayerCallback() {
                            override fun onPlay() {
                                Log.d(LOG_ID, "callback play called")
                                setGlucose()  // update duration for playing
                                session.setPlaybackState(buildState(PlaybackState.STATE_PLAYING))
                            }
                            override fun onStop() {
                                Log.d(LOG_ID, "callback onStop called")
                                session.setPlaybackState(buildState(PlaybackState.STATE_STOPPED))
                            }
                        })
                        session.setPlaybackState(buildState(PlaybackState.STATE_STOPPED))
                    } else {
                        CarMediaPlayer.setCallback(null)
                    }
                    notifyChildrenChanged(MEDIA_ROOT_ID)
                }
                Constants.AA_MEDIA_PLAYER_DURATION -> {
                    notifyChildrenChanged(MEDIA_ROOT_ID)
                    if(playBackState==PlaybackState.STATE_PLAYING) {
                        session.setPlaybackState(buildState(PlaybackState.STATE_PLAYING))
                    }
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

    fun setItem() {
        try {
            Log.d(LOG_ID, "set current media: $curMediaItem")
            when(curMediaItem) {
                MEDIA_GLUCOSE_ID -> {
                    setGlucose()
                }
                MEDIA_NOTIFICATION_TOGGLE_ID -> {
                    curMediaItem = MEDIA_GLUCOSE_ID
                    Log.d(LOG_ID, "Toggle notification")
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, !CarNotification.enable_notification)
                        apply()
                    }
                }
               MEDIA_SPEAK_TOGGLE_ID -> {
                   curMediaItem = MEDIA_GLUCOSE_ID
                    Log.d(LOG_ID, "Toggle speak")
                    with(sharedPref.edit()) {
                        putBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, !sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false))
                        apply()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setItem exception: " + exc.message.toString() )
        }
    }

    private fun setGlucose() {
        if (sharedPref.getBoolean(Constants.SHARED_PREF_CAR_MEDIA,true)) {
            Log.i(LOG_ID, "setGlucose called")
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
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration())
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, getIcon(400)!!)
                    .build()
            )
            if(playBackState == PlaybackState.STATE_PLAYING && lastGlucoseTime < ReceiveData.time) {
                // restart playing to have the current elapsed time
                //session.setPlaybackState(buildState(PlaybackState.STATE_STOPPED))
                session.setPlaybackState(buildState(PlaybackState.STATE_PLAYING))
            }
            lastGlucoseTime = ReceiveData.time
        } else {
            session.setPlaybackState(buildState(PlaybackState.STATE_NONE))
        }
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

    private fun getNotificationToggleIcon(): Bitmap? {
        if(CarNotification.enable_notification) {
            return ContextCompat.getDrawable(applicationContext, R.drawable.icon_popup_white)?.toBitmap()
        }
        return ContextCompat.getDrawable(applicationContext, R.drawable.icon_popup_off_white)?.toBitmap()
    }
/*
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
*/
    private fun createNotificationToggleItem(): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_NOTIFICATION_TOGGLE_ID)
            .setTitle(resources.getString(CR.string.gda_media_notification_toggle_title))
            .setSubtitle(resources.getString(if(CarNotification.enable_notification) CR.string.gda_notifications_on else CR.string.gda_notifications_off))
            .setIconBitmap(getNotificationToggleIcon())
        return MediaBrowserCompat.MediaItem(
            mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun getSpeakToggleIcon(): Bitmap? {
        if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false))
            return ContextCompat.getDrawable(applicationContext, CR.drawable.icon_volume_normal_white)?.toBitmap()
        else
            return ContextCompat.getDrawable(applicationContext, CR.drawable.icon_volume_off_white)?.toBitmap()
    }

    private fun createSpeakToggleItem(): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_SPEAK_TOGGLE_ID)
            .setTitle(resources.getString(CR.string.gda_media_speak_toggle_title))
            .setSubtitle(resources.getString(if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false)) CR.string.gda_speak_on else CR.string.gda_speak_off))
            .setIconBitmap(getSpeakToggleIcon())
        return MediaBrowserCompat.MediaItem(
            mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun buildState(state: Int): PlaybackStateCompat? {
        val duration = getDuration()
        val position = getPosition()
        Log.d(LOG_ID, "buildState called for state $state - pos: ${position}/${duration}")
        playBackState = state
        val bundleWithDuration = if (duration == 0L) null else Bundle().apply {
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration) // duration in Millisekunden
        }
        return PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
            .setState(
                state,
                position,
                1f,
                SystemClock.elapsedRealtime()
            ).setExtras(bundleWithDuration)
            .build()
    }

    private fun getPosition(): Long {
        return if(CarMediaPlayer.hasCallback())
            CarMediaPlayer.currentPosition
        else
            System.currentTimeMillis()-ReceiveData.time
    }

    private fun getDuration(): Long {
        return if(CarMediaPlayer.hasCallback())
            CarMediaPlayer.duration
        else
            sharedPref.getInt(Constants.AA_MEDIA_PLAYER_DURATION, 0) * 60000L
    }

}

