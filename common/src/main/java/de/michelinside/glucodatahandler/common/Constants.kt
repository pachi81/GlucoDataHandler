package de.michelinside.glucodatahandler.common

object Constants {
    const val CAPABILITY = "glucodata_intent"
    const val GLUCODATA_INTENT_MESSAGE_PATH = "/glucodata_intent"
    const val GLUCODATA_BROADCAST_ACTION = "glucodata.Minute"
    const val GLUCOSE_CONVERSION_FACTOR = 18.0182F
    const val GLUCOSE_MIN_VALUE = 40
    const val GLUCOSE_MAX_VALUE = 400
    const val SHARED_PREF_TAG = "GlucoDataHandlerPrefs"
    const val SHARED_PREF_SEND_TO_GLUCODATA_AOD = "send_to_glucodata_aod"
    const val SHARED_PREF_SEND_TO_XDRIP = "send_to_xdrip"
    const val SHARED_PREF_FOREGROUND_SERVICE = "foreground_service"
    const val SHARED_PREF_TARGET_MIN = "target_min_value"
    const val SHARED_PREF_TARGET_MAX = "target_max_value"
    const val ACTION_STOP_FOREGROUND = "stop_foreground"

    const val XDRIP_ACTION_GLUCOSE_READING = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING"
}