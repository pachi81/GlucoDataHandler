package de.michelinside.glucodatahandler.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.AlarmNotificationWear
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils

class AlarmTypeActivity : AppCompatActivity(), NotifierInterface {
    private val LOG_ID = "GDH.Main.AlarmType"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var txtAlarmTitle: TextView
    private lateinit var switchUseCustomSound: SwitchCompat
    private lateinit var btnSelectSound: Button
    private lateinit var txtCustomSound: TextView
    private lateinit var btnTestAlarm: Button
    private lateinit var seekBarSoundLevel: AppCompatSeekBar
    private lateinit var txtSoundLevel: TextView
    private var alarmType = AlarmType.NONE
    private var alarmPrefix = ""
    private var alarmTitle = ""

    private var ringtoneSelecter: ActivityResultLauncher<Intent>? = null
    private val useCustomSoundPref: String get() {
        return alarmPrefix + "use_custom_sound"
    }
    private val customSoundPref: String get() {
        return alarmPrefix + "custom_sound"
    }
    private val soundLevelPref: String get() {
        return alarmPrefix + "sound_level"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_alarm_type)

            if(intent.extras == null || !intent.extras!!.containsKey("type") || !intent.extras!!.containsKey("prefix") || !intent.extras!!.containsKey("title")) {
                Log.e(LOG_ID, "Missing extras: ${Utils.dumpBundle(intent.extras)}")
                finish()
            }

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            alarmType = AlarmType.fromIndex(intent.extras!!.getInt("type"))
            alarmPrefix = intent.extras!!.getString("prefix")!!
            alarmTitle = intent.extras!!.getString("title")!!
            Log.d(LOG_ID, "Create for $alarmTitle with prefix: $alarmPrefix")

            txtAlarmTitle = findViewById(R.id.txtAlarmTitle)
            switchUseCustomSound = findViewById(R.id.switchUseCustomSound)
            btnSelectSound = findViewById(R.id.btnSelectSound)
            txtCustomSound = findViewById(R.id.txtCustomSound)
            btnTestAlarm = findViewById(R.id.btnTestAlarm)
            seekBarSoundLevel = findViewById(R.id.seekBarSoundLevel)
            txtSoundLevel = findViewById(R.id.txtSoundLevel)

            txtAlarmTitle.text = alarmTitle


            seekBarSoundLevel.max = AlarmNotificationWear.getMaxSoundLevel()
            seekBarSoundLevel.progress = sharedPref.getInt(soundLevelPref, -1)
            txtSoundLevel.text = seekBarSoundLevel.progress.toString()
            seekBarSoundLevel.setOnSeekBarChangeListener(SeekBarChangeListener(soundLevelPref, txtSoundLevel))

            switchUseCustomSound.isChecked = sharedPref.getBoolean(useCustomSoundPref, false)
            switchUseCustomSound.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Use custom sound changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(useCustomSoundPref, isChecked)
                        apply()
                    }
                    btnSelectSound.isEnabled = isChecked
                    updateSoundText()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            btnSelectSound.isEnabled = switchUseCustomSound.isChecked
            updateSoundText()

            btnTestAlarm.setOnClickListener {
                Log.d(LOG_ID, "Test alarm button clicked!")
                btnTestAlarm.isEnabled = false
                AlarmNotificationWear.triggerTest(alarmType, this)
            }

            setRingtoneSelect(Uri.parse(sharedPref.getString(customSoundPref, "")))

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            Log.v(LOG_ID, "onPause called")
            super.onPause()
            InternalNotifier.remNotifier(this, this)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onResume() {
        try {
            Log.v(LOG_ID, "onResume called")
            super.onResume()
            InternalNotifier.addNotifier(this, this, mutableSetOf(NotifySource.NOTIFICATION_STOPPED))
            update()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onDestroy() {
        try {
            Log.v(LOG_ID, "onDestroy called")
            super.onDestroy()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    private fun setRingtoneSelect(curUri: Uri?) {
        Log.v(LOG_ID, "setRingtoneSelect called with uri $curUri" )
        if(ringtoneSelecter == null) {
            ringtoneSelecter = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                Log.v(LOG_ID, "$alarmType result ${result.resultCode}: ${result.data}")
                if (result.resultCode == Activity.RESULT_OK) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setRingtoneResult(result.data!!.getParcelableExtra(
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java))
                    } else {
                        @Suppress("DEPRECATION")
                        setRingtoneResult(result.data!!.getParcelableExtra(
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI))
                    }
                }
            }
        }
        btnSelectSound.setOnClickListener {
            Log.v(LOG_ID, "Select custom sound clicked")
            val ringtoneIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            ringtoneIntent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALL
            )
            ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, curUri)
            ringtoneSelecter!!.launch(ringtoneIntent)
        }
    }

    private fun setRingtoneResult(newUri: Uri?) {
        Log.i(LOG_ID, "Set custom ringtone: $newUri")
        with (sharedPref.edit()) {
            putString(customSoundPref, newUri?.toString())
            apply()
        }
        setRingtoneSelect(newUri)
        updateSoundText()
    }

    private fun update() {
        btnTestAlarm.isEnabled = true
    }

    private fun updateSoundText() {
        if(btnSelectSound.isEnabled) {
            val uri = Uri.parse(sharedPref.getString(customSoundPref, ""))
            if (uri != null && uri.toString().isNotEmpty()) {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                val title = ringtone.getTitle(this)
                if (title.isNullOrEmpty()) {
                    txtCustomSound.text = resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_sound_summary)
                    seekBarSoundLevel.isEnabled = false
                } else {
                    Log.d(LOG_ID, "Ringtone '$title' for uri $uri")
                    txtCustomSound.text = title
                    seekBarSoundLevel.isEnabled = true
                }
            } else {
                txtCustomSound.text = resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_sound_summary)
                seekBarSoundLevel.isEnabled = false
            }
        } else {
            txtCustomSound.text = resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_app_sound)
            seekBarSoundLevel.isEnabled = true
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for $dataSource")
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }



    inner class SeekBarChangeListener(val preference: String, val txtLevel: TextView) : SeekBar.OnSeekBarChangeListener {
        private val LOG_ID = "GDH.Main.AlarmTypeSeekBar"
        private var curProgress = -1
        private var lastSoundLevel = -1
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            try {
                Log.v(LOG_ID, "onProgressChanged called for $preference with progress=$progress - fromUser=$fromUser")
                if(fromUser) {
                    curProgress = progress
                    txtLevel.text = curProgress.toString()
                    setSoundLevel(curProgress)
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            try {
                Log.v(LOG_ID, "onStartTrackingTouch called")
                lastSoundLevel = AlarmNotificationWear.getCurrentSoundLevel()
                if(seekBar!=null)
                    setSoundLevel(seekBar.progress)
                AlarmNotificationWear.startSound(alarmType, baseContext, false, forTest = true)
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            try {
                Log.v(LOG_ID, "onStopTrackingTouch called with current progress: $curProgress")
                AlarmNotificationWear.stopVibrationAndSound()
                setSoundLevel(lastSoundLevel)
                with (sharedPref.edit()) {
                    putInt(preference, curProgress)
                    apply()
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }

        private fun setSoundLevel(level: Int) {
            Log.v(LOG_ID, "setSoundLevel: $level")
            if(level >= 0) {
                AlarmNotificationWear.setSoundLevel(level)
            } else {
                AlarmNotificationWear.setSoundLevel(lastSoundLevel)
            }
        }

    }

}
