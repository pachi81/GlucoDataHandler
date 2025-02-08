package de.michelinside.glucodatahandler.common.database
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_values")
class GlucoseValue (
    @PrimaryKey val timestamp: Long,
    var value: Int
)