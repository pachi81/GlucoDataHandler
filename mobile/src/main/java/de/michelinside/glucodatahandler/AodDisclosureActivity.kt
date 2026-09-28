package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.R as CR

/**
 * Prominent disclosure and consent screen for the optional Always-On Display (AOD) feature.
 *
 * This is the only code path that opens the Android accessibility settings on behalf of the
 * AOD service. Every entry point (main screen card, settings switch, recovery paths) goes
 * through this screen, so the user always reads the disclosure and taps "Agree" first.
 * "Not now", the back key and the up arrow never count as consent.
 */
class AodDisclosureActivity : AppCompatActivity() {

    companion object {
        private const val LOG_ID = "GDH.AodDisclosure"
        const val EXTRA_RECOVERY = "recovery"

        /** Intent for the disclosure. [recovery] adds the note that Android turned the service off. */
        fun createIntent(context: Context, recovery: Boolean = false): Intent =
            Intent(context, AodDisclosureActivity::class.java).putExtra(EXTRA_RECOVERY, recovery)

        /**
         * Intents that open the Android accessibility settings: the accessibility overview
         * (with this app pre-selected on Android 14+), then the settings root as fallback.
         * The per-service details page is a protected system action and is not usable by apps.
         */
        private fun accessibilitySettingsIntents(context: Context): List<Intent> {
            val intents = mutableListOf<Intent>()
            val overview = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overview.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            intents.add(overview)
            intents.add(Intent(Settings.ACTION_SETTINGS))
            return intents
        }
    }

    private var recovery = false
    private lateinit var sharedPref: SharedPreferences

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onReturnFromSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            setContentView(R.layout.activity_aod_disclosure)
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.aod_disclosure_root)) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            sharedPref = getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            recovery = intent.getBooleanExtra(EXTRA_RECOVERY, false)
            Log.i(LOG_ID, "Disclosure shown - recovery: $recovery")

            // getText keeps the <b> spans of the string resources
            findViewById<TextView>(R.id.txtAodDisclosureTitle).text = getText(CR.string.aod_disclosure_title)
            findViewById<TextView>(R.id.txtAodDisclosureBody).text = getText(CR.string.aod_disclosure_body)
            findViewById<TextView>(R.id.txtAodDisclosureSteps).text = getText(CR.string.aod_disclosure_steps)
            findViewById<TextView>(R.id.txtAodDisclosureRecovery).visibility =
                if (recovery) View.VISIBLE else View.GONE

            findViewById<Button>(R.id.btnAodAgree).setOnClickListener { agree() }
            findViewById<Button>(R.id.btnAodNotNow).setOnClickListener { decline() }
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    decline()
                }
            })
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString())
            finishWithResult(false)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        decline()
        return true
    }

    private fun agree() {
        try {
            Log.i(LOG_ID, "User agreed to the AOD disclosure")
            if (AODAccessibilityService.isAdvancedProtectionActive(this)) {
                Toast.makeText(this, CR.string.aod_advanced_protection_enabled_info, Toast.LENGTH_LONG).show()
                finishWithResult(false)
                return
            }
            sharedPref.edit {
                putLong(Constants.SHARED_PREF_AOD_CONSENT_TIME, System.currentTimeMillis())
                putInt(Constants.SHARED_PREF_AOD_CONSENT_VERSION, BuildConfig.VERSION_CODE)
            }
            if (AODAccessibilityService.isAccessibilitySettingsEnabled(this)) {
                // service already enabled in Android: consent was the only missing part
                setAodEnabled(true)
                Toast.makeText(this, CR.string.aod_enabled_message, Toast.LENGTH_SHORT).show()
                finishWithResult(true)
                return
            }
            for (settingsIntent in accessibilitySettingsIntents(this)) {
                try {
                    settingsLauncher.launch(settingsIntent)
                    return
                } catch (exc: Exception) {  // ActivityNotFoundException, SecurityException on some OEM builds
                    Log.w(LOG_ID, "Settings intent " + settingsIntent.action + " failed: " + exc.toString())
                }
            }
            Toast.makeText(this, CR.string.aod_not_enabled_message, Toast.LENGTH_LONG).show()
            finishWithResult(false)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "agree exception: " + exc.message.toString())
            finishWithResult(false)
        }
    }

    private fun onReturnFromSettings() {
        try {
            val enabled = AODAccessibilityService.isAccessibilitySettingsEnabled(this)
            Log.i(LOG_ID, "Back from accessibility settings - service enabled: $enabled")
            setAodEnabled(enabled)
            Toast.makeText(
                this,
                if (enabled) CR.string.aod_enabled_message else CR.string.aod_not_enabled_message,
                Toast.LENGTH_SHORT
            ).show()
            finishWithResult(enabled)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReturnFromSettings exception: " + exc.message.toString())
            finishWithResult(false)
        }
    }

    private fun decline() {
        Log.i(LOG_ID, "User declined the AOD disclosure - recovery: $recovery")
        if (recovery) {
            // the service is off anyway; keep the setting consistent with reality
            setAodEnabled(false)
        }
        finishWithResult(false)
    }

    private fun setAodEnabled(enabled: Boolean) {
        sharedPref.edit {
            putBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, enabled)
        }
    }

    private fun finishWithResult(ok: Boolean) {
        setResult(if (ok) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}
