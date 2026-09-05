package com.barcodebridge.app.ui.scan

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.barcodebridge.app.scanner.BarcodeAnalyzer
import com.barcodebridge.app.scanner.DetectedBarcode

/**
 * Live CameraX preview bound to the current lifecycle, running an ML Kit
 * [BarcodeAnalyzer] on every frame. Reports the bound [Camera] once ready so
 * the caller can drive torch/zoom, and supports pinch-to-zoom and
 * tap-to-focus directly on the preview surface.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomRatioChange: (Float) -> Unit,
    onCameraReady: (Camera) -> Unit,
    onBarcodesDetected: (List<DetectedBarcode>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    val currentZoomRatio by rememberUpdatedState(zoomRatio)

    AndroidView(
        modifier = modifier
            .pointerInput(boundCamera) {
                detectTapGestures { offset ->
                    val camera = boundCamera ?: return@detectTapGestures
                    val factory = SurfaceOrientedMeteringPointFactory(size.width.toFloat(), size.height.toFloat())
                    val point = factory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .disableAutoCancel()
                        .build()
                    camera.cameraControl.startFocusAndMetering(action)
                }
            }
            .pointerInput(minZoomRatio, maxZoomRatio) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    val newRatio = (currentZoomRatio * zoomChange).coerceIn(minZoomRatio, maxZoomRatio)
                    onZoomRatioChange(newRatio)
                }
            },
        factory = {
            previewView.also {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysisUseCase ->
                            analysisUseCase.setAnalyzer(
                                ContextCompat.getMainExecutor(context),
                                BarcodeAnalyzer(onBarcodesDetected = onBarcodesDetected),
                            )
                        }
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                        boundCamera = camera
                        onCameraReady(camera)
                    } catch (exc: Exception) {
                        Log.e("CameraPreview", "Failed to bind camera use cases", exc)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        },
    )
}
