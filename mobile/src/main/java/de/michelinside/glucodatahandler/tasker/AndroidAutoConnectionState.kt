package de.michelinside.glucodatahandler.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied

class AndroidAutoConnectionStateHelper(config: TaskerPluginConfig<Unit>) : TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate<AndroidAutoConnectionStateRunner>(config) {
    override val runnerClass get() = AndroidAutoConnectionStateRunner::class.java
}

class AndroidAutoConnectionState : Activity(), TaskerPluginConfigNoInput {
    override val context: Context get() = applicationContext
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            AndroidAutoConnectionStateHelper(this).finishForTasker()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + ex)
        }
    }
}


class AndroidAutoConnectionStateRunner : TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<Unit>, update: Unit?): TaskerPluginResultCondition<Unit> {
        Log.d(LOG_ID, "getSatisfiedCondition is active: " + isActive)
        return if (isActive != null && isActive!!) TaskerPluginResultConditionSatisfied(context) else TaskerPluginResultConditionUnsatisfied()
    }
}

private val LOG_ID = "GDH.Tasker.AndroidAutoConnectionState"
private var isActive: Boolean? = null
fun Context.setAndroidAutoConnectionState(state: Boolean) {
    try {
        Log.d(LOG_ID, "set ConnectionState: " + state.toString() + " current: " + isActive)
        if (isActive == null || isActive != state) {
            isActive = state
            Log.d(LOG_ID, "trigger state change")
            AndroidAutoConnectionState::class.java.requestQuery(this)
        }
    } catch (ex: Exception) {
        Log.e(LOG_ID, "setAndroidAutoConnectionState exception: " + ex)
    }
}