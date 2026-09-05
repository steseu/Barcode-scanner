package com.barcodebridge.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "inhalt")
    val content: String,
    @ColumnInfo(name = "format")
    val format: String,
    @ColumnInfo(name = "zeitstempel")
    val timestampMillis: Long,
    @ColumnInfo(name = "notiz", defaultValue = "")
    val note: String = "",
    @ColumnInfo(name = "sessionId")
    val sessionId: Long? = null,
)
