package de.michelinside.glucodatahandler.common.notification

import android.media.AudioManager
import de.michelinside.glucodatahandler.common.R
enum class SoundMode(val icon: Int, val ringerMode:Int) {
    OFF(R.drawable.icon_off, -1),
    SILENT(R.drawable.icon_volume_off, AudioManager.RINGER_MODE_SILENT),
    VIBRATE(R.drawable.icon_volume_vibrate, AudioManager.RINGER_MODE_VIBRATE),
    NORMAL(R.drawable.icon_volume_normal, AudioManager.RINGER_MODE_NORMAL)
}