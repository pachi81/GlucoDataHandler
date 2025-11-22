package de.michelinside.glucodatahandler.common.database

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
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
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job // Added import
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

object dbAccess {
    private val LOG_ID = "GDH.dbAccess"
    private val DATABASE_NAME = "gdh_database"
    private var database: Database? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val active: Boolean get() {
        return database != null
    }

    private val migration_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try {
                Log.i(LOG_ID, "migration from 1 to 2")
                db.execSQL("UPDATE glucose_values SET TIMESTAMP = ((TIMESTAMP / 1000) * 1000)")
            } catch (exc: Exception) {
                Log.e(LOG_ID, "migration exception: $exc")
                db.execSQL("DELETE FROM glucose_values")
            }
        }
    }

    private val migration_2_3 = object : androidx.room.migration.Migration(2, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try {
                Log.i(LOG_ID, "migration from 2 to 4")
                // Correct CREATE TABLE statement matching the LogEntry entity exactly
                db.execSQL("CREATE TABLE `log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `priority` INTEGER NOT NULL, `tag` TEXT NOT NULL, `msg` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `pid` INTEGER NOT NULL, `tid` INTEGER NOT NULL)")
            } catch (exc: Exception) {
                Log.e(LOG_ID, "migration exception: $exc")
            }
        }
    }

    private val migration_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            try {
                Log.i(LOG_ID, "migration from 3 to 4")
                // Re-create table with new structure
                db.execSQL("DROP TABLE IF EXISTS `log`")
                db.execSQL("CREATE TABLE `log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `priority` INTEGER NOT NULL, `tag` TEXT NOT NULL, `msg` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `pid` INTEGER NOT NULL, `tid` INTEGER NOT NULL)")
            } catch (exc: Exception) {
                Log.e(LOG_ID, "migration exception: $exc")
            }
        }
    }

    fun init(context: Context) {
        Log.v(LOG_ID, "init")
        try {
            createDatabase(context.applicationContext, true)
            cleanUpOldData()
            PackageUtils.registerReceiver(context.applicationContext, InternalActionReceiver(), IntentFilter(Intent.ACTION_DATE_CHANGED))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.toString() + ": " + exc.stackTraceToString() )
        }
    }

    private fun createDatabase(context: Context, retryOnError: Boolean) {
        Log.v(LOG_ID, "createDatabase")
        try {
            database = Room.databaseBuilder(
                context.applicationContext,
                Database::class.java,
                DATABASE_NAME
            )
                .addMigrations(migration_1_2, migration_2_3, migration_3_4)
                .build()

        } catch (exc: Exception) {
            Log.e(LOG_ID, "createDatabase exception: " + exc.toString() + ": " + exc.stackTraceToString() )
            if(retryOnError) {
                if(deleteDatabase(context))
                    createDatabase(context, false)
            }
        }
    }

    private fun deleteDatabase(context: Context): Boolean {
        Log.w(LOG_ID, "Attempting to delete and recreate the database. USER DATA WILL BE LOST for $DATABASE_NAME.")
        try {
            val dbPath = context.getDatabasePath(DATABASE_NAME)
            val walPath = File(dbPath.path + "-wal")
            val shmPath = File(dbPath.path + "-shm")

            val deletedMain: Boolean
            val deletedWal: Boolean
            val deletedShm: Boolean

            if (dbPath.exists()) {
                deletedMain = dbPath.delete()
                Log.i(LOG_ID, "Main database file ($DATABASE_NAME) deletion result: $deletedMain")
            } else {
                Log.i(LOG_ID, "Main database file ($DATABASE_NAME) did not exist.")
                deletedMain = true // Consider it "successfully deleted" if it wasn't there
            }

            if (walPath.exists()) {
                deletedWal = walPath.delete()
                Log.i(LOG_ID, "WAL file (${walPath.name}) deletion result: $deletedWal")
            } else {
                Log.i(LOG_ID, "WAL file (${walPath.name}) did not exist.")
                deletedWal = true
            }

            if (shmPath.exists()) {
                deletedShm = shmPath.delete()
                Log.i(LOG_ID, "SHM file (${shmPath.name}) deletion result: $deletedShm")
            } else {
                Log.i(LOG_ID, "SHM file (${shmPath.name}) did not exist.")
                deletedShm = true
            }

            if (deletedMain && deletedWal && deletedShm) {
                Log.i(LOG_ID, "All relevant database files for $DATABASE_NAME deleted (or did not exist). Attempting to recreate.")
                // Try building again
                return true
            } else {
                Log.e(LOG_ID, "Failed to delete all database files. Main: $deletedMain, WAL: $deletedWal, SHM: $deletedShm. Cannot recreate.")
            }
        } catch (deleteException: Exception) {
            Log.e(LOG_ID, "Exception during database deletion/recreation process.", deleteException)
        }
        return false
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


    fun getLastTopNGlucoseValues(count: Int): List<GlucoseValue> = runBlocking {
        if(active) {
            scope.async {
                try {
                    Log.d(LOG_ID, "getTopNGlucoseValues - count: $count")
                    database!!.glucoseValuesDao().getLastTopNValues(count)
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
                }            }.await()
        } else {
            false
        }
    }

    private fun updateTimestamps(values: List<GlucoseValue>): List<GlucoseValue> {
        val updated = mutableListOf<GlucoseValue>()
        val minTime = System.currentTimeMillis()-Constants.DB_MAX_DATA_TIME_MS
        values.forEach { 
            if(it.timestamp > minTime && GlucoDataUtils.isGlucoseValid(it.value)) {
                updated.add(GlucoseValue(GlucoDataUtils.getGlucoseTime(it.timestamp), it.value))
            } else {
                Log.w(LOG_ID, "Invalid value ${it.value} at ${Utils.getUiTimeStamp(it.timestamp)} (${it.timestamp})")
            }
        }
        return updated
    }

    fun addGlucoseValue(time: Long, value: Int) {
        if(active && GlucoDataUtils.isGlucoseValid(value)) {
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
                    Handler(GlucoDataService.context!!.mainLooper).post {
                        if(Utils.getElapsedTimeMinute(values.last().timestamp) < 20 && GlucoDataService.context != null) {
                            ReceiveData.triggerRecalculateDeltaAndTime()
                        }
                        if(values.size > 10) {
                            GlucoseStatistics.reset()  // trigger re-calculation!
                        }
                        // trigger update of db data
                        val extras = Bundle()
                        extras.putLong(Constants.EXTRA_START_TIME, values.first().timestamp)
                        extras.putLong(Constants.EXTRA_END_TIME, values.last().timestamp)
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.DB_DATA_CHANGED, extras)
                        if(!internal) {
                            // trigger dbsync with watch
                            GlucoDataService.sendCommand(Command.REQUEST_DB_SYNC)
                        }
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "addGlucoseValues exception: $exc")
                }
            }
        }
    }

    fun getFirstTimestamp(): Long = runBlocking {
        if (active) {
            scope.async {
                try {
                    database!!.glucoseValuesDao().getFirstTimestamp()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getFirstTimestamp exception: $exc")
                    0L
                }
            }.await()
        } else {
            0L
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
        Log.i(LOG_ID, "Convert ${data.size} values to json")
        val gson = Gson()
        return gson.toJson(data)
    }

    fun addGlucoseValuesFromJson(jsonData: String) {
        val gson = Gson()
        val data = gson.fromJson(jsonData, Array<GlucoseValue>::class.java).toList()
        Log.i(LOG_ID, "${data.size} values received")
        addGlucoseValues(data, true)
    }


    /**********************************************************************************************/
    /**********************************************************************************************/
    /**********************************************************************************************/
    /**********************************************************************************************/
    /**********************************************************************************************/


    fun addLogs(logs: List<LogEntry>): Job? { // Changed to return Job?
        if(active) {
            return scope.launch { // return the Job
                try {
                    database!!.logDao().insertLogs(logs)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "addLogs exception: $exc")
                }
            }
        }
        return null
    }

    fun getLogs(): List<LogEntry> {
        return if(active) {
            runBlocking {
                database!!.logDao().getLogs()
            }
        } else {
            emptyList()
        }
    }

    fun deleteOldLogs(minTime: Long) {
        if(active) {
            scope.launch {
                try {
                    Log.i(LOG_ID, "deleteOldLogs - minTime: ${Utils.getUiTimeStamp(minTime)}")
                    database!!.logDao().deleteOldLogs(minTime)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "deleteOldLogs exception: $exc")
                }
            }
        }
    }

    fun deleteOldDebugLogs(minTime: Long) {
        if(active) {
            scope.launch {
                try {
                    Log.i(LOG_ID, "deleteOldDebugLogs - minTime: ${Utils.getUiTimeStamp(minTime)}")
                    database!!.logDao().deleteOldDebugLogs(minTime)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "deleteOldDebugLogs exception: $exc")
                }
            }
        }
    }

    fun deleteAllLogs() {
        if(active) {
            scope.launch {
                try {
                    Log.i(LOG_ID, "deleteAllLogs")
                    database!!.logDao().clearAndReset()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "deleteAllLogs exception: $exc")
                }
            }
        }
    }

}
