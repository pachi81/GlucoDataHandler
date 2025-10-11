package de.michelinside.glucodatahandler.common.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log")
class LogEntry (
    val priority: Int,
    val tag: String,
    val msg: String,
    val forUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val pid: Int = android.os.Process.myPid(),
    val tid: Int = android.os.Process.myTid(),
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)