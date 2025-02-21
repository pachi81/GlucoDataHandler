package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.databinding.ActivityWearBinding
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.setPadding
import de.michelinside.glucodatahandler.common.chart.ChartBitmapView
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmState
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.settings.AlarmsActivity
import de.michelinside.glucodatahandler.settings.SettingsActivity
import de.michelinside.glucodatahandler.settings.SourcesActivity
import java.text.DateFormat
import java.time.Duration
import java.util.Date
import kotlin.time.Duration.Companion.days

class WearActivity : AppCompatActivity(), NotifierInterface {

    private val LOG_ID = "GDH.Main"
    private lateinit var sharedPref: SharedPreferences
    private lateinit var binding: ActivityWearBinding
    private lateinit var alarmIcon: ImageView
    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var timeText: TextView
    private lateinit var deltaText: TextView
    private lateinit var iobText: TextView
    private lateinit var cobText: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtValueInfo: TextView
    private lateinit var tableDetails: TableLayout
    private lateinit var tableDelta: TableLayout
    private lateinit var tableConnections: TableLayout
    private lateinit var tableAlarms: TableLayout
    private lateinit var tableNotes: TableLayout
    private lateinit var btnSettings: Button
    private lateinit var btnSources: Button
    private lateinit var btnAlarms: Button
    private lateinit var chartImage: ImageView
    private var doNotUpdate = false
    private var requestNotificationPermission = false
    private lateinit var chartBitmap: ChartBitmapView

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            installSplashScreen()
            super.onCreate(savedInstanceState)

            //setTheme(android.R.style.Theme_DeviceDefault)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            binding = ActivityWearBinding.inflate(layoutInflater)
            setContentView(binding.root)

            alarmIcon = findViewById(R.id.alarmIcon)
            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            timeText = findViewById(R.id.timeText)
            deltaText = findViewById(R.id.deltaText)
            iobText = findViewById(R.id.iobText)
            cobText = findViewById(R.id.cobText)
            txtValueInfo = findViewById(R.id.txtValueInfo)
            tableConnections = findViewById(R.id.tableConnections)
            tableAlarms = findViewById(R.id.tableAlarms)
            tableDetails = findViewById(R.id.tableDetails)
            tableDelta = findViewById(R.id.tableDelta)
            tableNotes = findViewById(R.id.tableNotes)
            chartImage = findViewById(R.id.graphImage)

            txtVersion = findViewById(R.id.txtVersion)
            txtVersion.text = BuildConfig.VERSION_NAME

            ReceiveData.initData(this)

            alarmIcon.setOnClickListener {
                toggleAlarm()
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
            chartBitmap = ChartBitmapView(chartImage, this, Constants.SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION)
            chartImage.setOnClickListener {
                Log.v(LOG_ID, "Chart Image clicked!")
                val intent = Intent(this, GraphActivity::class.java)
                startActivity(intent)
            }

            if(requestPermission())
                GlucoDataServiceWear.start(this)
            PackageUtils.updatePackages(this)
            checkUncaughtException()
            TextToSpeechUtils.initTextToSpeech(this)
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
            GlucoDataService.checkServices(this)
            ScreenEventReceiver.switchOn(this) // on resume can only be called, if display is on, so update screen event
            doNotUpdate = false
            update()
            InternalNotifier.addNotifier(this, this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.DISPLAY_STATE_CHANGED,
                NotifySource.IOB_COB_CHANGE,
                NotifySource.IOB_COB_TIME,
                NotifySource.MESSAGECLIENT,
                NotifySource.CAPILITY_INFO,
                NotifySource.NODE_BATTERY_LEVEL,
                NotifySource.SETTINGS,
                NotifySource.CAR_CONNECTION,
                NotifySource.TIME_VALUE,
                NotifySource.SOURCE_STATE_CHANGE,
                NotifySource.ALARM_STATE_CHANGED))
            if (requestNotificationPermission && Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Notification permission granted")
                requestNotificationPermission = false
                GlucoDataServiceWear.start(this)
            }
            GlucoDataService.checkForConnectedNodes(true)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onDestroy() {
        Log.v(LOG_ID, "onDestroy called")
        super.onDestroy()
        chartBitmap.close()
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

    private fun requestExactAlarmPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!Utils.canScheduleExactAlarms(this)) {
                    Log.i(LOG_ID, "Request exact alarm permission...")
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "requestExactAlarmPermission exception: " + exc.message.toString() )
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
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon())
            viewIcon.contentDescription = ReceiveData.getRateAsText(this)

            timeText.text = "🕒 ${ReceiveData.getElapsedRelativeTimeAsString(this)}"
            timeText.contentDescription = ReceiveData.getElapsedRelativeTimeAsString(this, true)
            deltaText.text = "Δ ${ReceiveData.getDeltaAsString()}"
            iobText.text = "💉 ${ReceiveData.getIobAsString()}"
            iobText.contentDescription = getString(CR.string.info_label_iob) + " " + ReceiveData.getIobAsString()
            cobText.text = "🍔 ${ReceiveData.getCobAsString()}"
            iobText.contentDescription = getString(CR.string.info_label_cob) + " " + ReceiveData.getCobAsString()
            iobText.visibility = if (ReceiveData.isIobCobObsolete()) View.GONE else View.VISIBLE
            cobText.visibility = iobText.visibility

            txtValueInfo.visibility = if(ReceiveData.time>0) View.GONE else View.VISIBLE

            updateNotesTable()
            updateAlarmsTable()
            updateConnectionsTable()
            updateDeltaTable()
            updateDetailsTable()
            updateAlarmIcon()

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    private fun toggleAlarm() {
        try {
            doNotUpdate = true
            val state = AlarmNotificationWear.getAlarmState(this)
            if(AlarmNotificationWear.channelActive(this)) {
                Log.d(LOG_ID, "toggleAlarm called for state $state")
                when (state) {
                    AlarmState.SNOOZE -> AlarmHandler.setSnooze(0)  // disable snooze
                    AlarmState.DISABLED -> {
                        val lastState = sharedPref.getInt(Constants.SHARED_PREF_WEAR_LAST_ALARM_STATE, 1)
                        Log.d(LOG_ID, "Last alarm state $lastState")
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, (lastState and 1) == 1)
                            putBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, (lastState and 2) == 2)
                            apply()
                        }
                    }
                    AlarmState.INACTIVE,
                    AlarmState.ACTIVE -> {
                        var lastState = 0
                        if(sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false))
                            lastState = 1
                        if(sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, false))
                            lastState = lastState or 2
                        Log.d(LOG_ID, "Saving last alarm state $lastState")
                        with(sharedPref.edit()) {
                            putInt(Constants.SHARED_PREF_WEAR_LAST_ALARM_STATE, lastState)
                            putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                            putBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, false)
                            apply()
                        }
                    }
                    AlarmState.TEMP_DISABLED -> {
                        AlarmHandler.disableInactiveTime()
                    }
                }
            } else {
                Log.w(LOG_ID, "Alarm channel inactive!")
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                    apply()
                }
            }
            updateAlarmIcon()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "toggleAlarm exception: " + exc + "\n" + exc.stackTraceToString() )
        }
        doNotUpdate = false
    }

    private fun updateAlarmIcon() {
        try {
            if(!AlarmNotificationWear.channelActive(this)) {
                Log.w(LOG_ID, "Alarm channel inactive!")
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                    apply()
                }
            }
            val state = AlarmNotificationWear.getAlarmState(this)
            Log.v(LOG_ID, "updateAlarmIcon called for state $state")
            alarmIcon.isEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ENABLE_ALARM_ICON_TOGGLE, true)
            alarmIcon.setImageIcon(Icon.createWithResource(this, state.icon))
            alarmIcon.contentDescription = resources.getString(CR.string.alarm_toggle_state,
                when(state) {
                    AlarmState.SNOOZE -> resources.getString(state.descr, AlarmHandler.snoozeShortTimestamp)
                    AlarmState.TEMP_DISABLED -> resources.getString(state.descr, AlarmHandler.inactiveEndTimestamp)
                    else -> resources.getString(state.descr)
                })
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received from: " + dataSource.toString())
        update()
    }


    private fun updateNotesTable() {
        tableNotes.removeViews(1, maxOf(0, tableNotes.childCount - 1))
        if (!Channels.notificationChannelActive(this, ChannelType.WEAR_FOREGROUND)) {
            val onClickListener = View.OnClickListener {
                try {
                    if (!Channels.notificationChannelActive(this, ChannelType.WEAR_FOREGROUND)) {
                        requestNotificationPermission = true
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName))
                    } else {
                        updateNotesTable()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "updateNotesTable exception: " + exc.message.toString() )
                    if(requestPermission()) {
                        GlucoDataServiceWear.start(this)
                        updateNotesTable()
                    }
                }
            }
            tableNotes.addView(createRow(CR.string.activity_main_notification_permission, onClickListener))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Utils.canScheduleExactAlarms(this)) {
            Log.w(LOG_ID, "Schedule exact alarm is not active!!!")
            val onClickListener = View.OnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Schedule exact alarm exception: " + exc.message.toString() )
                }
            }
            tableNotes.addView(createRow(CR.string.activity_main_schedule_exact_alarm, onClickListener))
        }
        if (Utils.isHighContrastTextEnabled(this)) {
            Log.w(LOG_ID, "High contrast is active")
            val onClickListener = View.OnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "High contrast exception: " + exc.message.toString() )
                }
            }
            tableNotes.addView(createRow(CR.string.activity_main_high_contrast_enabled, onClickListener))
        }
        checkTableVisibility(tableNotes)
    }

    private fun updateConnectionsTable() {
        tableConnections.removeViews(1, maxOf(0, tableConnections.childCount - 1))
        if (SourceStateData.lastState != SourceState.NONE) {
            tableConnections.addView(createRow(SourceStateData.lastSource.resId, SourceStateData.getStateMessage(this)))
            if(SourceStateData.lastErrorInfo.isNotEmpty()) {
                // add error specific information in an own row
                tableConnections.addView(createRow(SourceStateData.lastErrorInfo))
            }
            tableConnections.addView(createRow(CR.string.request_timestamp, Utils.getUiTimeStamp(SourceStateData.lastStateTime)))
        }

        if (WearPhoneConnection.nodesConnected) {
            if (WearPhoneConnection.connectionError) {
                val onResetClickListener = View.OnClickListener {
                    GlucoDataService.resetWearPhoneConnection()
                }
                tableConnections.addView(createRow(CR.string.source_phone, resources.getString(CR.string.detail_reset_connection), onResetClickListener))
            } else {
                val onCheckClickListener = View.OnClickListener {
                    GlucoDataService.checkForConnectedNodes(false)
                }
                val states = WearPhoneConnection.getNodeConnectionStates(this)
                if(states.size == 1 ) {
                    val state = states.values.first()
                    tableConnections.addView(createRow(CR.string.source_phone, state, onCheckClickListener))
                } else {
                    states.forEach { (name, state) ->
                        tableConnections.addView(createRow(name, state, onCheckClickListener))
                    }
                }
            }
        }
        if (AlarmNotificationWear.isAaConnected) {
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
            if(!ReceiveData.delta15Min.isNaN())
                tableDelta.addView(createRow(CR.string.delta_per_15_minute, GlucoDataUtils.deltaToString(ReceiveData.delta15Min, true)))
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

    private fun createColumn(text: String, end: Boolean, onClickListener: View.OnClickListener? = null) : TextView {
        val textView = TextView(this)
        textView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1F)
        textView.text = text
        textView.textSize = 14F
        if (end)
            textView.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        else
            textView.gravity = Gravity.CENTER_VERTICAL
        if(onClickListener != null)
            textView.setOnClickListener(onClickListener)
        return textView
    }

    private fun createRow(keyResId: Int, value: String, onClickListener: View.OnClickListener? = null) : TableRow {
        return createRow(resources.getString(keyResId), value, onClickListener)
    }

    private fun createRow(key: String, value: String, onClickListener: View.OnClickListener? = null) : TableRow {
        val row = TableRow(this)
        row.weightSum = 2f
        //row.setBackgroundColor(resources.getColor(R.color.table_row))
        row.setPadding(Utils.dpToPx(5F, this))
        row.addView(createColumn(key, false, onClickListener))
        row.addView(createColumn(value, true, onClickListener))
        return row
    }

    private fun createRow(valueResId: Int, onClickListener: View.OnClickListener? = null) : TableRow {
        return createRow(resources.getString(valueResId), onClickListener)
    }

    private fun createRow(value: String, onClickListener: View.OnClickListener? = null) : TableRow {
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
        }
    }
}
