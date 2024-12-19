package de.michelinside.glucodatahandler.common.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.preference.Preference
import de.michelinside.glucodatahandler.common.Constants

object PreferenceHelper {
    private val LOG_ID = "GDH.PreferenceHelper"

    private fun checkReplaceSecondString(text: String): String {
        if(Constants.IS_SECOND)
            return text.replace("GlucoDataHandler", "GDH Second").replace("GlucoDataAuto", "GDA Second")
        return text
    }

    fun replaceSecondSummary(pref: Preference?) {
        pref?.summary = checkReplaceSecondString(pref?.summary.toString())
    }

    fun replaceSecondTitle(pref: Preference?) {
        pref?.title = checkReplaceSecondString(pref?.title.toString())
    }

    fun setLinkOnClick(preference: Preference?, linkResId: Int, context: Context) {
        preference?.setOnPreferenceClickListener {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(context.resources.getText(linkResId).toString())
                )
                context.startActivity(browserIntent)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "setLinkOnClick exception for key ${preference.key}" + exc.toString())
            }
            true
        }
    }
}