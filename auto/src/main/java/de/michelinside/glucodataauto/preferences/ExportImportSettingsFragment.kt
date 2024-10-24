package de.michelinside.glucodataauto.preferences

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.preference.Preference
import de.michelinside.glucodataauto.MainActivity.Companion.CREATE_FILE
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ExportImportSettingsFragment: SettingsFragmentBase(R.xml.pref_export_import) {

    companion object {
        const val CREATE_PHONE_FILE = 1
        const val EXPORT_PHONE_SETTINGS = 2
        const val IMPORT_PHONE_SETTINGS = 3
    }
    override fun initPreferences() {
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
                val fileName = "GDA_settings_" + currentDateandTime
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

        val prefSaveMobileLogs = findPreference<Preference>(Constants.SHARED_PREF_SAVE_MOBILE_LOGS)
        prefSaveMobileLogs!!.setOnPreferenceClickListener {
            SaveLogs(downloadUri)
            true
        }
    }

    private fun SaveLogs(downloadUri: Uri?) {
        try {
            Log.v(LOG_ID, "Save mobile logs called")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                val currentDateandTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                    Date()
                )
                val fileName = "GDA_" + currentDateandTime + ".txt"
                putExtra(Intent.EXTRA_TITLE, fileName)
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
            }
            startActivityForResult(intent, CREATE_FILE)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving mobile logs exception: " + exc.message.toString() )
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
                        CREATE_PHONE_FILE -> Utils.saveLogs(requireContext(), uri)
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