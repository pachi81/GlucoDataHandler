package de.michelinside.glucodatahandler

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmState
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.watch.LogcatReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import de.michelinside.glucodatahandler.common.R as CR


class MainActivity : AppCompatActivity(), NotifierInterface {
    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtWearInfo: TextView
    private lateinit var txtCarInfo: TextView
    private lateinit var txtSourceInfo: TextView
    private lateinit var txtBatteryOptimization: TextView
    private lateinit var txtHighContrastEnabled: TextView
    private lateinit var txtScheduleExactAlarm: TextView
    private lateinit var txtNotificationPermission: TextView
    private lateinit var btnSources: Button
    private lateinit var sharedPref: SharedPreferences
    private lateinit var optionsMenu: Menu
    private var alarmIcon: MenuItem? = null
    private var snoozeMenu: MenuItem? = null
    private var floatingWidgetItem: MenuItem? = null
    private val LOG_ID = "GDH.Main"
    private var requestNotificationPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            Log.v(LOG_ID, "onCreate called")

            GlucoDataServiceMobile.start(this, true)

            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            txtLastValue = findViewById(R.id.txtLastValue)
            txtWearInfo = findViewById(R.id.txtWearInfo)
            txtCarInfo = findViewById(R.id.txtCarInfo)
            txtSourceInfo = findViewById(R.id.txtSourceInfo)
            txtBatteryOptimization = findViewById(R.id.txtBatteryOptimization)
            txtHighContrastEnabled = findViewById(R.id.txtHighContrastEnabled)
            txtScheduleExactAlarm = findViewById(R.id.txtScheduleExactAlarm)
            txtNotificationPermission = findViewById(R.id.txtNotificationPermission)
            btnSources = findViewById(R.id.btnSources)

            PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            ReceiveData.initData(this)

            txtVersion = findViewById(R.id.txtVersion)
            txtVersion.text = BuildConfig.VERSION_NAME

            txtWearInfo.setOnClickListener{
                GlucoDataService.checkForConnectedNodes()
            }

            btnSources.setOnClickListener{
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.SORUCE_FRAGMENT.value)
                startActivity(intent)
            }

            val sendToAod = sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)

            if(!sharedPref.contains(Constants.SHARED_PREF_GLUCODATA_RECEIVERS)) {
                val receivers = HashSet<String>()
                if (sendToAod)
                    receivers.add("de.metalgearsonic.glucodata.aod")
                Log.i(LOG_ID, "Upgrade receivers to " + receivers.toString())
                with(sharedPref.edit()) {
                    putStringSet(Constants.SHARED_PREF_GLUCODATA_RECEIVERS, receivers)
                    apply()
                }
            }

            if(!sharedPref.contains(Constants.SHARED_PREF_XDRIP_RECEIVERS)) {
                val receivers = HashSet<String>()
                receivers.add("com.eveningoutpost.dexdrip")
                Log.i(LOG_ID, "Upgrade receivers to " + receivers.toString())
                with(sharedPref.edit()) {
                    putStringSet(Constants.SHARED_PREF_XDRIP_RECEIVERS, receivers)
                    apply()
                }
            }

            if (requestPermission())
                GlucoDataServiceMobile.start(this, true)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            InternalNotifier.remNotifier(this, this)
            Log.v(LOG_ID, "onPause called")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.message.toString() )
        }
    }

    override fun onResume() {
        try {
            super.onResume()
            Log.v(LOG_ID, "onResume called")
            update()
            InternalNotifier.addNotifier(this, this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.IOB_COB_CHANGE,
                NotifySource.IOB_COB_TIME,
                NotifySource.MESSAGECLIENT,
                NotifySource.CAPILITY_INFO,
                NotifySource.NODE_BATTERY_LEVEL,
                NotifySource.SETTINGS,
                NotifySource.CAR_CONNECTION,
                NotifySource.OBSOLETE_VALUE,
                NotifySource.ALARM_STATE_CHANGED,
                NotifySource.SOURCE_STATE_CHANGE))
            checkExactAlarmPermission()
            checkBatteryOptimization()
            checkHighContrast()

            if (requestNotificationPermission && Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Notification permission granted")
                requestNotificationPermission = false
                txtNotificationPermission.visibility = View.GONE
                PermanentNotification.showNotifications()
            }
            GlucoDataService.checkForConnectedNodes(true)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.message.toString() )
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            Log.d(LOG_ID, "Notification permission allowed: $isGranted")
        }


    private fun showPermissionRationaleDialog(title: String, message: String, okListener: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(resources.getText(CR.string.button_ok), okListener)
            .show()
    }
    fun requestPermission() : Boolean {
        requestNotificationPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
            Log.i(LOG_ID, "Request notification permission...")
            requestNotificationPermission = true
            /*
            txtNotificationPermission.visibility = View.VISIBLE
            txtNotificationPermission.setOnClickListener {
                val intent: Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                startActivity(intent)
            }*/
            if (this.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.POST_NOTIFICATIONS)) {
                showPermissionRationaleDialog(resources.getString(CR.string.permission_notification_title), resources.getString(CR.string.permission_notification_message)) { _, _ -> requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            } else {
                this.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 3)
            }
            return false
        } else {
            txtNotificationPermission.visibility = View.GONE
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            Log.i(LOG_ID, "Request exact alarm permission...")
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder
                .setTitle(CR.string.request_exact_alarm_title)
                .setMessage(CR.string.request_exact_alarm_summary)
                .setPositiveButton(CR.string.button_ok) { dialog, which ->
                    startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
                .setNegativeButton(CR.string.button_cancel) { dialog, which ->
                    // Do something else.
                }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }
    private fun checkExactAlarmPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
                Log.w(LOG_ID, "Schedule exact alarm is not active!!!")
                txtScheduleExactAlarm.visibility = View.VISIBLE
                txtScheduleExactAlarm.setOnClickListener {
                    startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            } else {
                txtScheduleExactAlarm.visibility = View.GONE
                Log.i(LOG_ID, "Schedule exact alarm is active")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkBatteryOptimization exception: " + exc.message.toString() )
        }
    }

    private fun checkBatteryOptimization() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(LOG_ID, "Battery optimization is active")
                txtBatteryOptimization.visibility = View.VISIBLE
                txtBatteryOptimization.setOnClickListener {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            } else {
                txtBatteryOptimization.visibility = View.GONE
                Log.i(LOG_ID, "Battery optimization is inactive")
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        try {
            Log.v(LOG_ID, "onCreateOptionsMenu called")
            val inflater = menuInflater
            inflater.inflate(R.menu.menu_items, menu)
            MenuCompat.setGroupDividerEnabled(menu!!, true)
            optionsMenu = menu
            alarmIcon = optionsMenu.findItem(R.id.action_alarm_toggle)
            snoozeMenu = optionsMenu.findItem(R.id.group_snooze_title)
            floatingWidgetItem = optionsMenu.findItem(R.id.action_floating_widget_toggle)
            updateAlarmIcon()
            updateMenuItems()
            return true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreateOptionsMenu exception: " + exc.message.toString() )
        }
        return true
    }

    private fun updateMenuItems() {
        if(floatingWidgetItem!=null)
            floatingWidgetItem!!.isVisible = Settings.canDrawOverlays(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            Log.v(LOG_ID, "onOptionsItemSelected for " + item.itemId.toString())
            when(item.itemId) {
                R.id.action_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.SETTINGS_FRAGMENT.value)
                    startActivity(intent)
                    return true
                }
                R.id.action_sources -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.SORUCE_FRAGMENT.value)
                    startActivity(intent)
                    return true
                }
                R.id.action_alarms -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.ALARM_FRAGMENT.value)
                    startActivity(intent)
                    return true
                }
                R.id.action_help -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getText(CR.string.help_link).toString())
                    )
                    startActivity(browserIntent)
                    return true
                }
                R.id.action_support -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getText(CR.string.support_link).toString())
                    )
                    startActivity(browserIntent)
                    return true
                }
                R.id.action_contact -> {
                    val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        "mailto","GlucoDataHandler@michel-inside.de", null))
                    mailIntent.putExtra(Intent.EXTRA_SUBJECT, "GlucoDataHander v" + BuildConfig.VERSION_NAME)
                    mailIntent.putExtra(Intent.EXTRA_TEXT, "")
                    if (mailIntent.resolveActivity(packageManager) != null) {
                        startActivity(mailIntent)
                    }
                    return true
                }
                R.id.action_save_mobile_logs -> {
                    SaveLogs(AppSource.PHONE_APP)
                    return true
                }
                R.id.action_save_wear_logs -> {
                    SaveLogs(AppSource.WEAR_APP)
                    return true
                }
                R.id.group_log_title -> {
                    Log.v(LOG_ID, "log group selected")
                    val menuIt: MenuItem = optionsMenu.findItem(R.id.action_save_wear_logs)
                    menuIt.isEnabled = WearPhoneConnection.nodesConnected && !LogcatReceiver.isActive
                }
                R.id.group_snooze_title -> {
                    Log.v(LOG_ID, "snooze group selected - snoozeActive=${AlarmHandler.isSnoozeActive}")
                    val snoozeStop: MenuItem = optionsMenu.findItem(R.id.action_stop_snooze)
                    snoozeStop.isVisible = AlarmHandler.isSnoozeActive
                }
                R.id.action_stop_snooze -> {
                    AlarmHandler.setSnooze(0L)
                    return true
                }
                R.id.action_snooze_60 -> {
                    AlarmHandler.setSnooze(60L)
                    return true
                }
                R.id.action_snooze_90 -> {
                    AlarmHandler.setSnooze(90L)
                    return true
                }
                R.id.action_snooze_120 -> {
                    AlarmHandler.setSnooze(120L)
                    return true
                }
                R.id.action_alarm_toggle -> {
                    toggleAlarm()
                    return true
                }
                R.id.action_floating_widget_toggle -> {
                    Log.v(LOG_ID, "Floating widget toggle")
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, !sharedPref.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false))
                        apply()
                    }
                }
                else -> return super.onOptionsItemSelected(item)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onOptionsItemSelected exception: " + exc.message.toString() )
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleAlarm() {
        try {
            val state = AlarmNotification.getAlarmState(this)
            if(Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.v(LOG_ID, "toggleAlarm called for state $state")
                when (state) {
                    AlarmState.SNOOZE -> AlarmHandler.setSnooze(0)  // disable snooze
                    AlarmState.DISABLED -> {
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, true)
                            apply()
                        }
                    }
                    AlarmState.INACTIVE,
                    AlarmState.ACTIVE -> {
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                            apply()
                        }
                    }
                }
            } else if(state != AlarmState.DISABLED) {
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                    apply()
                }
            }
            updateAlarmIcon()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    private fun updateAlarmIcon() {
        try {
            val state = AlarmNotification.getAlarmState(this)
            Log.v(LOG_ID, "updateAlarmIcon called for state $state")
            if(alarmIcon != null) {
                alarmIcon!!.icon = ContextCompat.getDrawable(this, state.icon)
            }
            if(snoozeMenu != null) {
                snoozeMenu!!.isVisible = (state != AlarmState.DISABLED)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    private fun update() {
        try {
            Log.v(LOG_ID, "update values")
            txtBgValue.text = ReceiveData.getClucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getClucoseColor())
            if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon())
            txtLastValue.text = ReceiveData.getAsString(this)
            if (WearPhoneConnection.nodesConnected) {
                txtWearInfo.text = resources.getString(CR.string.activity_main_connected_label, WearPhoneConnection.getBatterLevelsAsString())
            }
            else
                txtWearInfo.text = resources.getText(CR.string.activity_main_disconnected_label)
            if (Utils.isGlucoDataAutoAvailable(this)) {
                txtCarInfo.text = if (CarModeReceiver.AA_connected) resources.getText(CR.string.activity_main_car_connected_label) else resources.getText(CR.string.activity_main_car_disconnected_label)
                txtCarInfo.visibility = View.VISIBLE
            } else {
                txtCarInfo.visibility = View.GONE
            }

            if (ReceiveData.time == 0L) {
                btnSources.visibility = View.VISIBLE
            } else {
                btnSources.visibility = View.GONE
            }

            txtSourceInfo.text = SourceStateData.getState(this)
            updateAlarmIcon()
            updateMenuItems()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.v(LOG_ID, "OnNotifyData called for $dataSource")
        update()
    }

    private fun SaveLogs(source: AppSource) {
        try {
            Log.v(LOG_ID, "Save logs called for " + source)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                val currentDateandTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "GDH_" + source + "_" + currentDateandTime + ".txt"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            startActivityForResult(intent, if (source == AppSource.WEAR_APP) CREATE_WEAR_FILE else CREATE_PHONE_FILE)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving mobile logs exception: " + exc.message.toString() )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            Log.v(LOG_ID, "onActivityResult called for requestCode: " + requestCode + " - resultCode: " + resultCode + " - data: " + Utils.dumpBundle(data?.extras))
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.also { uri ->
                    Log.v(LOG_ID, "Save logs to " + uri)
                    if (requestCode == CREATE_PHONE_FILE) {
                        Utils.saveLogs(this, uri)
                    } else if(requestCode == CREATE_WEAR_FILE) {
                        LogcatReceiver.requestLogs(this, uri)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving logs exception: " + exc.message.toString() )
        }
    }

    companion object {
        const val CREATE_PHONE_FILE = 1
        const val CREATE_WEAR_FILE = 2
    }
}