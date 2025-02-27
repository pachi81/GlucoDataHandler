package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
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
import android.widget.LinearLayout
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
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.chart.ChartCreator
import de.michelinside.glucodatahandler.common.chart.GlucoseChart
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmState
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.preferences.AlarmFragment
import de.michelinside.glucodatahandler.preferences.LockscreenSettingsFragment
import de.michelinside.glucodatahandler.watch.WatchDrip
import java.text.DateFormat
import java.time.Duration
import java.util.Date
import kotlin.time.Duration.Companion.days
import de.michelinside.glucodatahandler.common.R as CR


class MainActivity : AppCompatActivity(), NotifierInterface {
    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var timeText: TextView
    private lateinit var deltaText: TextView
    private lateinit var iobText: TextView
    private lateinit var cobText: TextView
    private lateinit var iobCobLayout: LinearLayout
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var tableDetails: TableLayout
    private lateinit var tableDelta: TableLayout
    private lateinit var tableConnections: TableLayout
    private lateinit var tableAlarms: TableLayout
    private lateinit var tableNotes: TableLayout
    private lateinit var btnSources: Button
    private lateinit var sharedPref: SharedPreferences
    private lateinit var optionsMenu: Menu
    private lateinit var chart: GlucoseChart
    private var alarmIcon: MenuItem? = null
    private var snoozeMenu: MenuItem? = null
    private var floatingWidgetItem: MenuItem? = null
    private val LOG_ID = "GDH.Main"
    private var requestNotificationPermission = false
    private var doNotUpdate = false
    private lateinit var chartCreator: ChartCreator

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
            iobCobLayout = findViewById(R.id.layout_iob_cob)
            txtLastValue = findViewById(R.id.txtLastValue)
            btnSources = findViewById(R.id.btnSources)
            tableConnections = findViewById(R.id.tableConnections)
            tableAlarms = findViewById(R.id.tableAlarms)
            tableDetails = findViewById(R.id.tableDetails)
            tableDelta = findViewById(R.id.tableDelta)
            tableNotes = findViewById(R.id.tableNotes)
            chart = findViewById(R.id.chart)

            PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            ReceiveData.initData(this)

            txtVersion = findViewById(R.id.txtVersion)
            txtVersion.text = BuildConfig.VERSION_NAME

            btnSources.setOnClickListener{
                try {
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.SORUCE_FRAGMENT.value)
                    startActivity(intent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "btn source exception: " + exc.message.toString() )
                }
            }
            Dialogs.updateColorScheme(this)

            if (requestPermission())
                GlucoDataServiceMobile.start(this)
            TextToSpeechUtils.initTextToSpeech(this)
            chartCreator = ChartCreator(chart, this, Constants.SHARED_PREF_GRAPH_DURATION_PHONE_MAIN, Constants.SHARED_PREF_GRAPH_TRANSPARENCY_PHONE_MAIN)
            chartCreator.create()
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
            GlucoDataService.checkServices(this)
            checkUncaughtException()
            doNotUpdate = false
            update()
            chartCreator.resume()
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
            checkMissingPermissions()
            checkNewSettings()

            if (requestNotificationPermission && Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Notification permission granted")
                requestNotificationPermission = false
                PermanentNotification.showNotifications()
            }
            GlucoDataService.checkForConnectedNodes(true)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.message.toString() )
        }
    }

    override fun onDestroy() {
        Log.v(LOG_ID, "onDestroy called")
        super.onDestroy()
        chartCreator.close()
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
            if (this.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.POST_NOTIFICATIONS)) {
                Dialogs.showOkDialog(this, CR.string.permission_notification_title, CR.string.permission_notification_message) { _, _ -> requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            } else {
                this.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 3)
            }
            return false
        }
        requestExactAlarmPermission()
        return true
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Utils.canScheduleExactAlarms(this)) {
            Log.i(LOG_ID, "Request exact alarm permission...")
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder
                .setTitle(CR.string.request_exact_alarm_title)
                .setMessage(CR.string.request_exact_alarm_summary)
                .setPositiveButton(CR.string.button_ok) { dialog, which ->
                    try {
                        startActivity(
                            Intent(
                                ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "requestExactAlarmPermission exception: " + exc.message.toString() )
                    }
                }
                .setNegativeButton(CR.string.button_cancel) { dialog, which ->
                    // Do something else.
                }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }

    private fun checkMissingPermissions() {
        var permissionRequested = false
        if(sharedPref.contains(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED) && sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, false)) {
            if (!AlarmNotification.hasFullscreenPermission(this)) {
                permissionRequested = true
                Dialogs.showOkCancelDialog(this,
                    resources.getString(CR.string.permission_missing_title),
                    resources.getString(CR.string.setting_permission_missing_message, resources.getString(CR.string.alarm_fullscreen_notification_enabled)),
                    { _, _ ->
                        AlarmFragment.requestFullScreenPermission(this)
                    },
                    { _, _ ->
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, false)
                            apply()
                        }
                    }
                )
            }
        }
        if(!permissionRequested && sharedPref.contains(Constants.SHARED_PREF_AOD_WP_ENABLED) && sharedPref.getBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, false)) {
            if (!AODAccessibilityService.isAccessibilitySettingsEnabled(this)) {
                Dialogs.showOkCancelDialog(this,
                    resources.getString(CR.string.permission_missing_title),
                    resources.getString(CR.string.setting_permission_missing_message, resources.getString(CR.string.pref_cat_aod)),
                    { _, _ -> LockscreenSettingsFragment.requestAccessibilitySettings(this) },
                    { _, _ ->
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, false)
                            apply()
                        }
                    }
                )
            }
        }
    }

    private fun checkNewSettings() {
        try {
            if(!sharedPref.contains(Constants.SHARED_PREF_DISCLAIMER_SHOWN)) {
                Dialogs.showOkDialog(this,
                    CR.string.gdh_disclaimer_title,
                    CR.string.gdh_disclaimer_message,
                    null
                )
                with(sharedPref.edit()) {
                    putString(Constants.SHARED_PREF_DISCLAIMER_SHOWN, BuildConfig.VERSION_NAME)
                    apply()
                }
            }
            if(!sharedPref.contains(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU)) {
                if(sharedPref.getBoolean(Constants.SHARED_PREF_LIBRE_ENABLED, false)) {
                    Dialogs.showOkCancelDialog(this,
                        resources.getString(CR.string.src_cat_libreview),
                        resources.getString(CR.string.src_libre_tou_message),
                        { _, _ ->
                            with(sharedPref.edit()) {
                                putBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, true)
                                apply()
                            }
                        },
                        { _, _ ->
                            with(sharedPref.edit()) {
                                putBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, false)
                                apply()
                            }
                        })
                } else {
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, true)
                        apply()
                    }
                }
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkNewSettings exception: " + exc.message.toString() )
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
        if(floatingWidgetItem!=null) {
            floatingWidgetItem!!.isVisible = Settings.canDrawOverlays(this)
            if(floatingWidgetItem!!.isVisible) {
                if(sharedPref.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false)) {
                    floatingWidgetItem!!.title = resources.getString(CR.string.pref_floating_widget) + " " + resources.getString(CR.string.enabled)
                } else {
                    floatingWidgetItem!!.title = resources.getString(CR.string.pref_floating_widget) + " " + resources.getString(CR.string.disabled)
                }
            }
        }
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
                R.id.action_google_groups -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getText(CR.string.google_gdh_group_url).toString())
                    )
                    startActivity(browserIntent)
                    return true
                }
                R.id.action_facebook -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getText(CR.string.facebook_gdh_group_url).toString())
                    )
                    startActivity(browserIntent)
                    return true
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
                    updateMenuItems()
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
            doNotUpdate = true
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
                    AlarmState.TEMP_DISABLED -> {
                        AlarmHandler.disableInactiveTime()
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
            updateAlarmIcon()
            updateAlarmsTable()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
        doNotUpdate = false
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
                alarmIcon!!.isEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ENABLE_ALARM_ICON_TOGGLE, true)
                alarmIcon!!.icon = ContextCompat.getDrawable(this, state.icon)
                alarmIcon!!.title = resources.getString(CR.string.alarm_toggle_state,
                    when(state) {
                        AlarmState.SNOOZE -> resources.getString(state.descr, AlarmHandler.snoozeShortTimestamp)
                        AlarmState.TEMP_DISABLED -> resources.getString(state.descr, AlarmHandler.inactiveEndTimestamp)
                        else -> resources.getString(state.descr)
                    })
            }
            if(snoozeMenu != null) {
                snoozeMenu!!.isVisible = (state == AlarmState.ACTIVE || state == AlarmState.SNOOZE)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun update() {
        try {
            Log.v(LOG_ID, "update values - doNotUpdate=$doNotUpdate")
            if(doNotUpdate)
                return
            txtBgValue.text = ReceiveData.getGlucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon(withShadow = true))
            viewIcon.contentDescription = ReceiveData.getRateAsText(this)

            timeText.text = "🕒 ${ReceiveData.getElapsedRelativeTimeAsString(this)}"
            timeText.contentDescription = ReceiveData.getElapsedRelativeTimeAsString(this, true)
            deltaText.text = "Δ ${ReceiveData.getDeltaAsString()}"
            iobText.text = "💉 " + ReceiveData.getIobAsString()
            iobText.contentDescription = getString(CR.string.info_label_iob) + " " + ReceiveData.getIobAsString()
            iobText.visibility = if (ReceiveData.isIobCobObsolete()) View.GONE else View.VISIBLE
            cobText.text = "🍔 " + ReceiveData.getCobAsString()
            cobText.contentDescription = getString(CR.string.info_label_cob) + " " + ReceiveData.getCobAsString()
            cobText.visibility = iobText.visibility
            iobCobLayout.visibility = iobText.visibility

            txtLastValue.visibility = if(ReceiveData.time>0) View.GONE else View.VISIBLE

            if (ReceiveData.time == 0L) {
                btnSources.visibility = View.VISIBLE
            } else {
                btnSources.visibility = View.GONE
            }
            //chartHandler.update()
            updateNotesTable()
            updateAlarmsTable()
            updateConnectionsTable()
            updateDeltaTable()
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

    private fun updateNotesTable() {
        tableNotes.removeViews(1, maxOf(0, tableNotes.childCount - 1))
        if (!Channels.notificationChannelActive(this, ChannelType.MOBILE_FOREGROUND)) {
            val onClickListener = OnClickListener {
                try {
                    if (!Channels.notificationChannelActive(this, ChannelType.MOBILE_FOREGROUND)) {
                        requestNotificationPermission = true
                        val intent: Intent = if (Channels.notificationActive(this)) { // only the channel is inactive!
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, ChannelType.MOBILE_FOREGROUND.channelId)
                        } else {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                        }
                        startActivity(intent)
                    } else {
                        updateNotesTable()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "updateNotesTable exception: " + exc.message.toString() )
                    if(requestPermission()) {
                        GlucoDataServiceMobile.start(this)
                        updateNotesTable()
                    }
                }
            }
            tableNotes.addView(createRow(CR.string.activity_main_notification_permission, onClickListener))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Utils.canScheduleExactAlarms(this)) {
            Log.w(LOG_ID, "Schedule exact alarm is not active!!!")
            val onClickListener = OnClickListener {
                try {
                    startActivity(
                        Intent(
                            ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Schedule exact alarm exception: " + exc.message.toString() )
                }
            }
            tableNotes.addView(createRow(CR.string.activity_main_schedule_exact_alarm, onClickListener))
        }
        if (Utils.isHighContrastTextEnabled(this)) {
            Log.w(LOG_ID, "High contrast is active")
            val onClickListener = OnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "High contrast alarm exception: " + exc.message.toString() )
                }
            }
            tableNotes.addView(createRow(CR.string.activity_main_high_contrast_enabled, onClickListener))
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Log.w(LOG_ID, "Battery optimization is active")
            val onClickListener = OnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Battery optimization exception: " + exc.message.toString() )
                }
            }
            tableNotes.addView(createRow(CR.string.activity_main_battery_optimization_disabled, onClickListener))
        }
        checkTableVisibility(tableNotes)
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
            if(SourceStateData.lastState == SourceState.ERROR) {
                if(SourceStateData.lastSource == DataSource.DEXCOM_SHARE && msg.contains("500:")) {
                    val us_account = sharedPref.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false)
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getString(if(us_account)CR.string.dexcom_account_us_url else CR.string.dexcom_account_non_us_url))
                    )
                    val onClickListener = OnClickListener {
                        try {
                            startActivity(browserIntent)
                        } catch (exc: Exception) {
                            Log.e(LOG_ID, "Dexcom browse exception: " + exc.message.toString() )
                        }
                    }
                    tableConnections.addView(
                        createRow(
                            resources.getString(if(us_account) CR.string.dexcom_share_check_us_account else CR.string.dexcom_share_check_non_us_account),
                            onClickListener
                        )
                    )
                }
            }
            if(SourceStateData.lastErrorInfo.isNotEmpty()) {
                // add error specific information in an own row
                tableConnections.addView(createRow(SourceStateData.lastErrorInfo))
            }
            tableConnections.addView(createRow(CR.string.request_timestamp, Utils.getUiTimeStamp(SourceStateData.lastStateTime)))
        }

        if (WearPhoneConnection.nodesConnected) {
            if (WearPhoneConnection.connectionError) {
                val onResetClickListener = OnClickListener {
                    GlucoDataService.resetWearPhoneConnection()
                }
                tableConnections.addView(createRow(CR.string.source_wear, resources.getString(CR.string.detail_reset_connection), onResetClickListener))
            } else {
                val onCheckClickListener = OnClickListener {
                    GlucoDataService.checkForConnectedNodes(false)
                }
                WearPhoneConnection.getNodeConnectionStates(this).forEach { (name, state) ->
                    tableConnections.addView(createRow(name, state, onCheckClickListener))
                }
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
        if(ReceiveData.time > 0) {
            val alarmType = ReceiveData.getAlarmType()
            if (alarmType != AlarmType.OK)
                tableAlarms.addView(createRow(CR.string.info_label_alarm, resources.getString(alarmType.resId)))
            val deltaAlarmType = ReceiveData.getDeltaAlarmType()
            if (deltaAlarmType != AlarmType.NONE)
                tableAlarms.addView(createRow(CR.string.info_label_alarm, resources.getString(deltaAlarmType.resId)))
        }
        if (AlarmHandler.isTempInactive)
            tableAlarms.addView(createRow(CR.string.temp_disabled_until, AlarmHandler.inactiveEndTimestamp))
        else if (AlarmHandler.isSnoozeActive)
            tableAlarms.addView(createRow(CR.string.snooze_until, AlarmHandler.snoozeTimestamp))
        checkTableVisibility(tableAlarms)
    }

    private fun updateDeltaTable() {
        tableDelta.removeViews(1, maxOf(0, tableDelta.childCount - 1))
        if(!ReceiveData.isObsoleteShort()) {
            if(!ReceiveData.delta1Min.isNaN())
                tableDelta.addView(createRow(CR.string.delta_per_minute, GlucoDataUtils.deltaToString(ReceiveData.delta1Min, true)))
            if(!ReceiveData.delta5Min.isNaN())
                tableDelta.addView(createRow(CR.string.delta_per_5_minute, GlucoDataUtils.deltaToString(ReceiveData.delta5Min, true)))
            if(BuildConfig.DEBUG) {
                if(!ReceiveData.delta10Min.isNaN())
                    tableDelta.addView(createRow(CR.string.delta_per_10_minute, GlucoDataUtils.deltaToString(ReceiveData.delta10Min, true)))
            }
            if(!ReceiveData.delta15Min.isNaN())
                tableDelta.addView(createRow(CR.string.delta_per_15_minute, GlucoDataUtils.deltaToString(ReceiveData.delta15Min, true)))
            if(BuildConfig.DEBUG) {
                if(!ReceiveData.calculatedRate.isNaN()) {
                    tableDelta.addView(createRow("Calculated rate", Utils.round(ReceiveData.calculatedRate, 2).toString() + " (" + GlucoDataUtils.getRateDegrees(ReceiveData.calculatedRate).toString() + "°)"))
                }
                if(!ReceiveData.sourceRate.isNaN()) {
                    tableDelta.addView(createRow("Source rate", Utils.round(ReceiveData.sourceRate, 2).toString() + " (" + GlucoDataUtils.getRateDegrees(ReceiveData.sourceRate).toString() + "°)"))
                }
            } else {
                if(!ReceiveData.rate.isNaN()) {
                    tableDelta.addView(createRow(CR.string.trend, GlucoDataUtils.getRateDegrees(ReceiveData.rate).toString() + "°"))
                }
            }
        }
        checkTableVisibility(tableDelta)
    }

    private fun updateDetailsTable() {
        tableDetails.removeViews(1, maxOf(0, tableDetails.childCount - 1))
        if(ReceiveData.time > 0) {
            if (!ReceiveData.isObsoleteLong() && sharedPref.getBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, false)) {
                tableDetails.addView(createRow(ReceiveData.getOtherUnit(), ReceiveData.getGlucoseAsOtherUnit() + " (Δ " + ReceiveData.getDeltaAsOtherUnit() + ")"))
            }
            tableDetails.addView(createRow(CR.string.info_label_timestamp, Utils.getUiTimeStamp(ReceiveData.time)))
            if (!ReceiveData.isIobCobObsolete(1.days.inWholeSeconds.toInt()))
                tableDetails.addView(createRow(CR.string.info_label_iob_cob_timestamp, DateFormat.getTimeInstance(
                    DateFormat.DEFAULT).format(Date(ReceiveData.iobCobTime))))
            if (ReceiveData.sensorID?.isNotEmpty() == true) {
                tableDetails.addView(createRow(CR.string.info_label_sensor_id, if(BuildConfig.DEBUG) "ABCDE12345" else ReceiveData.sensorID!!))
            }
            if(ReceiveData.sensorStartTime > 0) {
                val duration = Duration.ofMillis(System.currentTimeMillis() - ReceiveData.sensorStartTime)
                val days = duration.toDays()
                val hours = duration.minusDays(days).toHours()
                tableDetails.addView(createRow(CR.string.sensor_age_label, resources.getString(CR.string.sensor_age_value).format(days, hours)))

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

    private fun createRow(valueResId: Int, onClickListener: OnClickListener? = null) : TableRow {
        return createRow(resources.getString(valueResId), onClickListener)
    }

    private fun createRow(value: String, onClickListener: OnClickListener? = null) : TableRow {
        val row = TableRow(this)
        row.weightSum = 1f
        //row.setBackgroundColor(resources.getColor(R.color.table_row))
        row.setPadding(Utils.dpToPx(5F, this))
        row.addView(createColumn(value, false, onClickListener))
        return row
    }

    private fun checkUncaughtException() {
        Log.i(LOG_ID, "Check uncaught exception exists: ${sharedPref.getBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, false)} - " +
                "last occured at ${DateFormat.getDateTimeInstance().format(Date(sharedPref.getLong(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_TIME, 0)))}")
        if(sharedPref.getBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, false)) {
            val excMsg = sharedPref.getString(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_MESSAGE, "") ?: ""
            val time = sharedPref.getLong(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_TIME, 0)
            Log.e(LOG_ID, "Uncaught exception detected at ${DateFormat.getDateTimeInstance().format(Date(time))}: $excMsg")
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, false)
                apply()
            }

            if(time > 0 && (System.currentTimeMillis()- ReceiveData.time) < (60*60 * 1000)) {
                if (excMsg.contains("BadForegroundServiceNotificationException") || excMsg.contains(
                        "RemoteServiceException"
                    )
                ) {
                    Dialogs.showOkDialog(this, CR.string.app_crash_title, CR.string.app_crash_bad_notification, null)
                } else {
                    Dialogs.showOkDialog(this, CR.string.app_crash_title, CR.string.app_crash_message, null)
                }
            }
        }
    }
}