package de.michelinside.glucodatahandler.common.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseValueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertValue(values: GlucoseValue)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertValues(events: List<GlucoseValue>)

    @Query("SELECT * FROM glucose_values ORDER BY timestamp")
    fun getValues(): List<GlucoseValue>

    @Query("SELECT * FROM glucose_values WHERE timestamp >= :minTime ORDER BY timestamp")
    fun getValuesByTime(minTime: Long): List<GlucoseValue>

    @Query("SELECT * FROM glucose_values WHERE timestamp >= :minTime AND timestamp < :maxTime ORDER BY timestamp")
    fun getValuesInRange(minTime: Long, maxTime: Long): List<GlucoseValue>

    @Query("SELECT * FROM glucose_values ORDER BY timestamp")
    fun getLiveValues(): Flow<List<GlucoseValue>>

    @Query("SELECT * FROM glucose_values WHERE timestamp >= strftime('%s', 'now', '-' || :hours || ' hours') * 1000 ORDER BY timestamp")
    fun getLiveValuesByTimeSpan(hours: Int): Flow<List<GlucoseValue>>

    @Query("SELECT * FROM glucose_values WHERE timestamp >= :minTime ORDER BY timestamp")
    fun getLiveValuesByStartTime(minTime: Long): Flow<List<GlucoseValue>>

    @Query("SELECT timestamp from glucose_values ORDER BY timestamp DESC LIMIT 1")
    fun getLastTimestamp(): Long

    @Query("SELECT timestamp from glucose_values ORDER BY timestamp ASC LIMIT 1")
    fun getFirstTimestamp(): Long

    @Query("SELECT COUNT(*) from glucose_values")
    fun getCount(): Int

    @Query("SELECT COUNT(*) from glucose_values WHERE timestamp >= :minTime")
    fun getCountByTime(minTime: Long): Int

    @Query("SELECT MAX(value) FROM glucose_values")
    fun getMaxValue(): Int

    @Query("SELECT MAX(value) FROM glucose_values WHERE timestamp >= :minTime")
    fun getMaxValueByTime(minTime: Long): Int

    @Query("DELETE FROM glucose_values WHERE timestamp = :timestamp")
    fun deleteValue(timestamp: Long)

    @Query("DELETE FROM glucose_values WHERE timestamp IN (:timestamps)")
    fun deleteValues(timestamps: List<Long>)

    @Query("DELETE FROM glucose_values WHERE timestamp < :minTime")
    fun deleteOldValues(minTime: Long)

    @Query("DELETE FROM glucose_values")
    fun deleteAllValues()
}