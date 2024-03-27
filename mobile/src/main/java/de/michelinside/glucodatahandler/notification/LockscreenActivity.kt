package de.michelinside.glucodatahandler.notification

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.R as CR


class LockscreenActivity : AppCompatActivity(), NotifierInterface {
    private val LOG_ID = "GDH.AlarmLockscreenActivity"

    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var txtDelta: TextView
    private lateinit var txtAlarm: TextView
    private lateinit var btnDismiss: Button
    private lateinit var btnSnooze60: Button
    private lateinit var btnSnooze90: Button
    private lateinit var btnSnooze120: Button
    private lateinit var layoutSnooze: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "onCreate called")
            showWhenLockedAndTurnScreenOn()
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_lockscreen)
            hideSystemUI()

            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            txtDelta = findViewById(R.id.txtDelta)
            txtAlarm = findViewById(R.id.txtAlarm)
            btnDismiss = findViewById(R.id.btnDismiss)
            btnSnooze60 = findViewById(R.id.btnSnooze60)
            btnSnooze90 = findViewById(R.id.btnSnooze90)
            btnSnooze120 = findViewById(R.id.btnSnooze120)
            layoutSnooze = findViewById(R.id.layoutSnooze)

            val sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if (sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION, false))
                layoutSnooze.visibility = View.VISIBLE
            else
                layoutSnooze.visibility = View.GONE

            btnDismiss.setOnClickListener{
                AlarmNotification.stopCurrentNotification(this)
                finish()
            }
            btnSnooze60.setOnClickListener{
                AlarmHandler.setSnooze(60)
                AlarmNotification.stopCurrentNotification(this)
                finish()
            }
            btnSnooze90.setOnClickListener{
                AlarmHandler.setSnooze(90)
                AlarmNotification.stopCurrentNotification(this)
                finish()
            }
            btnSnooze120.setOnClickListener{
                AlarmHandler.setSnooze(120)
                AlarmNotification.stopCurrentNotification(this)
                finish()
            }
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES

            if (sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_DISMISS_KEYGUARD, false)) {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onResume() {
        try {
            Log.d(LOG_ID, "onResume called")
            super.onResume()
            update()
            InternalNotifier.addNotifier(this, this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onPause() {
        try {
            Log.d(LOG_ID, "onPause called")
            super.onPause()
            InternalNotifier.remNotifier(this, this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    private fun hideSystemUI() {
        // Enables sticky immersive mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
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
            txtDelta.text = "Δ ${ReceiveData.getDeltaAsString()}"
            val resId = AlarmNotification.getAlarmTextRes(ReceiveData.getAlarmType())
            if (resId != null) {
                txtAlarm.text = resources.getString(resId)
            } else {
                txtAlarm.text = resources.getString(CR.string.test_alarm)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString())
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for $dataSource")
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString())
        }
    }
}