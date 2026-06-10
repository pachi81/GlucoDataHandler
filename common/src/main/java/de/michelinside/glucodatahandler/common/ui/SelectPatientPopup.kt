package de.michelinside.glucodatahandler.common.ui

import android.content.Context
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.core.content.edit
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.tasks.LibreLinkSourceTask
import de.michelinside.glucodatahandler.common.tasks.MedtrumSourceTask
import de.michelinside.glucodatahandler.common.R

object SelectPatientPopup {
    private val LOG_ID = "GDH.SelectPatientPopup"

    fun show(context: Context, dataSource: DataSource) {
        Log.v(LOG_ID, "show called for $dataSource")
        val patients: MutableMap<String, String>?
        val patientIdKey: String
        val sharedPreferences = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        when(dataSource) {
            DataSource.MEDTRUM -> {
                patients = MedtrumSourceTask.patientData
                patientIdKey = Constants.SHARED_PREF_MEDTRUM_PATIENT_ID
            }
            DataSource.LIBRELINK -> {
                patients = LibreLinkSourceTask.patientData
                patientIdKey = Constants.SHARED_PREF_LIBRE_PATIENT_ID
            }
            else -> {
                patients = null
                patientIdKey = ""
            }
        }
        if(patients.isNullOrEmpty()) {
            Log.e(LOG_ID, "No patients found for source $dataSource")
            return
        }
        Log.d(LOG_ID, "Found ${patients.size} patients for source $dataSource")
        val patientId = sharedPreferences.getString(patientIdKey, "")!!
        val item = patients.keys.indexOf(patientId)
        var newPatientIdx = -1
        Dialogs.showSelectItemDialog(context,
            R.string.select_patient_title,
            patients.values.toTypedArray(),
            item,
            { _, _ ->
                Log.d(LOG_ID, "Selected patient $newPatientIdx")
                if(newPatientIdx >= 0 && newPatientIdx < patients.size) {
                    val newPatient = patients.keys.elementAt(newPatientIdx)
                    Log.d(LOG_ID, "Selected patient ${patients.values.elementAt(newPatientIdx)} ($newPatient}")
                    sharedPreferences.edit {
                        putString(patientIdKey, newPatient)
                    }
                }
            },
            { _, which ->
                Log.d(LOG_ID, "Selected patient $which")
                newPatientIdx = which
            }
        )
    }
}