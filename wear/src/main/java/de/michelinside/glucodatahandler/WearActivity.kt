package de.michelinside.glucodatahandler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.databinding.ActivityWearBinding
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import de.michelinside.glucodatahandler.settings.AlarmsActivity
import de.michelinside.glucodatahandler.settings.SettingsActivity
import de.michelinside.glucodatahandler.settings.SourcesActivity

class WearActivity : AppCompatActivity(), NotifierInterface {

    private val LOG_ID = "GDH.Main"
    private lateinit var binding: ActivityWearBinding
    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var txtVersion: TextView
    private lateinit var txtValueInfo: TextView
    private lateinit var txtConnInfo: TextView
    private lateinit var txtSourceInfo: TextView
    private lateinit var txtHighContrastEnabled: TextView
    private lateinit var txtScheduleExactAlarm: TextView
    private lateinit var btnSettings: Button
    private lateinit var btnSources: Button
    private lateinit var btnAlarms: Button
    private var requestNotificationPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            installSplashScreen()
            super.onCreate(savedInstanceState)

            //setTheme(android.R.style.Theme_DeviceDefault)

            binding = ActivityWearBinding.inflate(layoutInflater)
            setContentView(binding.root)

            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            txtValueInfo = findViewById(R.id.txtValueInfo)
            txtConnInfo = findViewById(R.id.txtConnInfo)
            txtSourceInfo = findViewById(R.id.txtSourceInfo)
            txtHighContrastEnabled = findViewById(R.id.txtHighContrastEnabled)
            txtScheduleExactAlarm = findViewById(R.id.txtScheduleExactAlarm)

            txtVersion = findViewById(R.id.txtVersion)
            txtVersion.text = BuildConfig.VERSION_NAME

            ReceiveData.initData(this)

            txtConnInfo.setOnClickListener{
                GlucoDataService.checkForConnectedNodes()
            }

            btnSettings = findViewById(R.id.btnSettings)
            btnSettings.setOnClickListener {
                Log.v(LOG_ID, "Settings button clicked!")
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }

            btnSources = findViewById(R.id.btnSources)
            btnSources.setOnClickListener {
                Log.v(LOG_ID, "Sources button clicked!")
                val intent = Intent(this, SourcesActivity::class.java)
                startActivity(intent)
            }


            btnAlarms = findViewById(R.id.btnAlarms)
            btnAlarms.setOnClickListener {
                Log.v(LOG_ID, "Alarm button clicked!")
                val intent = Intent(this, AlarmsActivity::class.java)
                startActivity(intent)
            }
            if(requestPermission())
                GlucoDataServiceWear.start(this, true)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            InternalNotifier.remNotifier(this, this)
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
            InternalNotifier.addNotifier(this, this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.IOB_COB_CHANGE,
                NotifySource.IOB_COB_TIME,
                NotifySource.MESSAGECLIENT,
                NotifySource.CAPILITY_INFO,
                NotifySource.NODE_BATTERY_LEVEL,
                NotifySource.SETTINGS,
                NotifySource.OBSOLETE_VALUE,
                NotifySource.SOURCE_SETTINGS,
                NotifySource.ALARM_SETTINGS,
                NotifySource.SOURCE_STATE_CHANGE,
                NotifySource.ALARM_STATE_CHANGED))
            checkExactAlarmPermission()
            checkHighContrast()
            if (requestNotificationPermission && Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Notification permission granted")
                requestNotificationPermission = false
                GlucoDataServiceWear.start(this, true)
            }
            GlucoDataService.checkForConnectedNodes(true)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    fun requestPermission() : Boolean {
        requestNotificationPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Request notification permission...")
                requestNotificationPermission = true
                this.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 3)
                return false
            }
        }
        requestExactAlarmPermission()
        return true
    }

    private fun canScheduleExactAlarms(): Boolean {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    private fun requestExactAlarmPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
                Log.i(LOG_ID, "Request exact alarm permission...")
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "requestExactAlarmPermission exception: " + exc.message.toString() )
        }
    }
    private fun checkExactAlarmPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
                Log.w(LOG_ID, "Schedule exact alarm is not active!!!")
                txtScheduleExactAlarm.visibility = View.VISIBLE
                txtScheduleExactAlarm.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            } else {
                txtScheduleExactAlarm.visibility = View.GONE
                Log.i(LOG_ID, "Schedule exact alarm is active")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkBatteryOptimization exception: " + exc.message.toString() )
        }
    }

    private fun checkHighContrast() {
        try {
            if (Utils.isHighContrastTextEnabled(this)) {
                Log.w(LOG_ID, "High contrast is active")
                txtHighContrastEnabled.visibility = View.VISIBLE
                txtHighContrastEnabled.setOnClickListener {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            } else {
                txtHighContrastEnabled.visibility = View.GONE
                Log.i(LOG_ID, "High contrast is inactive")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkBatteryOptimization exception: " + exc.message.toString() )
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
                viewIcon.setImageIcon(BitmapUtils.getRateAsIcon())
                txtValueInfo.text = ReceiveData.getAsString(this)
                if (WearPhoneConnection.nodesConnected) {
                    txtConnInfo.text = resources.getString(CR.string.activity_connected_label, WearPhoneConnection.getBatterLevelsAsString())
                } else
                    txtConnInfo.text = resources.getText(CR.string.activity_disconnected_label)
            }
           txtSourceInfo.text = SourceStateData.getState(this)

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received from: " + dataSource.toString())
        update()
    }
}
