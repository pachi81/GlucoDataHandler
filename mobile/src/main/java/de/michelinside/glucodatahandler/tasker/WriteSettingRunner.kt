package de.michelinside.glucodatahandler.tasker

import android.content.Context
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import kotlinx.coroutines.runBlocking

class WriteSettingRunner : TaskerPluginRunnerActionNoOutput<TaskerWriteSettingData>() {
    private val LOG_ID = "GDH.Tasker.WriteSettingRunner"
    override fun run(context: Context, input: TaskerInput<TaskerWriteSettingData>): TaskerPluginResult<Unit> {
        try {
            val key = input.regular.key
            val type = input.regular.type
            val value = input.regular.value
            Log.d(LOG_ID, "run called with key $key with type $type and value $value")

            val supportedValues = context.resources
                .getStringArray(CR.array.tasker_supported_settings_values)

            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            return runBlocking {
                try {
                    if (supportedValues.contains(key)) {
                        Log.i(LOG_ID, "handle changed setting for key $key with type $type and value $value")
                        if (key.startsWith("wear_")) {
                            val wear_setting = key.substringAfter("wear_", "dummy")
                            val extras = Bundle()
                            extras.putBoolean(wear_setting, value == "true")
                            GlucoDataService.service?.sendToConnectedDevices(NotifySource.TASKER_SETTINGS, extras)
                        } else {
                            with(sharedPref.edit()) {
                                putBoolean(key, value == "true")
                                apply()
                            }
                        }
                        TaskerPluginResultSucess()
                    } else {
                        TaskerPluginResultError(100, "Unknown setting $key with type $type and value $value")
                    }
                } catch (ex: Exception) {
                    Log.e(LOG_ID, "runBlocking exception for key $key with type $type and value $value: " + ex)
                    TaskerPluginResultError(100, "run exception: " + ex)
                }
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "run exception for input $input: " + ex)
            return TaskerPluginResultError(100, "run exception: " + ex)
        }
    }
}