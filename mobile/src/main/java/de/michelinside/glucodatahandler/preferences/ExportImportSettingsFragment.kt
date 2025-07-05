package de.michelinside.glucodatahandler.preferences

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.preference.Preference
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.common.utils.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ExportImportSettingsFragment: SettingsFragmentBase(R.xml.pref_export_import) {

    companion object {
        const val EXPORT_PHONE_SETTINGS = 3
        const val IMPORT_PHONE_SETTINGS = 4
    }
    override fun initPreferences() {
        try {
            Log.v(LOG_ID, "initPreferences called")

            var downloadUri: Uri? = null
            MediaScannerConnection.scanFile(requireContext(), arrayOf(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ).absolutePath), null
            ) { s: String, uri: Uri ->
                Log.v(LOG_ID, "Set URI $uri for path $s")
                downloadUri = uri
            }

            val prefExportSettings = findPreference<Preference>(Constants.SHARED_PREF_EXPORT_SETTINGS)
            prefExportSettings!!.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    val currentDateandTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                        Date()
                    )
                    val fileName = (if(Constants.IS_SECOND) "GDH_SECOND_" else "GDH_") + "phone_settings_" + currentDateandTime
                    putExtra(Intent.EXTRA_TITLE, fileName)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
                }
                startActivityForResult(intent, EXPORT_PHONE_SETTINGS)
                true
            }
            val prefImportSettings = findPreference<Preference>(Constants.SHARED_PREF_IMPORT_SETTINGS)
            prefImportSettings!!.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
                }
                startActivityForResult(intent, IMPORT_PHONE_SETTINGS)
                true
            }

            val prefResetDb = findPreference<Preference>(Constants.SHARED_PREF_RESET_DATABASE)
            prefResetDb!!.setOnPreferenceClickListener {
                Dialogs.showOkCancelDialog(requireContext(), resources.getString(CR.string.reset_db), resources.getString(CR.string.reset_db_warning),
                    { _, _ ->
                        GlucoDataService.resetDB()
                    }
                )
                true
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "initPreferences exception: " + exc.message.toString() )
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            Log.v(LOG_ID, "onActivityResult called for requestCode: " + requestCode + " - resultCode: " + resultCode + " - data: " + Utils.dumpBundle(data?.extras))
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.also { uri ->
                    Log.i(LOG_ID, "Export/Import for ${requestCode} to $uri")
                    when(requestCode) {
                        EXPORT_PHONE_SETTINGS -> Utils.saveSettings(requireContext(), uri)
                        IMPORT_PHONE_SETTINGS -> Utils.readSettings(requireContext(), uri)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Export/Import exception for ${requestCode}: " + exc.message.toString() )
        }
    }

}