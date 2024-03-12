package de.michelinside.glucodataauto.android_auto

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
import androidx.media.MediaBrowserServiceCompat
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class CarMediaBrowserService: MediaBrowserServiceCompat(), NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AA.CarMediaBrowserService"
    private val MEDIA_ROOT_ID = "root"
    private val MEDIA_GLUCOSE_ID = "glucose_value"
    private lateinit var  sharedPref: SharedPreferences
    private lateinit var session: MediaSessionCompat

    companion object {
        var active = false
    }

    override fun onCreate() {
        Log.v(LOG_ID, "onCreate")
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
                    if (!sharedPref.getBoolean(Constants.SHARED_PREF_CAR_MEDIA,true)) {
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_CAR_MEDIA,true)
                            apply()
                        }
                        createMediaItem()
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
        Log.v(LOG_ID, "onDestroy")
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
            Log.v(LOG_ID, "onGetRoot - package: " + clientPackageName + " - UID: " + clientUid.toString())
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
            Log.v(LOG_ID, "onLoadChildren for parent: " + parentId)
            if (MEDIA_ROOT_ID == parentId) {
                result.sendResult(mutableListOf(createMediaItem()))
            } else {
                result.sendResult(null)
            }
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

    private fun createMediaItem(): MediaBrowserCompat.MediaItem {
        Log.v(LOG_ID, "createMediaItem called")
        if (sharedPref.getBoolean(Constants.SHARED_PREF_CAR_MEDIA,true)) {
            session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED))
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                        ReceiveData.getClucoseAsString() + " (Δ " + ReceiveData.getDeltaAsString() + ")"
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
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_GLUCOSE_ID)
            .setTitle(ReceiveData.getClucoseAsString() + " (Δ " + ReceiveData.getDeltaAsString() + ")\n" + ReceiveData.getElapsedTimeMinuteAsString(this))
            //.setSubtitle(ReceiveData.timeformat.format(Date(ReceiveData.time)))
            .setIconBitmap(getIcon()!!)
        return MediaBrowserCompat.MediaItem(
            mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun buildState(state: Int): PlaybackStateCompat? {
        Log.v(LOG_ID, "buildState called for state " + state)
        return PlaybackStateCompat.Builder()
            .setState(
                state,
                0,
                1f,
                SystemClock.elapsedRealtime()
            )
            .build()
    }

}

