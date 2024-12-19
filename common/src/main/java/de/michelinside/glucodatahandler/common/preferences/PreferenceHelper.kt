package de.michelinside.glucodatahandler.common.preferences

import androidx.preference.Preference
import de.michelinside.glucodatahandler.common.Constants

object PreferenceHelper {
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
}