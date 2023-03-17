package de.michelinside.glucodatahandler

import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils


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

    private fun getIcon(color: Int, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(Utils.textRateToBitmap(ReceiveData.getClucoseAsString(), ReceiveData.rate, color, ReceiveData.isObsolete(300) && !ReceiveData.isObsolete(),width, height))
    }

    override fun getImage(): SmallImage {
        return  SmallImage.Builder(
            image = getIcon(ReceiveData.getClucoseColor()),
            type = SmallImageType.PHOTO
        ).setAmbientImage(getIcon(Color.WHITE))
            .build()
    }

    override fun getLargeImageComplicationData(): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = getIcon(ReceiveData.getClucoseColor(), 500, 500),
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