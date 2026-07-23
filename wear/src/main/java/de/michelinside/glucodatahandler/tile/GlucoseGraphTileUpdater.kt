package de.michelinside.glucodatahandler.tile

import android.content.Context
import android.os.Bundle
import androidx.wear.tiles.TileService
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Log

/**
 * Requests a refresh of the [GlucoseGraphTileService] whenever new data arrives.
 *
 * Registered as a notifier in [de.michelinside.glucodatahandler.GlucoDataServiceWear], mirroring
 * [de.michelinside.glucodatahandler.ActiveComplicationHandler]. requestUpdate on a tile that is not
 * currently added to the carousel is a harmless no-op, so no screen-off bookkeeping is needed here.
 */
object GlucoseGraphTileUpdater : NotifierInterface {
    private const val LOG_ID = "GDH.GlucoseGraphTileUpdater"

    // Incremented on every update so the tile's resources version always changes and the renderer
    // never serves a stale cached image.
    var updateCount = 0
        private set

    val filter = mutableSetOf(
        NotifySource.MESSAGECLIENT,
        NotifySource.BROADCAST,
        NotifySource.SETTINGS,
        NotifySource.OBSOLETE_VALUE,
        NotifySource.TIME_VALUE,
        NotifySource.GRAPH_CHANGED
    )

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            updateCount++
            TileService.getUpdater(context)
                .requestUpdate(GlucoseGraphTileService::class.java)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString())
        }
    }
}
