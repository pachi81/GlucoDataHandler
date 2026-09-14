package de.michelinside.glucodatahandler.common.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils
import java.util.Calendar


object Dialogs {

    const val LOG_ID = "GDH.Dialogs"

    fun showDialog(context: Context, titleResId: Int, messageResId: Int, okButtonRes: Int, okListener: DialogInterface.OnClickListener?) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.resources.getString(titleResId))
            .setMessage(context.resources.getString(messageResId))
            .setPositiveButton(context.resources.getText(okButtonRes), okListener)
            .show()
    }
    fun showOkDialog(context: Context, titleResId: Int, messageResId: Int, okListener: DialogInterface.OnClickListener?) {
        showDialog(context, titleResId, messageResId, R.string.button_ok, okListener)
    }

    fun showOkCancelDialog(context: Context, titleResId: Int, messageResId: Int, okListener: DialogInterface.OnClickListener?, cancelListener: DialogInterface.OnClickListener? = null) {
        showOkCancelDialog(context, context.resources.getString(titleResId), context.resources.getString(messageResId), okListener, cancelListener)
    }

    fun showOkCancelDialog(context: Context, title: String, message: String, okListener: DialogInterface.OnClickListener?, cancelListener: DialogInterface.OnClickListener? = null) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(context.resources.getText(R.string.button_ok), okListener)
            .setNegativeButton(context.resources.getText(R.string.button_cancel), cancelListener)
            .show()
    }

    fun showAcceptCancelDialog(context: Context, titleResId: Int, messageResId: Int, okListener: DialogInterface.OnClickListener?, cancelListener: DialogInterface.OnClickListener? = null) {
        showAcceptCancelDialog(context, context.resources.getString(titleResId), context.resources.getString(messageResId), okListener, cancelListener)
    }

    fun showAcceptCancelDialog(context: Context, title: String, message: String, okListener: DialogInterface.OnClickListener?, cancelListener: DialogInterface.OnClickListener? = null) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(context.resources.getText(R.string.button_accept), okListener)
            .setNegativeButton(context.resources.getText(R.string.button_cancel), cancelListener)
            .show()
    }

    fun showYesNoDialog(context: Context, titleResId: Int, messageResId: Int, okListener: DialogInterface.OnClickListener?, cancelListener: DialogInterface.OnClickListener? = null) {
        showYesNoDialog(context, context.resources.getString(titleResId), context.resources.getString(messageResId), okListener, cancelListener)
    }

    fun showYesNoDialog(context: Context, title: String, message: String, okListener: DialogInterface.OnClickListener?, cancelListener: DialogInterface.OnClickListener? = null) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(context.resources.getText(R.string.button_yes), okListener)
            .setNegativeButton(context.resources.getText(R.string.button_no), cancelListener)
            .show()
    }

    fun showSelectItemDialog(context: Context, titleResId: Int, items: Array<String>, selectedItem: Int, okListener: DialogInterface.OnClickListener?, selectItemListener: DialogInterface.OnClickListener?, cancelListener: DialogInterface.OnClickListener? = null) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.resources.getString(titleResId))
            .setPositiveButton(context.resources.getText(R.string.button_ok), okListener)
            .setNeutralButton(context.resources.getText(R.string.button_cancel), cancelListener)
            .setSingleChoiceItems(items, selectedItem,selectItemListener)
            .show()
    }

    fun showDateTimePicker(
        context: Context,
        initialTime: Long,
        onDateTimeSelected: (Long) -> Unit
    ) {
        try {
            Log.d(LOG_ID, "showDateTimePicker called with inital $initialTime")
            val calendar = Calendar.getInstance()
            if (initialTime > 0)
                calendar.timeInMillis = initialTime

            val datePickerDialog = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    Log.d(LOG_ID, "Selected date: $dayOfMonth.$month.$year")
                    val timePickerDialog = TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            val resultCalendar = Calendar.getInstance()
                            resultCalendar.set(Calendar.YEAR, year)
                            resultCalendar.set(Calendar.MONTH, month)
                            resultCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            resultCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            resultCalendar.set(Calendar.MINUTE, minute)
                            resultCalendar.set(Calendar.SECOND, 0)
                            resultCalendar.set(Calendar.MILLISECOND, 0)
                            Log.d(LOG_ID, "Selected time to ${Utils.getUiTimeStamp(resultCalendar.timeInMillis)}")
                            onDateTimeSelected(resultCalendar.timeInMillis)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true // 24h format
                    )
                    timePickerDialog.show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error showing date/time picker: ${e.message}")
        }
    }

    fun updateColorScheme(context: Context) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        val colorScheme = sharedPref.getString(Constants.SHARED_PREF_APP_COLOR_SCHEME, "")
        // This will be the top level handling of theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // If you don't want to adapt the device's theme settings, uncomment the snippet below
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            uiModeManager.setApplicationNightMode(
                when (colorScheme) {
                    "light" -> UiModeManager.MODE_NIGHT_NO // User set this explicitly
                    "dark" -> UiModeManager.MODE_NIGHT_YES // User set this explicitly
                    else -> UiModeManager.MODE_NIGHT_AUTO // Follow the device Dark Theme settings when not define yet by user
                }
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(
                when (colorScheme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO // User set this explicitly
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES // User set this explicitly
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM // For Android 10 and 11, follow the device Dark Theme settings when not define yet by user
                }
            )

        }
    }
}