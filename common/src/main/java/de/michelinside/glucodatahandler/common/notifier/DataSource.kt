package de.michelinside.glucodatahandler.common.notifier

import de.michelinside.glucodatahandler.common.R

enum class DataSource(val resId: Int) {
    NONE(R.string.empty_string),
    JUGGLUCO(R.string.source_juggluco),
    XDRIP(R.string.source_xdrip),
    PHONE(R.string.source_phone),
    WEAR(R.string.source_wear),
    LIBREVIEW(R.string.source_libreview),
    NIGHTSCOUT(R.string.source_nightscout),
    AAPS(R.string.source_aaps);
    
    companion object {
        fun fromIndex(idx: Int): DataSource {
            DataSource.values().forEach {
                if(it.ordinal == idx) {
                    return it
                }
            }
            return NONE
        }
    }
}