package com.barcodebridge.app.data.repository

import com.barcodebridge.app.data.local.dao.ScanDao
import com.barcodebridge.app.data.local.entity.ScanEntity
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.domain.model.ScanRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

@Singleton
class ScanRepositoryImpl @Inject constructor(
    private val scanDao: ScanDao,
) : ScanRepository {

    override fun observeScans(filter: ScanFilter): Flow<List<ScanRecord>> =
        scanDao.observeFiltered(
            query = filter.query?.takeIf { it.isNotBlank() },
            format = filter.format?.name,
            sessionId = filter.sessionId,
            startMillis = filter.startInstant?.toEpochMilliseconds(),
            endMillis = filter.endInstant?.toEpochMilliseconds(),
        ).map { entities -> entities.map(::toDomain) }

    override fun observeAll(): Flow<List<ScanRecord>> =
        scanDao.observeAll().map { entities -> entities.map(::toDomain) }

    override suspend fun getById(id: Long): ScanRecord? = scanDao.getById(id)?.let(::toDomain)

    override suspend fun getByIds(ids: List<Long>): List<ScanRecord> =
        scanDao.getByIds(ids).map(::toDomain)

    override suspend fun insert(record: ScanRecord): Long = scanDao.insert(toEntity(record))

    override suspend fun update(record: ScanRecord) = scanDao.update(toEntity(record))

    override suspend fun delete(record: ScanRecord) = scanDao.delete(toEntity(record))

    override suspend fun deleteByIds(ids: List<Long>) = scanDao.deleteByIds(ids)

    override suspend fun isDuplicate(
        content: String,
        format: BarcodeFormat,
        sessionId: Long?,
        now: Instant,
        withinWindow: Duration,
    ): Boolean {
        val recent = scanDao.getRecentForDuplicateCheck(sessionId, limit = DUPLICATE_CHECK_WINDOW_SIZE)
        val lastMatch = recent.firstOrNull { it.content == content && it.format == format.name }
            ?: return false
        val elapsed = now.toEpochMilliseconds() - lastMatch.timestampMillis
        return elapsed in 0..withinWindow.inWholeMilliseconds
    }

    private fun toDomain(entity: ScanEntity): ScanRecord = ScanRecord(
        id = entity.id,
        content = entity.content,
        format = runCatching { BarcodeFormat.valueOf(entity.format) }.getOrDefault(BarcodeFormat.UNKNOWN),
        timestamp = Instant.fromEpochMilliseconds(entity.timestampMillis),
        note = entity.note,
        sessionId = entity.sessionId,
    )

    private companion object {
        /** How many recent scans to compare against when suppressing continuous-scan duplicates. */
        const val DUPLICATE_CHECK_WINDOW_SIZE = 5
    }

    private fun toEntity(record: ScanRecord): ScanEntity = ScanEntity(
        id = record.id,
        content = record.content,
        format = record.format.name,
        timestampMillis = record.timestamp.toEpochMilliseconds(),
        note = record.note,
        sessionId = record.sessionId,
    )
}
