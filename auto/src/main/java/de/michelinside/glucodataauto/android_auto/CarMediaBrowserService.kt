package de.michelinside.glucodataauto.android_auto

import de.michelinside.glucodataauto.R
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import de.michelinside.glucodatahandler.common.R as CR


class CarMediaBrowserService: MediaBrowserServiceCompat(), NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AA.CarMediaBrowserService"
    private val MEDIA_ROOT_ID = "root"
    private val MEDIA_GLUCOSE_ID = "glucose_value"
    private val MEDIA_NOTIFICATION_TOGGLE_ID = "toggle_notification"
    private lateinit var  sharedPref: SharedPreferences
    private lateinit var session: MediaSessionCompat
    private val player = MediaPlayer()
    private var curMediaItem = MEDIA_GLUCOSE_ID

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
                            player.reset()
                            val uri =
                                "android.resource://" + applicationContext.packageName + "/" + CR.raw.silence
                            player.setDataSource(applicationContext, Uri.parse(uri))
                            player.setOnCompletionListener {
                                Log.d(LOG_ID, "setOnCompletionListener called")
                                onStop()
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
                        session.setPlaybackState(buildState(PlaybackState.STATE_STOPPED))
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "onStop exception: " + exc.message.toString() )
                    }
                }
            })

            sessionToken = session.sessionToken

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
            setItem()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onLoadChildren exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.v(LOG_ID, "OnNotifyData called")
        try {
            notifyChildrenChanged(MEDIA_ROOT_ID)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.v(LOG_ID, "onSharedPreferenceChanged called for key " + key)
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
            else -> {
                BitmapUtils.getGlucoseTrendBitmap(width = size, height = size)
            }
        }
    }

    fun setItem() {
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
        }
    }

    private fun setGlucose() {
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

