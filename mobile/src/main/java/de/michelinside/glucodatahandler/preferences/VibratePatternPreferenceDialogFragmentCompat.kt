package de.michelinside.glucodatahandler.preferences

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.preference.PreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.notification.VibratePattern
import de.michelinside.glucodatahandler.common.notification.Vibrator


class VibratePatternPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {
    companion object {
        private val LOG_ID = "GDH.VibratePatternPreferenceDialog"
        private var curAmplitude = -1
        fun initial(key: String, amplitude: Int) : VibratePatternPreferenceDialogFragmentCompat {
            Log.d(LOG_ID, "initial called for key: $key - amplitude: $amplitude" )
            curAmplitude = amplitude
            val dialog = VibratePatternPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            dialog.arguments = bundle
            return dialog
        }
    }

    private var vibratePatternPreference: VibratePatternPreference? = null
    private var currentPattern = ""
    private lateinit var currentView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_ID, "onCreate called with bundle: " +  savedInstanceState?.toString() )
        try {
            vibratePatternPreference = preference as VibratePatternPreference
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Setting preference exception: " + exc.toString())
        }
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        Log.d(LOG_ID, "onBindDialogView called for view: " +  view.transitionName.toString() + " preference " + preference.javaClass )
        try {
            currentView = view
            setCurrentPattern(vibratePatternPreference!!.getPattern())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onBindDialogView exception: " + exc.toString())
        }
    }

    fun setCurrentPattern(pattern: String) {
        currentPattern = pattern
        Log.d(LOG_ID, "Pattern set to: " + currentPattern)
        updatePattern()
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        Log.d(LOG_ID, "onDialogClosed called with positiveResult: " +  positiveResult.toString() )
        try {
            Vibrator.cancel()
            if(positiveResult) {
                vibratePatternPreference!!.setPattern(currentPattern)
            }
        } catch (exc: Exception) {

            Log.e(LOG_ID, "onDialogClosed exception: " + exc.toString())
        }
    }

    private fun createRadioButtons(group: RadioGroup): RadioButton? {
        var current: RadioButton? = null
        VibratePattern.entries.forEach {
            val ch = RadioButton(requireContext())
            ch.text = resources.getString(it.resId)
            ch.hint = it.key
            if (currentPattern == ch.hint) {
                current = ch
            }
            group.addView(ch)
        }
        return current
    }

    private fun updatePattern() {
        try {
            val receiverLayout = currentView.findViewById<LinearLayout>(R.id.linearLayout)
            receiverLayout.removeAllViews()

            val group = RadioGroup(requireContext())
            val current = createRadioButtons(group)
            receiverLayout.addView(group)
            if (current != null) {
                current.isChecked = true
                Log.v(LOG_ID, "Current currentPattern set for ${current.text}")
            } else {
                currentPattern = ""
            }
            group.setOnCheckedChangeListener { _, checkedId ->
                Log.i(LOG_ID, "OnCheckedChangeListener called with checkedId: $checkedId")
                try {
                    group.findViewById<RadioButton>(checkedId).let {
                        val buttonView = it as RadioButton
                        currentPattern = buttonView.hint.toString()
                        Log.v(LOG_ID, "Set currentPattern: $currentPattern")
                        val pattern = VibratePattern.getByKey(currentPattern).pattern
                        if(pattern != null) {
                            Vibrator.vibrate(pattern, -1, curAmplitude)
                        }
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "OnCheckedChangeListener exception: " + exc.toString())
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updatePattern exception: " + exc.message.toString() )
        }
    }

}