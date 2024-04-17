package de.michelinside.glucodatahandler

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.michelinside.glucodatahandler.common.R

object Dialogs {
    fun showOkDialog(context: Context, title: String, message: String, okListener: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(context.resources.getText(R.string.button_ok), okListener)
            .show()
    }
}