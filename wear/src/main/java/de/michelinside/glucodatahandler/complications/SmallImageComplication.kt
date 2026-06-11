package de.michelinside.glucodatahandler

import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.BitmapPool
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.IconBitmapPool


class SmallTrendImageComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage = arrowImage(id)
    override fun getDescription(): String {
        return getDescriptionForContent(trend = true)
    }
}
class SmallTrendSmallImageComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage = arrowImage(id, true)
    override fun getDescription(): String {
        return getDescriptionForContent(trend = true)
    }
}

class TrendIconComplication: BgValueComplicationService() {
    override fun getIcon(id: Int): MonochromaticImage = arrowIcon(id)
    override fun getDescription(): String {
        return getDescriptionForContent(trend = true)
    }
}

class SmallValueImageComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage = glucoseImage(id)
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
    }
}
class SmallValueSmallImageComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage = glucoseImage(id, true)
    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
    }
}

class SmallImageGlucoseWithTrendComplication: BgValueComplicationService() {

    override fun getImage(id: Int): SmallImage = getGlucoseTrendImage(id)

    override fun getLargeImageComplicationData(id: Int): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = BitmapUtils.getGlucoseTrendIcon("SmallImageGlucoseWithTrend_large_$id", ReceiveData.getGlucoseColor(), 500, 500),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction(id))
            .build()
    }

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
    }
}

class IconGlucoseWithTrendComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage {
        val icon = BitmapUtils.getGlucoseTrendIcon("IconGlucoseWithTrendComp_$id", Color.WHITE)
        return  SmallImage.Builder(
            image = icon,
            type = SmallImageType.PHOTO
        ).setAmbientImage(icon)
            .build()
    }

    override fun getIconComplicationData(id: Int): ComplicationData {
        val icon = BitmapUtils.getGlucoseTrendIcon("IconGlucoseWithTrendComp_$id", Color.WHITE)
        val imageIcon = MonochromaticImage.Builder(
            image = icon
        ).setAmbientImage(icon).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction(id))
            .build()
    }

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
    }
}

class SmallImageGlucoseWithTrendSmallComplication: BgValueComplicationService() {

    override fun getImage(id: Int): SmallImage = getGlucoseTrendImage(id, true)

    override fun getLargeImageComplicationData(id: Int): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = BitmapUtils.getGlucoseTrendIcon("SmallImageGlucoseWithTrendSmall_large_$id", ReceiveData.getGlucoseColor(), 500, 500, true),
            contentDescription = descriptionText()
        )
            .setTapAction(getTapAction(id))
            .build()
    }

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true, trend = true)
    }
}

class ValueIconComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getGlucoseAsIcon("ValueIconComp_$id", color = Color.WHITE, roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(BitmapUtils.getGlucoseAsIcon("ValueIconComp_mono_$id", color = Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(id: Int): ComplicationData {
        val icon = BitmapUtils.getGlucoseAsIcon("ValueIconComp_$id", color = Color.WHITE)
        val imageIcon = MonochromaticImage.Builder(
            image = icon
        ).setAmbientImage(icon).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction(id))
            .build()
    }

    override fun getDescription(): String {
        return getDescriptionForContent(glucose = true)
    }
}

class DeltaIconComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getDeltaAsIcon("DeltaIconComp_$id", color = Color.WHITE, roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(BitmapUtils.getDeltaAsIcon("DeltaIconComp_mono_$id", color = Color.WHITE))
            .build()
    }

    override fun getIconComplicationData(id: Int): ComplicationData {
        val icon = BitmapUtils.getDeltaAsIcon("DeltaIconComp_$id", color = Color.WHITE)
        val imageIcon = MonochromaticImage.Builder(
            image = icon
        ).setAmbientImage(icon).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction(id))
            .build()
    }

    override fun getDescription(): String {
        return getDescriptionForContent(delta = true)
    }
}

class DeltaImageComplication: BgValueComplicationService() {
    override fun getImage(id: Int): SmallImage {
        return  SmallImage.Builder(
            image = BitmapUtils.getDeltaAsIcon("DeltaImageComp_$id", color = ReceiveData.getGlucoseColor(), roundTarget = true),
            type = SmallImageType.PHOTO
        ).setAmbientImage(BitmapUtils.getDeltaAsIcon("DeltaImageComp_mono_$id", color = Color.WHITE))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true)
    }
}

open class TimeImageComplication: TimeComplicationBase() {
    protected fun getTimeIcon(id: Int, color: Int): Icon {
        return IconBitmapPool.createIcon("TimeImageComp_${color}_$id", BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = color, resizeFactor = 0.9F))
    }
    override fun getImage(id: Int): SmallImage {
        return  SmallImage.Builder(
            image = getTimeIcon(id, ReceiveData.getGlucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getTimeIcon(id, Color.WHITE))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(time = true)
    }
}

class TimeIconComplication: TimeImageComplication() {

    override fun getImage(id: Int): SmallImage {
        val icon = getTimeIcon(id, Color.WHITE)
        return  SmallImage.Builder(
            image = icon,
            type = SmallImageType.PHOTO
        ).setAmbientImage(icon)
            .build()
    }

    override fun getIconComplicationData(id: Int): ComplicationData {
        val icon = getTimeIcon(id, Color.WHITE)
        val imageIcon = MonochromaticImage.Builder(
            image = icon
        ).setAmbientImage(icon).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction(id))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(time = true)
    }
}

open class SmallImageDeltaWithTimeComplication: TimeComplicationBase() {
    protected fun getDeltaTimeIcon(id: Int, color: Int): Icon {
        val deltaBitmap = BitmapUtils.getDeltaAsBitmap(color = color, roundTarget = true, height = 45, width = 90, top = true)
        val timeBitmap = BitmapUtils.textToBitmap(ReceiveData.getElapsedTimeMinuteAsString(this, true), color = color, height = 45, width = 75, bottom = true)
        val icon = IconBitmapPool.createIcon("SmallImageDeltaWithTimeComp_${color}_$id", BitmapUtils.createComboBitmap(timeBitmap!!, deltaBitmap!!, height = 100, width = 100))
        BitmapPool.returnBitmap(deltaBitmap)
        BitmapPool.returnBitmap(timeBitmap)
        return icon
    }

    override fun getImage(id: Int): SmallImage {
        return  SmallImage.Builder(
            image = getDeltaTimeIcon(id, ReceiveData.getGlucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getDeltaTimeIcon(id, Color.WHITE))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true, time = true)
    }
}

class IconDeltaWithTimeComplication: SmallImageDeltaWithTimeComplication() {
    override fun getImage(id: Int): SmallImage {
        val icon = getDeltaTimeIcon(id, Color.WHITE)
        return  SmallImage.Builder(
            image = icon,
            type = SmallImageType.PHOTO
        ).setAmbientImage(icon)
            .build()
    }

    override fun getIconComplicationData(id: Int): ComplicationData {
        val icon = getDeltaTimeIcon(id, Color.WHITE)
        val imageIcon = MonochromaticImage.Builder(
            image = icon
        ).setAmbientImage(icon).build()

        return MonochromaticImageComplicationData.Builder(
            imageIcon,
            descriptionText()
        )
            .setTapAction(getTapAction(id))
            .build()
    }
    override fun getDescription(): String {
        return getDescriptionForContent(delta = true, time = true)
    }
}