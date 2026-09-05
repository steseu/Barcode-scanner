package com.barcodebridge.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.barcodebridge.app.data.local.entity.ScanEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Persistence half of the scan flow: a successful detection is written by
 * [com.barcodebridge.app.data.repository.ScanRepositoryImpl] as a [ScanEntity]
 * and must immediately reappear through the same DAO queries the History
 * screen observes. Runs against a real SQLite database (in-memory), which is
 * why it lives in androidTest rather than as a JVM unit test.
 */
@RunWith(AndroidJUnit4::class)
class ScanDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ScanDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.scanDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertedScanIsReturnedByObserveAll() = runBlocking {
        val id = dao.insert(ScanEntity(content = "4006381333931", format = "EAN_13", timestampMillis = 1_000L))

        val all = dao.observeAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().id).isEqualTo(id)
        assertThat(all.first().content).isEqualTo("4006381333931")
    }

    @Test
    fun filteringByFormatOnlyReturnsMatchingScans() = runBlocking {
        dao.insert(ScanEntity(content = "111", format = "EAN_13", timestampMillis = 1_000L))
        dao.insert(ScanEntity(content = "222", format = "QR_CODE", timestampMillis = 2_000L))

        val filtered = dao.observeFiltered(
            query = null, format = "QR_CODE", sessionId = null, startMillis = null, endMillis = null,
        ).first()

        assertThat(filtered).hasSize(1)
        assertThat(filtered.first().content).isEqualTo("222")
    }

    @Test
    fun searchQueryMatchesContentAndNote() = runBlocking {
        dao.insert(ScanEntity(content = "apple-123", format = "QR_CODE", timestampMillis = 1_000L, note = ""))
        dao.insert(ScanEntity(content = "999", format = "QR_CODE", timestampMillis = 2_000L, note = "apple crate"))
        dao.insert(ScanEntity(content = "banana", format = "QR_CODE", timestampMillis = 3_000L))

        val results = dao.observeFiltered(
            query = "apple", format = null, sessionId = null, startMillis = null, endMillis = null,
        ).first()

        assertThat(results).hasSize(2)
    }

    @Test
    fun deleteByIdsRemovesOnlyTargetedScans() = runBlocking {
        val id1 = dao.insert(ScanEntity(content = "a", format = "QR_CODE", timestampMillis = 1_000L))
        val id2 = dao.insert(ScanEntity(content = "b", format = "QR_CODE", timestampMillis = 2_000L))

        dao.deleteByIds(listOf(id1))

        val remaining = dao.observeAll().first()
        assertThat(remaining.map { it.id }).containsExactly(id2)
    }
}
