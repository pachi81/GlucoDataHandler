package de.michelinside.glucodatahandler.common

object Constants {
    const val CAPABILITY_PHONE = "glucodata_intent_mobile"
    const val CAPABILITY_WEAR = "glucodata_intent_wear"
    const val GLUCODATA_INTENT_MESSAGE_PATH = "/glucodata_intent"
    const val BATTERY_INTENT_MESSAGE_PATH = "/battery_intent"
    const val SETTINGS_INTENT_MESSAGE_PATH = "/settings_intent"
    const val SOURCE_SETTINGS_INTENT_MESSAGE_PATH = "/source_settings_intent"
    const val REQUEST_DATA_MESSAGE_PATH = "/request_data_intent"
    const val REQUEST_LOGCAT_MESSAGE_PATH = "/request_logcat_intent"
    const val LOGCAT_CHANNEL_PATH = "/logcat_intent"
    const val GLUCODATA_BROADCAST_ACTION = "glucodata.Minute"
    const val SETTINGS_BUNDLE = "settings_bundle"
    const val SOURCE_SETTINGS_BUNDLE = "source_settings_bundle"
    const val GLUCOSE_CONVERSION_FACTOR = 18.0182F
    const val GLUCOSE_MIN_VALUE = 40
    const val GLUCOSE_MAX_VALUE = 400
    const val ACTION_STOP_FOREGROUND = "stop_foreground"

    const val EXTRA_SOURCE_PACKAGE = "gdh.source_package"

    const val XDRIP_ACTION_GLUCOSE_READING = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING"
    const val XDRIP_BROADCAST_ACTION = "com.eveningoutpost.dexdrip.BgEstimate"

    const val GLUCODATA_ACTION = "de.michelinside.glucodatahandler.GLUCODATA"
    const val GLUCODATAAUTO_STATE_ACTION = "de.michelinside.glucodataauto.state"
    const val PACKAGE_GLUCODATAAUTO = "de.michelinside.glucodataauto"
    const val GLUCODATAAUTO_STATE_EXTRA = "state"

    const val VALUE_OBSOLETE_SHORT_SEC = 300
    const val VALUE_OBSOLETE_LONG_SEC  = 600

    const val SHARED_PREF_TAG = "GlucoDataHandlerPrefs"
    const val SHARED_PREF_AUTO_TAG = "GlucoDataAutoPrefs"
    const val SHARED_PREF_EXTRAS_TAG = "GlucoDataExtrasPrefs"
    const val SHARED_PREF_NO_GLUCODATAAUTO = "no_gda_info"
    const val SHARED_PREF_SEND_TO_GLUCODATAAUTO = "send_to_glucodataauto"
    const val SHARED_PREF_SEND_TO_GLUCODATA_AOD = "send_to_glucodata_aod"
    const val SHARED_PREF_GLUCODATA_RECEIVERS = "glucodata_receivers"
    const val SHARED_PREF_SEND_TO_XDRIP = "send_to_xdrip"
    const val SHARED_PREF_XDRIP_RECEIVERS = "xdrip_receivers"
    const val SHARED_PREF_SEND_XDRIP_BROADCAST = "send_xdrip_broadcast"
    const val SHARED_PREF_XDRIP_BROADCAST_RECEIVERS = "xdrip_broadcast_receivers"
    const val SHARED_PREF_TARGET_MIN = "target_min_value"
    const val SHARED_PREF_TARGET_MAX = "target_max_value"
    const val SHARED_PREF_CAR_NOTIFICATION = "car_notification"
    const val SHARED_PREF_CAR_NOTIFICATION_INTERVAL = "car_notification_interval"   // deprecated as changed to seekbar
    const val SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY = "car_notification_alarm_only"
    const val SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM = "car_notification_interval_num"
    const val SHARED_PREF_CAR_NOTIFICATION_REAPPEAR_INTERVAL = "car_notification_reappear_interval"
    const val SHARED_PREF_CAR_MEDIA = "car_media"
    const val SHARED_PREF_CAR_MEDIA_ICON_STYLE = "aa_media_player_icon_style"
    const val SHARED_PREF_NOTIFY_DURATION_LOW = "notify_duration_low"
    const val SHARED_PREF_NOTIFY_DURATION_HIGH = "notify_duration_high"
    const val SHARED_PREF_USE_MMOL = "use_mmol"
    const val SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL = "show_all_glucodata_receivers"
    const val SHARED_PREF_LOW_GLUCOSE = "low_glucose"
    const val SHARED_PREF_HIGH_GLUCOSE = "high_glucose"
    const val SHARED_PREF_FIVE_MINUTE_DELTA = "five_minute_delta"
    const val SHARED_PREF_COLOR_ALARM = "color_alarm"
    const val SHARED_PREF_COLOR_OUT_OF_RANGE = "color_out_of_range"
    const val SHARED_PREF_COLOR_OK = "color_ok"
    //const val SHARED_PREF_PERMANENT_NOTIFICATION = "permanent_notification"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_ICON = "status_bar_notification_icon"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY = "permanent_notification_empty"
    const val SHARED_PREF_SECOND_PERMANENT_NOTIFICATION = "second_permanent_notification"
    const val SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON = "second_status_bar_notification_icon"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON = "status_bar_notification_big_icon"
    const val SHARED_PREF_LARGE_ARROW_ICON = "large_arrow_icon"
    const val SHARED_PREF_FLOATING_WIDGET = "floating_widget"
    const val SHARED_PREF_FLOATING_WIDGET_STYLE = "floating_widget_style"
    const val SHARED_PREF_FLOATING_WIDGET_SIZE = "floating_widget_size"
    const val SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY = "floating_widget_transparency"
    const val SHARED_PREF_WIDGET_TRANSPARENCY = "widget_transparency"
    const val SHARED_PREF_RELATIVE_TIME = "relative_time"
    const val SHARED_PREF_SEND_TO_BANGLEJS = "send_to_banglejs"
    const val SHARED_PREF_WATCHDRIP = "watchdrip_enabled"
    const val SHARED_PREF_WATCHDRIP_RECEIVERS = "watchdrip_receivers"

    // internal app preferences (not changed by settings) -> use separate tag for not trigger onChanged events
    const val SHARED_PREF_INTERNAL_TAG = "GlucoDataHandlerInternalAppPrefs"
    const val SHARED_PREF_FLOATING_WIDGET_X = "floating_widget_x"
    const val SHARED_PREF_FLOATING_WIDGET_Y = "floating_widget_y"

    const val WIDGET_STYLE_GLUCOSE = "glucose"
    const val WIDGET_STYLE_GLUCOSE_TREND = "glucose_trend"
    const val WIDGET_STYLE_GLUCOSE_TREND_DELTA = "glucose_trend_delta"
    const val WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA = "glucose_trend_delta_time"
    const val WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA_IOB_COB = "glucose_trend_delta_time_iob_cob"

    // Wear only preferences
    const val SHARED_PREF_NOTIFICATION = "notification"
    const val SHARED_PREF_FOREGROUND_SERVICE = "foreground_service"
    const val SHARED_PREF_WEAR_COLORED_AOD = "colored_aod"

    // Data source preferences
    const val SHARED_PREF_SOURCE_INTERVAL="source_interval"
    const val SHARED_PREF_SOURCE_DELAY="source_delay"

    const val SHARED_PREF_LIBRE_ENABLED="source_libre_enabled"
    const val SHARED_PREF_LIBRE_USER="source_libre_user"
    const val SHARED_PREF_LIBRE_PASSWORD="source_libre_password"
    const val SHARED_PREF_LIBRE_RECONNECT="source_libre_reconnect"
    const val SHARED_PREF_LIBRE_TOKEN="source_libre_token"
    const val SHARED_PREF_LIBRE_TOKEN_EXPIRE="source_libre_token_expire"
    const val SHARED_PREF_LIBRE_REGION="source_libre_region"
    const val SHARED_PREF_LIBRE_PATIENT_ID="source_libre_patient_id"

    const val SHARED_PREF_NIGHTSCOUT_ENABLED="src_ns_enabled"
    const val SHARED_PREF_NIGHTSCOUT_URL="src_ns_url"
    const val SHARED_PREF_NIGHTSCOUT_SECRET="src_ns_secret"
    const val SHARED_PREF_NIGHTSCOUT_TOKEN="src_ns_token"
    const val SHARED_PREF_NIGHTSCOUT_IOB_COB="src_ns_iob_cob"

    const val SHARED_PREF_DUMMY_VALUES = "dummy_values"

    // Android Auto
    const val AA_MEDIA_ICON_STYLE_TREND = "trend"
    const val AA_MEDIA_ICON_STYLE_GLUCOSE_TREND = "glucose_trend"
}
