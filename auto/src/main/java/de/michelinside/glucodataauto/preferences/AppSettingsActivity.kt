package de.michelinside.glucodataauto

//noinspection SuspiciousImport
import android.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodataauto.preferences.SettingsFragment
import de.michelinside.glucodatahandler.common.R as RC

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar!!.title = this.applicationContext.resources.getText(RC.string.menu_settings)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, SettingsFragment())
            .commit()
    }
}