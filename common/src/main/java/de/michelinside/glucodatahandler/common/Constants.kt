package de.michelinside.glucodatahandler.common

object Constants {
    const val CAPABILITY = "glucodata_intent"
    const val GLUCODATA_INTENT_MESSAGE_PATH = "/glucodata_intent"
    const val BATTERY_INTENT_MESSAGE_PATH = "/battery_intent"
    const val SETTINGS_INTENT_MESSAGE_PATH = "/settings_intent"
    const val REQUEST_DATA_MESSAGE_PATH = "/request_data_intent"
    const val GLUCODATA_BROADCAST_ACTION = "glucodata.Minute"
    const val SETTINGS_BUNDLE = "settings_bundle"
    const val GLUCOSE_CONVERSION_FACTOR = 18.0182F
    const val GLUCOSE_MIN_VALUE = 40
    //const val GLUCOSE_MAX_VALUE = 400
    const val ACTION_STOP_FOREGROUND = "stop_foreground"

    const val XDRIP_ACTION_GLUCOSE_READING = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING"


    const val SHARED_PREF_TAG = "GlucoDataHandlerPrefs"
    const val SHARED_PREF_SEND_TO_GLUCODATA_AOD = "send_to_glucodata_aod"
    const val SHARED_PREF_SEND_TO_XDRIP = "send_to_xdrip"
    const val SHARED_PREF_FOREGROUND_SERVICE = "foreground_service"
    const val SHARED_PREF_TARGET_MIN = "target_min_value"
    const val SHARED_PREF_TARGET_MAX = "target_max_value"
    const val SHARED_PREF_NOTIFICATION = "notification"
    const val SHARED_PREF_CAR_NOTIFICATION = "car_notification"
    const val SHARED_PREF_NOTIFY_DURATION_LOW = "notify_duration_low"
    const val SHARED_PREF_NOTIFY_DURATION_HIGH = "notify_duration_high"
    const val SHARED_PREF_USE_MMOL = "use_mmol"
    const val SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL = "show_all_glucodata_receivers"
    const val SHARED_PREF_GLUCODATA_RECEIVERS = "glucodata_receivers"
    const val SHARED_PREF_LOW_GLUCOSE = "low_glucose"
    const val SHARED_PREF_HIGH_GLUCOSE = "high_glucose"
    const val SHARED_PREF_FIVE_MINUTE_DELTA = "five_minute_delta"
}