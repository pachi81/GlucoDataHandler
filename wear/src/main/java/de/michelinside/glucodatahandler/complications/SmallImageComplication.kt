package de.michelinside.glucodatahandler

import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.BitmapPool
import de.michelinside.glucodatahandler.common.utils.BitmapUtils


class SmallTrendImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = arrowImage()
    override fun getDescription(): String {
        return getDescriptionForContent(trend = true)
    }
}
class SmallTrendSmallImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = arrowImage(true)
    override fun getDescription(): String {
        return getDescriptionForContent(trend = true)
    }
}

class TrendIconComplication: BgValueComplicationService() {
    override fun getIcon(): MonochromaticImage = arrowIcon()
    override fun getDescription(): String {
        return getDescriptionForContent(trend = true)
    }
}

class SmallValueImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = glucoseImage()
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
    }
}
class SmallValueSmallImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage = glucoseImage(true)
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
    }
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

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
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

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
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

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
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

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
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

    override fun getDescription(): String {
        return getDescriptionForContent(delta = true)
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
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true)
    }
}

open class TimeImageComplication: TimeComplicationBase() {
    protected fun getTimeIcon(color: Int): Icon {
        return BitmapUtils.createIcon(BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = color, resizeFactor = 0.9F))
    }
    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getTimeIcon(ReceiveData.getGlucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getTimeIcon(Color.WHITE))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(time = true)
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
    override fun getDescription(): String {
        return getDescriptionForContent(time = true)
    }
}

open class SmallImageDeltaWithTimeComplication: TimeComplicationBase() {
    protected fun getDeltaTimeIcon(color: Int): Icon {
        val deltaBitmap = BitmapUtils.getDeltaAsBitmap(color = color, roundTarget = true, height = 45, width = 90, top = true)
        val timeBitmap = BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = color, height = 45, width = 75, bottom = true)
        val icon = BitmapUtils.createIcon(BitmapUtils.createComboBitmap(timeBitmap!!, deltaBitmap!!, height = 100, width = 100))
        BitmapPool.returnBitmap(deltaBitmap)
        BitmapPool.returnBitmap(timeBitmap)
        return icon
    }

    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getDeltaTimeIcon(ReceiveData.getGlucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getDeltaTimeIcon(Color.WHITE))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true, time = true)
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
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true, time = true)
    }
}