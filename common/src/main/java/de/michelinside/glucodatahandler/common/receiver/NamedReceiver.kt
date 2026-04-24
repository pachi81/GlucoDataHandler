package de.michelinside.glucodatahandler.common.receiver

import de.michelinside.glucodatahandler.common.service.ReceiverManager

interface NamedReceiver {
    fun getName(): String

    fun isRegistered(): Boolean {
        return ReceiverManager.isRegistered(this)
    }
}