package de.michelinside.glucodatahandler.tasker

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

@TaskerInputRoot
class TaskerWriteSettingData @JvmOverloads constructor(
    @field:TaskerInputField("key") var key: String = "",
    @field:TaskerInputField("type") var type: String = "",
    @field:TaskerInputField("value") var value: String? = null
)