package de.michelinside.glucodatahandler.common.preferences

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat

abstract class PreferenceFragmentCompatBase : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PreferenceHelper.updateViewPadding(view)
    }
}