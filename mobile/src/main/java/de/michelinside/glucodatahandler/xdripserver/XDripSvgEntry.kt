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
    val units_hint: String? = null
)