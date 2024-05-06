package de.michelinside.glucodatahandler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
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
    private lateinit var binding: ActivityWearBinding
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

            binding = ActivityWearBinding.inflate(layoutInflater)
            setContentView(binding.root)

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

            PackageUtils.updatePackages(this)

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
                NotifySource.CAR_CONNECTION,
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
            txtBgValue.text = ReceiveData.getGlucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon())

            timeText.text = "🕒 ${ReceiveData.getElapsedTimeMinuteAsString(this)}"
            deltaText.text = "Δ ${ReceiveData.getDeltaAsString()}"
            iobText.text = "💉 " + ReceiveData.getIobAsString()
            cobText.text = "🍔 " + ReceiveData.getCobAsString()
            iobText.visibility = if (ReceiveData.isIobCobObsolete(Constants.VALUE_OBSOLETE_LONG_SEC)) View.GONE else View.VISIBLE
            cobText.visibility = iobText.visibility

            txtValueInfo.visibility = if(ReceiveData.time>0) View.GONE else View.VISIBLE

            updateAlarmsTable()
            updateConnectionsTable()
            updateDetailsTable()

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
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
            val onClickListener = View.OnClickListener {
                GlucoDataService.checkForConnectedNodes(true)
            }
            if(WearPhoneConnection.getNodeBatterLevels().size == 1 ) {
                val level = WearPhoneConnection.getNodeBatterLevels().values.first()
                tableConnections.addView(createRow(CR.string.source_phone, if (level > 0) "$level%" else "?%", onClickListener))
            } else {
                WearPhoneConnection.getNodeBatterLevels().forEach { name, level ->
                    tableConnections.addView(createRow(name, if (level > 0) "$level%" else "?%", onClickListener))
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
        if(ReceiveData.getAlarmType() != AlarmType.OK) {
            tableAlarms.addView(createRow(CR.string.info_label_alarm, resources.getString(ReceiveData.getAlarmType().resId) + (if (ReceiveData.forceAlarm) " ⚠" else "" )))
        }
        if (AlarmHandler.isSnoozeActive)
            tableAlarms.addView(createRow(CR.string.snooze, AlarmHandler.snoozeTimestamp))
        checkTableVisibility(tableAlarms)
    }

    private fun updateDetailsTable() {
        tableDetails.removeViews(1, maxOf(0, tableDetails.childCount - 1))
        if (ReceiveData.isMmol)
            tableDetails.addView(createRow(CR.string.info_label_raw, "${ReceiveData.rawValue} mg/dl"))
        if (!ReceiveData.isIobCobObsolete(1.days.inWholeSeconds.toInt()))
            tableDetails.addView(createRow(CR.string.info_label_iob_cob_timestamp, DateFormat.getTimeInstance(
                DateFormat.DEFAULT).format(Date(ReceiveData.iobCobTime))))
        if (ReceiveData.sensorID?.isNotEmpty() == true) {
            tableDetails.addView(createRow(CR.string.info_label_sensor_id, if(BuildConfig.DEBUG) "ABCDE12345" else ReceiveData.sensorID!!))
        }
        if(ReceiveData.source != DataSource.NONE)
            tableDetails.addView(createRow(CR.string.info_label_source, resources.getString(ReceiveData.source.resId)))
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
