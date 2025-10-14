package de.michelinside.glucodatahandler.xdripserver

import kotlinx.serialization.Serializable

@Serializable
data class XDripSvgEntry(
    val _id: String? = null,
    val device: String? = null,
    val dateString: String? = null,
    val sysTime: String? = null,
    val date: Long,
    val sgv: Int,
    val delta: Double,
    val direction: String,
    val noise: Int,
    val filtered: Int? = null,
    val unfiltered: Int? = null,
    val rssi: Int? = null,
    val type: String? = null,
    val units_hint: String? = null,
    val sensor_status: String? = null
)

@Serializable
data class PebbleEntry(
    val status: List<PebbleStatus>,
    val bgs: List<PebbleBg>,
    val cals: List<Int>
)

@Serializable
data class PebbleStatus(
    val now: Long
)

@Serializable
data class PebbleBg(
    val sgv: String,
    val trend: Int,
    val direction: String,
    val datetime: Long,
    val bgdelta: String,
    val iob: String
)

@Serializable
data class SettingsEntry(
    val settings: SettingsData
)

@Serializable
data class SettingsData(
    val units: String,
    val thresholds: SettingsThresholds
)

@Serializable
data class SettingsThresholds(
    val bgHigh: Int,
    val bgLow: Int
)