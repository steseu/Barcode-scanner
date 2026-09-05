package com.barcodebridge.app.ui.scan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.barcodebridge.app.R

/** Fixed high-contrast green: the frame sits on top of live camera output, not on a themed surface. */
private val HitFrameColor = Color(0xFF4CAF50)

/** Target frame drawn over the camera preview to guide barcode framing. */
@Composable
fun ScanTargetOverlay(modifier: Modifier = Modifier, highlighted: Boolean) {
    val frameColor = if (highlighted) HitFrameColor else Color.White
    val description = stringResource(R.string.cd_scan_overlay)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    ) {
        val frameWidth = size.width * 0.75f
        val frameHeight = frameWidth * 0.62f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        drawRoundRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 6f),
        )
    }
}

/** Brief full-screen flash used as the "visual flash" scan feedback channel. */
@Composable
fun ScanFlashOverlay(modifier: Modifier = Modifier, flashKey: Any?) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(flashKey) {
        if (flashKey != null) {
            alpha.snapTo(0.55f)
            alpha.animateTo(0f, animationSpec = tween(250))
        }
    }
    if (alpha.value > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha.value))
        )
    }
}
