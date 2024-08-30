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

class TimeImageComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        val timeBitmap = BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = ReceiveData.getGlucoseColor(), resizeFactor = 0.9F)
        val timeBitmapMonochrome = BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = Color.WHITE, resizeFactor = 0.9F)
        return  SmallImage.Builder(
            image = Icon.createWithBitmap(timeBitmap),
            type = SmallImageType.PHOTO
        ).setAmbientImage(Icon.createWithBitmap(timeBitmapMonochrome))
            .build()
    }
}

class SmallImageDeltaWithTimeComplication: BgValueComplicationService() {
    override fun getImage(): SmallImage {
        val deltaBitmap = BitmapUtils.getDeltaAsBitmap(color = ReceiveData.getGlucoseColor(), roundTarget = true, height = 50, width = 90, top = true)
        val timeBitmap = BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = ReceiveData.getGlucoseColor(), height = 50, width = 75, bottom = true)
        val comboBitmap = BitmapUtils.createComboBitmap(timeBitmap!!, deltaBitmap!!, height = 100, width = 100)

        val deltaBitmapMonochrome = BitmapUtils.getDeltaAsBitmap(color = Color.WHITE, roundTarget = true, height = 50, width = 90, top = true)
        val timeBitmapMonochrome = BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = Color.WHITE, height = 50, width = 75, bottom = true)
        val comboBitmapMonochrome = BitmapUtils.createComboBitmap(timeBitmapMonochrome!!, deltaBitmapMonochrome!!, height = 100, width = 100)

        return  SmallImage.Builder(
            image = Icon.createWithBitmap(comboBitmap),
            type = SmallImageType.PHOTO
        ).setAmbientImage(Icon.createWithBitmap(comboBitmapMonochrome))
            .build()
    }
}