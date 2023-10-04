package de.michelinside.glucodatahandler.tasker

import com.joaomgcd.taskerpluginlibrary.SimpleResult
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.SimpleResultSuccess
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput

class WriteSettingHelper(config: TaskerPluginConfig<TaskerWriteSettingData>) : TaskerPluginConfigHelperNoOutput<TaskerWriteSettingData, WriteSettingRunner>(config) {
    override val runnerClass: Class<WriteSettingRunner> = WriteSettingRunner::class.java
    override val inputClass: Class<TaskerWriteSettingData> = TaskerWriteSettingData::class.java

    override fun isInputValid(input: TaskerInput<TaskerWriteSettingData>): SimpleResult {
        return if (input.regular.key.isBlank()) SimpleResultError("Please select a setting!")
        else SimpleResultSuccess()
    }
}