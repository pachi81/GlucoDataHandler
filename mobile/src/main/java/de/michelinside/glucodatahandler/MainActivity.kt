package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import de.michelinside.glucodatahandler.common.*


class MainActivity : AppCompatActivity(), ReceiveDataInterface {
    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtWearInfo: TextView
    private lateinit var switchSendToAod: SwitchCompat
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


        txtBgValue = findViewById(R.id.txtBgValue)
        viewIcon = findViewById(R.id.viewIcon)
        txtLastValue = findViewById(R.id.txtLastValue)
        txtWearInfo = findViewById(R.id.txtWearInfo)

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

        ReceiveData.readTargets(this)

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        Log.d(LOG_ID, "onCreateOptionsMenu called")
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_items, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(LOG_ID, "onOptionsItemSelected for " + item.itemId.toString())
        if (item.itemId == R.id.action_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun update() {
        try {
            Log.d(LOG_ID, "update values")
            txtBgValue.text = ReceiveData.getClucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getClucoseColor())
            viewIcon.setImageIcon(ReceiveData.getArrowIcon())
            txtLastValue.text = ReceiveData.getAsString(this)
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