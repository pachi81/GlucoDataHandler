package de.michelinside.glucodataauto

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.core.view.setPadding
import androidx.preference.PreferenceManager
import de.michelinside.glucodataauto.preferences.SettingsActivity
import de.michelinside.glucodataauto.preferences.SettingsFragmentClass
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.GitHubVersionChecker
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import java.time.Duration
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
    private lateinit var tableNotes: TableLayout
    private lateinit var btnSources: Button
    private lateinit var txtNoData: TextView
    private lateinit var sharedPref: SharedPreferences
    private lateinit var versionChecker: GitHubVersionChecker
    private var notificationIcon: MenuItem? = null
    private var speakIcon: MenuItem? = null
    private val LOG_ID = "GDH.AA.Main"

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
            btnSources = findViewById(R.id.btnSources)
            tableConnections = findViewById(R.id.tableConnections)
            tableAlarms = findViewById(R.id.tableAlarms)
            tableNotes = findViewById(R.id.tableNotes)
            tableDetails = findViewById(R.id.tableDetails)
            txtNoData = findViewById(R.id.txtNoData)
            versionChecker = GitHubVersionChecker("GlucoDataAuto", BuildConfig.VERSION_NAME, this)

            PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            ReceiveData.initData(this)
            TextToSpeechUtils.initTextToSpeech(this)

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
            Log.d(LOG_ID, "onPause called")
            super.onPause()
            InternalNotifier.remNotifier(this, this)
            GlucoDataServiceAuto.stopDataSync(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.message.toString() )
        }
    }

    override fun onResume() {
        try {
            Log.d(LOG_ID, "onResume called")
            super.onResume()
            checkUncaughtException()
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
                NotifySource.TIME_VALUE,
                NotifySource.SOURCE_STATE_CHANGE,
                NotifySource.NEW_VERSION_AVAILABLE,
                NotifySource.TTS_STATE_CHANGED))

            GlucoDataServiceAuto.startDataSync()
            versionChecker.checkVersion(1)
            checkNewSettings()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.message.toString() )
        }
    }

    fun requestPermission() : Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Request notification permission...")
                this.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 3)
                return false
            }
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
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
                .setNegativeButton(CR.string.button_cancel) { dialog, which ->
                    // Do something else.
                }
            val dialog: AlertDialog = builder.create()
            dialog.show()
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
            notificationIcon = menu.findItem(R.id.action_notification_toggle)
            speakIcon = menu.findItem(R.id.action_speak_toggle)
            updateNotificationIcon()
            updateSpeakIcon()
            return true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreateOptionsMenu exception: " + exc.message.toString() )
        }
        return true
    }


    private fun updateNotificationIcon() {
        try {
            if(notificationIcon != null) {
                var enabled = sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false)
                if(enabled && !Channels.notificationChannelActive(this, ChannelType.ANDROID_AUTO)) {
                    Log.i(LOG_ID, "Disable car notification as there is no permission!")
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false)
                        apply()
                    }
                    enabled = false
                }
                notificationIcon!!.icon = ContextCompat.getDrawable(this, if(enabled) R.drawable.icon_popup_white else R.drawable.icon_popup_off_white)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateAlarmIcon exception: " + exc.message.toString() )
        }
    }

    private fun updateSpeakIcon() {
        try {
            if(speakIcon != null) {
                if(!TextToSpeechUtils.isAvailable()) {
                    speakIcon!!.isVisible = false
                } else {
                    speakIcon!!.isVisible = true
                    speakIcon!!.icon = ContextCompat.getDrawable(this, if(sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false)) CR.drawable.icon_volume_normal_white else CR.drawable.icon_volume_off_white)
                }
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
                    val intent = Intent(this, SettingsActivity::class.java)
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
                R.id.action_notification_toggle -> {
                    Log.v(LOG_ID, "notification toggle")
                    if(!sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false) && !Channels.notificationChannelActive(this, ChannelType.ANDROID_AUTO))
                    {
                        val intent: Intent = if (Channels.notificationActive(this)) { // only the channel is inactive!
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, ChannelType.ANDROID_AUTO.channelId)
                        } else {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                        }
                        startActivity(intent)
                    } else {
                        with(sharedPref.edit()) {
                            putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, !sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false))
                            apply()
                        }
                        updateNotificationIcon()
                    }
                }
                R.id.action_speak_toggle -> {
                    with(sharedPref.edit()) {
                        putBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, !sharedPref.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false))
                        apply()
                    }
                    updateSpeakIcon()
                }
                else -> return super.onOptionsItemSelected(item)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onOptionsItemSelected exception: " + exc.message.toString() )
        }
        return super.onOptionsItemSelected(item)
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

            if(ReceiveData.time == 0L) {
                txtLastValue.visibility = View.VISIBLE
                txtNoData.visibility = View.VISIBLE
                btnSources.visibility = View.VISIBLE
            } else {
                txtLastValue.visibility = View.GONE
                txtNoData.visibility = View.GONE
                btnSources.visibility = View.GONE
            }
            updateNotesTable()
            updateAlarmsTable()
            updateConnectionsTable()
            updateDetailsTable()

            updateNotificationIcon()
            updateSpeakIcon()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: " + exc.message.toString() )
        }
    }

    private fun updateConnectionsTable() {
        tableConnections.removeViews(1, maxOf(0, tableConnections.childCount - 1))
        if (SourceStateData.lastState != SourceState.NONE) {
            val msg = SourceStateData.getStateMessage(this)
            tableConnections.addView(createRow(SourceStateData.lastSource.resId,msg))
            if(SourceStateData.lastState == SourceState.ERROR && SourceStateData.lastSource == DataSource.DEXCOM_SHARE) {
                if (msg.contains("500:")) { // invalid password
                    val us_account = sharedPref.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false)
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getString(if(us_account)CR.string.dexcom_account_us_url else CR.string.dexcom_account_non_us_url))
                    )
                    val onClickListener = View.OnClickListener {
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
            if(SourceStateData.lastErrorInfo.isNotEmpty()) {
                // add error specific information in an own row
                tableConnections.addView(createRow(SourceStateData.lastErrorInfo))
            }
            tableConnections.addView(createRow(CR.string.request_timestamp, Utils.getUiTimeStamp(SourceStateData.lastStateTime)))
        }
        tableConnections.addView(createRow(CR.string.pref_cat_android_auto, if (GlucoDataServiceAuto.connected) resources.getString(CR.string.connected_label) else resources.getString(CR.string.disconnected_label)))
        checkTableVisibility(tableConnections)
    }

    private fun updateNotesTable() {
        tableNotes.removeViews(1, maxOf(0, tableNotes.childCount - 1))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Utils.canScheduleExactAlarms(this)) {
            Log.w(LOG_ID, "Schedule exact alarm is not active!!!")
            val onClickListener = View.OnClickListener {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            tableNotes.addView(createRow(resources.getString(CR.string.activity_main_schedule_exact_alarm), onClickListener))
        }
        if(!sharedPref.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false) && !Channels.notificationChannelActive(this, ChannelType.ANDROID_AUTO))
        {
            val onClickListener = View.OnClickListener {
                val intent: Intent = if (Channels.notificationActive(this)) { // only the channel is inactive!
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ChannelType.ANDROID_AUTO.channelId)
                } else {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                }
                startActivity(intent)
            }
            tableNotes.addView(createRow(resources.getString(CR.string.gda_notification_permission), onClickListener))
        }
        if (versionChecker.hasNewVersion) {
            val onClickListener = View.OnClickListener {
                Dialogs.showOkCancelDialog(this, resources.getString(CR.string.new_version).format(versionChecker.version), versionChecker.content,
                    { _, _ -> val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(versionChecker.url)
                        )
                        startActivity(browserIntent)
                    }
                )
            }
            tableNotes.addView(createRow(resources.getString(CR.string.new_version).format(versionChecker.version), onClickListener))
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Log.w(LOG_ID, "Battery optimization is active")
            val onClickListener = View.OnClickListener {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            tableNotes.addView(createRow(resources.getString(CR.string.gda_battery_optimization_disabled), onClickListener))
        }
        if (!TextToSpeechUtils.isAvailable()) {
            val onClickListener = View.OnClickListener {
                TextToSpeechUtils.initTextToSpeech(this)
            }
            tableNotes.addView(createRow(resources.getString(CR.string.no_tts), onClickListener))
        }
        checkTableVisibility(tableNotes)
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
            tableAlarms.addView(createRow(CR.string.snooze_until, AlarmHandler.snoozeTimestamp))
        checkTableVisibility(tableAlarms)
    }

    private fun updateDetailsTable() {
        tableDetails.removeViews(1, maxOf(0, tableDetails.childCount - 1))
        if(!GlucoDataServiceAuto.patientName.isNullOrEmpty()) {
            tableDetails.addView(createRow(CR.string.patient_name, GlucoDataServiceAuto.patientName!!))
        }

        if(ReceiveData.time > 0) {
            if (ReceiveData.isMmol)
                tableDetails.addView(createRow(CR.string.info_label_raw, "${ReceiveData.rawValue} mg/dl"))
            tableDetails.addView(createRow(CR.string.info_label_timestamp, Utils.getUiTimeStamp(ReceiveData.time)))
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

    private fun createRow(value: String, onClickListener: View.OnClickListener? = null) : TableRow {
        val row = TableRow(this)
        row.weightSum = 1f
        //row.setBackgroundColor(resources.getColor(R.color.table_row))
        row.setPadding(Utils.dpToPx(5F, this))
        row.addView(createColumn(value, false, onClickListener))
        return row
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.v(LOG_ID, "new intent received")
        update()
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