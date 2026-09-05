package com.barcodebridge.app.data.repository

import com.barcodebridge.app.data.local.dao.SessionDao
import com.barcodebridge.app.data.local.entity.SessionEntity
import com.barcodebridge.app.domain.model.ScanSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {

    override fun observeAll(): Flow<List<ScanSession>> =
        sessionDao.observeAll().map { entities -> entities.map(::toDomain) }

    override suspend fun getById(id: Long): ScanSession? = sessionDao.getById(id)?.let(::toDomain)

    override suspend fun create(name: String): Long = sessionDao.insert(
        SessionEntity(name = name, createdAtMillis = Clock.System.now().toEpochMilliseconds())
    )

    override suspend fun rename(id: Long, newName: String) {
        val existing = sessionDao.getById(id) ?: return
        sessionDao.update(existing.copy(name = newName))
    }

    override suspend fun delete(session: ScanSession) = sessionDao.delete(
        SessionEntity(id = session.id, name = session.name, createdAtMillis = session.createdAt.toEpochMilliseconds())
    )

    private fun toDomain(entity: SessionEntity): ScanSession = ScanSession(
        id = entity.id,
        name = entity.name,
        createdAt = Instant.fromEpochMilliseconds(entity.createdAtMillis),
    )
}
