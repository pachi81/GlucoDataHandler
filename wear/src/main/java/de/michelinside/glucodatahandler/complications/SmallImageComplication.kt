package de.michelinside.glucodatahandler

import android.graphics.Color
import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.BitmapUtils


class SmallTrendImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = arrowImage()
}
class SmallTrendSmallImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = arrowImage(true)
}

class TrendIconComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
}

class SmallValueImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = glucoseImage()
}
class SmallValueSmallImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = glucoseImage(true)
}

class SmallImageGlucoseWithTrendComplication: BgValueComplicationService() {

    override fun getImage(): SmallImage = getGlucoseTrendImage()

    override fun getLargeImageComplicationData(): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = BitmapUtils.getGlucoseTrendIcon(ReceiveData.getClucoseColor(), 500, 500),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}
class SmallImageGlucoseWithTrendSmallComplication: BgValueComplicationService() {

    override fun getImage(): SmallImage = getGlucoseTrendImage(true)

    override fun getLargeImageComplicationData(): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = BitmapUtils.getGlucoseTrendIcon(ReceiveData.getClucoseColor(), 500, 500, true),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}

class ValueIconComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getGlucoseAsIcon(color = Color.WHITE, roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(BitmapUtils.getGlucoseAsIcon(color = Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = BitmapUtils.getGlucoseAsIcon(color = Color.WHITE)
        ).setAmbientImage(BitmapUtils.getGlucoseAsIcon(color = Color.WHITE)).build()

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
            image = BitmapUtils.getDeltaAsIcon(color = Color.WHITE, roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(BitmapUtils.getDeltaAsIcon(color = Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = BitmapUtils.getDeltaAsIcon(color = Color.WHITE)
        ).setAmbientImage(BitmapUtils.getDeltaAsIcon(color = Color.WHITE)).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}