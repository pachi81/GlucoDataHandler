package de.michelinside.glucodataauto.preferences

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.preference.Preference
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class LogSettingsFragment: SettingsFragmentBase(R.xml.pref_logging) {

    companion object {
        const val CREATE_PHONE_FILE = 1
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

            val prefSaveMobileLogs = findPreference<Preference>(Constants.SHARED_PREF_SAVE_MOBILE_LOGS)
            prefSaveMobileLogs!!.setOnPreferenceClickListener {
                SaveLogs(downloadUri)
                true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initPreferences exception: " + exc.message.toString() )
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
            startActivityForResult(intent, CREATE_PHONE_FILE)

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
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Export/Import exception for ${requestCode}: " + exc.message.toString() )
        }
    }
}