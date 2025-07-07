package de.michelinside.glucodatahandler.common.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import de.michelinside.glucodatahandler.common.Constants

object PreferenceHelper {
    private val LOG_ID = "GDH.PreferenceHelper"
    private var marginTop = -1
    private var marginBottom = -1
    private var marginLeft = -1
    private var marginRight = -1


    fun resetViewPadding() {
        Log.d(LOG_ID, "reset called")
        marginTop = -1
        marginBottom = -1
        marginLeft = -1
        marginRight = -1
    }

    fun updateViewPadding(view: View) {
        Log.d(LOG_ID, "updateView called")
        if(marginTop >= 0) {
            Log.d(LOG_ID, "Update padding")
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = marginTop
                bottomMargin = marginBottom
                leftMargin = marginLeft
                rightMargin = marginRight
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                Log.d(LOG_ID, "Insets: " + systemBars.toString())
                marginTop = systemBars.top
                marginBottom = systemBars.bottom
                marginLeft = systemBars.left
                marginRight = systemBars.right
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = marginTop
                    bottomMargin = marginBottom
                    leftMargin = marginLeft
                    rightMargin = marginRight
                }
                insets
            }
        }
    }

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