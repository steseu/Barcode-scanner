package com.barcodebridge.app.scanner

import android.content.Context
import android.net.Uri
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class ImageScanResult(val content: String, val format: BarcodeFormat)

/** Runs the same on-device ML Kit detector against a still image (gallery pick). */
@Singleton
class ImageFileScanner @Inject constructor() {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(BarcodeFormat.SUPPORTED_MLKIT_FORMATS_MASK)
            .build()
    )

    suspend fun scan(context: Context, uri: Uri): List<ImageScanResult> {
        val inputImage = InputImage.fromFilePath(context, uri)
        val barcodes = scanner.process(inputImage).await()
        return barcodes.mapNotNull { barcode ->
            val content = barcode.rawValue ?: return@mapNotNull null
            ImageScanResult(content, BarcodeFormat.fromMlKit(barcode.format))
        }
    }
}
