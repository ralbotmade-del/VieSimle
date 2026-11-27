package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PhotoFilterPreset(val label: String) {
    ORIGINAL("Original"),
    CRISP_SCAN("Scan N&B"),
    HIGH_CONTRAST("Contraste +"),
    WARM_LIGHT("Lumière +"),
    VIVID("Couleurs vives")
}

data class PhotoRetouchParams(
    val rotationQuarterTurns: Int = 0,
    val filterPreset: PhotoFilterPreset = PhotoFilterPreset.ORIGINAL,
    val brightness: Float = 0f,       // -60f .. +60f
    val contrast: Float = 1f,         // 0.65f .. 1.75f
    val saturation: Float = 1f,       // 0f .. 1.6f
    val cropHorizontalTrim: Float = 0f, // 0f .. 0.22f each side
    val cropVerticalTrim: Float = 0f    // 0f .. 0.22f each side
) {
    val isDefault: Boolean
        get() = rotationQuarterTurns % 4 == 0 &&
            filterPreset == PhotoFilterPreset.ORIGINAL &&
            brightness == 0f &&
            contrast == 1f &&
            saturation == 1f &&
            cropHorizontalTrim == 0f &&
            cropVerticalTrim == 0f
}

fun applyPhotoRetouch(source: Bitmap, params: PhotoRetouchParams): Bitmap {
    if (params.isDefault) return source

    val quarterTurns = ((params.rotationQuarterTurns % 4) + 4) % 4
    val rotated = if (quarterTurns != 0) {
        val matrix = Matrix().apply { postRotate(quarterTurns * 90f) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } else {
        source
    }

    val hTrim = params.cropHorizontalTrim.coerceIn(0f, 0.22f)
    val vTrim = params.cropVerticalTrim.coerceIn(0f, 0.22f)
    val cropped = if (hTrim > 0.005f || vTrim > 0.005f) {
        val left = (rotated.width * hTrim).toInt().coerceIn(0, rotated.width - 2)
        val top = (rotated.height * vTrim).toInt().coerceIn(0, rotated.height - 2)
        val cropW = (rotated.width - left * 2).coerceAtLeast(2)
        val cropH = (rotated.height - top * 2).coerceAtLeast(2)
        Bitmap.createBitmap(rotated, left, top, cropW, cropH)
    } else {
        rotated
    }

    val presetSat: Float
    val presetContrast: Float
    val presetBrightness: Float
    when (params.filterPreset) {
        PhotoFilterPreset.ORIGINAL -> {
            presetSat = 1f; presetContrast = 1f; presetBrightness = 0f
        }
        PhotoFilterPreset.CRISP_SCAN -> {
            presetSat = 0.06f; presetContrast = 1.38f; presetBrightness = 14f
        }
        PhotoFilterPreset.HIGH_CONTRAST -> {
            presetSat = 0.92f; presetContrast = 1.28f; presetBrightness = 6f
        }
        PhotoFilterPreset.WARM_LIGHT -> {
            presetSat = 1.08f; presetContrast = 1.06f; presetBrightness = 22f
        }
        PhotoFilterPreset.VIVID -> {
            presetSat = 1.32f; presetContrast = 1.12f; presetBrightness = 4f
        }
    }

    val totalSat = (presetSat * params.saturation).coerceIn(0f, 2f)
    val totalContrast = (presetContrast * params.contrast).coerceIn(0.5f, 2.2f)
    val totalBrightness = (presetBrightness + params.brightness).coerceIn(-90f, 90f)

    val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val cm = ColorMatrix().apply {
        setSaturation(totalSat)
        val translate = (-0.5f * totalContrast + 0.5f) * 255f + totalBrightness
        val adjustmentMatrix = ColorMatrix(
            floatArrayOf(
                totalContrast, 0f, 0f, 0f, translate,
                0f, totalContrast, 0f, 0f, translate,
                0f, 0f, totalContrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        postConcat(adjustmentMatrix)
    }
    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(cm)
    }
    canvas.drawBitmap(cropped, 0f, 0f, paint)
    return out
}

@Composable
fun PhotoRetouchCard(
    sourceBitmap: Bitmap,
    params: PhotoRetouchParams,
    onParamsChange: (PhotoRetouchParams) -> Unit,
    primaryActionLabel: String,
    onApplyRetouch: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    var renderedPreview by remember(sourceBitmap) { mutableStateOf(sourceBitmap) }
    var showFineTuning by remember { mutableStateOf(false) }
    var showCropControls by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sourceBitmap, params) {
        renderedPreview = withContext(Dispatchers.Default) {
            val previewSource = scaleDownForPreview(sourceBitmap, maxDim = 960)
            applyPhotoRetouch(previewSource, params)
        }
    }

    val cropIndicatorAlpha by animateFloatAsState(
        targetValue = if (params.cropHorizontalTrim > 0f || params.cropVerticalTrim > 0f) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "crop_badge"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("photo_retouch_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Studio retouche photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (!params.isDefault) {
                    TextButton(
                        onClick = { onParamsChange(PhotoRetouchParams()) },
                        modifier = Modifier.testTag("retouch_reset_btn")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Réinitialiser")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = renderedPreview.asImageBitmap(),
                    contentDescription = "Aperçu photo retouchée",
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                    contentScale = ContentScale.Fit
                )
                if (cropIndicatorAlpha > 0.05f) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * cropIndicatorAlpha),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Recadré",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Filter presets strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotoFilterPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = params.filterPreset == preset,
                        onClick = { onParamsChange(params.copy(filterPreset = preset)) },
                        leadingIcon = if (params.filterPreset == preset) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        } else null,
                        label = { Text(preset.label) },
                        modifier = Modifier.testTag("retouch_preset_${preset.name.lowercase()}")
                    )
                }
            }

            // Quick tools row: Rotate, Auto-enhance, Fine-tune sliders, Crop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onParamsChange(params.copy(rotationQuarterTurns = (params.rotationQuarterTurns + 1) % 4))
                    },
                    modifier = Modifier.weight(1f).testTag("retouch_rotate_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("90°")
                }
                FilledTonalButton(
                    onClick = {
                        onParamsChange(
                            params.copy(
                                filterPreset = PhotoFilterPreset.CRISP_SCAN,
                                brightness = 10f,
                                contrast = 1.15f
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("retouch_auto_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Auto")
                }
                OutlinedButton(
                    onClick = { showFineTuning = !showFineTuning },
                    modifier = Modifier.weight(1f).testTag("retouch_tune_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Réglages")
                }
                OutlinedButton(
                    onClick = { showCropControls = !showCropControls },
                    modifier = Modifier.weight(1f).testTag("retouch_crop_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Rogner")
                }
            }

            AnimatedVisibility(visible = showFineTuning) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetouchSliderRow(
                        label = "Luminosité",
                        valueText = "${params.brightness.toInt()}",
                        value = params.brightness,
                        range = -60f..60f,
                        onValueChange = { onParamsChange(params.copy(brightness = it)) }
                    )
                    RetouchSliderRow(
                        label = "Contraste",
                        valueText = "${(params.contrast * 100).toInt()}%",
                        value = params.contrast,
                        range = 0.65f..1.75f,
                        onValueChange = { onParamsChange(params.copy(contrast = it)) }
                    )
                    RetouchSliderRow(
                        label = "Saturation",
                        valueText = "${(params.saturation * 100).toInt()}%",
                        value = params.saturation,
                        range = 0f..1.6f,
                        onValueChange = { onParamsChange(params.copy(saturation = it)) }
                    )
                }
            }

            AnimatedVisibility(visible = showCropControls) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetouchSliderRow(
                        label = "Rogner bords gauche/droite",
                        valueText = "${(params.cropHorizontalTrim * 100).toInt()}%",
                        value = params.cropHorizontalTrim,
                        range = 0f..0.22f,
                        onValueChange = { onParamsChange(params.copy(cropHorizontalTrim = it)) }
                    )
                    RetouchSliderRow(
                        label = "Rogner haut/bas",
                        valueText = "${(params.cropVerticalTrim * 100).toInt()}%",
                        value = params.cropVerticalTrim,
                        range = 0f..0.22f,
                        onValueChange = { onParamsChange(params.copy(cropVerticalTrim = it)) }
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val finalBmp = withContext(Dispatchers.Default) {
                            applyPhotoRetouch(sourceBitmap, params)
                        }
                        onApplyRetouch(finalBmp)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("retouch_apply_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(primaryActionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoRetouchDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onSaveRetouched: (Bitmap) -> Unit
) {
    var params by remember(sourceBitmap) { mutableStateOf(PhotoRetouchParams()) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Retoucher la photo") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Fermer")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                PhotoRetouchCard(
                    sourceBitmap = sourceBitmap,
                    params = params,
                    onParamsChange = { params = it },
                    primaryActionLabel = "Enregistrer la photo retouchée",
                    onApplyRetouch = onSaveRetouched
                )
            }
        }
    }
}

@Composable
private fun RetouchSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

private fun scaleDownForPreview(source: Bitmap, maxDim: Int): Bitmap {
    val longest = maxOf(source.width, source.height)
    if (longest <= maxDim) return source
    val scale = maxDim.toFloat() / longest.toFloat()
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, w, h, true)
}
