package de.michelinside.glucodatahandler.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied

class WearConnectionStateHelper(config: TaskerPluginConfig<Unit>) : TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate<WearConnectionStateRunner>(config) {
    override val runnerClass get() = WearConnectionStateRunner::class.java
}

class WearConnectionState : Activity(), TaskerPluginConfigNoInput {
    override val context: Context get() = applicationContext
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOG_ID, "onCreate called")
        super.onCreate(savedInstanceState)
        WearConnectionStateHelper(this).finishForTasker()
    }
}


class WearConnectionStateRunner : TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<Unit>, update: Unit?): TaskerPluginResultCondition<Unit> {
        Log.d(LOG_ID, "getSatisfiedCondition is active: " + isActive)
        return if (isActive != null && isActive!!) TaskerPluginResultConditionSatisfied(context) else TaskerPluginResultConditionUnsatisfied()
    }
}

private val LOG_ID = "GDH.Tasker.WearConnectionState"
private var isActive: Boolean? = null
fun Context.setWearConnectionState(state: Boolean) {
    Log.d(LOG_ID, "set ConnectionState: " + state.toString() + " current: " + isActive)
    if (isActive == null || isActive != state) {
        isActive = state
        Log.d(LOG_ID, "trigger state change")
        WearConnectionState::class.java.requestQuery(this)
    }
}