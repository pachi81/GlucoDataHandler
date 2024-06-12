package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.ActiveComplicationHandler
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.PackageUtils

class ComplicationsTapActionActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.ComplicationsTapActionActivity"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var showAllSwitch: SwitchCompat
    private val filter = mutableSetOf<String>()
    private var receiver = ""
    private var hasChanged = false
    private val actions = HashMap<String, String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_complication_tap_action)
            initFilter()

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            receiver = sharedPref.getString(Constants.SHARED_PREF_COMPLICATION_TAP_ACTION, "")!!
            hasChanged = false

            showAllSwitch = findViewById(R.id.showAllSwitch)
            showAllSwitch.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL, false)
            showAllSwitch.setOnCheckedChangeListener { _, isChecked ->
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL, isChecked)
                    apply()
                }
                updateReceiver(isChecked)
            }

            updateReceiver(showAllSwitch.isChecked)


        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            Log.v(LOG_ID, "onPause called")
            super.onPause()
            if(hasChanged) {
                Log.d(LOG_ID, "Update complications for new receiver $receiver")
                ActiveComplicationHandler.OnNotifyData(this, NotifySource.SETTINGS, null)
                hasChanged = false
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onResume() {
        try {
            Log.v(LOG_ID, "onResume called")
            super.onResume()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onDestroy() {
        try {
            Log.v(LOG_ID, "onDestroy called")
            super.onDestroy()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    private fun getActions(context: Context): HashMap<String, String> {
        if(actions.isEmpty()) {
            actions[""] = context.resources.getString(de.michelinside.glucodatahandler.common.R.string.no_action)
        }
        if(BuildConfig.DEBUG) {
            actions[Constants.ACTION_DUMMY_VALUE] = "Dummy value"
        }
        return actions
    }


    private fun initFilter() {
        filter.add(this.packageName)
        filter.add(Constants.PACKAGE_JUGGLUCO)
        filter.add(Constants.PACKAGE_GLUCODATAAUTO)
        filter.add(Constants.PACKAGE_AAPS)
        filter.add(Constants.PACKAGE_XDRIP)
        filter.add(Constants.PACKAGE_XDRIP_PLUS)
        filter.add(Constants.PACKAGE_LIBRELINK)
        filter.add(Constants.PACKAGE_LIBRELINKUP)
        filter.add(Constants.PACKAGE_DEXCOM)
    }


    private fun filterContains(value: String): Boolean {
        filter.forEach {
            if(value.startsWith(it))
                return true
        }
        return false
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
                val ch = RadioButton(this)
                ch.text = item.value
                ch.hint = item.key
                if (receiver == ch.hint) {
                    current = ch
                }
                ch.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked) {
                        receiver = buttonView.hint.toString()
                        Log.v(LOG_ID, "Set receiver: $receiver")
                        with(sharedPref.edit()) {
                            putString(Constants.SHARED_PREF_COMPLICATION_TAP_ACTION, receiver)
                            apply()
                        }
                        hasChanged = true
                    }
                }
                group.addView(ch)
            }
        }
        return current
    }

    private fun updateReceiver(all: Boolean) {
        try {
            val receiverLayout: LinearLayout = findViewById(R.id.receiverLayout)
            val receivers = PackageUtils.getPackages(this)
            Log.d(LOG_ID, receivers.size.toString() + " receivers found!")
            receiverLayout.removeAllViews()

            val group = RadioGroup(this)
            var current = createRadioButtons(group, getActions(this), true, false)
            val curApp = createRadioButtons(group, receivers, all, true)
            if(current == null && curApp != null)
                current = curApp
            receiverLayout.addView(group)
            if (current != null) {
                current.isChecked = true
                Log.v(LOG_ID, "Current receiver set for ${current.text}")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateReceiver exception: " + exc.message.toString() )
        }
    }


}