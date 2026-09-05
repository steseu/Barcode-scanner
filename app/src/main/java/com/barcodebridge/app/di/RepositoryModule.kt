package com.barcodebridge.app.di

import com.barcodebridge.app.data.repository.ScanRepository
import com.barcodebridge.app.data.repository.ScanRepositoryImpl
import com.barcodebridge.app.data.repository.SessionRepository
import com.barcodebridge.app.data.repository.SessionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
