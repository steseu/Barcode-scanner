package com.barcodebridge.app.domain.model

import kotlinx.datetime.Instant

data class ScanRecord(
    val id: Long = 0,
    val content: String,
    val format: BarcodeFormat,
    val timestamp: Instant,
    val note: String = "",
    val sessionId: Long? = null,
)

data class ScanSession(
    val id: Long = 0,
    val name: String,
    val createdAt: Instant,
)
