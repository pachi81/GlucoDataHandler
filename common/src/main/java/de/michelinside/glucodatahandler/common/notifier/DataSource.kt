package de.michelinside.glucodatahandler.common.notifier

import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R

enum class DataSource(val resId: Int, val interval5Min: Boolean = false) {
    NONE(R.string.empty_string),
    JUGGLUCO(R.string.source_juggluco),
    XDRIP(R.string.source_xdrip),
    PHONE(R.string.source_phone),
    WEAR(R.string.source_wear),
    LIBRELINK(R.string.source_libreview),
    NIGHTSCOUT(R.string.source_nightscout),
    AAPS(R.string.source_aaps),
    GDH(if(Constants.IS_SECOND) R.string.source_gdh_second else R.string.source_gdh),
    DEXCOM_SHARE(R.string.source_dexcom_share, true),
    DEXCOM_BYODA(R.string.source_dexcom_byoda, true),
    NS_EMULATOR(R.string.source_ns_emulator, true),
    DIABOX(R.string.source_diabox),
    NOTIFICATION(R.string.source_notification);

    companion object {
        fun fromIndex(idx: Int): DataSource {
            entries.forEach {
                if(it.ordinal == idx) {
                    return it
                }
            }
            return NONE
        }
    }
}