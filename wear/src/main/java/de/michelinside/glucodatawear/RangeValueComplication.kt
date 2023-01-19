package de.michelinside.glucodatahandler

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.ReceiveData

open class RangeValueComplication(descrResId: Int = R.string.range_bg_value_content_description): BgValueComplicationService(ComplicationType.RANGED_VALUE) {
    private val descriptionResourceId = descrResId

    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())
        return RangedValueComplicationData.Builder(
            value = ReceiveData.rawValue.toFloat(),
            min = 40F,
            max = maxOf(300F, ReceiveData.rawValue.toFloat()),
            contentDescription = resText(descriptionResourceId)
        )
            .setText(glucoseText())
            .setMonochromaticImage(getIcon())
            .setTapAction(getTapAction())
            .build()
    }
}

class RangeValueWithIconComplication: RangeValueComplication(R.string.range_bg_value_with_arrow_content_description) {
    override fun getIcon(): MonochromaticImage? = arrowIcon()
}