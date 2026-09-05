package com.barcodebridge.app.data.repository

import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.domain.model.ScanRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

data class ScanFilter(
    val query: String? = null,
    val format: BarcodeFormat? = null,
    val sessionId: Long? = null,
    val startInstant: Instant? = null,
    val endInstant: Instant? = null,
)

interface ScanRepository {
    fun observeScans(filter: ScanFilter = ScanFilter()): Flow<List<ScanRecord>>
    fun observeAll(): Flow<List<ScanRecord>>
    suspend fun getById(id: Long): ScanRecord?
    suspend fun getByIds(ids: List<Long>): List<ScanRecord>

    /** Inserts a new scan and returns its generated id. */
    suspend fun insert(record: ScanRecord): Long
    suspend fun update(record: ScanRecord)
    suspend fun delete(record: ScanRecord)
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Looks at the most recently inserted scans (within the same session, if
     * any) to decide whether [content]/[format] is a duplicate scanned again
     * inside [withinWindow] of the last identical scan. Used by continuous
     * scan mode to suppress accidental re-reads of the same barcode.
     */
    suspend fun isDuplicate(
        content: String,
        format: BarcodeFormat,
        sessionId: Long?,
        now: Instant,
        withinWindow: kotlin.time.Duration,
    ): Boolean
}
