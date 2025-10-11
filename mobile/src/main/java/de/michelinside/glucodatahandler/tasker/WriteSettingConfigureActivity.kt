package de.michelinside.glucodatahandler.tasker

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
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
        try {
            Log.d(LOG_ID, "assignFromInput called with input: $input")
            key = input.regular.key
            type = input.regular.type
            value = input.regular.value
        } catch (ex: Exception) {
            Log.e(LOG_ID, "assignFromInput exception: " + ex)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            helper.onCreate()
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                enableEdgeToEdge()
            setContentView(binding.root)

            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    Log.d(LOG_ID, "Insets: " + systemBars.toString())
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                    insets
                }
            }

            val supportedValues = resources
                .getStringArray(CR.array.tasker_supported_settings_values)

            if (supportedValues.contains(key)) {
                binding.spinnerSetting.setSelection(supportedValues.asList().indexOf(key))
            }

            if (value == "true")
                binding.switchSetting.isChecked = true

            binding.apply.setOnClickListener {
                try {
                    Log.d(LOG_ID, "apply clicked for pos ${binding.spinnerSetting.selectedItemPosition} - count ${supportedValues.size}")
                    key = supportedValues[binding.spinnerSetting.selectedItemPosition]
                    Log.d(LOG_ID, "key: $key")
                    type = "bool"
                    value = binding.switchSetting.isChecked.toString()

                    helper.finishForTasker()
                } catch (ex: Exception) {
                    Log.e(LOG_ID, "OnClickListener exception: " + ex)
                }
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + ex)
        }
    }
}