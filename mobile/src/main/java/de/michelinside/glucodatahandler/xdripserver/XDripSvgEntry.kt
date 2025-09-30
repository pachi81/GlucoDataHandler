package de.michelinside.glucodatahandler.xdripserver

import kotlinx.serialization.Serializable

@Serializable
data class XDripSvgEntry(
    val _id: String,
    val device: String,
    val dateString: String,
    val sysTime: String,
    val date: Long,
    val sgv: Int,
    val delta: Double,
    val direction: String,
    val noise: Int,
    val filtered: Int,
    val unfiltered: Int,
    val rssi: Int,
    val type: String,
    val units_hint: String? = null
)