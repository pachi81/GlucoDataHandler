package de.michelinside.glucodatahandler.common.database
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GlucoseValue::class], version = 1)
abstract class Database: RoomDatabase() {
    abstract fun glucoseValuesDao(): GlucoseValueDao
}