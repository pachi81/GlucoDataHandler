package de.michelinside.glucodatahandler

//noinspection SuspiciousImport
import android.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.preferences.SettingsFragment
import de.michelinside.glucodatahandler.preferences.SourceFragment
import de.michelinside.glucodatahandler.common.R as RC

enum class SettingsFragmentClass(val value: Int, val titleRes: Int) {
    SETTINGS_FRAGMENT(0, RC.string.menu_settings),
    SORUCE_FRAGMENT(1, RC.string.menu_sources)
}
class SettingsActivity : AppCompatActivity() {
    companion object {
        const val FRAGMENT_EXTRA = "fragment"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when(intent.getIntExtra(FRAGMENT_EXTRA, 0)) {
            SettingsFragmentClass.SETTINGS_FRAGMENT.value -> {
                this.supportActionBar!!.title = this.applicationContext.resources.getText(SettingsFragmentClass.SETTINGS_FRAGMENT.titleRes)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, SettingsFragment())
                    .commit()
            }
            SettingsFragmentClass.SORUCE_FRAGMENT.value -> {
                this.supportActionBar!!.title = this.applicationContext.resources.getText(SettingsFragmentClass.SORUCE_FRAGMENT.titleRes)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, SourceFragment())
                    .commit()
            }
        }
    }
}