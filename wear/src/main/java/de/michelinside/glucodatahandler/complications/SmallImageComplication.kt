package de.michelinside.glucodatahandler.complications

import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData


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
            photoImage = getGlucoseTrendIcon(ReceiveData.getClucoseColor(), 500, 500),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}

class ValueIconComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getGlucoseAsIcon(forImage = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getGlucoseAsIcon())
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = getGlucoseAsIcon()
        ).setAmbientImage(getGlucoseAsIcon()).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}