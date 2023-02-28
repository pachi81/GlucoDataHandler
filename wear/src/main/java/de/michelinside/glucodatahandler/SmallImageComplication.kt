package de.michelinside.glucodatahandler

import androidx.wear.watchface.complications.data.*


class SmallTrendImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = arrowImage()
}

class TrendIconComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class SmallValueImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = glucoseImage()
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