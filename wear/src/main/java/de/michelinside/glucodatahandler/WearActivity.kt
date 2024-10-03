package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.app.AlarmManager
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
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmState
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.settings.AlarmsActivity
import de.michelinside.glucodatahandler.settings.SettingsActivity
import de.michelinside.glucodatahandler.settings.SourcesActivity
import java.text.DateFormat
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
    private lateinit var tableConnections: TableLayout
    private lateinit var tableAlarms: TableLayout
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
            txtHighContrastEnabled = findViewById(R.id.txtHighContrastEnabled)
            txtScheduleExactAlarm = findViewById(R.id.txtScheduleExactAlarm)

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
            if(requestPermission())
                GlucoDataServiceWear.start(this)
            PackageUtils.updatePackages(this)
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
                NotifySource.CAR_CONNECTION,
                NotifySource.TIME_VALUE,
                NotifySource.SOURCE_STATE_CHANGE,
                NotifySource.ALARM_STATE_CHANGED))
            checkExactAlarmPermission()
            checkHighContrast()
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

    @SuppressLint("SetTextI18n")
    private fun update() {
        try {
            txtBgValue.text = ReceiveData.getGlucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon())

            timeText.text = "🕒 ${ReceiveData.getElapsedRelativeTimeAsString(this)}"
            deltaText.text = "Δ ${ReceiveData.getDeltaAsString()}"
            iobText.text = "💉 ${ReceiveData.getIobAsString()}"
            cobText.text = "🍔 ${ReceiveData.getCobAsString()}"
            iobText.visibility = if (ReceiveData.isIobCobObsolete()) View.GONE else View.VISIBLE
            cobText.visibility = iobText.visibility

            txtValueInfo.visibility = if(ReceiveData.time>0) View.GONE else View.VISIBLE

            updateAlarmsTable()
            updateConnectionsTable()
            updateDetailsTable()
            updateAlarmIcon()

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    private fun toggleAlarm() {
        try {
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
                    AlarmState.TEMP_INACTIVE -> {
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED, false)
                            apply()
                        }
                    }
                }
            } else {
                Log.w(LOG_ID, "Alarm channel inactive!")
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                    apply()
                }
            }
            //updateAlarmIcon()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "toggleAlarm exception: " + exc + "\n" + exc.stackTraceToString() )
        }
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
            alarmIcon.setImageIcon(Icon.createWithResource(this, state.icon))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received from: " + dataSource.toString())
        update()
    }

    private fun updateConnectionsTable() {
        tableConnections.removeViews(1, maxOf(0, tableConnections.childCount - 1))
        if (SourceStateData.lastState != SourceState.NONE)
            tableConnections.addView(createRow(SourceStateData.lastSource.resId,
                SourceStateData.getStateMessage(this)))

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
                if(WearPhoneConnection.getNodeBatterLevels().size == 1 ) {
                    val level = WearPhoneConnection.getNodeBatterLevels().values.first()
                    tableConnections.addView(createRow(CR.string.source_phone, if (level > 0) "$level%" else "?%", onCheckClickListener))
                } else {
                    WearPhoneConnection.getNodeBatterLevels().forEach { (name, level) ->
                        tableConnections.addView(createRow(name, if (level > 0) "$level%" else "?%", onCheckClickListener))
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
        if (AlarmHandler.isSnoozeActive)
            tableAlarms.addView(createRow(CR.string.snooze, AlarmHandler.snoozeTimestamp))
        checkTableVisibility(tableAlarms)
    }

    private fun updateDetailsTable() {
        tableDetails.removeViews(1, maxOf(0, tableDetails.childCount - 1))
        if(ReceiveData.time > 0) {
            if (!ReceiveData.isObsoleteLong() && sharedPref.getBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, false)) {
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
}
