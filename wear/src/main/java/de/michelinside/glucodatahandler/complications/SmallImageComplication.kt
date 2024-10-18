package de.michelinside.glucodatahandler

import android.graphics.Color
import android.graphics.drawable.Icon
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
            photoImage = BitmapUtils.getGlucoseTrendIcon(ReceiveData.getGlucoseColor(), 500, 500),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}

class IconGlucoseWithTrendComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getGlucoseTrendIcon(Color.WHITE),
            type = SmallImageType.PHOTO
        ).setAmbientImage(BitmapUtils.getGlucoseTrendIcon(Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = BitmapUtils.getGlucoseTrendIcon(Color.WHITE)
        ).setAmbientImage(BitmapUtils.getGlucoseTrendIcon(Color.WHITE)).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}

class SmallImageGlucoseWithTrendSmallComplication: BgValueComplicationService() {

    override fun getImage(): SmallImage = getGlucoseTrendImage(true)

    override fun getLargeImageComplicationData(): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = BitmapUtils.getGlucoseTrendIcon(ReceiveData.getGlucoseColor(), 500, 500, true),
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

class DeltaImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getDeltaAsIcon(color = ReceiveData.getGlucoseColor(), roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(BitmapUtils.getDeltaAsIcon(color = Color.WHITE))
            .build()
    }
}

open class TimeImageComplication: BgValueComplicationService() {
    protected fun getTimeIcon(color: Int): Icon {
        return Icon.createWithBitmap(BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = color, resizeFactor = 0.9F))
    }
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getTimeIcon(ReceiveData.getGlucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getTimeIcon(Color.WHITE))
            .build()
    }
}

class TimeIconComplication: TimeImageComplication() {

    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getTimeIcon(Color.WHITE),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getTimeIcon(Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = getTimeIcon(Color.WHITE)
        ).setAmbientImage(getTimeIcon(Color.WHITE)).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}

open class SmallImageDeltaWithTimeComplication: BgValueComplicationService() {
    protected fun getDeltaTimeIcon(color: Int): Icon {
        val deltaBitmap = BitmapUtils.getDeltaAsBitmap(color = color, roundTarget = true, height = 50, width = 90, top = true)
        val timeBitmap = BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = color, height = 50, width = 75, bottom = true)
        return Icon.createWithBitmap(BitmapUtils.createComboBitmap(timeBitmap!!, deltaBitmap!!, height = 100, width = 100))
    }

    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getDeltaTimeIcon(ReceiveData.getGlucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getDeltaTimeIcon(Color.WHITE))
            .build()
    }
}

class IconDeltaWithTimeComplication: SmallImageDeltaWithTimeComplication() {
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getDeltaTimeIcon(Color.WHITE),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getDeltaTimeIcon(Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(): ComplicationData {
        val imageIcon = MonochromaticImage.Builder(
            image = getDeltaTimeIcon(Color.WHITE)
        ).setAmbientImage(getDeltaTimeIcon(Color.WHITE)).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction())
            .build()
    }
}