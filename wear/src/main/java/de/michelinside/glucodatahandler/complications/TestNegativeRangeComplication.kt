package de.michelinside.glucodatahandler.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData

class TestNegativeRangeComplication: BgValueComplicationService() {
    override fun getRangeValueComplicationData(): ComplicationData {
        return RangedValueComplicationData.Builder(
            value = -1F,
            min = 0F,
            max = 4F,
            contentDescription = descriptionText()
        )
            .setText(plainText("Test"))
            .setTitle(plainText("-1"))
            .build()
    }
}