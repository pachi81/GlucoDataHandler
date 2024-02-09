package de.michelinside.glucodataauto

//noinspection SuspiciousImport
import android.R
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodataauto.preferences.SettingsFragment
import de.michelinside.glucodatahandler.common.R as RC

class SettingsActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.AA.SettingsActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            if (savedInstanceState==null) {
                this.supportActionBar!!.title = this.applicationContext.resources.getText(RC.string.menu_settings)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, SettingsFragment())
                    .commit()
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + ex)
        }
    }
}