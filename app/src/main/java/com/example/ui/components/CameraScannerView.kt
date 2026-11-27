package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Live CameraX document scanner view that captures a high-resolution frame,
 * normalizes orientation/dimensions, and forwards the Bitmap to the Firebase AI Logic OCR pipeline.
 */
@Composable
fun CameraScannerView(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit,
    existingPageCount: Int = 0,
    grayscaleAutoEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener(
            {
                try {
                    val camProvider = cameraProviderFuture.get()
                    provider = camProvider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    camProvider.unbindAll()
                    val camera = camProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    imageCapture = capture
                    boundCamera = camera
                    cameraError = null
                } catch (e: Exception) {
                    Log.e("CameraScannerView", "CameraX initialization failed", e)
                    val msg = "Caméra indisponible sur cet appareil : ${e.localizedMessage ?: "erreur matérielle"}."
                    cameraError = msg
                    onError(msg)
                }
            },
            executor
        )

        onDispose {
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Document alignment guide frame with animated scan line
        val frameColor = MaterialTheme.colorScheme.primaryContainer
        val scanTransition = rememberInfiniteTransition(label = "camera_scan_beam")
        val scanProgress by scanTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scan_beam_progress"
        )
        val pulseAlpha by scanTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scan_border_alpha"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth = size.width * 0.84f
            val frameHeight = size.height * 0.62f
            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f

            drawRoundRect(
                color = frameColor.copy(alpha = pulseAlpha),
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(24f, 24f),
                style = Stroke(width = 4.5f)
            )

            val beamY = top + 16f + (frameHeight - 32f) * scanProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        frameColor.copy(alpha = 0.9f),
                        Color.Transparent
                    ),
                    startX = left,
                    endX = left + frameWidth
                ),
                start = Offset(left + 12f, beamY),
                end = Offset(left + frameWidth - 12f, beamY),
                strokeWidth = 4f
            )
        }

        // Top controls bar (Close & Torch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = CircleShape) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("camerax_close_button")
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Fermer la caméra", tint = Color.White)
                }
            }

            Surface(color = Color.Black.copy(alpha = 0.58f), shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = buildString {
                        append(if (existingPageCount > 0) "Page ${existingPageCount + 1}" else "Cadrez le document")
                        if (grayscaleAutoEnabled) append(" · PDF N&B auto")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            val hasFlash = boundCamera?.cameraInfo?.hasFlashUnit() == true
            if (hasFlash) {
                Surface(color = Color.Black.copy(alpha = 0.55f), shape = CircleShape) {
                    IconButton(
                        onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            boundCamera?.cameraControl?.enableTorch(next)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("camerax_flash_button")
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                            contentDescription = if (torchEnabled) "Désactiver le flash" else "Activer le flash",
                            tint = Color.White
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        cameraError?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onClose) { Text("Revenir aux options") }
                }
            }
        }

        // Bottom capture button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    isCapturing = true
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val bitmap = imageProxyToRotatedScaledBitmap(image)
                                    isCapturing = false
                                    onImageCaptured(bitmap)
                                } catch (e: Exception) {
                                    isCapturing = false
                                    onError("Erreur lors du traitement de l’image : ${e.localizedMessage}")
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                onError("Capture impossible : ${exception.localizedMessage ?: "réessayez"}")
                            }
                        }
                    )
                },
                enabled = !isCapturing && imageCapture != null,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics(mergeDescendants = true) {}
                    .testTag("camerax_capture_button")
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (grayscaleAutoEnabled) "Capturer & convertir en PDF N&B"
                        else "Capturer pour analyse OCR"
                    )
                }
            }
        }
    }
}

private fun imageProxyToRotatedScaledBitmap(image: ImageProxy, maxDim: Int = 1600): Bitmap {
    val raw = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees
    val rotated = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    } else {
        raw
    }

    val longest = maxOf(rotated.width, rotated.height)
    return if (longest > maxDim) {
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = (rotated.width * scale).toInt().coerceAtLeast(1)
        val h = (rotated.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(rotated, w, h, true)
    } else {
        rotated
    }
}
