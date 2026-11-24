package de.michelinside.glucodatahandler.healthconnect

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import android.widget.Button
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.michelinside.glucodatahandler.R

class HealthConnectPermissionActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.HealthConnectPermissionActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" -> showRationale()
            "android.intent.action.VIEW_PERMISSION_USAGE" -> {
                showRationale()
            }
            else -> {
                finish()
            }
        }
    }

    private fun showRationale() {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContentView(R.layout.activity_health_connect_permission)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                Log.d(LOG_ID, "Insets: $systemBars")
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }
        findViewById<Button>(R.id.buttonClose).setOnClickListener { finish() }
    }
}
