package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import androidx.core.content.edit
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R

abstract class MultiPatientSourceTask(enabledKey: String, source: DataSource) : DataSourceTask(enabledKey, source) {
    protected abstract val LOG_ID: String
    protected abstract val patientIdKey: String
    private val patientData = mutableMapOf<String, String>()
    private var patientId = ""

    protected abstract fun getPatientData(): MutableMap<String, String>?

    protected abstract fun getPatientValue(patientId: String): Boolean

    override fun reset() {
        patientData.clear()
        patientId = GlucoDataService.sharedPref!!.getString(patientIdKey, "")!!
        Log.i(LOG_ID, "Reset called - using patientId: $patientId")
    }

    protected fun needPatientData(): Boolean {
        return (patientId.isEmpty() && patientData.size <= 1) || (patientId.isNotEmpty() && !patientData.containsKey(patientId))
    }

    override fun getValue(): Boolean {
        Log.v(LOG_ID, "getValue called - using patientId: $patientId")
        if(needPatientData()) {
            Log.i(LOG_ID, "Need patient data (current size: ${patientData.size}) - id set: ${patientId.isNotEmpty()} - data contains id: ${patientData.containsKey(patientId)}")
            if(!handlePatientData(getPatientData()))
                return false
        }
        if(patientId.isEmpty() || !patientData.containsKey(patientId)) {
            if(patientData.isEmpty())
                return false  // error should be set by source!
            if(patientData.size > 1) {
                setState(SourceState.MULTI_PATIENT)
                return false
            }
            setPatientId(patientData.keys.first())
        }
        Log.d(LOG_ID, "Get value for patient ${getPatient(patientId)}")
        return getPatientValue(patientId)
    }

    private fun handlePatientData(newPatientData: MutableMap<String, String>?): Boolean {
        Log.d(LOG_ID, "Handle patient data: $newPatientData")
        if(newPatientData == null) {
            return false
        }
        if(newPatientData.isEmpty()) {
            Log.e(LOG_ID, "No patient data found!")
            setLastError(GlucoDataService.context!!.resources.getString(R.string.source_no_patient))
            reset()
            return false
        }
        val triggerChange = patientData.size != newPatientData.size
        newPatientData.toMap(patientData)
        if (patientId.isNotEmpty() && !patientData.keys.contains(patientId)) {
            setPatientId(if(patientData.size == 1) patientData.keys.first() else "")
            Log.w(LOG_ID, "Reset patient as it is not in the list to ${getPatient(patientId)}")
            return true
        }
        if(patientId.isEmpty() && patientData.size == 1) {
            setPatientId(patientData.keys.first())
        } else {
            Log.i(LOG_ID, "Using current patient ${getPatient(patientId)}")
            if(triggerChange) {
                Handler(GlucoDataService.context!!.mainLooper).post {
                    InternalNotifier.notify(GlucoDataService.context!!, NotifySource.PATIENT_DATA_CHANGED, null)
                }
            }
        }
        return true
    }

    private fun setPatientId(id: String) {
        patientId = id
        Log.i(LOG_ID, "Using patient ${getPatient(patientId)}")
        GlucoDataService.sharedPref!!.edit {
            putString(patientIdKey, patientId)
        }
        Handler(GlucoDataService.context!!.mainLooper).post {
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.PATIENT_DATA_CHANGED, null)
        }
    }

    protected fun getPatient(id: String): String {
        if(patientData.containsKey(id)) {
            if(Constants.RELEASE)
                return patientData.keys.indexOf(id).toString()
            return patientData[id]?: "<not found!>"
        }
        return "<not set>"
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        Log.v(LOG_ID, "MultiPatient checkPreferenceChanged called for $key")
        var trigger = false
        if (key == null) {
            patientId = sharedPreferences.getString(patientIdKey, "")!!
            //setPatientId("")
            trigger = true
        } else if(key == patientIdKey) {
            if (patientId != sharedPreferences.getString(patientIdKey, "")) {
                patientId = sharedPreferences.getString(patientIdKey, "")!!
                Log.w(LOG_ID, "PatientID changed to ${getPatient(patientId)}")
                GlucoDataService.resetDB()
                trigger = false // will be triggered by reset of db!
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || trigger
    }

}