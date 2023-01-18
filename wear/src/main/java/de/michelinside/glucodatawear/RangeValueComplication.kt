package de.michelinside.glucodatahandler

import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import de.michelinside.glucodatahandler.common.ReceiveData

class RangeValueWithTitleComplication: BgValueComplicationService() {
    private val LOG_ID = "GlucoDataHandler.RangeValueWithTitleComplication"
    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())
        if (request.complicationType != ComplicationType.RANGED_VALUE) {
            return null
        }
        val text: ComplicationText = PlainComplicationText.Builder(
            text = ReceiveData.getRateSymbol().toString()
        ).build()
        val title: ComplicationText = PlainComplicationText.Builder(
            text = ReceiveData.getClucoseAsString()
        ).build()
        val contentDescription = PlainComplicationText.Builder(
            text = getText(R.string.range_bg_value_with_arrow_content_description)
        ).build()
        val icon = MonochromaticImage.Builder(
            image = Icon.createWithResource(this, getArrowIcon())
        ).build()
        return RangedValueComplicationData.Builder(
            value = ReceiveData.rawValue.toFloat(),
            min = 20F,
            max = maxOf(300F, ReceiveData.rawValue.toFloat()),
            contentDescription = contentDescription
        )
            .setText(title)
            .setMonochromaticImage(icon)
            .setTapAction(getTapAction())
            .build()
    }
}

class RangeValueOnlyComplication: BgValueComplicationService() {
    private val LOG_ID = "GlucoDataHandler.RangeValueOnlyComplication"
    override fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "getComplicationData called for " + request.complicationType.toString())
        if (request.complicationType != ComplicationType.RANGED_VALUE) {
            return null
        }
        val text: ComplicationText = PlainComplicationText.Builder(
            text = ReceiveData.getRateSymbol().toString()
        ).build()
        val contentDescription = PlainComplicationText.Builder(
            text = getText(R.string.range_bg_value_content_description)
        ).build()
        return RangedValueComplicationData.Builder(
            value = ReceiveData.rawValue.toFloat(),
            min = 20F,
            max = maxOf(300F, ReceiveData.rawValue.toFloat()),
            contentDescription = contentDescription
        )
            .setText(text)
            .setTapAction(getTapAction())
            .build()
    }
}