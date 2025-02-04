package de.michelinside.glucodatahandler.common.database

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.room.Room
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.InternalActionReceiver
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object dbAccess {
    private val LOG_ID = "GDH.dbAccess"
    private var database: Database? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun init(context: Context) {
        Log.v(LOG_ID, "init")
        try {
            database = Room.databaseBuilder(
                context.applicationContext,
                Database::class.java,
                "gdh_database"
            ).build()
            cleanUpOldData()
            PackageUtils.registerReceiver(context, InternalActionReceiver(), IntentFilter(Intent.ACTION_DATE_CHANGED))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.toString() + ": " + exc.stackTraceToString() )
        }
    }

    fun getGlucoseValues(minTime: Long = 0L): List<GlucoseValue> = runBlocking {
        scope.async {
            if(database != null) {
                Log.v(LOG_ID, "getGlucoseValues - minTime: ${Utils.getUiTimeStamp(minTime)}")
                database!!.glucoseValuesDao().getValuesByTime(minTime)
            } else {
                Log.e(LOG_ID, "getGlucoseValues - database is null")
                emptyList()
            }
        }.await()
    }

    fun getLiveValues(): Flow<List<GlucoseValue>> {
        return database!!.glucoseValuesDao().getLiveValues()
    }

    fun getLiveValuesByStartTime(minTime: Long): Flow<List<GlucoseValue>> {
            return database!!.glucoseValuesDao().getLiveValuesByStartTime(minTime)
    }

    fun getLiveValuesByTimeSpan(hours: Int): Flow<List<GlucoseValue>> {
        if(hours > 0)
            return database!!.glucoseValuesDao().getLiveValuesByTimeSpan(hours)
        return database!!.glucoseValuesDao().getLiveValues()
    }

    fun hasGlucoseValues(minTime: Long = 0L): Boolean = runBlocking {
        scope.async {
            if(database != null) {
                Log.v(LOG_ID, "hasGlucoseValues - minTime: ${Utils.getUiTimeStamp(minTime)}")
                database!!.glucoseValuesDao().getCountByTime(minTime) > 0
            } else {
                false
            }
        }.await()
    }

    fun addGlucoseValue(time: Long, value: Int) {
        if(database != null) {
            scope.launch {
                Log.v(LOG_ID, "Add new value $value at ${Utils.getUiTimeStamp(time)}")
                database!!.glucoseValuesDao().insertValue(GlucoseValue(time, value))
            }
        }
    }

    fun addGlucoseValues(values: List<GlucoseValue>) {
        if(database != null && values.isNotEmpty()) {
            scope.launch {
                Log.v(LOG_ID, "Add ${values.size} values")
                database!!.glucoseValuesDao().insertValues(values)
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.GRAPH_DATA_CHANGED, null)
            }
        }
    }

    fun getFirstLastTimestamp(): Pair<Long, Long> = runBlocking {
        scope.async {
            if(database != null) {
                val first = database!!.glucoseValuesDao().getFirstTimestamp()
                val last = database!!.glucoseValuesDao().getLastTimestamp()
                Pair(first, last)
            } else {
                Pair(0L, 0L)
            }
        }.await()
    }

    fun getMaxValue(minTime: Long = 0L): Int = runBlocking {
        scope.async {
            if(database != null) {
                Log.v(LOG_ID, "getMaxValue - minTime: ${Utils.getUiTimeStamp(minTime)}")
                if(minTime == 0L)
                    database!!.glucoseValuesDao().getMaxValue()
                else
                    database!!.glucoseValuesDao().getMaxValueByTime(minTime)
            } else 0
        }.await()
    }

    fun deleteValues(timestamps: List<Long>) = runBlocking {
        if(database != null) {
            scope.launch {
                Log.d(LOG_ID, "delete - ${timestamps.size} values")
                database!!.glucoseValuesDao().deleteValues(timestamps)
            }
        }
    }

    fun deleteAllValues() = runBlocking {
        scope.launch {
            Log.v(LOG_ID, "deleteAllValues")
            database!!.glucoseValuesDao().deleteAllValues()
        }
    }

    fun deleteOldValues(minTime: Long) {
        scope.launch {
            Log.v(LOG_ID, "deleteOldValues - minTime: ${Utils.getUiTimeStamp(minTime)}")
            database!!.glucoseValuesDao().deleteOldValues(minTime)
        }
    }

    fun cleanUpOldData() {
        deleteOldValues(System.currentTimeMillis()-Constants.DB_MAX_DATA_TIME_MS)
    }
}