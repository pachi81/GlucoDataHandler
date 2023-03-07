package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.fragment.app.DialogFragment
import de.michelinside.glucodatahandler.common.Constants


class SelectReceiverFragment : DialogFragment() {
    private val LOG_ID = "GlucoDataHandler.SelectReceiver"
    private lateinit var btnOK: Button
    private lateinit var btnCancel: Button
    private lateinit var showAllSwitch: Switch
    private lateinit var sharedPref: SharedPreferences
    private var receiverSet = HashSet<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(LOG_ID, "onCreateView called")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_select_receiver, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "onViewCreated called")
            super.onViewCreated(view, savedInstanceState)

            sharedPref = MainActivity.getContext().getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            btnOK = view.findViewById<Button>(R.id.btnOK)
            btnOK.setOnClickListener {
                save()
                dismiss()
            }

            btnCancel = view.findViewById<Button>(R.id.btnCancel)
            btnCancel.setOnClickListener {
                dismiss()
            }

            showAllSwitch = view.findViewById<Switch>(R.id.showAllSwitch)
            showAllSwitch.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL, false)
            showAllSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                updateReceivers(view, isChecked)
            }

            updateReceivers(view, showAllSwitch.isChecked)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onViewCreated exception: " + exc.message.toString() )
        }
    }

    private fun save() {
        try {
            Log.d(LOG_ID, "Saving "+ receiverSet.size.toString() + " receivers: " + receiverSet.toString())
            with(sharedPref.edit()) {
                putStringSet(Constants.SHARED_PREF_GLUCODATA_RECEIVERS, receiverSet)
                putBoolean(Constants.SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL, showAllSwitch.isChecked)
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "save exception: " + exc.message.toString() )
        }
    }

    private fun updateReceivers(view: View, all: Boolean) {
        try {
            val savedReceivers = sharedPref.getStringSet(Constants.SHARED_PREF_GLUCODATA_RECEIVERS, HashSet<String>())
            Log.d(LOG_ID, savedReceivers?.size.toString() + " receivers loaded: " + savedReceivers?.toString())

            val receiverLayout = view.findViewById<LinearLayout>(R.id.receiverLayout)
            val receivers = getReceivers(MainActivity.getContext(), all)
            Log.d(LOG_ID, receivers.size.toString() + " receivers found!" )
            val receiverScrollView = view.findViewById<ScrollView>(R.id.receiverScrollView)
            if (receivers.size > 5) {
                receiverScrollView.layoutParams.height = resources.displayMetrics.heightPixels/2
            } else
                receiverScrollView.layoutParams.height = WRAP_CONTENT
            receiverLayout.removeAllViews()
            receiverSet.clear()
            if (receivers.size == 0) {
                val txt = TextView(MainActivity.getContext())
                txt.setText(R.string.select_receiver_no_glucodata_receiver)
                receiverLayout.addView(txt)
            }
            else {
                for (recv in receivers.toSortedMap()) {
                    val ch = CheckBox(MainActivity.getContext())
                    ch.text = recv.key
                    ch.hint = recv.value
                    ch.setOnCheckedChangeListener { buttonView, isChecked ->
                        if (isChecked) {
                            receiverSet.add(buttonView.hint.toString())
                        } else {
                            receiverSet.remove(buttonView.hint.toString())
                        }
                    }
                    if (savedReceivers!!.contains(recv.value) ) {
                        ch.isChecked = true
                        receiverSet.add(recv.value)
                    }
                    receiverLayout.addView(ch)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateReceivers exception: " + exc.message.toString() )
        }
    }

    private fun getReceivers(context: Context, all: Boolean): HashMap<String, String> {
        val names = HashMap<String, String>()
        val receivers: List<ResolveInfo>
        if (all) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            receivers = context.packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        } else {
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            receivers = context.packageManager.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        }
        for (resolveInfo in receivers) {
            val pkgName = resolveInfo.activityInfo.packageName
            val name = resolveInfo.activityInfo.loadLabel(context.packageManager).toString()
            if (pkgName != null && pkgName != context.packageName ) {
                names[name] = pkgName
            }
        }
        return names
    }
}