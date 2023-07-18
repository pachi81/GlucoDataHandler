package de.michelinside.glucodatahandler

import android.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.preferences.SettingsFragment

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, SettingsFragment())
            .commit()

    }
}