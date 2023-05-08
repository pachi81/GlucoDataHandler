package de.michelinside.glucodatahandler

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import de.michelinside.glucodatahandler.common.notifier.*
import java.util.*

class CarMediaBrowserService: MediaBrowserServiceCompat(), NotifierInterface {
    private val LOG_ID = "GlucoDataHandler.CarMediaBrowserService"
    private val MEDIA_ROOT_ID = "root"
    private lateinit var session: MediaSessionCompat

    override fun onCreate() {
        Log.d(LOG_ID, "onCreate")
        try {
            super.onCreate()

            session = MediaSessionCompat(this, "MyMusicService")
            sessionToken = session.sessionToken

            InternalNotifier.addNotifier(this, mutableSetOf(
                NotifyDataSource.BROADCAST,
                NotifyDataSource.MESSAGECLIENT))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onDestroy() {
        Log.d(LOG_ID, "onDestroy")
        try {
            InternalNotifier.remNotifier(this)
            session.release()
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
                result.sendResult(mutableListOf(createMediaItem()))
            } else {
                result.sendResult(null)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onLoadChildren exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called")
        try {
            notifyChildrenChanged(MEDIA_ROOT_ID)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    fun getIcon(): Bitmap? {
        return Utils.textRateToBitmap(ReceiveData.getClucoseAsString(), ReceiveData.rate, ReceiveData.getClucoseColor(), ReceiveData.isObsolete(300), ReceiveData.isObsolete(300) && !ReceiveData.isObsolete())
    }

    private fun createMediaItem(): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_ROOT_ID)
            .setTitle("Delta: " + ReceiveData.getDeltaAsString() + " " + ReceiveData.timeformat.format(Date(ReceiveData.time)))
            //.setSubtitle(ReceiveData.timeformat.format(Date(ReceiveData.time)))
            .setIconBitmap(getIcon()!!)
        return MediaBrowserCompat.MediaItem(
            mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }
}