package de.michelinside.glucodatahandler.tile

import android.content.Context
import android.os.Bundle
import androidx.wear.tiles.TileService
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Log

/**
 * Requests a refresh of the [GlucoseDetailsTileService] whenever new data arrives.
 */
object GlucoseDetailsTileUpdater : NotifierInterface {
    private const val LOG_ID = "GDH.tile.details.updater"

    var updateCount = 0
        private set

    val filter = mutableSetOf(
        NotifySource.MESSAGECLIENT,
        NotifySource.BROADCAST,
        NotifySource.SETTINGS,
        NotifySource.OBSOLETE_VALUE,
        NotifySource.TIME_VALUE,
        NotifySource.SENSOR_AGE_CHANGED
    )

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source $dataSource")
            updateCount++
            TileService.getUpdater(context)
                .requestUpdate(GlucoseDetailsTileService::class.java)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString())
        }
    }
}
