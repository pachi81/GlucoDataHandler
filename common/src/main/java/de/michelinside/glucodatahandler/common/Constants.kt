package de.michelinside.glucodatahandler.common

import android.graphics.Color

object Constants {
    const val CAPABILITY_PHONE = "glucodata_intent_mobile"
    const val CAPABILITY_WEAR = "glucodata_intent_wear"
    const val GLUCODATA_INTENT_MESSAGE_PATH = "/glucodata_intent"
    const val BATTERY_INTENT_MESSAGE_PATH = "/battery_intent"
    const val SETTINGS_INTENT_MESSAGE_PATH = "/settings_intent"
    const val SOURCE_SETTINGS_INTENT_MESSAGE_PATH = "/source_settings_intent"
    const val ALARM_SETTINGS_INTENT_MESSAGE_PATH = "/alarm_settings_intent"
    const val REQUEST_DATA_MESSAGE_PATH = "/request_data_intent"
    const val REQUEST_LOGCAT_MESSAGE_PATH = "/request_logcat_intent"
    const val LOGCAT_CHANNEL_PATH = "/logcat_intent"
    const val DB_SYNC_CHANNEL_PATH = "/db_sync_intent"
    const val COMMAND_PATH = "/command_intent"
    const val GLUCODATA_BROADCAST_ACTION = "glucodata.Minute"
    const val SETTINGS_BUNDLE = "settings_bundle"
    const val SOURCE_SETTINGS_BUNDLE = "source_settings_bundle"
    const val ALARM_SETTINGS_BUNDLE = "alarm_settings_bundle"
    const val ALARM_EXTRA_BUNDLE = "alarm_extras_bundle"
    const val COMMAND_EXTRA = "command"
    const val COMMAND_BUNDLE = "command_bundle"
    const val GLUCOSE_CONVERSION_FACTOR = 18.0182F
    const val GLUCOSE_MIN_VALUE = 40
    const val GLUCOSE_MAX_VALUE = 600
    const val ACTION_STOP_FOREGROUND = "stop_foreground"
    const val ACTION_START_FOREGROUND = "start_foreground"

    const val ACTION_PREFIX = "gdh_action_"
    const val ACTION_FLOATING_WIDGET_TOGGLE = ACTION_PREFIX + "floating_widget_toggle"
    const val ACTION_DUMMY_VALUE = ACTION_PREFIX + "dummy_value"
    const val ACTION_SPEAK = ACTION_PREFIX + "speak"
    const val ACTION_GRAPH = ACTION_PREFIX + "show_graph"

    const val IS_SECOND = BuildConfig.BUILD_TYPE == "second"
    const val RELEASE = (BuildConfig.BUILD_TYPE == "release" || BuildConfig.BUILD_TYPE == "second")

    val PACKAGE_GLUCODATAHANDLER = if (IS_SECOND) "de.michelinside.glucodatahandler.second" else "de.michelinside.glucodatahandler"
    val PACKAGE_GLUCODATAAUTO = if (IS_SECOND) "de.michelinside.glucodataauto.second" else "de.michelinside.glucodataauto"
    const val PACKAGE_JUGGLUCO = "tk.glucodata"

    const val EXTRA_SOURCE_PACKAGE = "gdh.source_package"
    const val EXTRA_SOURCE_INDEX = "gdh.source_idx"
    const val EXTRA_AA_CONNECTED = "gdh.aa_connected"

    const val XDRIP_ACTION_GLUCOSE_READING = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING"

    const val GLUCODATAAUTO_STATE_ACTION = "de.michelinside.glucodataauto.state"
    const val GLUCODATAAUTO_STATE_EXTRA = "state"


    //const val VALUE_OBSOLETE_SHORT_SEC = 300
    const val VALUE_OBSOLETE_LONG_SEC  = 600
    const val VALUE_IOB_COBOBSOLETE_SEC  = 1800

    const val SHARED_PREF_TAG = "GlucoDataHandlerPrefs"
    const val SHARED_PREF_AUTO_TAG = "GlucoDataAutoPrefs"
    const val SHARED_PREF_EXTRAS_TAG = "GlucoDataExtrasPrefs"
    const val SHARED_PREF_NO_GLUCODATAAUTO = "no_gda_info"
    const val SHARED_PREF_SEND_TO_GLUCODATAAUTO = "send_to_glucodataauto"
    const val SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO = "send_pref_to_glucodataauto"
    const val SHARED_PREF_SEND_TO_GLUCODATA_AOD = "send_to_glucodata_aod"
    const val SHARED_PREF_GLUCODATA_RECEIVERS = "glucodata_receivers"
    const val SHARED_PREF_SEND_TO_XDRIP = "send_to_xdrip"
    const val SHARED_PREF_XDRIP_RECEIVERS = "xdrip_receivers"
    const val SHARED_PREF_SEND_XDRIP_BROADCAST = "send_xdrip_broadcast"
    const val SHARED_PREF_XDRIP_BROADCAST_RECEIVERS = "xdrip_broadcast_receivers"
    const val SHARED_PREF_XDRIP_BROADCAST_SERVICE_API = "xdrip_broadcast_service_api"
    const val SHARED_PREF_TARGET_MIN = "target_min_value"
    const val SHARED_PREF_TARGET_MAX = "target_max_value"
    const val SHARED_PREF_USE_MMOL = "use_mmol"
    const val SHARED_PREF_GLUCODATA_RECEIVER_SHOW_ALL = "show_all_glucodata_receivers"
    const val SHARED_PREF_LOW_GLUCOSE = "low_glucose"
    const val SHARED_PREF_HIGH_GLUCOSE = "high_glucose"
    const val SHARED_PREF_FIVE_MINUTE_DELTA = "five_minute_delta"
    const val SHARED_PREF_COLOR_ALARM = "color_alarm"
    const val SHARED_PREF_COLOR_OUT_OF_RANGE = "color_out_of_range"
    const val SHARED_PREF_COLOR_OK = "color_ok"
    const val SHARED_PREF_COLOR_OBSOLETE = "color_obsolete"
    const val SHARED_PREF_OBSOLETE_TIME = "obsolete_time"
    //const val SHARED_PREF_PERMANENT_NOTIFICATION = "permanent_notification"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_ICON = "status_bar_notification_icon"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_COLORED_ICON = "status_bar_notification_colored_icon"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY = "permanent_notification_empty"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT = "permanent_notification_custom_layout"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION = "permanent_notification_tap_action"
    const val SHARED_PREF_SECOND_PERMANENT_NOTIFICATION = "second_permanent_notification"
    const val SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON = "second_status_bar_notification_icon"
    const val SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION = "second_permanent_notification_tap_action"
    const val SHARED_PREF_THIRD_PERMANENT_NOTIFICATION = "third_permanent_notification"
    const val SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON = "third_status_bar_notification_icon"
    const val SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_TAP_ACTION = "third_permanent_notification_tap_action"
    const val SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON = "status_bar_notification_big_icon"
    const val SHARED_PREF_LARGE_ARROW_ICON = "large_arrow_icon"
    const val SHARED_PREF_FLOATING_WIDGET = "floating_widget"
    const val SHARED_PREF_FLOATING_WIDGET_STYLE = "floating_widget_style"
    const val SHARED_PREF_FLOATING_WIDGET_SIZE = "floating_widget_size"
    const val SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION = "floating_widget_size_migration"
    const val SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY = "floating_widget_transparency"
    const val SHARED_PREF_FLOATING_WIDGET_TIME_TO_CLOSE = "floating_widget_time_to_close"
    const val SHARED_PREF_FLOATING_WIDGET_TAP_ACTION = "floating_widget_tap_action"
    const val SHARED_PREF_FLOATING_WIDGET_GRAPH_DURATION = "graph_duration_floating_widget"
    const val SHARED_PREF_FLOATING_WIDGET_LOCK_POSITION = "floating_widget_lock_position"
    const val SHARED_PREF_WIDGET_TRANSPARENCY = "widget_transparency"
    const val SHARED_PREF_WIDGET_TAP_ACTION = "widget_tap_action"
    const val SHARED_PREF_RELATIVE_TIME = "relative_time"
    const val SHARED_PREF_SEND_TO_BANGLEJS = "send_to_banglejs"
    const val SHARED_PREF_WATCHDRIP = "watchdrip_enabled"
    const val SHARED_PREF_WATCHDRIP_RECEIVERS = "watchdrip_receivers"
    const val SHARED_PREF_CHECK_WEAR_OS_CONNECTION = "wear_os_check_connection"
    const val SHARED_PREF_RESET_WEAR_OS_CONNECTION = "wear_os_reset_connection"
    const val SHARED_PREF_OPEN_WATCH_DRIP_LINK = "open_watchdrip_link"
    const val SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT = "uncaught_exception_detected"
    const val SHARED_PREF_UNCAUGHT_EXCEPTION_TIME = "uncaught_exception_time"
    const val SHARED_PREF_UNCAUGHT_EXCEPTION_MESSAGE = "uncaught_exception_message"
    const val SHARED_PREF_SHOW_OTHER_UNIT = "show_other_unit"
    const val SHARED_PREF_EXPORT_SETTINGS = "export_settings"
    const val SHARED_PREF_IMPORT_SETTINGS = "import_settings"
    const val SHARED_PREF_SAVE_MOBILE_LOGS = "save_mobile_logs"
    const val SHARED_PREF_SAVE_WEAR_LOGS = "save_wear_logs"


    const val SHARED_PREF_WATCHFACES_PUJIE = "pref_watchfaces_pujie"
    const val SHARED_PREF_WATCHFACES_DMM = "pref_watchfaces_dmm"
    const val SHARED_PREF_WATCHFACES_GDC = "pref_watchfaces_gdc"


    // internal app preferences (not changed by settings) -> use separate tag for not trigger onChanged events
    const val SHARED_PREF_INTERNAL_TAG = "GlucoDataHandlerInternalAppPrefs"
    const val SHARED_PREF_FLOATING_WIDGET_X = "floating_widget_x"
    const val SHARED_PREF_FLOATING_WIDGET_Y = "floating_widget_y"

    const val WIDGET_STYLE_GLUCOSE = "glucose"
    const val WIDGET_STYLE_GLUCOSE_TREND = "glucose_trend"
    const val WIDGET_STYLE_GLUCOSE_TREND_DELTA = "glucose_trend_delta"
    const val WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA = "glucose_trend_delta_time"
    const val WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA_IOB_COB = "glucose_trend_delta_time_iob_cob"
    const val WIDGET_STYLE_CHART_GLUCOSE_TREND_TIME_DELTA_IOB_COB = "chart_glucose_trend_delta_time_iob_cob"

    // Wear only preferences
    const val SHARED_PREF_WEAR_COLORED_AOD = "colored_aod"
    const val SHARED_PREF_COMPLICATION_TAP_ACTION = "complication_tap_action"

    // Data source preferences
    const val SHARED_PREF_SOURCE_INTERVAL="source_interval"
    const val SHARED_PREF_SOURCE_DELAY="source_delay"

    // offline sources
    const val SHARED_PREF_SOURCE_GDH_ENABLED="source_gdh_enabled"
    const val SHARED_PREF_SOURCE_JUGGLUCO_ENABLED="source_juggluco_enabled"
    const val SHARED_PREF_SOURCE_XDRIP_ENABLED="source_xdrip_enabled"
    const val SHARED_PREF_SOURCE_AAPS_ENABLED="source_aaps_enabled"
    const val SHARED_PREF_SOURCE_BYODA_ENABLED="source_byoda_enabled"
    const val SHARED_PREF_SOURCE_EVERSENSE_ENABLED="source_eversense_enabled"
    const val SHARED_PREF_SOURCE_DIABOX_ENABLED="source_diabox_enabled"

    // notification reader
    const val SHARED_PREF_SOURCE_NOTIFICATION_ENABLED="source_notification_enabled"
    const val SHARED_PREF_SOURCE_NOTIFICATION_READER_APP="notification_reader_app"
    const val SHARED_PREF_SOURCE_NOTIFICATION_READER_5_MINUTE_INTERVAl="notification_reader_five_minute_interval"
    const val SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX="notification_reader_app_regex"
    const val SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP="notification_reader_iob_app"
    const val SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX="notification_reader_iob_app_regex"

    // online sources
    const val SHARED_PREF_LIBRE_ENABLED="source_libre_enabled"
    const val SHARED_PREF_LIBRE_USER="source_libre_user"
    const val SHARED_PREF_LIBRE_PASSWORD="source_libre_password"
    const val SHARED_PREF_LIBRE_RECONNECT="source_libre_reconnect"
    const val SHARED_PREF_LIBRE_TOKEN="source_libre_token"
    const val SHARED_PREF_LIBRE_TOKEN_EXPIRE="source_libre_token_expire"
    const val SHARED_PREF_LIBRE_REGION="source_libre_region"
    const val SHARED_PREF_LIBRE_PATIENT_ID="source_libre_patient_id"
    const val SHARED_PREF_LIBRE_USER_ID="source_libre_user_id"
    const val SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU="source_libre_auto_accept_tou"
    const val SHARED_PREF_LIBRE_SERVER="source_libre_server"

    const val SHARED_PREF_DEXCOM_SHARE_ENABLED="source_dexcom_share_enabled"
    const val SHARED_PREF_DEXCOM_SHARE_USER="source_dexcom_share_user"
    const val SHARED_PREF_DEXCOM_SHARE_PASSWORD="source_dexcom_share_password"
    const val SHARED_PREF_DEXCOM_SHARE_USE_US_URL="source_dexcom_share_use_us_url"
    const val SHARED_PREF_DEXCOM_SHARE_RECONNECT="source_dexcom_share_reconnect"
    const val SHARED_PREF_DEXCOM_SHARE_ACCOUNT_LINK="source_dexcom_account_link"

    const val SHARED_PREF_NIGHTSCOUT_ENABLED="src_ns_enabled"
    const val SHARED_PREF_NIGHTSCOUT_URL="src_ns_url"
    const val SHARED_PREF_NIGHTSCOUT_SECRET="src_ns_secret"
    const val SHARED_PREF_NIGHTSCOUT_TOKEN="src_ns_token"
    const val SHARED_PREF_NIGHTSCOUT_IOB_COB="src_ns_iob_cob"

    const val SHARED_PREF_DUMMY_VALUES = "dummy_values"

    const val SHARED_PREF_LOCKSCREEN_WP_ENABLED = "lockscreen_enabled"
    const val SHARED_PREF_LOCKSCREEN_WP_Y_POS = "lockscreen_y_pos"
    const val SHARED_PREF_LOCKSCREEN_WP_STYLE = "lockscreen_style"
    const val SHARED_PREF_LOCKSCREEN_WP_SIZE = "lockscreen_size"
    const val SHARED_PREF_LOCKSCREEN_WP_GRAPH_DURATION = "lockscreen_graph_duration"

    const val SHARED_PREF_SOURCE_JUGGLUCO_SET_NS_IOB_ACTION = "source_juggluco_activate_local_nightscout_iob_action"
    const val SHARED_PREF_SOURCE_XDRIP_SET_NS_IOB_ACTION = "source_xdrip_activate_local_nightscout_iob_action"

    const val SHARED_PREF_APP_COLOR_SCHEME = "application_color_scheme"

    const val SHARED_PREF_EVERSENSE_ESEL_INFO = "source_eversense_info"

    // aod
    const val SHARED_PREF_AOD_WP_ENABLED = "aod_enabled"
    const val SHARED_PREF_AOD_WP_Y_POS = "aod_y_pos"
    const val SHARED_PREF_AOD_WP_STYLE = "aod_style"
    const val SHARED_PREF_AOD_WP_SIZE = "aod_size"
    const val SHARED_PREF_AOD_WP_COLOURED = "aod_coloured"
    const val SHARED_PREF_AOD_WP_GRAPH_DURATION = "aod_graph_duration"
    const val AOD_COLOUR = Color.LTGRAY

    // Alarm
    const val ALARM_SNOOZE_ACTION = "de.michelinside.glucodatahandler.SNOOZE"
    const val ALARM_STOP_NOTIFICATION_ACTION = "de.michelinside.glucodatahandler.STOP_NOTIFICATION"
    const val ALARM_SNOOZE_EXTRA_TIME = "snooze_time"
    const val ALARM_SNOOZE_EXTRA_NOTIFY_ID = "notification_id"
    const val ALARM_SNOOZE_EXTRA_START_APP = "start_application"
    const val ALARM_TYPE_EXTRA = "alarm_type"

    const val SHARED_PREF_WEAR_LAST_ALARM_STATE = "wear_last_alarm_state"

    const val SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND = "use_alarm_sound"
    const val SHARED_PREF_NOTIFICATION_VIBRATE = "alarm_vibrate_only"
    const val SHARED_PREF_ALARM_START_DELAY = "alarm_start_delay"
    const val SHARED_PREF_ALARM_START_DELAY_STRING = "alarm_start_delay_string"
    const val SHARED_PREF_NOTIFICATION_AUTO_CLOSE = "auto_close_notification"
    const val SHARED_PREF_ALARM_FORCE_VERY_LOW = "alarm_force_very_low"

    const val SHARED_PREF_ALARM_NOTIFICATION_ENABLED = "alarm_notifications_enabled"
    const val SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED = "alarm_fullscreen_notification_enabled"
    const val SHARED_PREF_ALARM_FULLSCREEN_DISMISS_KEYGUARD = "alarm_fullscreen_dismiss_keyguard"
    const val SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION = "alarm_snooze_on_notification"
    const val SHARED_PREF_ALARM_SNOOZE_NOTIFICATION_BUTTONS = "alarm_snooze_notification_buttons"
    const val SHARED_PREF_ALARM_FORCE_SOUND = "alarm_force_sound"
    const val SHARED_PREF_ALARM_FORCE_VIBRATION = "alarm_force_vibration"
    const val SHARED_PREF_ALARM_INACTIVE_ENABLED = "alarm_inactive_enabled"
    const val SHARED_PREF_ALARM_INACTIVE_START_TIME = "alarm_inactive_start_time"
    const val SHARED_PREF_ALARM_INACTIVE_END_TIME = "alarm_inactive_end_time"
    const val SHARED_PREF_ALARM_INACTIVE_WEEKDAYS = "alarm_inactive_weekdays"    
    const val SHARED_PREF_ENABLE_ALARM_ICON_TOGGLE = "enable_alarm_icon_toggle"

    const val SHARED_PREF_NO_ALARM_NOTIFICATION_WEAR_CONNECTED = "alarm_no_notification_wear_connected"
    const val SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED = "alarm_no_notification_auto_connected"
    const val SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED = "alarm_wear_no_popup_phone_connected"


    const val SHARED_PREF_ALARM_SUFFIX_ENABLED = "_enabled"
    const val SHARED_PREF_ALARM_SUFFIX_INTERVAL = "_interval"
    const val SHARED_PREF_ALARM_SUFFIX_USE_CUSTOM_SOUND = "_use_custom_sound"
    const val SHARED_PREF_ALARM_SUFFIX_CUSTOM_SOUND = "_custom_sound"
    const val SHARED_PREF_ALARM_SUFFIX_VIBRATE_PATTERN = "_vibrate_pattern"
    const val SHARED_PREF_ALARM_SUFFIX_VIBRATE_AMPLITUDE = "_vibrate_amplitude"
    const val SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY = "_sound_delay"
    const val SHARED_PREF_ALARM_SUFFIX_SOUND_LEVEL = "_sound_level"
    const val SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED = "_inactive_enabled"
    const val SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME = "_inactive_start_time"
    const val SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME = "_inactive_end_time"
    const val SHARED_PREF_ALARM_SUFFIX_INACTIVE_WEEKDAYS = "_inactive_weekdays"
    const val SHARED_PREF_ALARM_SUFFIX_TEST = "_test"
    const val SHARED_PREF_ALARM_SUFFIX_SAVE_SOUND = "_save_sound"
    const val SHARED_PREF_ALARM_SUFFIX_REPEAT = "_repeat"
    const val SHARED_PREF_ALARM_SUFFIX_REPEAT_UNTIL_CLOSE = "_repeat_until_close"
    const val SHARED_PREF_ALARM_SUFFIX_DELTA = "_delta"
    const val SHARED_PREF_ALARM_SUFFIX_OCCURRENCE_COUNT = "_occurrence_count"
    const val SHARED_PREF_ALARM_SUFFIX_BORDER = "_border"

    const val SHARED_PREF_ALARM_VERY_LOW = "alarm_very_low"
    const val SHARED_PREF_ALARM_LOW = "alarm_low"
    const val SHARED_PREF_ALARM_HIGH = "alarm_high"
    const val SHARED_PREF_ALARM_VERY_HIGH = "alarm_very_high"
    const val SHARED_PREF_ALARM_OBSOLETE = "alarm_obsolete"
    const val SHARED_PREF_ALARM_RISING_FAST = "alarm_rising_fast"
    const val SHARED_PREF_ALARM_FALLING_FAST = "alarm_falling_fast"

    const val SHARED_PREF_ALARM_TYPE_SETTINGS_CAT = "cat_alarm_settings"

    const val SHARED_PREF_BATTERY_RECEIVER_ENABLED = "battery_receiver_enabled"
    const val SHARED_PREF_SEND_TO_WATCH_INTERVAL = "send_to_watch_interval"
    const val SHARED_PREF_SEND_TO_RECEIVER_INTERVAL = "send_to_receiver_interval"

    const val SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE = "phone_wear_screen_off_update"

    const val SHARED_PREF_DISCLAIMER_SHOWN = "gdh_disclaimer_shown"

    // Android Auto
    const val AA_MEDIA_ICON_STYLE_TREND = "trend"
    const val AA_MEDIA_ICON_STYLE_GLUCOSE_TREND = "glucose_trend"
    const val AA_MEDIA_ICON_STYLE_GLUCOSE = "glucose"
    const val SHARED_PREF_FOREGROUND_SERVICE = "foreground_service"

    const val SHARED_PREF_CAR_NOTIFICATION = "car_notification"
    const val SHARED_PREF_CAR_NOTIFICATION_INTERVAL = "car_notification_interval"   // deprecated as changed to seekbar
    const val SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY = "car_notification_alarm_only"
    const val SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM = "car_notification_interval_num"
    const val SHARED_PREF_CAR_NOTIFICATION_REAPPEAR_INTERVAL = "car_notification_reappear_interval"
    const val SHARED_PREF_CAR_NOTIFICATION_SHOW_IOB_COB = "car_notification_show_iob_cob"

    const val SHARED_PREF_CAR_MEDIA = "car_media"
    const val AA_MEDIA_ICON_STYLE = "aa_media_player_icon_style"
    const val AA_MEDIA_SHOW_IOB_COB = "aa_media_player_show_iob_cob"
    const val AA_MEDIA_PLAYER_SPEAK_VALUES = "aa_media_player_speak_values"
    const val AA_MEDIA_PLAYER_SPEAK_NEW_VALUE = "aa_media_player_speak_new_value"
    const val AA_MEDIA_PLAYER_SPEAK_ALARM_ONLY = "aa_media_player_speak_alarm_only"
    const val AA_MEDIA_PLAYER_SPEAK_INTERVAL = "aa_media_player_speak_interval"
    const val AA_MEDIA_PLAYER_SPEAK_TEST = "aa_media_player_speak_test"
    const val AA_MEDIA_PLAYER_DURATION = "aa_media_player_duration"

    const val PATIENT_NAME = "patient_name"

    // database
    const val DB_MAX_DATA_WEAR_TIME_MS = (24*60*60*1000)  // 24h
    const val DB_MAX_DATA_TIME_MS = (7*24*60*60*1000)  // 7 days
    const val SHARED_PREF_RESET_DATABASE = "reset_db"
    const val SHARED_PREF_USE_RATE_CALCULATION = "rate_calculation"

    // graph
    const val GRAPH_ID = "graph_id"
    const val SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION = "graph_duration_wear_complication"
    const val SHARED_PREF_GRAPH_DURATION_PHONE_MAIN = "graph_duration_phone_main"
    const val SHARED_PREF_GRAPH_TRANSPARENCY_PHONE_MAIN = "graph_transparency_phone_main"
    const val SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET = "graph_duration_phone_widget"
    const val SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET = "graph_show_axis_phone_widget"
    const val SHARED_PREF_GRAPH_DURATION_PHONE_NOTIFICATION = "graph_duration_phone_notification"

}
