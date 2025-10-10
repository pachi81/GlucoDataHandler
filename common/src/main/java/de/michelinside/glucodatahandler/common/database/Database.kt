package de.michelinside.glucodatahandler.common.database
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GlucoseValue::class, LogEntry::class], version = 3)
abstract class Database: RoomDatabase() {
    abstract fun glucoseValuesDao(): GlucoseValueDao
    abstract fun logDao(): LogDao
}