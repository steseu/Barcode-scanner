package com.barcodebridge.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.barcodebridge.app.data.local.dao.ScanDao
import com.barcodebridge.app.data.local.dao.SessionDao
import com.barcodebridge.app.data.local.entity.ScanEntity
import com.barcodebridge.app.data.local.entity.SessionEntity

@Database(
    entities = [ScanEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "scan_history.db"
    }
}
