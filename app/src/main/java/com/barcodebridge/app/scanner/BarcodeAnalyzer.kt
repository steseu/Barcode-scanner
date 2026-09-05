package com.barcodebridge.app.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

data class DetectedBarcode(
    val content: String,
    val format: BarcodeFormat,
    /** Corner points in the analyzer's image coordinate space, for overlay highlighting. */
    val cornerPoints: List<Pair<Float, Float>>,
    val imageWidth: Int,
    val imageHeight: Int,
)

/**
 * CameraX [androidx.camera.core.ImageAnalysis.Analyzer] that runs on-device
 * ML Kit barcode detection on every frame. Detections are delivered via
 * [onBarcodesDetected] on an analyzer-owned thread; callers must hop to the
 * main thread themselves before touching UI state.
 */
class BarcodeAnalyzer(
    private val onBarcodesDetected: (List<DetectedBarcode>) -> Unit,
) : androidx.camera.core.ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(BarcodeFormat.SUPPORTED_MLKIT_FORMATS_MASK)
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val results = barcodes.mapNotNull { barcode ->
                    val content = barcode.rawValue ?: return@mapNotNull null
                    DetectedBarcode(
                        content = content,
                        format = BarcodeFormat.fromMlKit(barcode.format),
                        cornerPoints = barcode.cornerPoints?.map { it.x.toFloat() to it.y.toFloat() }
                            ?: emptyList(),
                        imageWidth = inputImage.width,
                        imageHeight = inputImage.height,
                    )
                }
                if (results.isNotEmpty()) onBarcodesDetected(results)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun close() {
        scanner.close()
    }
}
