package de.michelinside.glucodatahandler.common

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log

class EditTargetChanger(minTarget: Boolean, ctx: Context): TextWatcher {
    private val context = ctx
    private val min = minTarget
    private val LOG_ID = "GlucoDataHandler.EditChangeWatch"
    var updateInProgress = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if( !updateInProgress ) {
            Log.d(
                LOG_ID,
                "onTextChanged: s=" + s + " start=" + start.toString() + " before=" + before.toString() + " count=" + count.toString()
            )
            try {
                if (s != null && s.isNotEmpty())
                    ReceiveData.writeTarget(context, min, s.toString().toFloat())
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Changing target exception: " + exc.message.toString())
            }
        }
    }

    override fun afterTextChanged(s: Editable?) {
    }

}