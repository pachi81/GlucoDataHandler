package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*

open class LongTextComplication: BgValueComplicationService() {
    override fun getText(): PlainComplicationText {
        return glucoseAndDeltaText()
    }
}