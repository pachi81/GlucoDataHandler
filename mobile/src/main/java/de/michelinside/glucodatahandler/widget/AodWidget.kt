package de.michelinside.glucodatahandler.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.AODAccessibilityService
import de.michelinside.glucodatahandler.common.Constants

class AodWidget(context: Context): WallpaperBase(context, "GDH.AodWidget") {
    private var yPos = 75
    private var widgetColoured = false
    override val enabledPref = Constants.SHARED_PREF_AOD_WP_ENABLED
    override val stylePref = Constants.SHARED_PREF_AOD_WP_STYLE
    override val sizePref = Constants.SHARED_PREF_AOD_WP_SIZE
    override val MIN_SIZE = 10f
    override val MAX_SIZE = 24f

    private lateinit var service: AODAccessibilityService

    init {
        val activity = context as? AODAccessibilityService
        activity?.let {
            service = it
        }
    }

    override fun enable() {
        Log.d(LOG_ID, "enable called")
    }

    override fun disable() {
        Log.d(LOG_ID, "disable called")
    }

    override fun update() {
        Log.d(LOG_ID, "update called")
        try {
            service.removeAndCreateOverlay()
        } catch (e: Exception) {
            Log.e(LOG_ID, "update() - failed to remove and create overlay")
        }
    }

    override fun initSettings(sharedPreferences: SharedPreferences) {
        yPos = sharedPreferences.getInt(Constants.SHARED_PREF_AOD_WP_Y_POS, 75)
        widgetColoured = sharedPreferences.getBoolean(Constants.SHARED_PREF_AOD_WP_COLOURED, false)
        Log.d(LOG_ID, "Widget is coloured : $widgetColoured")
        super.initSettings(sharedPreferences)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.v(LOG_ID, "onSharedPreferenceChanged called for key $key")
            if (key == Constants.SHARED_PREF_AOD_WP_Y_POS && yPos != sharedPreferences.getInt(Constants.SHARED_PREF_AOD_WP_Y_POS, 75)) {
                yPos = sharedPreferences.getInt(Constants.SHARED_PREF_AOD_WP_Y_POS, 75)
                Log.d(LOG_ID, "New Y pos: $yPos")
                update()
            } else if(key == Constants.SHARED_PREF_AOD_WP_COLOURED && widgetColoured != sharedPreferences.getBoolean(Constants.SHARED_PREF_AOD_WP_COLOURED, false)) {
                widgetColoured = sharedPreferences.getBoolean(Constants.SHARED_PREF_AOD_WP_COLOURED, false)
                Log.d(LOG_ID, "Widget is coloured : $widgetColoured")
                update()
            } else {
                super.onSharedPreferenceChanged(sharedPreferences, key)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }

    fun getBitmap() : Bitmap? {
        return createWallpaperView(if (widgetColoured) null else Constants.AOD_COLOUR)
    }

    fun getYPos() : Int {
        return yPos
    }

}

