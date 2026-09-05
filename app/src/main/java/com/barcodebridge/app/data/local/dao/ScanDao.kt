package com.barcodebridge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.barcodebridge.app.data.local.entity.ScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Insert
    suspend fun insertAll(scans: List<ScanEntity>): List<Long>

    @Update
    suspend fun update(scan: ScanEntity)

    @Delete
    suspend fun delete(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): ScanEntity?

    /**
     * Single flexible query backing search + format filter + date-range filter.
     * A null bind parameter disables that predicate (SQLite short-circuits the
     * `IS NULL OR` branch), so the UI can combine any subset of filters.
     */
    @Query(
        """
        SELECT * FROM scans
        WHERE (:query IS NULL OR inhalt LIKE '%' || :query || '%' OR notiz LIKE '%' || :query || '%')
          AND (:format IS NULL OR format = :format)
          AND (:sessionId IS NULL OR sessionId = :sessionId)
          AND (:startMillis IS NULL OR zeitstempel >= :startMillis)
          AND (:endMillis IS NULL OR zeitstempel <= :endMillis)
        ORDER BY zeitstempel DESC
        """
    )
    fun observeFiltered(
        query: String?,
        format: String?,
        sessionId: Long?,
        startMillis: Long?,
        endMillis: Long?,
    ): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY zeitstempel DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE sessionId = :sessionId ORDER BY zeitstempel DESC")
    fun observeBySession(sessionId: Long): Flow<List<ScanEntity>>

    @Query(
        """
        SELECT * FROM scans
        WHERE sessionId IS :sessionId
        ORDER BY zeitstempel DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentForDuplicateCheck(sessionId: Long?, limit: Int = 5): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ScanEntity>
}
