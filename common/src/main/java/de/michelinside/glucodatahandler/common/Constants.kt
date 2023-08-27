package de.michelinside.glucodatahandler.common

object Constants {
    const val CAPABILITY_PHONE = "glucodata_intent_mobile"
    const val CAPABILITY_WEAR = "glucodata_intent_wear"
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

    const val VALUE_OBSOLETE_SHORT_SEC = 300
    const val VALUE_OBSOLETE_LONG_SEC  = 600

    const val SHARED_PREF_TAG = "GlucoDataHandlerPrefs"
    const val SHARED_PREF_SEND_TO_GLUCODATA_AOD = "send_to_glucodata_aod"
    const val SHARED_PREF_SEND_TO_XDRIP = "send_to_xdrip"
    const val SHARED_PREF_TARGET_MIN = "target_min_value"
    const val SHARED_PREF_TARGET_MAX = "target_max_value"
    const val SHARED_PREF_CAR_NOTIFICATION = "car_notification"
    const val SHARED_PREF_CAR_MEDIA = "car_media"
    const val SHARED_PREF_NOTIFY_DURATION_LOW = "notify_duration_low"
    const val SHARED_PREF_NOTIFY_DURATION_HIGH = "notify_duration_high"
    const val SHARED_PREF_USE_MMOL = "use_mmol"
    const val SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL = "show_all_glucodata_receivers"
    const val SHARED_PREF_GLUCODATA_RECEIVERS = "glucodata_receivers"
    const val SHARED_PREF_LOW_GLUCOSE = "low_glucose"
    const val SHARED_PREF_HIGH_GLUCOSE = "high_glucose"
    const val SHARED_PREF_FIVE_MINUTE_DELTA = "five_minute_delta"
    const val SHARED_PREF_COLOR_ALARM = "color_alarm"
    const val SHARED_PREF_COLOR_OUT_OF_RANGE = "color_out_of_range"
    const val SHARED_PREF_COLOR_OK = "color_ok"
    const val SHARED_PREF_PERMANENT_NOTIFICATION = "permanent_notification"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_ICON = "status_bar_notification_icon"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY = "permanent_notification_empty"
    const val SHARED_PREF_SECOND_PERMANENT_NOTIFICATION = "second_permanent_notification"
    const val SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON = "second_status_bar_notification_icon"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON = "status_bar_notification_big_icon"

    // Wear only preferences
    const val SHARED_PREF_NOTIFICATION = "notification"
    const val SHARED_PREF_FOREGROUND_SERVICE = "foreground_service"
    const val SHARED_PREF_WEAR_COLORED_AOD = "colored_aod"
}