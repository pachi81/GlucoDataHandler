package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest

class SmallImageComplication: BgValueComplicationService(ComplicationType.SMALL_IMAGE) {

    override fun getComplicationData(request: ComplicationRequest): ComplicationData {
        return SmallImageComplicationData.Builder (
            smallImage = arrowImage(),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}