package de.michelinside.glucodatahandler.tasker

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.databinding.TaskerWriteSettingBinding


class WriteSettingConfigureActivity : AppCompatActivity(),
    TaskerPluginConfig<TaskerWriteSettingData> {
    private val LOG_ID = "GDH.Tasker.WriteSettingConfigureActivity"
    override val context: Context
        get() = this
    override val inputForTasker: TaskerInput<TaskerWriteSettingData>
        get() = TaskerInput(TaskerWriteSettingData(key, type, value))

    private var key: String = ""
    private var type: String = ""
    private var value: String? = null

    private val helper by lazy { WriteSettingHelper(this) }
    private val binding by lazy { TaskerWriteSettingBinding.inflate(layoutInflater) }

    override fun assignFromInput(input: TaskerInput<TaskerWriteSettingData>) {
        key = input.regular.key
        type = input.regular.type
        value = input.regular.value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            helper.onCreate()

            setContentView(binding.root)

            val supportedValues = resources
                .getStringArray(R.array.tasker_supported_settings_values)

            if (supportedValues.contains(key)) {
                binding.spinnerSetting.setSelection(supportedValues.asList().indexOf(key))
            }

            if (value == "true")
                binding.switchSetting.isChecked = true

            binding.apply.setOnClickListener {
                key = supportedValues[binding.spinnerSetting.selectedItemPosition]
                type = "bool"
                value = binding.switchSetting.isChecked.toString()

                helper.finishForTasker()
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + ex)
        }
    }
}