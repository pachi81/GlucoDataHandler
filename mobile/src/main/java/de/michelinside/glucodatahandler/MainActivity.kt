package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
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
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.core.view.setPadding
import androidx.preference.PreferenceManager
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmState
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.preferences.AlarmFragment
import de.michelinside.glucodatahandler.watch.LogcatReceiver
import de.michelinside.glucodatahandler.watch.WatchDrip
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.days
import de.michelinside.glucodatahandler.common.R as CR


class MainActivity : AppCompatActivity(), NotifierInterface {
    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var timeText: TextView
    private lateinit var deltaText: TextView
    private lateinit var iobText: TextView
    private lateinit var cobText: TextView
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var tableDetails: TableLayout
    private lateinit var tableConnections: TableLayout
    private lateinit var tableAlarms: TableLayout
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

            GlucoDataServiceMobile.start(this)
            PackageUtils.updatePackages(this)

            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            timeText = findViewById(R.id.timeText)
            deltaText = findViewById(R.id.deltaText)
            iobText = findViewById(R.id.iobText)
            cobText = findViewById(R.id.cobText)
            txtLastValue = findViewById(R.id.txtLastValue)
            txtBatteryOptimization = findViewById(R.id.txtBatteryOptimization)
            txtHighContrastEnabled = findViewById(R.id.txtHighContrastEnabled)
            txtScheduleExactAlarm = findViewById(R.id.txtScheduleExactAlarm)
            txtNotificationPermission = findViewById(R.id.txtNotificationPermission)
            btnSources = findViewById(R.id.btnSources)
            tableConnections = findViewById(R.id.tableConnections)
            tableAlarms = findViewById(R.id.tableAlarms)
            tableDetails = findViewById(R.id.tableDetails)

            PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            ReceiveData.initData(this)

            txtVersion = findViewById(R.id.txtVersion)
            txtVersion.text = BuildConfig.VERSION_NAME

            btnSources.setOnClickListener{
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.SORUCE_FRAGMENT.value)
                startActivity(intent)
            }
            Dialogs.updateColorScheme(this)

            if (requestPermission())
                GlucoDataServiceMobile.start(this)

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
            checkUncaughtException()
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
                NotifySource.TIME_VALUE,
                NotifySource.ALARM_STATE_CHANGED,
                NotifySource.SOURCE_STATE_CHANGE))
            checkExactAlarmPermission()
            checkBatteryOptimization()
            checkHighContrast()
            checkFullscreenPermission()

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
                Dialogs.showOkDialog(this, CR.string.permission_notification_title, CR.string.permission_notification_message) { _, _ -> requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
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

    private fun checkFullscreenPermission() {
        if(sharedPref.contains(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED) && sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, true)) {
            if (!AlarmNotification.hasFullscreenPermission()) {
                Dialogs.showOkCancelDialog(this,
                    resources.getString(CR.string.permission_missing_title),
                    resources.getString(CR.string.setting_permission_missing_message, resources.getString(CR.string.alarm_fullscreen_notification_enabled)),
                    { _, _ -> AlarmFragment.requestFullScreenPermission(this) },
                    { _, _ ->
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, false)
                            apply()
                        }
                    }
                )
            }
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
                R.id.action_snooze_30 -> {
                    if(BuildConfig.DEBUG) {
                        AlarmHandler.setSnooze(1L)
                    } else {
                        AlarmHandler.setSnooze(30L)
                    }
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
            if(AlarmNotification.channelActive(this)) {
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
            } else {
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                    apply()
                }
                Dialogs.showOkDialog(this, CR.string.permission_alarm_notification_title, CR.string.permission_alarm_notification_message) { _, _ ->
                    val intent: Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                    startActivity(intent)
                }
            }
            //updateAlarmIcon()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    private fun updateAlarmIcon() {
        try {
            if(!AlarmNotification.channelActive(this)) {
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                    apply()
                }
            }
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

    @SuppressLint("SetTextI18n")
    private fun update() {
        try {
            Log.v(LOG_ID, "update values")
            txtBgValue.text = ReceiveData.getGlucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon(withShadow = true))

            timeText.text = "🕒 ${ReceiveData.getElapsedRelativeTimeAsString(this)}"
            deltaText.text = "Δ ${ReceiveData.getDeltaAsString()}"
            iobText.text = "💉 " + ReceiveData.getIobAsString()
            cobText.text = "🍔 " + ReceiveData.getCobAsString()
            iobText.visibility = if (ReceiveData.isIobCobObsolete()) View.GONE else View.VISIBLE
            cobText.visibility = iobText.visibility

            txtLastValue.visibility = if(ReceiveData.time>0) View.GONE else View.VISIBLE

            if (ReceiveData.time == 0L) {
                btnSources.visibility = View.VISIBLE
            } else {
                btnSources.visibility = View.GONE
            }

            updateAlarmsTable()
            updateConnectionsTable()
            updateDetailsTable()

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

    private fun updateConnectionsTable() {
        tableConnections.removeViews(1, maxOf(0, tableConnections.childCount - 1))
        if (SourceStateData.lastState != SourceState.NONE) {
            val msg = SourceStateData.getStateMessage(this)
            tableConnections.addView(
                createRow(
                    SourceStateData.lastSource.resId,
                    msg
                )
            )
            if(SourceStateData.lastState == SourceState.ERROR && SourceStateData.lastSource == DataSource.DEXCOM_SHARE) {
                if (msg.contains("500:")) { // invalid password
                    val us_account = sharedPref.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false)
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getString(if(us_account)CR.string.dexcom_account_us_url else CR.string.dexcom_account_non_us_url))
                    )
                    val onClickListener = OnClickListener {
                        startActivity(browserIntent)
                    }
                    tableConnections.addView(
                        createRow(
                            SourceStateData.lastSource.resId,
                            resources.getString(if(us_account) CR.string.dexcom_share_check_us_account else CR.string.dexcom_share_check_non_us_account),
                            onClickListener
                        )
                    )
                }
            }
        }

        if (WearPhoneConnection.nodesConnected) {
            val onClickListener = OnClickListener {
                GlucoDataService.checkForConnectedNodes(true)
            }
            WearPhoneConnection.getNodeBatterLevels().forEach { name, level ->
                tableConnections.addView(createRow(name, if (level > 0) "$level%" else "?%", onClickListener))
            }
        }
        if (WatchDrip.connected) {
            tableConnections.addView(createRow(CR.string.pref_switch_watchdrip_enabled, resources.getString(CR.string.connected_label)))
        }

        if (CarModeReceiver.AA_connected) {
            tableConnections.addView(createRow(CR.string.pref_cat_android_auto, resources.getString(CR.string.connected_label)))
        }
        checkTableVisibility(tableConnections)
    }

    private fun updateAlarmsTable() {
        tableAlarms.removeViews(1, maxOf(0, tableAlarms.childCount - 1))
        if(ReceiveData.time > 0 && ReceiveData.getAlarmType() != AlarmType.OK) {
            tableAlarms.addView(createRow(CR.string.info_label_alarm, resources.getString(ReceiveData.getAlarmType().resId) + (if (ReceiveData.forceAlarm) " ⚠" else "" )))
        }
        if (AlarmHandler.isSnoozeActive)
            tableAlarms.addView(createRow(CR.string.snooze, AlarmHandler.snoozeTimestamp))
        checkTableVisibility(tableAlarms)
    }

    private fun updateDetailsTable() {
        tableDetails.removeViews(1, maxOf(0, tableDetails.childCount - 1))
        if(ReceiveData.time > 0) {
            if (sharedPref.getBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, false)) {
                tableDetails.addView(createRow(ReceiveData.getOtherUnit(), ReceiveData.getGlucoseAsOtherUnit() + " (Δ " + ReceiveData.getDeltaAsOtherUnit() + ")"))
            }
            tableDetails.addView(createRow(CR.string.info_label_timestamp, DateFormat.getTimeInstance(
                DateFormat.DEFAULT).format(Date(ReceiveData.time))))
            if (!ReceiveData.isIobCobObsolete(1.days.inWholeSeconds.toInt()))
                tableDetails.addView(createRow(CR.string.info_label_iob_cob_timestamp, DateFormat.getTimeInstance(
                    DateFormat.DEFAULT).format(Date(ReceiveData.iobCobTime))))
            if (ReceiveData.sensorID?.isNotEmpty() == true) {
                tableDetails.addView(createRow(CR.string.info_label_sensor_id, if(BuildConfig.DEBUG) "ABCDE12345" else ReceiveData.sensorID!!))
            }
            if(ReceiveData.source != DataSource.NONE)
                tableDetails.addView(createRow(CR.string.info_label_source, resources.getString(ReceiveData.source.resId)))
        }
        checkTableVisibility(tableDetails)
    }

    private fun checkTableVisibility(table: TableLayout) {
        table.visibility = if(table.childCount <= 1) View.GONE else View.VISIBLE
    }

    private fun createColumn(text: String, end: Boolean, onClickListener: OnClickListener? = null) : TextView {
        val textView = TextView(this)
        textView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1F)
        textView.text = text
        textView.textSize = 18F
        if (end)
            textView.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        else
            textView.gravity = Gravity.CENTER_VERTICAL
        if(onClickListener != null)
            textView.setOnClickListener(onClickListener)
        return textView
    }

    private fun createRow(keyResId: Int, value: String, onClickListener: OnClickListener? = null) : TableRow {
        return createRow(resources.getString(keyResId), value, onClickListener)
    }

    private fun createRow(key: String, value: String, onClickListener: OnClickListener? = null) : TableRow {
        val row = TableRow(this)
        row.weightSum = 2f
        //row.setBackgroundColor(resources.getColor(R.color.table_row))
        row.setPadding(Utils.dpToPx(5F, this))
        row.addView(createColumn(key, false, onClickListener))
        row.addView(createColumn(value, true, onClickListener))
        return row
    }

    companion object {
        const val CREATE_PHONE_FILE = 1
        const val CREATE_WEAR_FILE = 2
    }

    private fun checkUncaughtException() {
        Log.d(LOG_ID, "Check uncaught exception ${sharedPref.getBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, false)}")
        if(sharedPref.getBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, false)) {
            val excMsg = sharedPref.getString(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_MESSAGE, "")
            Log.e(LOG_ID, "Uncaught exception detected last time: $excMsg")
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, false)
                apply()
            }
            Dialogs.showOkDialog(this, CR.string.app_crash_title, CR.string.app_crash_message, null)
        }
    }
}