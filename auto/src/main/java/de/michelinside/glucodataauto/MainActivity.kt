package de.michelinside.glucodataauto

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
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import de.michelinside.glucodataauto.preferences.SettingsActivity
import de.michelinside.glucodataauto.preferences.SettingsFragmentClass
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
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
    private lateinit var txtScheduleExactAlarm: TextView
    private lateinit var txtNotificationPermission: TextView
    private lateinit var btnSources: Button
    private lateinit var txtNoData: TextView
    private lateinit var sharedPref: SharedPreferences
    private var menuOpen = false
    private var notificationIcon: MenuItem? = null
    private val LOG_ID = "GDH.AA.Main"
    private var requestNotificationPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            Log.v(LOG_ID, "onCreate called")

            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            timeText = findViewById(R.id.timeText)
            deltaText = findViewById(R.id.deltaText)
            iobText = findViewById(R.id.iobText)
            cobText = findViewById(R.id.cobText)
            txtLastValue = findViewById(R.id.txtLastValue)
            txtBatteryOptimization = findViewById(R.id.txtBatteryOptimization)
            txtScheduleExactAlarm = findViewById(R.id.txtScheduleExactAlarm)
            txtNotificationPermission = findViewById(R.id.txtNotificationPermission)
            btnSources = findViewById(R.id.btnSources)
            tableConnections = findViewById(R.id.tableConnections)
            tableAlarms = findViewById(R.id.tableAlarms)
            tableDetails = findViewById(R.id.tableDetails)
            txtNoData = findViewById(R.id.txtNoData)

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
            GlucoDataServiceAuto.init(this)
            requestPermission()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            InternalNotifier.remNotifier(this, this)
            if(!menuOpen)
                GlucoDataServiceAuto.stopDataSync(this)
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
            InternalNotifier.addNotifier( this, this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.IOB_COB_CHANGE,
                NotifySource.IOB_COB_TIME,
                NotifySource.MESSAGECLIENT,
                NotifySource.CAPILITY_INFO,
                NotifySource.NODE_BATTERY_LEVEL,
                NotifySource.SETTINGS,
                NotifySource.CAR_CONNECTION,
                NotifySource.OBSOLETE_VALUE,
                NotifySource.SOURCE_STATE_CHANGE))
            checkExactAlarmPermission()
            checkBatteryOptimization()

            if (requestNotificationPermission && Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Notification permission granted")
                requestNotificationPermission = false
                txtNotificationPermission.visibility = View.GONE
            }
            if(!menuOpen)
                GlucoDataServiceAuto.startDataSync(this)
            menuOpen = false
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Request notification permission...")
                requestNotificationPermission = true
                this.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 3)
                return false
            }
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.i(LOG_ID, "Request exact alarm permission...")
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            Log.i(LOG_ID, "Request exact alarm permission...")
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder
                .setTitle(CR.string.request_exact_alarm_title)
                .setMessage(CR.string.request_exact_alarm_summary)
                .setPositiveButton(CR.string.button_ok) { dialog, which ->
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
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

    private fun checkBatteryOptimization() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(LOG_ID, "Battery optimization is inactive")
                txtBatteryOptimization.visibility = View.VISIBLE
                txtBatteryOptimization.setOnClickListener {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            } else {
                txtBatteryOptimization.visibility = View.GONE
                Log.i(LOG_ID, "Battery optimization is active")
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
            notificationIcon = menu.findItem(R.id.action_notification_toggle)
            updateNotificationIcon()
            return true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreateOptionsMenu exception: " + exc.message.toString() )
        }
        return true
    }


    private fun updateNotificationIcon() {
        try {
            if(notificationIcon != null) {
                val enabled = sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false)
                notificationIcon!!.icon = ContextCompat.getDrawable(this, if(enabled) R.drawable.icon_popup_white else R.drawable.icon_off_white)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            Log.v(LOG_ID, "onOptionsItemSelected for " + item.itemId.toString())
            when(item.itemId) {
                R.id.action_settings -> {
                    menuOpen = true
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    return true
                }
                R.id.action_sources -> {
                    menuOpen = true
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.SORUCE_FRAGMENT.value)
                    startActivity(intent)
                    return true
                }
                R.id.action_alarms -> {
                    menuOpen = true
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra(SettingsActivity.FRAGMENT_EXTRA, SettingsFragmentClass.ALARM_FRAGMENT.value)
                    startActivity(intent)
                    return true
                }
                R.id.action_help -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getText(CR.string.glucodataauto_link).toString())
                    )
                    startActivity(browserIntent)
                    return true
                }
                R.id.action_support -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getText(CR.string.gda_support_link).toString())
                    )
                    startActivity(browserIntent)
                    return true
                }
                R.id.action_contact -> {
                    val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        "mailto","GlucoDataHandler@michel-inside.de", null))
                    mailIntent.putExtra(Intent.EXTRA_SUBJECT, "GlucoDataAuto v" + BuildConfig.VERSION_NAME)
                    startActivity(mailIntent)
                    return true
                }
                R.id.action_save_mobile_logs -> {
                    SaveMobileLogs()
                    return true
                }
                R.id.action_notification_toggle -> {
                    Log.v(LOG_ID, "notification toggle")
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, !sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false))
                        apply()
                    }
                    updateNotificationIcon()
                }
                else -> return super.onOptionsItemSelected(item)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onOptionsItemSelected exception: " + exc.message.toString() )
        }
        return super.onOptionsItemSelected(item)
    }

    private fun update() {
        try {
            Log.v(LOG_ID, "update values")
            txtBgValue.text = ReceiveData.getGlucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon(withShadow = true))
            timeText.text = "🕒 ${ReceiveData.getElapsedRelativeTimeAsString(this)}"
            deltaText.text = "Δ ${ReceiveData.getDeltaAsString()}"
            iobText.text = "💉 " + ReceiveData.getIobAsString()
            cobText.text = "🍔 " + ReceiveData.getCobAsString()
            iobText.visibility = if (ReceiveData.isIobCobObsolete(Constants.VALUE_OBSOLETE_LONG_SEC)) View.GONE else View.VISIBLE
            cobText.visibility = iobText.visibility

            if(ReceiveData.time == 0L) {
                txtLastValue.visibility = View.VISIBLE
                txtNoData.visibility = View.VISIBLE
                btnSources.visibility = View.VISIBLE
            } else {
                txtLastValue.visibility = View.GONE
                txtNoData.visibility = View.GONE
                btnSources.visibility = View.GONE
            }
            updateAlarmsTable()
            updateConnectionsTable()
            updateDetailsTable()

            updateNotificationIcon()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: " + exc.message.toString() )
        }
    }

    private fun updateConnectionsTable() {
        tableConnections.removeViews(1, maxOf(0, tableConnections.childCount - 1))
        if (SourceStateData.lastState != SourceState.NONE)
            tableConnections.addView(createRow(
                SourceStateData.lastSource.resId,
                SourceStateData.getStateMessage(this)))

        if (WearPhoneConnection.nodesConnected) {
            val onClickListener = View.OnClickListener {
                GlucoDataService.checkForConnectedNodes(true)
            }
            WearPhoneConnection.getNodeBatterLevels().forEach { name, level ->
                tableConnections.addView(createRow(name, if (level > 0) "$level%" else "?%", onClickListener))
            }
        }
        tableConnections.addView(createRow(CR.string.pref_cat_android_auto, if (GlucoDataServiceAuto.connected) resources.getString(CR.string.connected_label) else resources.getString(CR.string.disconnected_label)))
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
            if (ReceiveData.isMmol)
                tableDetails.addView(createRow(CR.string.info_label_raw, "${ReceiveData.rawValue} mg/dl"))
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
        textView.textSize = 18F
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

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.v(LOG_ID, "new intent received")
        update()
    }

    private fun SaveMobileLogs() {
        try {
            Log.v(LOG_ID, "Save mobile logs called")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                val currentDateandTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                    Date()
                )
                val fileName = "GDA_" + currentDateandTime + ".txt"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            startActivityForResult(intent, CREATE_FILE)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving mobile logs exception: " + exc.message.toString() )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            Log.v(LOG_ID, "onActivityResult called for requestCode: " + requestCode + " - resultCode: " + resultCode + " - data: " + Utils.dumpBundle(data?.extras))
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == CREATE_FILE) {
                    data?.data?.also { uri ->
                        Utils.saveLogs(this, uri)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving logs exception: " + exc.message.toString() )
        }
    }

    companion object {
        const val CREATE_FILE = 1
    }
}