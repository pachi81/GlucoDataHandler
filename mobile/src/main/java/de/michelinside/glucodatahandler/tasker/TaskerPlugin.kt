package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import de.michelinside.glucodatahandler.common.ReceiveData
import android.app.Activity
import android.content.Context
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.*
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils

@TaskerInputRoot
@TaskerOutputObject()
@SuppressLint("NonConstantResourceId")
open class GlucodataValues {
    @field:TaskerInputField("glucose")
    @get:TaskerOutputVariable("glucose", R.string.glucose_label, R.string.glucose_html_label)
    val glucose: Float = ReceiveData.glucose
    @field:TaskerInputField("sensorId")
    @get:TaskerOutputVariable("sensorId", R.string.sensor_id_label, R.string.sensor_id_html_label)
    val sensorId: String? = ReceiveData.sensorID
    @field:TaskerInputField("rawValue")
    @get:TaskerOutputVariable("rawValue", R.string.raw_value_label, R.string.raw_value_html_label)
    val rawValue: Int = ReceiveData.rawValue
    @field:TaskerInputField("rate")
    @get:TaskerOutputVariable("rate", R.string.rate_label, R.string.rate_html_label)
    val rate: Float = ReceiveData.rate
    @field:TaskerInputField("rateLabel")
    @get:TaskerOutputVariable("rateLabel", R.string.rate_label_label, R.string.rate_label_html_label)
    val rateLabel: String? = ReceiveData.rateLabel
    @field:TaskerInputField("arrow")
    @get:TaskerOutputVariable("arrow", R.string.arrow_label, R.string.arrow_html_label)
    val arrow: String = GlucoDataUtils.getRateSymbol(ReceiveData.rate).toString()
    @field:TaskerInputField("alarm")
    @get:TaskerOutputVariable("alarm", R.string.alarm_label, R.string.alarm_html_label)
    val alarm: Int = ReceiveData.alarm
    @field:TaskerInputField("time")
    @get:TaskerOutputVariable("time", R.string.time_label, R.string.time_html_label)
    val time: Long = ReceiveData.time
    @field:TaskerInputField("timeDiff")
    @get:TaskerOutputVariable("timeDiff", R.string.time_diff_label, R.string.time_diff_html_label)
    val timeDiff: Long = ReceiveData.timeDiff
    @field:TaskerInputField("delta")
    @get:TaskerOutputVariable("delta", R.string.delta_label, R.string.delta_html_label)
    val delta: Float = ReceiveData.delta
    @field:TaskerInputField("unit")
    @get:TaskerOutputVariable("unit", R.string.unit_label, R.string.unit_html_label)
    val unit: String = ReceiveData.getUnit()
    @field:TaskerInputField("dexcomLabel")
    @get:TaskerOutputVariable("dexcomLabel", R.string.dexcom_label, R.string.dexcom_html_label)
    val dexcomLabel: String = GlucoDataUtils.getDexcomLabel(ReceiveData.rate)
    @field:TaskerInputField("iob")
    @get:TaskerOutputVariable("iob", R.string.iob_label, R.string.iob_html_label)
    val iob: Float = ReceiveData.iob
    @field:TaskerInputField("cob")
    @get:TaskerOutputVariable("cob", R.string.cob_label, R.string.cob_html_label)
    val cob: Float = ReceiveData.cob
}

private val LOG_ID = "GDH.Tasker.TaskerPlugin"
@TaskerInputRoot
@TaskerOutputObject()
@SuppressLint("NonConstantResourceId")
class GlucodataObsoleteValues : GlucodataValues() {
    @field:TaskerInputField("obsolete_time")
    @get:TaskerOutputVariable("obsolete_time", R.string.obsolete_time_label, R.string.obsolete_time_html_label)
    val obsolete_time: Long = ReceiveData.getElapsedTimeMinute()
}

class GlucodataValuesChangedRunner : TaskerPluginRunnerConditionEvent<GlucodataValues, GlucodataValues, GlucodataValues>() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<GlucodataValues>, update: GlucodataValues?): TaskerPluginResultCondition<GlucodataValues> {
        return TaskerPluginResultConditionSatisfied(context, update)
    }
}

class GlucodataEventHelper(config: TaskerPluginConfig<GlucodataValues>) : TaskerPluginConfigHelper<GlucodataValues, GlucodataValues, GlucodataValuesChangedRunner>(config) {
    override val runnerClass = GlucodataValuesChangedRunner::class.java
    override val inputClass = GlucodataValues::class.java
    override val outputClass = GlucodataValues::class.java
}

class GlucodataEvent : Activity(), TaskerPluginConfig<GlucodataValues> {
    override val context: Context get() = applicationContext
    override fun assignFromInput(input: TaskerInput<GlucodataValues>) {}
    override val inputForTasker = TaskerInput(GlucodataValues())

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "GlucodataEvent - onCreate called")
            super.onCreate(savedInstanceState)
            GlucodataEventHelper(this).finishForTasker()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "GlucodataEvent - onCreate exception: " + ex)
        }
    }
}

class GlucodataAlarmEvent : Activity(), TaskerPluginConfig<GlucodataValues> {
    override val context: Context get() = applicationContext
    override fun assignFromInput(input: TaskerInput<GlucodataValues>) {}
    override val inputForTasker = TaskerInput(GlucodataValues())

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "GlucodataAlarmEvent - onCreate called")
            super.onCreate(savedInstanceState)
            GlucodataEventHelper(this).finishForTasker()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "GlucodataAlarmEvent- onCreate exception: " + ex)
        }
    }
}


class GlucodataOsoleteValuesChangedRunner : TaskerPluginRunnerConditionEvent<GlucodataObsoleteValues, GlucodataObsoleteValues, GlucodataObsoleteValues>() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<GlucodataObsoleteValues>, update: GlucodataObsoleteValues?): TaskerPluginResultCondition<GlucodataObsoleteValues> {
        return TaskerPluginResultConditionSatisfied(context, update)
    }
}

class GlucodataObsoleteEventHelper(config: TaskerPluginConfig<GlucodataObsoleteValues>) : TaskerPluginConfigHelper<GlucodataObsoleteValues, GlucodataObsoleteValues, GlucodataOsoleteValuesChangedRunner>(config) {
    override val runnerClass = GlucodataOsoleteValuesChangedRunner::class.java
    override val inputClass = GlucodataObsoleteValues::class.java
    override val outputClass = GlucodataObsoleteValues::class.java
}

class GlucodataObsoleteEvent : Activity(), TaskerPluginConfig<GlucodataObsoleteValues> {
    override val context: Context get() = applicationContext
    override fun assignFromInput(input: TaskerInput<GlucodataObsoleteValues>) {}
    override val inputForTasker = TaskerInput(GlucodataObsoleteValues())

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "GlucodataObsoleteEvent - onCreate called")
            super.onCreate(savedInstanceState)
            GlucodataObsoleteEventHelper(this).finishForTasker()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "GlucodataObsoleteEvent - onCreate exception: " + ex)
        }
    }
}

object TaskerDataReceiver: NotifierInterface {
    private val LOG_ID = "GDH.Tasker.DataAction"
    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "sending new intent to tasker for source " + dataSource.toString())
            if (dataSource == NotifySource.OBSOLETE_VALUE) {
                GlucodataObsoleteEvent::class.java.requestQuery(context, GlucodataObsoleteValues())
            } else {
                if (ReceiveData.forceAlarm) {
                    Log.d(LOG_ID, "sending alarm event")
                    GlucodataAlarmEvent::class.java.requestQuery(context, GlucodataValues())
                }
                GlucodataEvent::class.java.requestQuery(context, GlucodataValues())
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }

}