package de.michelinside.glucodatahandler.tile

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.wear.protolayout.ResourceBuilders
import java.nio.ByteBuffer

// Downscale to the displayed size and encode as RGB_565 (2 bytes/px) to keep the inline payload
// small enough for the Tiles IPC channel (large parcels raise TransactionTooLargeException). The
// tile background is black, so dropping the alpha (transparent -> black) is visually lossless here.
internal fun inlineImage(source: Bitmap, targetW: Int, targetH: Int): ResourceBuilders.ImageResource {
    val scaled = if (source.width != targetW || source.height != targetH)
        source.scale(targetW, targetH) else source
    val rgb565 = scaled.copy(Bitmap.Config.RGB_565, false)
    val buffer = ByteBuffer.allocate(rgb565.byteCount)
    rgb565.copyPixelsToBuffer(buffer)
    return ResourceBuilders.ImageResource.Builder()
        .setInlineResource(
            ResourceBuilders.InlineImageResource.Builder()
                .setData(buffer.array())
                .setWidthPx(rgb565.width)
                .setHeightPx(rgb565.height)
                .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                .build()
        )
        .build()
}
