package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.ReceiveData

open class RangeValueComplication: BgValueComplicationService(ComplicationType.RANGED_VALUE) {

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())
        return RangedValueComplicationData.Builder(
            value = ReceiveData.rawValue.toFloat(),
            min = 40F,
            max = maxOf(300F, ReceiveData.rawValue.toFloat()),
            contentDescription = descriptionText()
        )
            .setText(glucoseText())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
}

class RangeValueWithIconComplication: RangeValueComplication() {
    override fun getIcon(): MonochromaticImage? = arrowIcon()
}