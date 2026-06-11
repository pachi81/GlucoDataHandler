package de.michelinside.glucodatahandler.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Log

class ScreensaverHandler(context: Context): WallpaperBase(context, "GDH.ScreensaverHandler") {
    override val enabledPref = "screensaver_enabled_dummy"
    override val stylePref = Constants.SHARED_PREF_SCREENSAVER_STYLE
    override val sizePref = Constants.SHARED_PREF_SCREENSAVER_SIZE
    override val MIN_SIZE = 6f
    override val MIN_VALUE_SIZE = 0f
    override val VALUE_RESIZE_FACTOR = 5f
    override val MAX_SIZE = 30f
    override val DEFAULT_FONT_SIZE = 10f
    
    override fun enable() {
        Log.d(LOG_ID, "enable called")
    }
    
    override fun disable() {
        Log.d(LOG_ID, "disable called")
    }
    
    private var onUpdate: (() -> Unit)? = null
    
    fun setOnUpdateListener(listener: () -> Unit) {
        onUpdate = listener
    }
    
    override fun update() {
        Log.d(LOG_ID, "update called")
        onUpdate?.invoke()
    }
    
    fun getUpdatedBitmap(): Bitmap? {
        return createWallpaperView()
    }
    
    override fun initSettings(sharedPreferences: SharedPreferences) {
        style = sharedPreferences.getString(stylePref, style)?: style
        size = sharedPreferences.getInt(sizePref, size)
        enabled = true // always enabled for screensaver
        Log.d(LOG_ID, "initSettings called for style $style and size $size and enabled $enabled")
        // No updateNotifier() here, because we want to call it when screensaver starts
    }
}
