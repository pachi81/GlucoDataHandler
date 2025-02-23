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
import com.ncorti.slidetoact.SlideToActView
import de.michelinside.glucodatahandler.PermanentNotification
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.chart.ChartBitmap
import de.michelinside.glucodatahandler.common.chart.ChartBitmapView
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.utils.Utils.isScreenReaderOn
import de.michelinside.glucodatahandler.common.R as CR


class LockscreenActivity : AppCompatActivity(), NotifierInterface {

    private lateinit var txtBgValue: TextView
    private lateinit var viewIcon: ImageView
    private lateinit var txtDelta: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtAlarm: TextView
    private lateinit var txtSnooze: TextView
    private lateinit var btnDismiss: SlideToActView
    private lateinit var btnClose: Button
    private lateinit var btnSnooze: SlideToActView
    private lateinit var btnSnooze1: Button
    private lateinit var btnSnooze2: Button
    private lateinit var btnSnooze3: Button
    private lateinit var layoutSnooze: LinearLayout
    private lateinit var layoutSnoozeButtons: LinearLayout
    private lateinit var graphImage: ImageView
    private var chartBitmap: ChartBitmapView? = null
    private var alarmType: AlarmType? = null
    private var notificationId: Int = -1
    private var createTime = 0L

    companion object {
        private val LOG_ID = "GDH.AlarmLockscreenActivity"
        private var activity: AppCompatActivity? = null
        fun close() {
            try {
                Log.d(LOG_ID, "close called for activity ${activity}")
                if(isActive()) {
                    activity?.finish()
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "close exception: " + exc.message.toString() )
            }
        }

        fun isActive(): Boolean {
            return activity != null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "onCreate called with params ${Utils.dumpBundle(this.intent.extras)}")
            activity = this
            showWhenLockedAndTurnScreenOn()
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_lockscreen)
            hideSystemUI()

            if(this.intent.extras?.containsKey(Constants.ALARM_TYPE_EXTRA) == true) {
                alarmType = AlarmType.fromIndex(this.intent.extras!!.getInt(Constants.ALARM_TYPE_EXTRA, ReceiveData.getAlarmType().ordinal))
            }

            if(this.intent.extras?.containsKey(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID) == true) {
                notificationId = this.intent.extras!!.getInt(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, -1)
            }
            createTime = System.currentTimeMillis()
            txtBgValue = findViewById(R.id.txtBgValue)
            viewIcon = findViewById(R.id.viewIcon)
            txtDelta = findViewById(R.id.txtDelta)
            txtTime = findViewById(R.id.txtTime)
            txtAlarm = findViewById(R.id.txtAlarm)
            btnDismiss = findViewById(R.id.btnDismiss)
            btnClose = findViewById(R.id.btnClose)
            btnSnooze = findViewById(R.id.btnSnooze)
            txtSnooze = findViewById(R.id.txtSnooze)
            btnSnooze1 = findViewById(R.id.btnSnooze60)
            btnSnooze2 = findViewById(R.id.btnSnooze90)
            btnSnooze3 = findViewById(R.id.btnSnooze120)
            layoutSnooze = findViewById(R.id.layoutSnooze)
            layoutSnoozeButtons = findViewById(R.id.layoutSnoozeButtons)
            graphImage = findViewById(R.id.graphImage)

            val sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            val snoozeValues = AlarmNotification.getSnoozeValues()
            if (snoozeValues.isEmpty())
                layoutSnooze.visibility = View.GONE
            else
                layoutSnooze.visibility = View.VISIBLE

            if(this.isScreenReaderOn()) {
                Log.d(LOG_ID, "Screen reader is on!")
                btnDismiss.visibility = View.GONE
                btnClose.visibility = View.VISIBLE
                btnSnooze.visibility = View.GONE
                txtSnooze.visibility = View.GONE
                layoutSnoozeButtons.visibility = View.VISIBLE
                graphImage.visibility = View.GONE
                btnClose.setOnClickListener{
                    Log.d(LOG_ID, "Stop button clicked!")
                    stop()
                }
            } else {
                btnClose.visibility = View.GONE
                btnDismiss.visibility = View.VISIBLE
                btnSnooze.visibility = View.VISIBLE
                txtSnooze.visibility = View.GONE
                layoutSnoozeButtons.visibility = View.GONE

                btnDismiss.onSlideCompleteListener =
                    object : SlideToActView.OnSlideCompleteListener {
                        override fun onSlideComplete(view: SlideToActView) {
                            Log.d(LOG_ID, "Slide to stop completed!")
                            stop()
                        }
                    }
                btnSnooze.onSlideCompleteListener =
                    object : SlideToActView.OnSlideCompleteListener {
                        override fun onSlideComplete(view: SlideToActView) {
                            Log.d(LOG_ID, "Slide to snooze completed!")
                            AlarmNotification.stopForLockscreenSnooze()
                            btnSnooze.visibility = View.GONE
                            txtSnooze.visibility = View.VISIBLE
                            layoutSnoozeButtons.visibility = View.VISIBLE
                        }
                    }
                chartBitmap = ChartBitmapView(graphImage, this, "")
            }
            btnSnooze1.visibility = View.GONE
            btnSnooze2.visibility = View.GONE
            btnSnooze3.visibility = View.GONE
            if(snoozeValues.size>0) {
                createSnoozeButton(btnSnooze1, snoozeValues.elementAt(0))
            }
            if(snoozeValues.size>1) {
                createSnoozeButton(btnSnooze2, snoozeValues.elementAt(1))
            }
            if(snoozeValues.size>2) {
                createSnoozeButton(btnSnooze3, snoozeValues.elementAt(2))
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

    private fun createSnoozeButton(button: Button, snooze: Long) {
        button.visibility = View.VISIBLE
        button.text = snooze.toString()
        button.contentDescription = resources.getString(CR.string.snooze) + " " + snooze.toString()
        button.setOnClickListener{
            AlarmHandler.setSnooze(snooze)
            stop()
        }
    }

    override fun onDestroy() {
        try {
            Log.v(LOG_ID, "onDestroy called")
            super.onDestroy()
            chartBitmap?.close()
            activity = null
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.message.toString() )
        }
    }

    override fun onResume() {
        try {
            Log.v(LOG_ID, "onResume called")
            super.onResume()
            update()
            InternalNotifier.addNotifier(this,this, mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.TIME_VALUE))
            if(!AlarmNotification.notificationActive)
                stop()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun onPause() {
        try {
            Log.v(LOG_ID, "onPause called")
            super.onPause()
            InternalNotifier.remNotifier(this, this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    private fun stop() {
        try {
            Log.d(LOG_ID, "stop called for id $notificationId")
            activity = null
            if (notificationId > 0)
                AlarmNotification.stopNotification(notificationId, this)
            else
                AlarmNotification.stopCurrentNotification(this)
            finish()
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
            txtBgValue.text = ReceiveData.getGlucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon(withShadow = true))
            viewIcon.contentDescription = ReceiveData.getRateAsText(this)
            txtDelta.text = "Δ ${ReceiveData.getDeltaAsString()}"
            txtTime.text = "🕒 ${ReceiveData.getElapsedTimeMinuteAsString(this)}"
            txtTime.contentDescription = ReceiveData.getElapsedTimeMinuteAsString(this)
            val resId = (if(alarmType != null) alarmType else ReceiveData.getAlarmType())?.let {
                AlarmNotificationBase.getAlarmTextRes(
                    it
                )
            }
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
            if((System.currentTimeMillis()-createTime) >= (5*60*1000)) {
                finish()
            } else {
                update()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString())
        }
    }
}