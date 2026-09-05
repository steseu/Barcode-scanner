package com.barcodebridge.app.data.repository

import com.barcodebridge.app.domain.model.ScanSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeAll(): Flow<List<ScanSession>>
    suspend fun getById(id: Long): ScanSession?
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, newName: String)
    suspend fun delete(session: ScanSession)
}
