package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.notification.VibratePattern


class VibratePatternPreference : DialogPreference {
    private val LOG_ID = "GDH.VibratePatternPreference"
    private var vibratePattern = ""
    constructor(context: Context?) : super(context!!)  {
        initPreference()
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        initPreference()
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
        initPreference()
    }

    fun initPreference() {
        Log.d(LOG_ID, "initPreference called")
        setPattern("", false)
    }

    override fun getDialogLayoutResource(): Int {
        Log.d(LOG_ID, "getDialogLayoutResource called")
        return R.layout.vibration_pattern_preference
    }

    fun getPattern(): String {
        Log.d(LOG_ID, "getPattern called")
        return vibratePattern
    }

    fun setPattern(newPattern: String, save: Boolean = true) {
        Log.d(LOG_ID, "setPattern called for $newPattern - save: $save")
        try {
            vibratePattern = newPattern // Save to Shared Preferences
            if(save)
                persistString(vibratePattern)
            this.summary = context.resources.getString(VibratePattern.getByKey(vibratePattern).resId)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setPattern exception: " + exc.toString())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onSetInitialValue(
        restorePersistedValue: Boolean,
        defaultValue: Any?
    ) {
        try {
            Log.d(LOG_ID, "onSetInitialValue called with restorePersistedValue " + restorePersistedValue.toString() + " - defaultValue " + defaultValue.toString())
            // Read the value. Use the default value if it is not possible.
            setPattern(if (restorePersistedValue || defaultValue == null) getPersistedString(vibratePattern) as String else defaultValue as String)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSetInitialValue exception: $exc")
        }
    }
}