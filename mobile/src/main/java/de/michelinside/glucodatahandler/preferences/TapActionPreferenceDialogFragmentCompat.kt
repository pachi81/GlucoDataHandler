package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.R as CR


class TapActionPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {
    companion object {
        private val LOG_ID = "GDH.TapActionPreferenceDialog"
        private val filter = mutableSetOf<String>()
        private val actions = HashMap<String, String>()
        fun initial(key: String) : TapActionPreferenceDialogFragmentCompat {
            Log.d(LOG_ID, "initial called for key: " +  key )
            val dialog = TapActionPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            dialog.arguments = bundle
            return dialog
        }

        private fun getReceivers(context: Context): HashMap<String, String> {
            return PackageUtils.getPackages(context)
        }
        private fun getActions(context: Context): HashMap<String, String> {
            if(actions.isEmpty()) {
                actions[""] = context.resources.getString(CR.string.no_action)
                actions[Constants.ACTION_FLOATING_WIDGET_TOGGLE] = context.resources.getString(CR.string.action_floating_widget_toggle)

            }
            return actions
        }

        fun getSummary(context: Context, value: String): String {
            val actions = getActions(context)
            if(actions.containsKey(value))
                return actions[value].toString()
            val receivers = getReceivers(context)
            if(receivers.containsKey(value))
                return receivers[value].toString()
            return context.resources.getString(CR.string.no_action)
        }

    }
    private var receiver = ""
    private lateinit var showAllSwitch: SwitchCompat
    private lateinit var sharedPref: SharedPreferences
    private var tapActionPreference: TapActionPreference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_ID, "onCreate called with bundle: " +  savedInstanceState?.toString() )
        try {
            initFilter()
            tapActionPreference = preference as TapActionPreference
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Setting preference exception: " + exc.toString())
        }
    }

    private fun initFilter() {
        filter.add(requireContext().packageName)
        filter.add(Constants.PACKAGE_JUGGLUCO)
        filter.add(Constants.PACKAGE_GLUCODATAAUTO)
        filter.add(Constants.PACKAGE_AAPS)
        filter.add(Constants.PACKAGE_XDRIP)
        filter.add(Constants.PACKAGE_XDRIP_PLUS)
    }

    private fun filterContains(value: String): Boolean {
        filter.forEach {
            if(value.startsWith(it))
                return true
        }
        return false
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        Log.d(LOG_ID, "onBindDialogView called for view: " +  view.transitionName.toString() + " preference " + preference.javaClass )
        try {
            receiver = tapActionPreference!!.getReceiver()
            Log.d(LOG_ID, "Receiver loaded: " + receiver)

            sharedPref = requireContext().getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            showAllSwitch = view.findViewById(R.id.showAllSwitch)
            showAllSwitch.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL, false)
            showAllSwitch.setOnCheckedChangeListener { _, isChecked ->
                updateReceiver(view, isChecked)
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
                    putBoolean(Constants.SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL, showAllSwitch.isChecked)
                    apply()
                }
                tapActionPreference!!.setReceiver(receiver)
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

        for (item in map) {
            if (all || filterContains(item.key)) {
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

            var current: RadioButton?
            val group = RadioGroup(requireContext())
            current = createRadioButtons(group, getActions(requireContext()), true, false)
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