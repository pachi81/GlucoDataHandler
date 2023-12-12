package de.michelinside.glucodatahandler

import android.graphics.Color
import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.Utils


class SmallTrendImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = arrowImage()
}

class TrendIconComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class SmallValueImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = glucoseImage()
}

class SmallImageGlucoseWithTrendComplication: BgValueComplicationService() {

    override fun getImage(): SmallImage = getGlucoseTrendImage()

    override fun getLargeImageComplicationData(): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = Utils.getGlucoseTrendIcon(ReceiveData.getClucoseColor(), 500, 500),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}

class ValueIconComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = Utils.getGlucoseAsIcon(color = Color.WHITE, roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(Utils.getGlucoseAsIcon(color = Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = Utils.getGlucoseAsIcon(color = Color.WHITE)
        ).setAmbientImage(Utils.getGlucoseAsIcon(color = Color.WHITE)).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}

class DeltaIconComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = Utils.getDeltaAsIcon(color = Color.WHITE, roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(Utils.getDeltaAsIcon(color = Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = Utils.getDeltaAsIcon(color = Color.WHITE)
        ).setAmbientImage(Utils.getDeltaAsIcon(color = Color.WHITE)).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}