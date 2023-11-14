package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.os.*
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.tasks.LibreViewSourceTask
import de.michelinside.glucodatahandler.databinding.ActivityWaerBinding

class WaerActivity : AppCompatActivity(), NotifierInterface {

    private val LOG_ID = "GlucoDataHandler.Main"
    private lateinit var binding: ActivityWaerBinding
    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var txtVersion: TextView
    private lateinit var txtValueInfo: TextView
    private lateinit var txtConnInfo: TextView
    private lateinit var txtSourceInfo: TextView
    private lateinit var switchColoredAod: SwitchCompat
    private lateinit var switchLargeTrendArrow: SwitchCompat
    private lateinit var switchNotifcation: SwitchCompat
    private lateinit var switchForground: SwitchCompat
    private lateinit var switchRelativeTime: SwitchCompat
    private lateinit var switchLibreSource: SwitchCompat
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        try {

            super.onCreate(savedInstanceState)

            binding = ActivityWaerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            txtValueInfo = findViewById(R.id.txtValueInfo)
            txtConnInfo = findViewById(R.id.txtConnInfo)
            txtSourceInfo = findViewById(R.id.txtSourceInfo)

            txtVersion = findViewById(R.id.txtVersion)
            txtVersion.text = BuildConfig.VERSION_NAME

            ReceiveData.initData(this)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            switchForground = findViewById(R.id.switchForground)
            switchForground.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
            switchForground.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Foreground service changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, isChecked)
                        apply()
                    }
                    val serviceIntent = Intent(this, GlucoDataServiceWear::class.java)
                    if (isChecked)
                        serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
                    else
                        serviceIntent.putExtra(Constants.ACTION_STOP_FOREGROUND, true)
                    this.startService(serviceIntent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing foreground service exception: " + exc.message.toString() )
                }
            }

            switchNotifcation = findViewById(R.id.switchNotifcation)
            switchNotifcation.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION, false)
            switchNotifcation.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Notification changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_NOTIFICATION, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            switchRelativeTime = findViewById(R.id.switchRelativeTime)
            switchRelativeTime.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, false)
            switchRelativeTime.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Relative time changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_RELATIVE_TIME, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing relative time exception: " + exc.message.toString() )
                }
            }

            switchColoredAod = findViewById(R.id.switchColoredAod)
            switchColoredAod.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false)
            switchColoredAod.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Colored AOD changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, isChecked)
                        apply()
                    }
                    // trigger update of each complication on change
                    ActiveComplicationHandler.OnNotifyData(this, NotifySource.SETTINGS, null)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing colored AOD exception: " + exc.message.toString() )
                }
            }

            switchLargeTrendArrow = findViewById(R.id.switchLargeTrendArrow)
            switchLargeTrendArrow.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_LARGE_ARROW_ICON, true)
            switchLargeTrendArrow.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Large arrow icon changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_LARGE_ARROW_ICON, isChecked)
                        apply()
                    }
                    // trigger update of each complication on change
                    ActiveComplicationHandler.OnNotifyData(this, NotifySource.SETTINGS, null)
                    update()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing large arrow icon exception: " + exc.message.toString() )
                }
            }

            switchLibreSource = findViewById(R.id.switchLibreView)
            switchLibreSource.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_LIBRE_ENABLED, true)
            switchLibreSource.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Libre view changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_LIBRE_ENABLED, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing large arrow icon exception: " + exc.message.toString() )
                }
            }

            GlucoDataServiceWear.start(this)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            InternalNotifier.remNotifier(this)
            Log.d(LOG_ID, "onPause called")
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onResume() {
        try {
            super.onResume()
            Log.d(LOG_ID, "onResume called")
            update()
            InternalNotifier.addNotifier(this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.CAPILITY_INFO,
                NotifySource.NODE_BATTERY_LEVEL,
                NotifySource.SETTINGS,
                NotifySource.OBSOLETE_VALUE,
                NotifySource.SOURCE_SETTINGS,
                NotifySource.SOURCE_STATE_CHANGE))
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    private fun update() {
        try {
            if(ReceiveData.time > 0) {
                txtBgValue.text = ReceiveData.getClucoseAsString()
                txtBgValue.setTextColor(ReceiveData.getClucoseColor())
                if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete()) {
                    txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    txtBgValue.paintFlags = 0
                }
                viewIcon.setImageIcon(Utils.getRateAsIcon())
                txtValueInfo.text = ReceiveData.getAsString(this)
                if (WearPhoneConnection.nodesConnected) {
                    txtConnInfo.text = String.format(resources.getText(CR.string.activity_connected_label).toString(), WearPhoneConnection.getBatterLevelsAsString())
                } else
                    txtConnInfo.text = resources.getText(CR.string.activity_disconnected_label)
            }
            txtSourceInfo.text =  String.format(resources.getText(CR.string.activity_main_source_label).toString(), LibreViewSourceTask.getState(this))
            val user = sharedPref.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
            val password = sharedPref.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
            switchLibreSource.isEnabled = user.isNotEmpty() && password.isNotEmpty()
            if(!switchLibreSource.isEnabled)
                switchLibreSource.isChecked = false
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received from: " + dataSource.toString())
        update()
        if (dataSource == NotifySource.SETTINGS) {
            if (extras != null && extras.containsKey(Constants.SHARED_PREF_NOTIFICATION))
                switchNotifcation.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION, false)
        }
    }
}
