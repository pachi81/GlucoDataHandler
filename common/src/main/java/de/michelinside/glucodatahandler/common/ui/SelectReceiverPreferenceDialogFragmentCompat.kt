package de.michelinside.glucodatahandler.common.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.common.R


class SelectReceiverPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {
    companion object {
        private val LOG_ID = "GDH.SelectReceiverPreferenceDialog"
        fun initial(key: String) : SelectReceiverPreferenceDialogFragmentCompat {
            Log.d(LOG_ID, "initial called for key: " +  key )
            val dialog = SelectReceiverPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            dialog.arguments = bundle
            return dialog
        }

        private fun getReceivers(context: Context): HashMap<String, String> {
            return PackageUtils.getPackages(context)
        }
        private fun getActions(context: Context): HashMap<String, String> {
            val actions = HashMap<String, String>()
            actions[""] = context.resources.getString(R.string.no_action)
            if (TextToSpeechUtils.isAvailable())
                actions[Constants.ACTION_SPEAK] = context.resources.getString(R.string.action_read_values)
            if(Settings.canDrawOverlays(context)) {
                actions[Constants.ACTION_FLOATING_WIDGET_TOGGLE] =
                    context.resources.getString(R.string.action_floating_widget_toggle)
            }
            if(BuildConfig.DEBUG /*|| BuildConfig.BUILD_TYPE == "second"*/) {
                actions[Constants.ACTION_DUMMY_VALUE] = "Dummy value"
            }
            return actions
        }

        fun getSummary(context: Context, value: String, default: String, isTapAction: Boolean = false): String {
            if(isTapAction) {
                val actions = getActions(context)
                if(actions.containsKey(value))
                    return actions[value].toString()
            }
            val receivers = getReceivers(context)
            if(receivers.containsKey(value))
                return receivers[value].toString()
            return default
        }

    }
    private var receiver = ""
    private lateinit var showAllSwitch: SwitchCompat
    private lateinit var sharedPref: SharedPreferences
    private var selectReceiverPreference: SelectReceiverPreference? = null
    private val showAllKey: String get() {
        return selectReceiverPreference!!.key + "_show_all"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_ID, "onCreate called with bundle: " +  savedInstanceState?.toString() )
        try {
            selectReceiverPreference = preference as SelectReceiverPreference
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Setting preference exception: " + exc.toString())
        }
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        Log.d(LOG_ID, "onBindDialogView called for view: " +  view.transitionName.toString() + " preference " + preference.javaClass )
        try {
            receiver = selectReceiverPreference!!.getReceiver()
            Log.d(LOG_ID, "Receiver loaded: " + receiver)
            
            sharedPref = requireContext().getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            showAllSwitch = view.findViewById(R.id.showAllSwitch)
            showAllSwitch.isChecked = sharedPref.getBoolean(showAllKey, false)
            showAllSwitch.setOnCheckedChangeListener { _, isChecked ->
                updateReceiver(view, isChecked)
            }

            if(selectReceiverPreference!!.description.isNotEmpty()) {
                val summary = view.findViewById<TextView>(R.id.txtSummary)
                summary.text = selectReceiverPreference!!.description
            }

            updateReceiver(view, showAllSwitch.isChecked)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onBindDialogView exception: " + exc.toString())
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        Log.d(LOG_ID, "onDialogClosed called with positiveResult: " +  positiveResult.toString() )
        try {
            if(positiveResult) {
                with(sharedPref.edit()) {
                    putBoolean(showAllKey, showAllSwitch.isChecked)
                    apply()
                }
                selectReceiverPreference!!.setReceiver(receiver)
            }
        } catch (exc: Exception) {

            Log.e(LOG_ID, "onDialogClosed exception: " + exc.toString())
        }
    }

    private fun createRadioButtons(group: RadioGroup, list: HashMap<String, String>, all: Boolean, sort: Boolean): RadioButton? {
        var current: RadioButton? = null
        val map = if(sort) {
            list.toList()
                .sortedBy { (_, value) -> value.lowercase() }
                .toMap()
        } else {
            list.toMap()
        }

        val filter = if(selectReceiverPreference!!.isTapAction) PackageUtils.getTapActionFilter(requireContext()) else PackageUtils.getReceiverFilter()

        for (item in map) {
            if (all || PackageUtils.filterContains(filter, item.key)) {
                val ch = RadioButton(requireContext())
                ch.text = item.value
                ch.hint = item.key
                if (receiver == ch.hint) {
                    current = ch
                }
                ch.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked) {
                        receiver = buttonView.hint.toString()
                        Log.v(LOG_ID, "Set receiver: $receiver")
                    }
                }
                group.addView(ch)
            }
        }
        return current
    }

    private fun updateReceiver(view: View, all: Boolean) {
        try {
            val receiverLayout = view.findViewById<LinearLayout>(R.id.receiverLayout)
            val receivers = getReceivers(requireContext())
            Log.d(LOG_ID, receivers.size.toString() + " receivers found!")
            val receiverScrollView = view.findViewById<ScrollView>(R.id.receiverScrollView)
            receiverLayout.removeAllViews()

            var current: RadioButton? = null
            val group = RadioGroup(requireContext())
            if(selectReceiverPreference!!.isTapAction)
                current = createRadioButtons(group, getActions(requireContext()), true, false)
            else {
                // add empty receiver
                val emptyReceiver = HashMap<String, String>()
                emptyReceiver[""] = requireContext().resources.getString(R.string.no_receiver)
                current = createRadioButtons(group, emptyReceiver, true, false)
            }
            val curApp = createRadioButtons(group, receivers, all, true)
            if(current == null && curApp != null)
                current = curApp
            receiverLayout.addView(group)
            if (current != null) {
                current.isChecked = true
                Log.v(LOG_ID, "Current receiver set for ${current.text}")
            } else {
                receiver = ""
            }
            if (group.childCount > 10) {
                receiverScrollView.layoutParams.height = resources.displayMetrics.heightPixels / 2
            } else {
                receiverScrollView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateReceiver exception: " + exc.message.toString() )
        }
    }

}