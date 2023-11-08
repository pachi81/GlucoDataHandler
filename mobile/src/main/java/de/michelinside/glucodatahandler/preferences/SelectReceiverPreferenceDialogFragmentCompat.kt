package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R as CR


class SelectReceiverPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {
    companion object {
        private val LOG_ID = "GlucoDataHandler.SelectReceiverPreferenceDialog"
        fun initial(key: String) : SelectReceiverPreferenceDialogFragmentCompat {
            Log.d(LOG_ID, "initial called for key: " +  key )
            val dialog = SelectReceiverPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            dialog.arguments = bundle
            return dialog
        }
    }
    private var receiverSet = HashSet<String>()
    private lateinit var showAllSwitch: SwitchCompat
    private lateinit var sharedPref: SharedPreferences
    private var selectReceiverPreference: SelectReceiverPreference? = null

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
            val savedReceivers = selectReceiverPreference!!.getReceivers()
            Log.d(LOG_ID, savedReceivers.size.toString() + " receivers loaded: " + savedReceivers.toString())
            receiverSet.addAll(savedReceivers)

            sharedPref = requireContext().getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            showAllSwitch = view.findViewById(R.id.showAllSwitch)
            showAllSwitch.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL, false)
            showAllSwitch.setOnCheckedChangeListener { _, isChecked ->
                updateReceivers(view, isChecked)
            }

            updateReceivers(view, showAllSwitch.isChecked)
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
                selectReceiverPreference!!.setReceivers(receiverSet)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDialogClosed exception: " + exc.toString())
        }
    }


    private fun updateReceivers(view: View, all: Boolean) {
        try {
            val receiverLayout = view.findViewById<LinearLayout>(R.id.receiverLayout)
            val receivers = getReceivers(all)
            Log.d(LOG_ID, receivers.size.toString() + " receivers found!" )
            val receiverScrollView = view.findViewById<ScrollView>(R.id.receiverScrollView)
            if (receivers.size > 5) {
                receiverScrollView.layoutParams.height = resources.displayMetrics.heightPixels/2
            } else
                receiverScrollView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            receiverLayout.removeAllViews()
            val currentReceivers = receiverSet.toHashSet()
            receiverSet.clear()
            if (receivers.size == 0) {
                val txt = TextView(requireContext())
                txt.setText(CR.string.select_receiver_no_glucodata_receiver)
                receiverLayout.addView(txt)
            }
            else {
                for (receiver in receivers.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
                    val ch = CheckBox(requireContext())
                    ch.text = receiver.key
                    ch.hint = receiver.value
                    ch.setOnCheckedChangeListener { buttonView, isChecked ->
                        if (isChecked) {
                            receiverSet.add(buttonView.hint.toString())
                        } else {
                            receiverSet.remove(buttonView.hint.toString())
                        }
                    }
                    if (currentReceivers.contains(receiver.value) ) {
                        ch.isChecked = true
                        receiverSet.add(receiver.value)
                    }
                    receiverLayout.addView(ch)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateReceivers exception: " + exc.message.toString() )
        }
    }

    private fun getReceivers(all: Boolean): HashMap<String, String> {
        val names = HashMap<String, String>()
        if (BuildConfig.DEBUG) {
            names["Wusel Dusel"] = "wusel.dusel"
            names["dummy"] = "dummy"
        }
        val receivers: List<ResolveInfo>
        if (all) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            receivers = requireContext().packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        } else {
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            receivers = requireContext().packageManager.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        }
        for (resolveInfo in receivers) {
            val pkgName = resolveInfo.activityInfo.packageName
            val name = resolveInfo.activityInfo.loadLabel(requireContext().packageManager).toString()
            if (pkgName != null && pkgName != requireContext().packageName ) {
                names[name] = pkgName
            }
        }
        return names
    }
}