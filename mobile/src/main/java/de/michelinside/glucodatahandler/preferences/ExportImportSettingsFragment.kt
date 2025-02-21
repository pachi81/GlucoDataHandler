package de.michelinside.glucodatahandler.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.preference.Preference
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.watch.LogcatReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ExportImportSettingsFragment: SettingsFragmentBase(R.xml.pref_export_import),
    NotifierInterface {

    companion object {
        const val CREATE_PHONE_FILE = 1
        const val CREATE_WEAR_FILE = 2
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

            val prefSaveMobileLogs = findPreference<Preference>(Constants.SHARED_PREF_SAVE_MOBILE_LOGS)
            prefSaveMobileLogs!!.setOnPreferenceClickListener {
                SaveLogs(AppSource.PHONE_APP, downloadUri)
                true
            }

            val prefSaveWearLogs = findPreference<Preference>(Constants.SHARED_PREF_SAVE_WEAR_LOGS)
            prefSaveWearLogs!!.isEnabled = WearPhoneConnection.nodesConnected && !LogcatReceiver.isActive
            prefSaveWearLogs.setOnPreferenceClickListener {
                SaveLogs(AppSource.WEAR_APP, downloadUri)
                true
            }

            val prefResetDb = findPreference<Preference>(Constants.SHARED_PREF_RESET_DATABASE)
            prefResetDb!!.setOnPreferenceClickListener {
                Dialogs.showOkCancelDialog(requireContext(), resources.getString(CR.string.reset_db), resources.getString(CR.string.reset_db_warning),
                    { _, _ ->
                        dbAccess.deleteAllValues()
                    }
                )
                true
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "initPreferences exception: " + exc.message.toString() )
        }

    }

    override fun onResume() {
        try {
            super.onResume()
            InternalNotifier.addNotifier(requireContext(), this, mutableSetOf(NotifySource.CAPILITY_INFO))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.message.toString() )
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            InternalNotifier.remNotifier(requireContext(), this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.message.toString() )
        }
    }

    private fun updateWearPrefs() {
        val prefSaveWearLogs = findPreference<Preference>(Constants.SHARED_PREF_SAVE_WEAR_LOGS)
        prefSaveWearLogs!!.isEnabled = WearPhoneConnection.nodesConnected && !LogcatReceiver.isActive
    }

    private fun SaveLogs(source: AppSource, downloadUri: Uri?) {
        try {
            Log.v(LOG_ID, "Save logs called for " + source)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                val currentDateandTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                    Date()
                )
                val fileName = (if(Constants.IS_SECOND) "GDH_SECOND_" else "GDH_") + source + "_" + currentDateandTime + ".txt"
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            startActivityForResult(intent, if (source == AppSource.WEAR_APP) CREATE_WEAR_FILE else CREATE_PHONE_FILE)

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
                        CREATE_WEAR_FILE -> LogcatReceiver.requestLogs(requireContext(), uri)
                        EXPORT_PHONE_SETTINGS -> Utils.saveSettings(requireContext(), uri)
                        IMPORT_PHONE_SETTINGS -> Utils.readSettings(requireContext(), uri)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Export/Import exception for ${requestCode}: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            if(dataSource == NotifySource.CAPILITY_INFO) {
                updateWearPrefs()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }
}