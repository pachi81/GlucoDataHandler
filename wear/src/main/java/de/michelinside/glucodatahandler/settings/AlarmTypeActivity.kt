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
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
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
import de.michelinside.glucodatahandler.common.notification.VibratePattern
import de.michelinside.glucodatahandler.common.notification.Vibrator
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils

class AlarmTypeActivity : AppCompatActivity(), NotifierInterface {
    private val LOG_ID = "GDH.Main.AlarmType"

    companion object {
        private var ringtonePickerSupportsSilent = true
        private var ringtonePickerSupportsDefault = true
    }

    private lateinit var sharedPref: SharedPreferences
    private lateinit var txtAlarmTitle: TextView
    private lateinit var switchUseCustomSound: SwitchCompat
    private lateinit var btnSelectSound: Button
    private lateinit var txtCustomSound: TextView
    private lateinit var seekBarSoundLevel: AppCompatSeekBar
    private lateinit var txtSoundLevel: TextView
    private lateinit var txtVibrationPattern: TextView
    private lateinit var btnSelectVibration: Button
    private lateinit var seekBarVibrationAmplitude: AppCompatSeekBar
    private lateinit var txtVibrationAmplitude: TextView
    private lateinit var btnTestAlarm: Button
    private var alarmType = AlarmType.NONE
    private var alarmTitle = ""

    private var ringtoneSelecter: ActivityResultLauncher<Intent>? = null
    private val useCustomSoundPref: String get() {
        return alarmType.setting!!.getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_USE_CUSTOM_SOUND)
    }
    private val customSoundPref: String get() {
        return alarmType.setting!!.getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_CUSTOM_SOUND)
    }
    private val soundLevelPref: String get() {
        return alarmType.setting!!.getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_LEVEL)
    }
    private val vibrateAmplitudePref: String get() {
        return alarmType.setting!!.getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_VIBRATE_AMPLITUDE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_alarm_type)

            if(intent.extras == null || !intent.extras!!.containsKey("type") || !intent.extras!!.containsKey("title")) {
                Log.e(LOG_ID, "Missing extras: ${Utils.dumpBundle(intent.extras)}")
                finish()
            }

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            alarmType = AlarmType.fromIndex(intent.extras!!.getInt("type"))
            if (alarmType.setting == null) {
                Log.e(LOG_ID, "Unsupported alarm type for creating activity: $alarmType!")
                finish()
            }

            alarmTitle = intent.extras!!.getString("title")!!
            Log.d(LOG_ID, "Create for $alarmTitle for: $alarmType")

            txtAlarmTitle = findViewById(R.id.txtAlarmTitle)
            switchUseCustomSound = findViewById(R.id.switchUseCustomSound)
            btnSelectSound = findViewById(R.id.btnSelectSound)
            txtCustomSound = findViewById(R.id.txtCustomSound)
            btnTestAlarm = findViewById(R.id.btnTestAlarm)
            seekBarSoundLevel = findViewById(R.id.seekBarSoundLevel)
            txtSoundLevel = findViewById(R.id.txtSoundLevel)
            txtVibrationPattern = findViewById(R.id.txtVibrationPattern)
            btnSelectVibration = findViewById(R.id.btnSelectVibration)
            seekBarVibrationAmplitude = findViewById(R.id.seekBarVibrationAmplitude)
            txtVibrationAmplitude = findViewById(R.id.txtVibrationAmplitude)

            txtAlarmTitle.text = alarmTitle


            seekBarSoundLevel.max = AlarmNotificationWear.getMaxSoundLevel()
            seekBarSoundLevel.progress = sharedPref.getInt(soundLevelPref, -1)
            txtSoundLevel.text = seekBarSoundLevel.progress.toString()
            seekBarSoundLevel.setOnSeekBarChangeListener(SeekBarChangeListener(soundLevelPref, txtSoundLevel, true))

            switchUseCustomSound.isChecked = sharedPref.getBoolean(useCustomSoundPref, false)
            switchUseCustomSound.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Use custom sound changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(useCustomSoundPref, isChecked)
                        apply()
                    }
                    btnSelectSound.isEnabled = isChecked
                    if(isChecked && !ringtonePickerSupportsSilent)
                        setRingtoneResult(null)
                    else
                        updateSoundText()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            btnSelectSound.isEnabled = switchUseCustomSound.isChecked
            updateSoundText()

            btnTestAlarm.setOnClickListener {
                try {
                    Log.d(LOG_ID, "Test alarm button clicked!")
                    btnTestAlarm.isEnabled = false
                    AlarmNotificationWear.triggerTest(alarmType, this)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Test alarm exception: " + exc.message.toString() )
                }
            }

            ringtoneSelecter = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                try {
                    handleRingtoneResult(result.resultCode, result.data)
                } catch( exc: Exception ) {
                    Log.e(LOG_ID, "Error setting ringtone: " + exc.message)
                }
            }

            btnSelectSound.setOnClickListener {
                try {
                    Log.d(LOG_ID, "Select custom sound clicked")
                    showRingtonePicker()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Select custom sound exception: " + exc.message.toString())
                }
            }

            // vibration
            btnSelectVibration.setOnClickListener {
                try {
                    Log.v(LOG_ID, "${btnSelectVibration.text} button clicked!")
                    val intent = Intent(this, VibrationPatternActivity::class.java)
                    intent.putExtra("type", alarmType.ordinal)
                    startActivity(intent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Select vibration exception: " + exc.message.toString() )
                }
            }

            val vibatrionAmbituteLayout = findViewById<RelativeLayout>(R.id.vibrationAmplitudeHead)
            vibatrionAmbituteLayout.visibility = if(Vibrator.hasAmplitudeControl()) View.VISIBLE else View.GONE
            seekBarVibrationAmplitude.visibility = vibatrionAmbituteLayout.visibility

            if (seekBarVibrationAmplitude.visibility == View.VISIBLE) {
                seekBarVibrationAmplitude.progress = sharedPref.getInt(vibrateAmplitudePref, 15)
                txtVibrationAmplitude.text = seekBarVibrationAmplitude.progress.toString()
                seekBarVibrationAmplitude.setOnSeekBarChangeListener(SeekBarChangeListener(vibrateAmplitudePref, txtVibrationAmplitude, false))
            }

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            Log.v(LOG_ID, "onPause called")
            super.onPause()
            Vibrator.cancel()
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


    private fun handleRingtoneResult(resultCode: Int, data: Intent?) {
        try {
            Log.i(LOG_ID, "handleRingtoneResult for $alarmType: $resultCode, ${Utils.dumpBundle(data?.extras)}")
            if(resultCode == Activity.RESULT_OK && data != null && data.extras != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRingtoneResult(data.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java))
                } else {
                    @Suppress("DEPRECATION")
                    setRingtoneResult(data.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI))
                }
            } else if(resultCode == Activity.RESULT_CANCELED) {
                Log.d(LOG_ID, "Ringtone select canceled - supportsSilent=$ringtonePickerSupportsSilent, supportsDefault=$ringtonePickerSupportsDefault")
                if(ringtonePickerSupportsDefault) {
                    ringtonePickerSupportsDefault = false
                    showRingtonePicker()
                    return
                }
                if(ringtonePickerSupportsSilent) {
                    ringtonePickerSupportsSilent = false
                    showRingtonePicker()
                    return
                }
                Log.e(LOG_ID, "Ringtone picker could not be shown!")
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, "Error handleRingtoneResult: " + exc.message)
        }
    }

    private fun showRingtonePicker() {
        try {
            if(ringtoneSelecter != null) {
                val curUri = Uri.parse(sharedPref.getString(customSoundPref, ""))
                Log.i(LOG_ID, "showRingtonePicker called with uri $curUri - supportsSilent=$ringtonePickerSupportsSilent, supportsDefault=$ringtonePickerSupportsDefault")
                val ringtoneIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                ringtoneIntent.putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALL
                )
                ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, ringtonePickerSupportsSilent)
                ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, ringtonePickerSupportsDefault)
                ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, curUri)
                ringtoneSelecter!!.launch(Intent.createChooser(ringtoneIntent, null))
            } else {
                Log.e(LOG_ID, "Ringtone selecter is null!")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Select custom sound exception: " + exc.message.toString())
        }
    }

    private fun setRingtoneResult(newUri: Uri?) {
        Log.i(LOG_ID, "Set custom ringtone: $newUri")
        with (sharedPref.edit()) {
            putString(customSoundPref, newUri?.toString())
            apply()
        }
        updateSoundText()
    }

    private fun update() {
        btnTestAlarm.isEnabled = true
        seekBarVibrationAmplitude.isEnabled = alarmType.setting!!.vibratePattern != null
        txtVibrationPattern.text = resources.getString(VibratePattern.getByKey(alarmType.setting!!.vibratePatternKey).resId)
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



    inner class SeekBarChangeListener(val preference: String, val txtLevel: TextView, val isSoundLevel: Boolean) : SeekBar.OnSeekBarChangeListener {
        private val LOG_ID = "GDH.Main.AlarmTypeSeekBar"
        private var curProgress = -1
        private var lastSoundLevel = -1
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            try {
                Log.v(LOG_ID, "onProgressChanged called for $preference with progress=$progress - fromUser=$fromUser")
                if(fromUser) {
                    curProgress = progress
                    txtLevel.text = curProgress.toString()
                    with (sharedPref.edit()) {
                        putInt(preference, curProgress)
                        apply()
                    }
                    if(isSoundLevel)
                        setSoundLevel(curProgress)
                    else if (alarmType.setting!!.vibratePattern != null)
                        Vibrator.vibrate(alarmType.setting!!.vibratePattern!!, -1, curProgress*17, AlarmNotificationWear.useAlarmStream)
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            try {
                Log.v(LOG_ID, "onStartTrackingTouch called")
                if(isSoundLevel) {
                    lastSoundLevel = AlarmNotificationWear.getCurrentSoundLevel()
                    if(seekBar!=null)
                        setSoundLevel(seekBar.progress)
                    AlarmNotificationWear.startSound(alarmType, baseContext, false, forTest = true)
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            try {
                Log.v(LOG_ID, "onStopTrackingTouch called with current progress: $curProgress")
                if(isSoundLevel) {
                    AlarmNotificationWear.stopVibrationAndSound()
                    setSoundLevel(lastSoundLevel)
                }
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
