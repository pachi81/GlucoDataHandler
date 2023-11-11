package de.michelinside.glucodatahandler.common.notifier

import de.michelinside.glucodatahandler.common.R

enum class NotifySource(private val resId: Int) {
    BROADCAST(R.string.source_broadcast),
    MESSAGECLIENT(R.string.source_message_client),
    CAPILITY_INFO(R.string.source_capility_info),
    BATTERY_LEVEL(R.string.source_battery_level),
    NODE_BATTERY_LEVEL(R.string.source_node_battery_level),
    SETTINGS(R.string.source_settings),
    CAR_CONNECTION(R.string.source_car_connection),
    OBSOLETE_VALUE(R.string.source_obsolete),
    TIME_VALUE(R.string.time_value);

    fun getResId(): Int {
        return resId
    }
}