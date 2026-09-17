package de.michelinside.glucodataauto.android_auto

import android.annotation.SuppressLint
import de.michelinside.glucodataauto.R
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Looper
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import de.michelinside.glucodataauto.GlucoDataServiceAuto.Companion.NOTIFICATION_ID
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.chart.ChartBitmapHandler
import de.michelinside.glucodatahandler.common.utils.BitmapPool
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.R as CR
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream

@OptIn(UnstableApi::class)
class CarMediaBrowserService: MediaLibraryService(), NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {

    private val MEDIA_ROOT_ID = "root"
    private val MEDIA_GLUCOSE_ID = "glucose_value"
    private val MEDIA_NOTIFICATION_TOGGLE_ID = "toggle_notification"
    private val MEDIA_SPEAK_TOGGLE_ID = "toggle_speak"
    private lateinit var  sharedPref: SharedPreferences
    private var session: MediaLibrarySession? = null
    private var curMediaItem = MEDIA_ROOT_ID
    private var playBackState = PlaybackState.STATE_STOPPED
    private var curBitmap: Bitmap? = null

    private val player = object : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State {
            val items = mutableListOf<MediaItemData>()
            items.add(MediaItemData.Builder(MEDIA_GLUCOSE_ID)
                .setMediaItem(createMediaItem())
                .setDurationUs(this@CarMediaBrowserService.getDuration() * 1000L)
                .setIsSeekable(true)
                .build())
            
            return State.Builder()
                .setAvailableCommands(Player.Commands.Builder()
                    .add(COMMAND_PLAY_PAUSE)
                    .add(COMMAND_STOP)
                    .add(COMMAND_GET_METADATA)
                    .add(COMMAND_GET_TIMELINE)
                    .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .build())
                .setPlaybackState(if (playBackState == PlaybackState.STATE_NONE) STATE_IDLE else STATE_READY)
                .setPlayWhenReady(playBackState == PlaybackState.STATE_PLAYING, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaylist(items)
                .setContentPositionMs(getPosition())
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            Log.i(LOG_ID, "handleSetPlayWhenReady: $playWhenReady")
            if (playWhenReady) onPlayAction() else onStopAction()
            playBackState = if (playWhenReady) PlaybackState.STATE_PLAYING else PlaybackState.STATE_STOPPED
            invalidateState()
            return Futures.immediateVoidFuture()
        }
        
        override fun handleStop(): ListenableFuture<*> {
            Log.i(LOG_ID, "handleStop")
            onStopAction()
            playBackState = PlaybackState.STATE_STOPPED
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        fun update() {
            invalidateState()
        }
    }

    companion object {
        private var isForegroundService = false
        private val LOG_ID = "GDH.AA.CarMediaBrowserService"
        private var service: CarMediaBrowserService? = null
        val active: Boolean get() = service != null

        fun setForeground(context: Context, foreground: Boolean) {
            try {
                Log.i(LOG_ID, "setForeground called with foreground=$foreground - isForegroundService=$isForegroundService - active=$active")
                if(!active || foreground != isForegroundService) {
                    val serviceIntent = Intent(context, CarMediaBrowserService::class.java)
                    serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, foreground)
                    if (foreground)
                        context.startForegroundService(serviceIntent)
                    else
                        context.startService(serviceIntent)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "setForeground exception: " + exc.message.toString() )
            }
        }

        fun enable() {
            Log.d(LOG_ID, "enable")
            service?.enable()
        }

        fun disable() {
            Log.d(LOG_ID, "disable")
            service?.disable()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    override fun onCreate() {
        Log.i(LOG_ID, "onCreate")
        try {
            super.onCreate()
            GlucoDataServiceAuto.init(this)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)

            session = MediaLibrarySession.Builder(this, player, librarySessionCallback).build()

            // set callback depending on the current speak value to prevent speaking for values in background as affect on state!
            onSharedPreferenceChanged(sharedPref, Constants.AA_MEDIA_PLAYER_SPEAK_VALUES)

            TextToSpeechUtils.initTextToSpeech(this)
            service = this
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    private val librarySessionCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Log.d(LOG_ID, "onGetLibraryRoot")
            val rootItem = MediaItem.Builder()
                .setMediaId(MEDIA_ROOT_ID)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Log.d(LOG_ID, "onGetChildren for parent: $parentId")
            val items = mutableListOf<MediaItem>()
            if (parentId == MEDIA_ROOT_ID) {
                if(curMediaItem == MEDIA_ROOT_ID)
                    curMediaItem = MEDIA_GLUCOSE_ID
                items.add(createMediaItem())
                if (Channels.notificationChannelActive(this@CarMediaBrowserService, ChannelType.ANDROID_AUTO)) {
                    items.add(createNotificationToggleItem())
                }
                if(TextToSpeechUtils.isAvailable()) {
                    items.add(createSpeakToggleItem())
                }
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            Log.d(LOG_ID, "onAddMediaItems: ${mediaItems.map { it.mediaId }}")
            val resultItems = mutableListOf<MediaItem>()
            for (item in mediaItems) {
                when(item.mediaId) {
                    MEDIA_NOTIFICATION_TOGGLE_ID -> {
                        toggleNotification()
                        curMediaItem = MEDIA_GLUCOSE_ID
                        resultItems.add(createMediaItem())
                    }
                    MEDIA_SPEAK_TOGGLE_ID -> {
                        toggleSpeak()
                        curMediaItem = MEDIA_GLUCOSE_ID
                        resultItems.add(createMediaItem())
                    }
                    else -> {
                        curMediaItem = item.mediaId
                        resultItems.add(item)
                    }
                }
            }
            player.update()
            return Futures.immediateFuture(resultItems)
        }
    }

    private fun toggleNotification() {
        Log.d(LOG_ID, "Toggle notification")
        sharedPref.edit {
            putBoolean(
                Constants.SHARED_PREF_CAR_NOTIFICATION,
                !CarNotification.enable_notification
            )
        }
        session?.notifyChildrenChanged(MEDIA_ROOT_ID, 0, null)
    }

    private fun toggleSpeak() {
        Log.d(LOG_ID, "Toggle speak")
        sharedPref.edit {
            putBoolean(
                Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE,
                !sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false)
            )
        }
        session?.notifyChildrenChanged(MEDIA_ROOT_ID, 0, null)
    }

    private fun onPlayAction() {
        Log.i(LOG_ID, "onPlayAction called for $curMediaItem")
        try {
            if(curMediaItem == MEDIA_GLUCOSE_ID) {
                if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false))
                    CarMediaPlayer.play(applicationContext, !sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false))
                else
                    playBackState = PlaybackState.STATE_PLAYING
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPlayAction exception: " + exc.message.toString() )
        }
    }

    private fun onStopAction() {
        Log.i(LOG_ID, "onStopAction called")
        try {
            if(CarMediaPlayer.hasCallback())
                CarMediaPlayer.stop()
            else
                playBackState = PlaybackState.STATE_STOPPED
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStopAction exception: " + exc.message.toString() )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(LOG_ID, "onStartCommand called with intent ${Utils.dumpBundle(intent?.extras)}, flags $flags and startId $startId")
        try {
            val isForeground = intent?.getBooleanExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, false)?: true  // true as default for started from extern!
            Log.d(LOG_ID, "onStartCommand isForeground: $isForeground - isForegroundService: $isForegroundService")
            if (isForeground && !isForegroundService) {
                Log.i(LOG_ID, "Starting service in foreground!")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    startForeground(NOTIFICATION_ID, GlucoDataServiceAuto.getNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                else
                    startForeground(NOTIFICATION_ID, GlucoDataServiceAuto.getNotification(this))
                isForegroundService = true
            } else if ( isForegroundService && !isForeground ) {
                isForegroundService = false
                Log.i(LOG_ID, "Stopping service in foreground!")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error starting foreground in onStartCommand: ${exc.message}")
        }

        super.onStartCommand(intent, flags, startId)

        return START_STICKY  // keep alive
    }

    override fun onDestroy() {
        Log.w(LOG_ID, "onDestroy")
        try {
            disable()
            service = null
            isForegroundService = false
            CarMediaPlayer.setCallback(null)
            InternalNotifier.remNotifier(this, this)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            session?.release()
            session = null
            super.onDestroy()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.message.toString() )
        }
    }

    private fun enable() {
        Log.i(LOG_ID, "enable")
        InternalNotifier.addNotifier(this, this, mutableSetOf(
            NotifySource.BROADCAST,
            NotifySource.MESSAGECLIENT,
            NotifySource.SETTINGS,
            NotifySource.TIME_VALUE,
            NotifySource.GRAPH_CHANGED))
        ChartBitmapHandler.register(this, this.javaClass.simpleName)
    }

    private fun disable() {
        Log.i(LOG_ID, "disable")
        InternalNotifier.remNotifier(this, this)
        ChartBitmapHandler.unregister(this.javaClass.simpleName)
        BitmapPool.returnBitmap(curBitmap)
        curBitmap = null
        playBackState = PlaybackState.STATE_STOPPED
        player.update()
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for source $dataSource")
        try {
            if(dataSource == NotifySource.GRAPH_CHANGED && (!ChartBitmapHandler.isRegistered(this.javaClass.simpleName) || extras?.getInt(Constants.GRAPH_ID) != ChartBitmapHandler.chartId)) {
                Log.d(LOG_ID, "Ignore graph change")
                return // ignore
            }
            if(ChartBitmapHandler.hasBitmap(this.javaClass.simpleName)) {
                if(dataSource == NotifySource.BROADCAST || dataSource == NotifySource.MESSAGECLIENT) {
                    Log.d(LOG_ID, "Ignore glucose value and wait for chart update")
                    return
                }
                if(dataSource == NotifySource.TIME_VALUE && ReceiveData.getElapsedTimeMinute().mod(2) == 0) {
                    Log.d(LOG_ID, "Ignore time value and wait for chart update")
                    return
                }
            }
            player.update()
            session?.notifyChildrenChanged(MEDIA_ROOT_ID, 0, null)
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
                Constants.AA_MEDIA_ICON_STYLE,
                Constants.AA_MEDIA_PLAYER_COLORED,
                Constants.AA_MEDIA_SHOW_IOB_COB -> {
                    player.update()
                    session?.notifyChildrenChanged(MEDIA_ROOT_ID, 0, null)
                }
                Constants.AA_MEDIA_PLAYER_SPEAK_VALUES -> {
                    if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false)) {
                        CarMediaPlayer.setCallback(object : CarMediaPlayerCallback() {
                            override fun onPlay() {
                                Log.d(LOG_ID, "callback play called")
                                playBackState = PlaybackState.STATE_PLAYING
                                player.update()
                            }
                            override fun onStop() {
                                Log.d(LOG_ID, "callback onStop called")
                                playBackState = PlaybackState.STATE_STOPPED
                                player.update()
                            }
                        })
                        playBackState = PlaybackState.STATE_STOPPED
                        player.update()
                    } else {
                        CarMediaPlayer.setCallback(null)
                    }
                    session?.notifyChildrenChanged(MEDIA_ROOT_ID, 0, null)
                }
                Constants.AA_MEDIA_PLAYER_DURATION -> {
                    player.update()
                    if(playBackState==PlaybackState.STATE_PLAYING) {
                        player.update()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun getIcon(): Bitmap? {
        val coloredCover = sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_COLORED, true)
        val width = 600
        val height = 1000
        return when(sharedPref.getString(Constants.AA_MEDIA_ICON_STYLE, Constants.AA_MEDIA_ICON_STYLE_GRAPH)) {
            Constants.AA_MEDIA_ICON_STYLE_TREND -> {
                BitmapUtils.getRateAsBitmap(color = if(coloredCover) null else Color.WHITE, width = width, height = height)
            }
            Constants.AA_MEDIA_ICON_STYLE_GLUCOSE -> {
                BitmapUtils.getGlucoseAsBitmap(color = if(coloredCover) null else Color.WHITE, width = width, height = height)
            }
            Constants.AA_MEDIA_ICON_STYLE_GLUCOSE_TREND -> {
                BitmapUtils.getGlucoseTrendBitmap( color = if(coloredCover) null else Color.WHITE, width = 400, height = 400)
            }
            else -> {
                getBackgroundImage()
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun getBackgroundImage(): Bitmap? {
        val coloredCover = sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_COLORED, true)
        val width = 600
        val height = 1000
        try {
            val layoutId = if(ChartBitmapHandler.hasBitmap(this.javaClass.simpleName)) 
                R.layout.media_layout else R.layout.media_layout_no_graph
            
            Log.i(LOG_ID, "Create bitmap for layout: ${if(layoutId == R.layout.media_layout) "with graph" else "no graph"}")
            val lockscreenView = LayoutInflater.from(this).inflate(layoutId, null)
            val txtBgValue: TextView = lockscreenView.findViewById(R.id.glucose)
            val viewIcon: ImageView = lockscreenView.findViewById(R.id.trendImage)
            lockscreenView.setBackgroundColor(Color.BLACK)

            txtBgValue.text = ReceiveData.getGlucoseAsString()
            if(coloredCover)
                txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            else
                txtBgValue.setTextColor(Color.WHITE)
            
            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon(LOG_ID +"_trend", color = if(coloredCover) null else Color.WHITE,  width = 400, height = 400))

            if (layoutId == R.layout.media_layout) {
                val graphImage: ImageView = lockscreenView.findViewById(R.id.graphImage)
                Log.d(LOG_ID, "Update graphImage bitmap")
                graphImage.setImageBitmap(ChartBitmapHandler.getBitmap())
                if (!coloredCover) {
                    graphImage.setColorFilter(Color.WHITE)
                }
            }

            lockscreenView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            lockscreenView.layout(0, 0, width, height)
            
            Log.d(LOG_ID, "Rendered bitmap: ${lockscreenView.measuredWidth}x${lockscreenView.measuredHeight}")
            curBitmap = BitmapUtils.loadBitmapFromView(lockscreenView, curBitmap)
            return curBitmap
            
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error creating bitmap", e)
        }
        return BitmapUtils.getGlucoseTrendBitmap( color = if(coloredCover) null else Color.WHITE, width = height, height = height)
    }

    private fun createMediaMetadata(): MediaMetadata {
        var title = ReceiveData.getGlucoseAsString() + " (Δ " + ReceiveData.getDeltaAsString() + ")"
        if (sharedPref.getBoolean(Constants.AA_MEDIA_SHOW_IOB_COB, false) && !ReceiveData.isIobCobObsolete()) {
            title += "\n"
            if(!ReceiveData.iob.isNaN()) {
                title += "💉 " + ReceiveData.getIobAsString(true) + " "
            }
            if(!ReceiveData.cob.isNaN()) {
                title += "🍔 " + ReceiveData.getCobAsString(true)
            }
            title = title.trim()
        }
        var subtitle = ""
        if(!GlucoDataService.patientName.isNullOrEmpty())
            subtitle += GlucoDataService.patientName + " - "
        subtitle += "🕒 " + ReceiveData.getElapsedTimeMinuteAsString(this)

        return MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setSubtitle(subtitle)
            .setArtist(subtitle)
            .setArtworkData(getIcon()?.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setDurationMs(getDuration())
            .build()
    }

    private fun createMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(MEDIA_GLUCOSE_ID)
            .setMediaMetadata(createMediaMetadata())
            .build()
    }

    private fun getNotificationToggleIcon(): Bitmap? {
        if(CarNotification.enable_notification) {
            return ContextCompat.getDrawable(applicationContext, R.drawable.icon_popup_white)?.toBitmap()
        }
        return ContextCompat.getDrawable(applicationContext, R.drawable.icon_popup_off_white)?.toBitmap()
    }

    private fun createNotificationToggleItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(resources.getString(CR.string.gda_media_notification_toggle_title))
            .setSubtitle(resources.getString(if(CarNotification.enable_notification) CR.string.gda_notifications_on else CR.string.gda_notifications_off))
            .setArtworkData(getNotificationToggleIcon()?.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(MEDIA_NOTIFICATION_TOGGLE_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun getSpeakToggleIcon(): Bitmap? {
        if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false))
            return ContextCompat.getDrawable(applicationContext, CR.drawable.icon_volume_normal_white)?.toBitmap()
        else
            return ContextCompat.getDrawable(applicationContext, CR.drawable.icon_volume_off_white)?.toBitmap()
    }

    private fun createSpeakToggleItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(resources.getString(CR.string.gda_media_speak_toggle_title))
            .setSubtitle(resources.getString(if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false)) CR.string.gda_speak_on else CR.string.gda_speak_off))
            .setArtworkData(getSpeakToggleIcon()?.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(MEDIA_SPEAK_TOGGLE_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun getPosition(): Long {
        return if(CarMediaPlayer.hasCallback())
            CarMediaPlayer.currentPosition
        else
            System.currentTimeMillis()-ReceiveData.receiveTime
    }

    private fun getDuration(): Long {
        if(CarMediaPlayer.hasCallback()) {
            val duration = CarMediaPlayer.duration
            if (duration > 0) return duration
        }
        val duration = sharedPref.getInt(Constants.AA_MEDIA_PLAYER_DURATION, 0) * 60000L
        return if (duration > 0) duration else 600000L // 10 Min Default
    }

}
