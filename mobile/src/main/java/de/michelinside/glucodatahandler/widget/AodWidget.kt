package de.michelinside.glucodatahandler.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import de.michelinside.glucodatahandler.AODAccessibilityService
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier

class AodWidget(context: Context): WallpaperBase(context, "GDH.AodWidget") {
    private var yPos = 75
    override val enabledPref = Constants.SHARED_PREF_AOD_WP_ENABLED
    override val stylePref = Constants.SHARED_PREF_AOD_WP_STYLE
    override val sizePref = Constants.SHARED_PREF_AOD_WP_SIZE

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
        remove()
    }

    override fun update() {
        Log.d(LOG_ID, "update()")
        try {
            service.removeAndCreateOverlay()
        } catch (e: Exception) {
            Log.e(LOG_ID, "update() - failed to remove and create overlay")
        }
    }

    private fun remove() {
        Log.d(LOG_ID, "remove called")
        try {
            Log.d(LOG_ID, "removing notifier")
            InternalNotifier.remNotifier(context, this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "remove exception: " + exc.message.toString() )
        }
    }

    override fun initSettings(sharedPreferences: SharedPreferences) {
        yPos = sharedPreferences.getInt(Constants.SHARED_PREF_AOD_WP_Y_POS, 75)
        super.initSettings(sharedPreferences)
    }

    fun getBitmap() : Bitmap? {
        return createWallpaperView()
    }

    fun getYPos() : Int {
        return yPos
    }




}

