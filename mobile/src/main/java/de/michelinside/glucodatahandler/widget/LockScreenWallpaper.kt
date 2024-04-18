package de.michelinside.glucodatahandler.widget

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import kotlin.math.max


object LockScreenWallpaper : NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.LockScreenWallpaper"
    private var enabled = false
    private var yPos = 75


    fun create(context: Context) {
        try {
            Log.d(LOG_ID, "create called")
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.message.toString() )
        }
    }

    fun destroy(context: Context) {
        try {
            Log.d(LOG_ID, "destroy called")
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            disable(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.message.toString() )
        }
    }

    private fun enable(context: Context) {
        if (!enabled) {
            Log.d(LOG_ID, "enable called")
            enabled = true
            val filter = mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.OBSOLETE_VALUE,
                NotifySource.SETTINGS
            )
            InternalNotifier.addNotifier(context, this, filter)
            updateLockScreen(context)
        }
    }

    private fun disable(context: Context) {
        if (enabled) {
            Log.d(LOG_ID, "disable called")
            enabled = false
            InternalNotifier.remNotifier(context, this)
            setWallpaper(null, context)
        }
    }

    fun updateLockScreen(context: Context) {
        try {
            Log.v(LOG_ID, "updateLockScreen called - enabled=$enabled")
            if (enabled) {
                setWallpaper(getBitmapForWallpaper(context), context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString() )
        }
    }

    private fun setWallpaper(bitmap: Bitmap?, context: Context) {
        try {
            Log.v(LOG_ID, "updateLockScreen called for bitmap $bitmap")
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallpaper = if(bitmap != null) createWallpaper(bitmap, context) else null
            wallpaperManager.setBitmap(wallpaper, null, false, WallpaperManager.FLAG_LOCK)
            //wallpaper?.recycle()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString() )
        }

    }

    private fun createWallpaper(bitmap: Bitmap, context: Context): Bitmap? {
        try {
            Log.v(LOG_ID, "creatWallpaper called")
            val screenWidth = BitmapUtils.getScreenWidth()
            val screenHeigth = BitmapUtils.getScreenHeight()
            val screenDPI = BitmapUtils.getScreenDpi().toFloat()
            val wallpaper = Bitmap.createBitmap(screenWidth, screenHeigth, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(wallpaper)
            val drawable = BitmapDrawable(context.resources, bitmap)
            drawable.setBounds(0, 0, screenWidth, screenHeigth)
            val xOffset = ((screenWidth-bitmap.width)/2F) //*1.2F-(screenDPI*0.3F)
            val yOffset = max(0F, ((screenHeigth-bitmap.height)*yPos/100F)) //-(screenDPI*0.3F))
            Log.d(LOG_ID, "Create wallpaper at x=$xOffset/$screenWidth and y=$yOffset/$screenHeigth DPI=$screenDPI")
            canvas.drawBitmap(bitmap, xOffset, yOffset, Paint(Paint.ANTI_ALIAS_FLAG))
            bitmap.recycle()
            return wallpaper
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString() )
        }
        return null
    }

    private fun getBitmapForWallpaper(context: Context): Bitmap? {
        return BitmapUtils.getGlucoseTrendBitmap(width = 400, height = 400)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source $dataSource")
            updateLockScreen(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.v(LOG_ID, "onSharedPreferenceChanged called for key $key")
            var changed = false
            if (yPos != sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, 75)) {
                yPos = sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, 75)
                Log.d(LOG_ID, "New Y pos: $yPos")
                changed = true
            }
            if (enabled != sharedPreferences.getBoolean(Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED, false)) {
                if (sharedPreferences.getBoolean(Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED, false))
                    enable(GlucoDataService.context!!)
                else
                    disable(GlucoDataService.context!!)
            } else if (changed) {
                updateLockScreen(GlucoDataService.context!!)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}