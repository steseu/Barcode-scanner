package com.barcodebridge.app.ui.scan

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.barcodebridge.app.scanner.BarcodeAnalyzer
import com.barcodebridge.app.scanner.DetectedBarcode

/**
 * Live CameraX preview bound to the current lifecycle, running an ML Kit
 * [BarcodeAnalyzer] on every frame. Reports the bound [Camera] once ready so
 * the caller can drive torch/zoom, and supports pinch-to-zoom and
 * tap-to-focus directly on the preview surface.
 *
 * Binding lives in a [DisposableEffect] rather than the [AndroidView] factory
 * so that leaving this screen (e.g. switching to the History tab) actually
 * releases the camera and closes the ML Kit detector, instead of leaving both
 * running for the whole activity lifetime.
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
    onCameraUnavailable: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    // Keep late-arriving callbacks/state fresh without restarting the camera
    // or the gesture detectors on every recomposition.
    val currentZoomRatio by rememberUpdatedState(zoomRatio)
    val currentOnBarcodesDetected by rememberUpdatedState(onBarcodesDetected)
    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val currentOnCameraUnavailable by rememberUpdatedState(onCameraUnavailable)

    DisposableEffect(lifecycleOwner, previewView) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analyzer = BarcodeAnalyzer { detected -> currentOnBarcodesDetected(detected) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val provider = runCatching { providerFuture.get() }.getOrNull()
            if (provider == null) {
                currentOnCameraUnavailable()
                return@addListener
            }
            cameraProvider = provider

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(mainExecutor, analyzer) }

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                boundCamera = camera
                currentOnCameraReady(camera)
            } catch (exc: Exception) {
                // No usable back camera, or the device is out of camera
                // resources - surface it instead of showing a black screen.
                Log.e(TAG, "Failed to bind camera use cases", exc)
                currentOnCameraUnavailable()
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            cameraProvider?.unbindAll()
            analyzer.close()
            boundCamera = null
        }
    }

    AndroidView(
        modifier = modifier
            .pointerInput(previewView) {
                detectTapGestures { offset ->
                    val camera = boundCamera ?: return@detectTapGestures
                    // PreviewView's own factory maps touch coordinates through the
                    // preview's scaling/rotation, unlike a surface-oriented factory.
                    val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction
                        .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
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
        factory = { previewView },
    )
}

private const val TAG = "CameraPreview"
