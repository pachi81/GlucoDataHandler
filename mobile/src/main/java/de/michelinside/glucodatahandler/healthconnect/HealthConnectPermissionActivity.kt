package de.michelinside.glucodatahandler.healthconnect

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.michelinside.glucodatahandler.R

class HealthConnectPermissionActivity : AppCompatActivity() {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Set<String>>
    private val LOG_ID = "GDH.HealthConnectPermissionActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher =
            registerForActivityResult(HealthConnectManager.getPermissionRequestContract()) { grantedPermissions ->
                setResult(if (grantedPermissions.containsAll(HealthConnectManager.WRITE_GLUCOSE_PERMISSIONS)) RESULT_OK else RESULT_CANCELED)
                finish()
            }

        when (intent?.action) {
            "androidx.health.connect.client.ACTION_REQUEST_PERMISSIONS",
            "androidx.health.connect.client.ACTION_SHOW_PERMISSIONS_RATIONALE" -> {
                requestPermissionLauncher.launch(HealthConnectManager.WRITE_GLUCOSE_PERMISSIONS)
            }
            "android.intent.action.VIEW_PERMISSION_USAGE" -> {
                enableEdgeToEdge()
                setContentView(R.layout.activity_health_connect_permission)
                if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
                        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                        Log.d(LOG_ID, "Insets: " + systemBars.toString())
                        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                        insets
                    }
                }
                val closeButton: Button = findViewById(R.id.buttonClose)
                closeButton.setOnClickListener {
                    finish()
                }
            }
            else -> {
                finish()
            }
        }
    }
}
