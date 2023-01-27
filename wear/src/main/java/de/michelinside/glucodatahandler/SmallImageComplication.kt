package de.michelinside.glucodatahandler

import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData


class SmallTrendImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = arrowImage()
}

class TrendIconComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class SmallValueImageComplication: BgValueComplicationService() {
    private fun getColoredIcon(): Icon {
        val icon = getGlucoseAsIcon()
        icon.setTint(ReceiveData.getClucoseColor())
        icon.setTintMode(PorterDuff.Mode.SRC_IN)
        return icon
    }

    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getColoredIcon(),
            type = SmallImageType.ICON
        )
            .setAmbientImage(getGlucoseAsIcon())
            .build()
    }
}

class ValueIconComplication: BgValueComplicationService() {
    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = getGlucoseAsIcon()
        ).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}