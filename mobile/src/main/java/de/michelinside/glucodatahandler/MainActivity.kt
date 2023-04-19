package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import de.michelinside.glucodatahandler.common.*


class MainActivity : AppCompatActivity(), ReceiveDataInterface {
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtWearInfo: TextView
    private lateinit var switchSendToAod: SwitchCompat
    private lateinit var switchSendToXdrip: SwitchCompat
    private lateinit var numMin: EditText
    private lateinit var numMax: EditText
    private lateinit var switchNotifcation: SwitchCompat
    private lateinit var btnSelectTarget: Button
    private lateinit var sharedPref: SharedPreferences
    private val LOG_ID = "GlucoDataHandler.Main"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(LOG_ID, "onCreate called")
        val intent = Intent()
        val packageName = packageName
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        if (!GlucoDataService.running) {
            val serviceIntent = Intent(this, GlucoDataServiceMobile::class.java)
            this.startService(serviceIntent)
        }

        sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

        numMin = findViewById(R.id.numMin)
        numMax = findViewById(R.id.numMax)

        numMin.addTextChangedListener(EditTargetChanger(true, this))
        numMax.addTextChangedListener(EditTargetChanger(false, this))

        numMin.setText(sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MIN, ReceiveData.targetMin).toString())
        numMax.setText(sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MAX, ReceiveData.targetMax).toString())

        txtVersion = findViewById(R.id.txtVersion)
        txtVersion.text = BuildConfig.VERSION_NAME

        switchSendToAod = findViewById(R.id.switchSendToAod)
        btnSelectTarget = findViewById(R.id.btnSelectTarget)
        switchSendToAod.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)

        if(!sharedPref.contains(Constants.SHARED_PREF_GLUCODATA_RECEIVERS)) {
            val receivers = HashSet<String>()
            if (switchSendToAod.isChecked)
                receivers.add("de.metalgearsonic.glucodata.aod")
            Log.i(LOG_ID, "Upgrade receivers to " + receivers.toString())
            with(sharedPref.edit()) {
                putStringSet(Constants.SHARED_PREF_GLUCODATA_RECEIVERS, receivers)
                apply()
            }
        }

        btnSelectTarget.isVisible = switchSendToAod.isChecked
        switchSendToAod.setOnCheckedChangeListener { _, isChecked ->
            Log.d(LOG_ID, "Send to AOD changed: " + isChecked.toString())
            try {
                btnSelectTarget.isVisible = isChecked
                with (sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, isChecked)
                    apply()
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Changing send to AOD exception: " + exc.message.toString() )
            }
        }
        btnSelectTarget.setOnClickListener {
            val selectDialog = SelectReceiverFragment()
            selectDialog.show(this.supportFragmentManager, "selectReceiver")
        }


        switchSendToXdrip = findViewById(R.id.switchSendToXdrip)
        switchSendToXdrip.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, false)
        switchSendToXdrip.setOnCheckedChangeListener { _, isChecked ->
            Log.d(LOG_ID, "Send to xDrip changed: " + isChecked.toString())
            try {
                with (sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, isChecked)
                    apply()
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Changing send to xDrip exception: " + exc.message.toString() )
            }
        }

        switchNotifcation = findViewById(R.id.switchCarNotification)
        switchNotifcation.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, true)
        switchNotifcation.setOnCheckedChangeListener { _, isChecked ->
            Log.d(LOG_ID, "Car Notification changed: " + isChecked.toString())
            try {
                CarModeReceiver.enable_notification = isChecked
                with (sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, isChecked)
                    apply()
                }
                if(isChecked)
                    CarModeReceiver.showNotification(this)
                else
                    CarModeReceiver.cancelNotification()
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ReceiveData.remNotifier(this)
        Log.d(LOG_ID, "onPause called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_ID, "onResume called")
        update()
        ReceiveData.addNotifier(this)
    }

    private fun update() {
        try {
            Log.d(LOG_ID, "update values")
            txtLastValue = findViewById(R.id.txtLastValue)
            txtLastValue.text = ReceiveData.getAsString(this)
            txtWearInfo = findViewById(R.id.txtWearInfo)
            if (WearPhoneConnection.nodesConnected) {
                txtWearInfo.text = String.format(resources.getText(R.string.activity_main_connected_label).toString(), WearPhoneConnection.getBatterLevelsAsString())
            }
            else
                txtWearInfo.text = resources.getText(R.string.activity_main_disconnected_label)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: " + exc.message.toString() )
        }
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received")
        update()
    }
}