package de.michelinside.glucodatahandler.common.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertLogs(logs: List<LogEntry>)

    @Query("SELECT * FROM log ORDER BY timestamp")
    fun getLogs(): List<LogEntry>

    @Query("DELETE FROM log WHERE timestamp < :minTime")
    fun deleteOldLogs(minTime: Long)

    @Query("DELETE FROM log WHERE timestamp < :minTime AND priority <= 3")
    fun deleteOldDebugLogs(minTime: Long)

    @Query("DELETE FROM log")
    fun deleteAllLogs()


    @Query("DELETE FROM sqlite_sequence WHERE name='log'")
    suspend fun resetAutoIncrement()

    @Transaction
    suspend fun clearAndReset() {
        deleteAllLogs()
        resetAutoIncrement()
    }
}
