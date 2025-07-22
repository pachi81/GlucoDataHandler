package de.michelinside.glucodatahandler.common.database

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.room.Room
import com.google.gson.Gson
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.InternalActionReceiver
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.GlucoseStatistics
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

    val active: Boolean get() {
        return database != null
    }

    private val migration_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try {
                db.execSQL("UPDATE glucose_values SET TIMESTAMP = ((TIMESTAMP / 1000) * 1000)")
            } catch (exc: Exception) {
                Log.e(LOG_ID, "migration exception: $exc")
                db.execSQL("DELETE FROM glucose_values")
            }
        }
    }

    fun init(context: Context) {
        Log.v(LOG_ID, "init")
        try {
            database = Room.databaseBuilder(
                context.applicationContext,
                Database::class.java,
                "gdh_database"
            )
                .addMigrations(migration_1_2)
                .build()
            cleanUpOldData()
            PackageUtils.registerReceiver(context, InternalActionReceiver(), IntentFilter(Intent.ACTION_DATE_CHANGED))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.toString() + ": " + exc.stackTraceToString() )
        }
    }

    fun getGlucoseValues(minTime: Long = 0L): List<GlucoseValue> = runBlocking {
        if(active) {
            scope.async {
                try {
                    Log.d(LOG_ID, "getGlucoseValues - minTime: ${Utils.getUiTimeStamp(minTime)}")
                    database!!.glucoseValuesDao().getValuesByTime(minTime)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getGlucoseValues exception: $exc")
                    emptyList()
                }
            }.await()
        } else {
            emptyList()
        }
    }

    fun getGlucoseValuesInRange(minTime: Long, maxTime: Long): List<GlucoseValue> = runBlocking {
        if(active) {
            scope.async {
                try {
                    Log.v(LOG_ID, "getGlucoseValuesInRange - minTime: ${Utils.getUiTimeStamp(minTime)}, maxTime: ${Utils.getUiTimeStamp(maxTime)}")
                    database!!.glucoseValuesDao().getValuesInRange(minTime, maxTime)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getGlucoseValuesInRange exception: $exc")
                    emptyList()
                }
            }.await()
        } else {
            emptyList()
        }
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
        if(active) {
            scope.async {
                try {
                    Log.v(LOG_ID, "hasGlucoseValues - minTime: ${Utils.getUiTimeStamp(minTime)}")
                    database!!.glucoseValuesDao().getCountByTime(minTime) > 0
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "hasGlucoseValues exception: $exc")
                    false
                }
            }.await()
        } else {
            false
        }
    }

    private fun updateTimestamps(values: List<GlucoseValue>): List<GlucoseValue> {
        val updated = mutableListOf<GlucoseValue>()
        values.forEach {
            updated.add(GlucoseValue(GlucoDataUtils.getGlucoseTime(it.timestamp), it.value))
        }
        return updated
    }

    fun addGlucoseValue(time: Long, value: Int) {
        if(active) {
            scope.launch {
                try {
                    Log.d(LOG_ID, "Add new value $value at ${Utils.getUiTimeStamp(time)} ($time)")
                    database!!.glucoseValuesDao().insertValue(GlucoseValue(GlucoDataUtils.getGlucoseTime(time), value))
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "addGlucoseValue exception: $exc")
                }
            }
        }
    }

    fun addGlucoseValues(values: List<GlucoseValue>, internal: Boolean = false) {
        if(active && values.isNotEmpty()) {
            scope.launch {
                try {
                    Log.d(LOG_ID, "Add ${values.size} values from ${values.first().timestamp} to ${values.last().timestamp}")
                    database!!.glucoseValuesDao().insertValues(updateTimestamps(values))
                    if(Utils.getElapsedTimeMinute(values.last().timestamp) < 20 && GlucoDataService.context != null) {
                        Handler(GlucoDataService.context!!.mainLooper).post {
                            ReceiveData.triggerRecalculateDeltaAndTime()
                        }
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "addGlucoseValues exception: $exc")
                }
            }
            if(values.size > 10) {
                GlucoseStatistics.reset()  // trigger re-calculation!
            }
            // trigger update of db data
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.DB_DATA_CHANGED, null)
            if(!internal) {
                // trigger dbsync with watch
                GlucoDataService.sendCommand(Command.REQUEST_DB_SYNC)
            }
        }
    }

    fun getFirstLastTimestamp(): Pair<Long, Long> = runBlocking {
        if(active) {
            scope.async {
                try {
                    val first = database!!.glucoseValuesDao().getFirstTimestamp()
                    val last = database!!.glucoseValuesDao().getLastTimestamp()
                    Pair(first, last)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getFirstLastTimestamp exception: $exc")
                    Pair(0L, 0L)
                }
            }.await()
        } else {
            Pair(0L, 0L)
        }
    }

    fun getMaxValue(minTime: Long = 0L): Int = runBlocking {
        if(active) {
            scope.async {
                try {
                    Log.v(LOG_ID, "getMaxValue - minTime: ${Utils.getUiTimeStamp(minTime)}")
                    if(minTime == 0L)
                        database!!.glucoseValuesDao().getMaxValue()
                    else
                        database!!.glucoseValuesDao().getMaxValueByTime(minTime)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getMaxValue exception: $exc")
                    0
                }
            }.await()
        } else 0
    }

    fun getAverageValue(minTime: Long = 0L): Float = runBlocking {
        if(active) {
            scope.async {
                try {
                    Log.v(LOG_ID, "getAverageValue - minTime: ${Utils.getUiTimeStamp(minTime)}")
                    database!!.glucoseValuesDao().getAverageValue(minTime)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getAverageValue exception: $exc")
                    Float.NaN
                }
            }.await()
        } else Float.NaN
    }

    fun getValuesInRangeCount(minTime: Long, minVal: Int, maxVal: Int): Int = runBlocking {
        if(active) {
            scope.async {
                try {
                    Log.v(LOG_ID, "getValuesInRangeCount - minTime: ${Utils.getUiTimeStamp(minTime)} - from $minVal to $maxVal")
                    database!!.glucoseValuesDao().getValuesInRangeCount(minTime, minVal, maxVal)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getValuesInRangeCount exception: $exc")
                    0
                }
            }.await()
        } else 0
    }

    fun deleteValues(timestamps: List<Long>) = runBlocking {
        if(active) {
            GlucoseStatistics.reset()  // trigger re-calculation!
            scope.launch {
                try {
                    Log.i(LOG_ID, "delete - ${timestamps.size} values")
                    database!!.glucoseValuesDao().deleteValues(timestamps)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "deleteValues exception: $exc")
                }
            }
        }
    }

    fun deleteAllValues() = runBlocking {
        if(active) {
            GlucoseStatistics.reset()  // trigger re-calculation!
            scope.launch {
                try {
                    Log.i(LOG_ID, "deleteAllValues")
                    database!!.glucoseValuesDao().deleteAllValues()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "deleteAllValues exception: $exc")
                }
            }
        }
    }

    fun deleteOldValues(minTime: Long) {
        if(active) {
            GlucoseStatistics.reset()  // trigger re-calculation!
            scope.launch {
                try {
                    Log.i(LOG_ID, "deleteOldValues - minTime: ${Utils.getUiTimeStamp(minTime)}")
                    database!!.glucoseValuesDao().deleteOldValues(minTime)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "deleteOldValues exception: $exc")
                }
            }
        }
    }

    fun cleanUpOldData() {
        deleteOldValues(System.currentTimeMillis()-Constants.DB_MAX_DATA_TIME_MS)
    }

    fun getGlucoseValuesAsJson(minTime: Long): String {
        val data = getGlucoseValues(minTime)
        val gson = Gson()
        return gson.toJson(data)
    }

    fun addGlucoseValuesFromJson(jsonData: String) {
        val gson = Gson()
        val data = gson.fromJson(jsonData, Array<GlucoseValue>::class.java).toList()
        Log.i(LOG_ID, "${data.size} values received")
        addGlucoseValues(data, true)
    }
}