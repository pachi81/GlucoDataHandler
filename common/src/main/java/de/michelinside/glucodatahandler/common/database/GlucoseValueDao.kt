package de.michelinside.glucodatahandler.common.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class GlucoseValueStatistics(
    val average: Double?,
    val averageSquared: Double?
)

data class DailyGlucoseStatisticsSummary(
    val sampleCount: Long,
    val glucoseSum: Long,
    val glucoseSquaredSum: Long,
    val veryLowCount: Long,
    val lowCount: Long,
    val titrCount: Long,
    val aboveTitrCount: Long,
    val highCount: Long,
    val veryHighCount: Long,
    val customVeryLowCount: Long,
    val customLowCount: Long,
    val customInRangeCount: Long,
    val customHighCount: Long,
    val customVeryHighCount: Long
)

@Dao
interface GlucoseValueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertValue(values: GlucoseValue)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertValues(events: List<GlucoseValue>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDailyStatistics(statistics: DailyGlucoseStatistics)

    @Query("UPDATE glucose_values SET rate = :rate WHERE timestamp = :glucoseTime")
    fun updateRate(glucoseTime: Long, rate: Float)

    @Query("SELECT * FROM glucose_values ORDER BY timestamp")
    fun getValues(): List<GlucoseValue>

    @Query("SELECT * FROM glucose_values where timestamp = :timestamp")
    fun getValue(timestamp: Long): GlucoseValue?

    @Query("SELECT * FROM glucose_values WHERE timestamp >= :minTime ORDER BY timestamp")
    fun getValuesByTime(minTime: Long): List<GlucoseValue>

    @Query("SELECT * FROM glucose_values WHERE timestamp < :maxTime ORDER BY timestamp")
    fun getValuesBefore(maxTime: Long): List<GlucoseValue>

    @Query("SELECT * FROM glucose_values ORDER BY timestamp DESC LIMIT :count")
    fun getLastTopNValues(count: Int): List<GlucoseValue>

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
    fun getFirstTimestamp(): Long?

    @Query("SELECT dayStart FROM daily_glucose_statistics ORDER BY dayStart ASC LIMIT 1")
    fun getFirstDailyStatisticsDay(): Long?

    @Query("SELECT COUNT(*) from glucose_values")
    fun getCount(): Int

    @Query("SELECT COUNT(*) from glucose_values WHERE timestamp >= :minTime")
    fun getCountByTime(minTime: Long): Int

    @Query("SELECT MAX(value) FROM glucose_values")
    fun getMaxValue(): Int

    @Query("SELECT MAX(value) FROM glucose_values WHERE timestamp >= :minTime")
    fun getMaxValueByTime(minTime: Long): Int

    @Query("SELECT AVG(value) FROM glucose_values WHERE timestamp >= :minTime")
    fun getAverageValue(minTime: Long): Float

    @Query("SELECT AVG(value) AS average, AVG(value * value) AS averageSquared FROM glucose_values WHERE timestamp >= :minTime")
    fun getStatistics(minTime: Long): GlucoseValueStatistics

    @Query("SELECT COUNT(*) FROM glucose_values WHERE value >= :minVal AND value <= :maxVal AND timestamp >= :minTime")
    fun getValuesInRangeCount(minTime: Long, minVal: Int, maxVal: Int): Int

    @Query("""
        SELECT
            COALESCE(SUM(sampleCount), 0) AS sampleCount,
            COALESCE(SUM(glucoseSum), 0) AS glucoseSum,
            COALESCE(SUM(glucoseSquaredSum), 0) AS glucoseSquaredSum,
            COALESCE(SUM(veryLowCount), 0) AS veryLowCount,
            COALESCE(SUM(lowCount), 0) AS lowCount,
            COALESCE(SUM(titrCount), 0) AS titrCount,
            COALESCE(SUM(aboveTitrCount), 0) AS aboveTitrCount,
            COALESCE(SUM(highCount), 0) AS highCount,
            COALESCE(SUM(veryHighCount), 0) AS veryHighCount,
            COALESCE(SUM(customVeryLowCount), 0) AS customVeryLowCount,
            COALESCE(SUM(customLowCount), 0) AS customLowCount,
            COALESCE(SUM(customInRangeCount), 0) AS customInRangeCount,
            COALESCE(SUM(customHighCount), 0) AS customHighCount,
            COALESCE(SUM(customVeryHighCount), 0) AS customVeryHighCount
        FROM (
            SELECT sampleCount, glucoseSum, glucoseSquaredSum, veryLowCount, lowCount,
                titrCount, aboveTitrCount, highCount, veryHighCount, customVeryLowCount,
                customLowCount, customInRangeCount, customHighCount, customVeryHighCount
            FROM daily_glucose_statistics WHERE dayStart >= :minTime AND dayStart < :todayStart
            UNION ALL
            SELECT
                COUNT(*) AS sampleCount,
                COALESCE(SUM(value), 0) AS glucoseSum,
                COALESCE(SUM(value * value), 0) AS glucoseSquaredSum,
                SUM(CASE WHEN value < 54 THEN 1 ELSE 0 END) AS veryLowCount,
                SUM(CASE WHEN value BETWEEN 54 AND 69 THEN 1 ELSE 0 END) AS lowCount,
                SUM(CASE WHEN value BETWEEN 70 AND 140 THEN 1 ELSE 0 END) AS titrCount,
                SUM(CASE WHEN value BETWEEN 141 AND 180 THEN 1 ELSE 0 END) AS aboveTitrCount,
                SUM(CASE WHEN value BETWEEN 181 AND 250 THEN 1 ELSE 0 END) AS highCount,
                SUM(CASE WHEN value > 250 THEN 1 ELSE 0 END) AS veryHighCount,
                SUM(CASE WHEN value <= :lowValue THEN 1 ELSE 0 END) AS customVeryLowCount,
                SUM(CASE WHEN value > :lowValue AND value < :targetMinValue THEN 1 ELSE 0 END) AS customLowCount,
                SUM(CASE WHEN value BETWEEN :targetMinValue AND :targetMaxValue THEN 1 ELSE 0 END) AS customInRangeCount,
                SUM(CASE WHEN value > :targetMaxValue AND value < :highValue THEN 1 ELSE 0 END) AS customHighCount,
                SUM(CASE WHEN value >= :highValue THEN 1 ELSE 0 END) AS customVeryHighCount
            FROM glucose_values WHERE timestamp >= :todayStart
        )
    """)
    fun getDailyStatistics(
        minTime: Long,
        todayStart: Long,
        lowValue: Int,
        targetMinValue: Int,
        targetMaxValue: Int,
        highValue: Int
    ): DailyGlucoseStatisticsSummary

    @Query("SELECT * FROM daily_glucose_statistics WHERE dayStart = :dayStart")
    fun getDailyStatisticsForDay(dayStart: Long): DailyGlucoseStatistics?

    @Query("SELECT COUNT(*) FROM daily_glucose_statistics WHERE dayStart >= :minTime")
    fun getDailyStatisticsCount(minTime: Long): Int

    @Query("SELECT * FROM glucose_values WHERE timestamp < :timestamp ORDER BY timestamp DESC LIMIT 1")
    fun getPreviousValue(timestamp: Long): GlucoseValue?

    @Query("DELETE FROM glucose_values WHERE timestamp = :timestamp")
    fun deleteValue(timestamp: Long)

    @Query("DELETE FROM glucose_values WHERE timestamp IN (:timestamps)")
    fun deleteValues(timestamps: List<Long>)

    @Query("DELETE FROM glucose_values WHERE timestamp < :minTime")
    fun deleteOldValues(minTime: Long)

    @Query("DELETE FROM daily_glucose_statistics WHERE dayStart < :minDay")
    fun deleteOldDailyStatistics(minDay: Long)

    @Query("DELETE FROM glucose_values")
    fun deleteAllValues()

    @Query("DELETE FROM daily_glucose_statistics")
    fun deleteAllDailyStatistics()
}