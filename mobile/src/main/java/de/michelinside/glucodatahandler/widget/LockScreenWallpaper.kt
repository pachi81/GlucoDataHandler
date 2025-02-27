package de.michelinside.glucodatahandler.widget

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.core.graphics.drawable.toDrawable
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max


class LockScreenWallpaper(context: Context): WallpaperBase(context, "GDH.LockScreenWallpaper") {
    private var yPos = 75
    override val enabledPref = Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED
    override val stylePref = Constants.SHARED_PREF_LOCKSCREEN_WP_STYLE
    override val sizePref = Constants.SHARED_PREF_LOCKSCREEN_WP_SIZE
    override val chartDurationPref = Constants.SHARED_PREF_LOCKSCREEN_WP_GRAPH_DURATION

    @OptIn(DelicateCoroutinesApi::class)
    private fun setWallpaper(bitmap: Bitmap?, context: Context) {
        GlobalScope.launch {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                Log.i(LOG_ID, "Update lockscreen wallpaper for bitmap $bitmap")
                val wallpaper =  createWallpaper(bitmap, context)
                wallpaperManager.setBitmap(wallpaper, null, false, WallpaperManager.FLAG_LOCK)
                wallpaper!!.recycle()
            } catch (exc: Exception) {
                Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString())
            }
        }
    }

    private fun createWallpaper(bitmap: Bitmap?, context: Context): Bitmap? {
        try {
            Log.v(LOG_ID, "creatWallpaper called")
            val screenWidth = BitmapUtils.getScreenWidth()
            val screenHeigth = BitmapUtils.getScreenHeight()
            val wallpaper = Bitmap.createBitmap(screenWidth, screenHeigth, Bitmap.Config.ARGB_8888)
            if(bitmap != null) {
                val screenDPI = BitmapUtils.getScreenDpi().toFloat()
                val canvas = Canvas(wallpaper)
                val drawable = bitmap.toDrawable(context.resources)
                drawable.setBounds(0, 0, screenWidth, screenHeigth)
                val xOffset = ((screenWidth-bitmap.width)/2F) //*1.2F-(screenDPI*0.3F)
                val yOffset = max(0F, ((screenHeigth-bitmap.height)*yPos/100F)) //-(screenDPI*0.3F))
                Log.d(LOG_ID, "Create wallpaper at x=$xOffset/$screenWidth and y=$yOffset/$screenHeigth DPI=$screenDPI")
                canvas.drawBitmap(bitmap, xOffset, yOffset, Paint(Paint.ANTI_ALIAS_FLAG))
                bitmap.recycle()
            }
            return wallpaper
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString() )
        }
        return null
    }

    private fun getBitmapForWallpaper(): Bitmap? {
        return createWallpaperView()
    }

    override fun enable() {
        Log.d(LOG_ID, "enable called")
        update()
    }

    override fun disable() {
        setWallpaper(null, context)
    }

    override fun update() {
        try {
            Log.v(LOG_ID, "updateLockScreen called - enabled=$enabled")
            if (enabled) {
                setWallpaper(getBitmapForWallpaper(), context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString() )
        }
    }

    override fun initSettings(sharedPreferences: SharedPreferences) {
        yPos = sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, 75)
        super.initSettings(sharedPreferences)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.v(LOG_ID, "onSharedPreferenceChanged called for key $key")
            if (key == Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS && yPos != sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, 75)) {
                yPos = sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, 75)
                Log.d(LOG_ID, "New Y pos: $yPos")
                update()
            } else {
                super.onSharedPreferenceChanged(sharedPreferences, key)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }

    override fun getFilters(): MutableSet<NotifySource> {
        Log.d(LOG_ID, "getFilters called - enabled=$enabled")
        if(enabled)
            return mutableSetOf(NotifySource.AOD_STATE_CHANGED)
        return mutableSetOf()
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            if(dataSource == NotifySource.AOD_STATE_CHANGED) {
                if(extras != null) {
                    val aod_enabled = extras.getBoolean("aod_state")
                    Log.d(LOG_ID, "Display state changed - is AOD on: $aod_enabled")
                    if(aod_enabled)
                        disable()
                    else
                        enable()
                }
            } else
                super.OnNotifyData(context, dataSource, extras)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString() )
        }
    }
}