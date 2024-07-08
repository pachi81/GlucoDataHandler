package de.michelinside.glucodatahandler

import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R


object Dialogs {
    fun showOkDialog(context: Context, titleResId: Int, messageResId: Int, okListener: DialogInterface.OnClickListener?) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.resources.getString(titleResId))
            .setMessage(context.resources.getString(messageResId))
            .setPositiveButton(context.resources.getText(R.string.button_ok), okListener)
            .show()
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