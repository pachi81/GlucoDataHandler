package de.michelinside.glucodatahandler.tasker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.utils.Log


@TaskerInputRoot
@TaskerOutputObject()
@SuppressLint("NonConstantResourceId")
class WatchBattery @JvmOverloads constructor(
    @field:TaskerInputField("level")
    @get:TaskerOutputVariable("level", R.string.battery_level_label, R.string.battery_level_html_label)
    val level: Int = -1,
    @field:TaskerInputField("nodeId")
    @get:TaskerOutputVariable("nodeId", R.string.watch_id_label, R.string.watch_id_html_label)
    val nodeId: String = "<id>",
    @field:TaskerInputField("name")
    @get:TaskerOutputVariable("name", R.string.watch_name_label, R.string.watch_name_html_label)
    val name: String = "<name>"
)


private val LOG_ID = "GDH.Tasker.WatchBatteryPlugin"

class WatchBatteryActionRunner() : TaskerPluginRunnerConditionEvent<WatchBattery, WatchBattery, WatchBattery>() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<WatchBattery>, update: WatchBattery?): TaskerPluginResultCondition<WatchBattery> {
        return TaskerPluginResultConditionSatisfied(context, update)
    }
}


class WatchBatteryHelper(config: TaskerPluginConfig<WatchBattery>) : TaskerPluginConfigHelper<WatchBattery, WatchBattery, WatchBatteryActionRunner>(config) {
    override val runnerClass = WatchBatteryActionRunner::class.java
    override val inputClass = WatchBattery::class.java
    override val outputClass = WatchBattery::class.java
}


class WatchBatteryEvent : Activity(), TaskerPluginConfig<WatchBattery> {
    override val context: Context get() = applicationContext
    override fun assignFromInput(input: TaskerInput<WatchBattery>) {}
    override val inputForTasker = TaskerInput(WatchBattery())

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "WatchBatteryEvent - onCreate called")
            super.onCreate(savedInstanceState)
            WatchBatteryHelper(this).finishForTasker()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "WatchBatteryEvent- onCreate exception: " + ex)
        }
    }
}


object TaskerWatchBatteryReceiver: NotifierInterface {
    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            if(extras != null && extras.containsKey(BatteryReceiver.LEVEL) && extras.containsKey(Constants.EXTRA_NODE_ID)) {
                val nodeId = extras.getString(Constants.EXTRA_NODE_ID)
                if(nodeId != null) {
                    WearPhoneConnection.getNodeBatteryLevel(nodeId, false).firstNotNullOf { (name, level) ->
                        if (level > 0) {
                            Log.d(LOG_ID, "sending watch battery level $level for node $nodeId - name $name to tasker for source " + dataSource.toString())
                            WatchBatteryEvent::class.java.requestQuery(context, WatchBattery(level, nodeId, name))
                        } else {
                            Log.w(LOG_ID, "No level found for node $nodeId - name $name")
                        }
                    }
                } else {
                    Log.w(LOG_ID, "No node id received")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }

}