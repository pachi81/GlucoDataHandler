package de.michelinside.glucodatahandler.preferences

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper

class ScreensaverSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceHelper.resetViewPadding()
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            supportActionBar?.title = getString(de.michelinside.glucodatahandler.R.string.app_name)
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ScreensaverSettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
