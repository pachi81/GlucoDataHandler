package de.michelinside.glucodatahandler.tasker

import android.content.Context
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource
import kotlinx.coroutines.runBlocking

class WriteSettingRunner : TaskerPluginRunnerActionNoOutput<TaskerWriteSettingData>() {
    override fun run(context: Context, input: TaskerInput<TaskerWriteSettingData>): TaskerPluginResult<Unit> {
        val key = input.regular.key
        val type = input.regular.type
        val value = input.regular.value

        val supportedValues = context.resources
            .getStringArray(R.array.tasker_supported_settings_values)

        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        return runBlocking {
            if (supportedValues.contains(key)) {
                if (key.startsWith("wear_")) {
                    val wear_setting = key.substringAfter("wear_", "dummy")
                    val extras = Bundle()
                    extras.putBoolean(wear_setting, value == "true")
                    GlucoDataService.service?.sendToConnectedDevices(NotifyDataSource.SETTINGS, extras)
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
        }
    }
}