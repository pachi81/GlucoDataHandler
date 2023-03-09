package de.michelinside.glucodatahandler

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.databinding.ActivityWaerBinding


class WaerActivity : Activity(), ReceiveDataInterface {

    private val LOG_ID = "GlucoDataHandler.Main"
    private lateinit var binding: ActivityWaerBinding
    private lateinit var txtBgValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtValueInfo: TextView
    private lateinit var txtConnInfo: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var numMin: EditText
    private lateinit var numMax: EditText
    private lateinit var switchNotifcation: Switch
    private lateinit var switchForground: Switch
    private lateinit var sharedPref: SharedPreferences
    private lateinit var numMinChanger: EditTargetChanger
    private lateinit var numMaxChanger: EditTargetChanger
    private var useMmol: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {

            super.onCreate(savedInstanceState)

            binding = ActivityWaerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            txtVersion = findViewById(R.id.txtVersion)
            txtVersion.text = BuildConfig.VERSION_NAME

            numMin = findViewById(R.id.numMin)
            numMax = findViewById(R.id.numMax)
            numMinChanger = EditTargetChanger(true, this)
            numMaxChanger = EditTargetChanger(false, this)
            numMin.addTextChangedListener(numMinChanger)
            numMax.addTextChangedListener(numMaxChanger)

            ReceiveData.readTargets(this)
            useMmol = ReceiveData.isMmol
            updateMinMax()

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            switchForground = findViewById(R.id.switchForground)
            switchForground.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, false)
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

            GlucoDataServiceWear.start(this)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            ReceiveData.remNotifier(this)
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
            ReceiveData.addNotifier(this)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    private fun update() {
        try {
            if(ReceiveData.time > 0) {
                txtBgValue = findViewById(R.id.txtBgValue)
                txtBgValue.text = ReceiveData.getClucoseAsString()
                txtBgValue.setTextColor(ReceiveData.getClucoseColor())
                viewIcon = findViewById(R.id.viewIcon)
                viewIcon.setImageIcon(ReceiveData.getArrowIcon())
                txtValueInfo = findViewById(R.id.txtValueInfo)
                txtValueInfo.text = ReceiveData.getAsString(this, false)
                txtConnInfo = findViewById(R.id.txtConnInfo)
                if (WearPhoneConnection.nodesConnected) {
                    txtConnInfo.text = String.format(resources.getText(R.string.activity_connected_label).toString(), WearPhoneConnection.getBatterLevelsAsString())
                } else
                    txtConnInfo.text = resources.getText(R.string.activity_disconnected_label)

            }
            if (useMmol != ReceiveData.isMmol) {
                useMmol = ReceiveData.isMmol
                updateMinMax()
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    private fun updateMinMax() {
        try {
            Log.d(LOG_ID, "Update min/max values in UI")
            numMinChanger.updateInProgress = true
            numMaxChanger.updateInProgress = true
            numMin.setText(getTargetString(ReceiveData.targetMin))
            numMax.setText(getTargetString(ReceiveData.targetMax))
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
        numMinChanger.updateInProgress = false
        numMaxChanger.updateInProgress = false
    }

    private fun getTargetString(value: Float): String {
        if (useMmol)
            return value.toString()
        return value.toInt().toString()
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received from: " + dataSource.toString())
        update()
    }
}
