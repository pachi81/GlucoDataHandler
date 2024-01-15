package de.michelinside.glucodatahandler.complications

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import de.michelinside.glucodatahandler.BgValueComplicationService
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.ReceiveData

abstract class IobCobComplicationsBase : BgValueComplicationService() {
    fun iobText(withMarker: Boolean): PlainComplicationText {
        if (withMarker)
            return plainText("💉"+ReceiveData.iobString)
        return plainText(ReceiveData.iobString)
    }
    fun cobText(withMarker: Boolean): PlainComplicationText {
        if (withMarker)
            return plainText("🍔"+ReceiveData.cobString)
        return plainText(ReceiveData.cobString)
    }

    fun iobIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_injection)
        ).build()

    fun cobIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            image = Icon.createWithResource(this, R.drawable.icon_burger)
        ).build()
}

class IobCobComplication: IobCobComplicationsBase() {
    override fun getText(): PlainComplicationText = iobText(true)
    override fun getTitle(): PlainComplicationText = cobText(true)
}

class IobComplication: IobCobComplicationsBase() {
    override fun getText(): PlainComplicationText = iobText(false)
    override fun getIcon(): MonochromaticImage? = iobIcon()
}

class CobComplication: IobCobComplicationsBase() {
    override fun getText(): PlainComplicationText = cobText(false)
    override fun getIcon(): MonochromaticImage? = cobIcon()
}