package de.michelinside.glucodatahandler.common.receiver

import de.michelinside.glucodatahandler.common.GlucoDataService

interface NamedReceiver {
    fun getName(): String

    fun isRegistered(): Boolean {
        return GlucoDataService.isRegistered(this)
    }
}