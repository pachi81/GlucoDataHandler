package de.michelinside.glucodatahandler.complications

import androidx.wear.watchface.complications.data.PlainComplicationText
import de.michelinside.glucodatahandler.BgValueComplicationService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData

abstract class IobCobComplicationsBase : BgValueComplicationService() {
    fun IobText(withMarker: Boolean): PlainComplicationText {
        if (withMarker)
            return plainText("I:"+ReceiveData.iob.toString())
        return plainText(ReceiveData.iob.toString())
    }
    fun CobText(withMarker: Boolean): PlainComplicationText {
        if (withMarker)
            return plainText("C:"+ReceiveData.cob.toString())
        return plainText(ReceiveData.cob.toString())
    }
    fun IobTitle(): PlainComplicationText {
        return plainText(applicationContext.getText(R.string.info_label_iob))
    }
    fun CobTitle(): PlainComplicationText {
        return plainText(applicationContext.getText(R.string.info_label_cob))
    }
}

class IobCobComplication: IobCobComplicationsBase() {
    override fun getText(): PlainComplicationText = IobText(true)
    override fun getTitle(): PlainComplicationText = CobText(true)
}

class IobComplication: IobCobComplicationsBase() {
    override fun getText(): PlainComplicationText = IobText(false)
    override fun getTitle(): PlainComplicationText = IobTitle()
}

class CobComplication: IobCobComplicationsBase() {
    override fun getText(): PlainComplicationText = CobText(false)
    override fun getTitle(): PlainComplicationText = CobTitle()
}