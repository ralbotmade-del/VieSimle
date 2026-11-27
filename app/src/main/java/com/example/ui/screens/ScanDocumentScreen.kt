package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.AiAssistantRepository
import com.example.export.PdfGenerator
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.VaultDocument
import com.example.ui.components.CameraScannerView
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.PhotoRetouchCard
import com.example.ui.components.PhotoRetouchParams
import com.example.ui.viewmodel.OcrScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private data class CapturedScanPage(
    val id: String,
    val originalFile: File,
    val originalBitmap: Bitmap,
    val grayscalePreviewFile: File? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDocumentScreen(
    ocrState: OcrScanState,
    onAnalyzeBitmap: (Bitmap, Boolean, File?, Int, Boolean) -> Unit,
    onReanalyzeText: (String, String, String?) -> Unit = { _, _, _ -> },
    onResetScan: () -> Unit,
    onSaveScannedDocument: (VaultDocument, Deadline?) -> Unit,
    onOpenSavedDocument: (String) -> Unit = {},
    onManualEntryFallback: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var capturedPages by remember { mutableStateOf<List<CapturedScanPage>>(emptyList()) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var generatedPdf by remember { mutableStateOf<PdfGenerator.GeneratedGrayscalePdf?>(null) }
    var isConvertingGrayscalePdf by remember { mutableStateOf(false) }
    var showGrayscalePreview by remember { mutableStateOf(true) }
    var showRetouchPanel by remember { mutableStateOf(false) }
    var retouchParams by remember { mutableStateOf(PhotoRetouchParams()) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showLiveCameraX by remember { mutableStateOf(false) }
    var autoConvertGrayscalePdf by remember { mutableStateOf(true) }
    var autoSaveToFirestore by remember { mutableStateOf(true) }
    var markAsSensitive by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val activePage = capturedPages.getOrNull(selectedPageIndex.coerceIn(0, (capturedPages.size - 1).coerceAtLeast(0)))

    fun rebuildGrayscalePdfAndMaybeAnalyze(
        pages: List<CapturedScanPage>,
        triggerOcrAnalysis: Boolean
    ) {
        if (pages.isEmpty()) {
            generatedPdf = null
            return
        }
        scope.launch {
            isConvertingGrayscalePdf = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PdfGenerator.generateGrayscaleScanPdf(
                        context = context,
                        pageBitmaps = pages.map { it.originalBitmap },
                        filePrefix = "scan_administratif_nb",
                        enhanceContrast = true
                    )
                }.getOrNull()
            }
            isConvertingGrayscalePdf = false
            if (result != null) {
                generatedPdf = result
                capturedPages = pages.mapIndexed { idx, page ->
                    page.copy(grayscalePreviewFile = result.previewImageFiles.getOrNull(idx))
                }
                showGrayscalePreview = true
                if (triggerOcrAnalysis) {
                    val primaryBitmap = result.grayscaleBitmaps.firstOrNull() ?: pages.first().originalBitmap
                    onAnalyzeBitmap(
                        primaryBitmap,
                        autoSaveToFirestore,
                        if (autoConvertGrayscalePdf) result.pdfFile else null,
                        result.pageCount,
                        markAsSensitive
                    )
                }
            } else {
                infoMessage = "Impossible de convertir le document en PDF niveaux de gris."
                if (triggerOcrAnalysis) {
                    onAnalyzeBitmap(pages.first().originalBitmap, autoSaveToFirestore, null, pages.size, markAsSensitive)
                }
            }
        }
    }

    fun ingestCapturedBitmap(bmp: Bitmap, triggerOcrImmediately: Boolean) {
        scope.launch {
            val savedOriginalFile = withContext(Dispatchers.IO) {
                saveBitmapToScanCache(context, bmp)
            }
            val newPage = CapturedScanPage(
                id = "page_${System.currentTimeMillis()}_${capturedPages.size + 1}",
                originalFile = savedOriginalFile,
                originalBitmap = bmp
            )
            val updatedPages = capturedPages + newPage
            capturedPages = updatedPages
            selectedPageIndex = updatedPages.lastIndex
            retouchParams = PhotoRetouchParams()
            infoMessage = null

            if (autoConvertGrayscalePdf) {
                rebuildGrayscalePdfAndMaybeAnalyze(updatedPages, triggerOcrAnalysis = triggerOcrImmediately)
            } else {
                generatedPdf = null
                if (triggerOcrImmediately) {
                    onAnalyzeBitmap(bmp, autoSaveToFirestore, null, updatedPages.size, markAsSensitive)
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            val bmp = decodeScaledBitmap(context, pendingCameraUri!!)
            if (bmp != null) {
                ingestCapturedBitmap(bmp, triggerOcrImmediately = capturedPages.isEmpty())
            } else {
                infoMessage = "Impossible de lire l’image capturée."
            }
        }
    }

    fun launchSystemCameraFallback() {
        val scanDir = File(context.cacheDir, "scans").apply { mkdirs() }
        val photoFile = File(scanDir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showLiveCameraX = true
        } else {
            infoMessage = "Permission caméra refusée. Vous pouvez importer une image depuis la galerie ou vos fichiers."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = decodeScaledBitmap(context, uri)
            if (bmp != null) {
                ingestCapturedBitmap(bmp, triggerOcrImmediately = capturedPages.isEmpty())
            } else {
                infoMessage = "Impossible de charger l’image sélectionnée."
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/")) {
                val bmp = decodeScaledBitmap(context, uri)
                if (bmp != null) {
                    ingestCapturedBitmap(bmp, triggerOcrImmediately = capturedPages.isEmpty())
                }
            } else {
                infoMessage = "Fichier sélectionné (${uri.lastPathSegment}). Saisissez ou vérifiez ses informations manuellement."
                onManualEntryFallback()
            }
        }
    }

    fun launchCameraXScanner() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) showLiveCameraX = true else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (showLiveCameraX) {
        BackHandler { showLiveCameraX = false }
        CameraScannerView(
            onImageCaptured = { bmp ->
                showLiveCameraX = false
                ingestCapturedBitmap(bmp, triggerOcrImmediately = capturedPages.isEmpty())
            },
            onError = { err ->
                showLiveCameraX = false
                infoMessage = err
                launchSystemCameraFallback()
            },
            onClose = { showLiveCameraX = false },
            existingPageCount = capturedPages.size,
            grayscaleAutoEnabled = autoConvertGrayscalePdf
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner & PDF N&B") },
                navigationIcon = {
                    IconButton(onClick = {
                        onResetScan()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(visible = capturedPages.isEmpty()) {
                Image(
                    painter = painterResource(id = R.drawable.img_scan_studio_banner_1795607683673),
                    contentDescription = "Studio de numérisation VieSimple",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Capture & Conversion Controls Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Numérisation CameraX & PDF niveaux de gris", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conversion automatique en PDF N&B", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Convertit les documents capturés en PDF niveaux de gris haute lisibilité.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoConvertGrayscalePdf,
                            onCheckedChange = { enabled ->
                                autoConvertGrayscalePdf = enabled
                                if (enabled && capturedPages.isNotEmpty()) {
                                    rebuildGrayscalePdfAndMaybeAnalyze(capturedPages, triggerOcrAnalysis = false)
                                } else if (!enabled) {
                                    generatedPdf = null
                                }
                            },
                            modifier = Modifier.testTag("scan_grayscale_pdf_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enregistrer directement sur Firestore", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Classe automatiquement le document administratif et crée son échéance si détectée.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSaveToFirestore,
                            onCheckedChange = { autoSaveToFirestore = it },
                            modifier = Modifier.testTag("scan_auto_save_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Protéger comme document sensible", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "Exige empreinte digitale ou reconnaissance faciale à l’ouverture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = markAsSensitive,
                            onCheckedChange = { markAsSensitive = it },
                            modifier = Modifier.testTag("scan_sensitive_switch")
                        )
                    }

                    Button(
                        onClick = { launchCameraXScanner() },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("scan_camera_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (capturedPages.isEmpty()) "Scanner un papier administratif (CameraX)"
                            else "Ajouter une nouvelle page (CameraX)"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("scan_gallery_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Galerie")
                        }
                        OutlinedButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("scan_file_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Fichiers")
                        }
                    }

                    OutlinedButton(
                        onClick = onManualEntryFallback,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Saisir un document manuellement")
                    }
                }
            }

            infoMessage?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            // Coil AsyncImage Preview + Grayscale PDF Card
            AnimatedVisibility(visible = capturedPages.isNotEmpty() && activePage != null) {
                activePage?.let { page ->
                    val displayFile = if (showGrayscalePreview && page.grayscalePreviewFile != null) {
                        page.grayscalePreviewFile
                    } else {
                        page.originalFile
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().testTag("scan_coil_preview_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.FilterBAndW, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Aperçu numérisé (${capturedPages.size} page${if (capturedPages.size > 1) "s" else ""})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        capturedPages = emptyList()
                                        generatedPdf = null
                                        selectedPageIndex = 0
                                        onResetScan()
                                    },
                                    modifier = Modifier.testTag("scan_clear_pages_btn")
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Effacer")
                                }
                            }

                            if (isConvertingGrayscalePdf) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(
                                        "Conversion automatique en PDF niveaux de gris…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Coil AsyncImage active page preview
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(displayFile)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = if (showGrayscalePreview && page.grayscalePreviewFile != null) {
                                        "Aperçu PDF niveaux de gris page ${selectedPageIndex + 1}"
                                    } else {
                                        "Aperçu original page ${selectedPageIndex + 1}"
                                    },
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                        .testTag("scan_coil_preview")
                                )
                            }

                            // View toggle (Grayscale PDF vs Original) & multi-page thumbnail strip via Coil
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (page.grayscalePreviewFile != null) {
                                    FilterChip(
                                        selected = showGrayscalePreview,
                                        onClick = { showGrayscalePreview = true },
                                        leadingIcon = { Icon(Icons.Outlined.FilterBAndW, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text("Rendu PDF N&B") },
                                        modifier = Modifier.testTag("scan_toggle_grayscale_preview")
                                    )
                                }
                                FilterChip(
                                    selected = !showGrayscalePreview || page.grayscalePreviewFile == null,
                                    onClick = { showGrayscalePreview = false },
                                    label = { Text("Photo originale") },
                                    modifier = Modifier.testTag("scan_toggle_original_preview")
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = { showRetouchPanel = !showRetouchPanel },
                                    modifier = Modifier.testTag("scan_toggle_retouch_btn")
                                ) {
                                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = "Retoucher la page")
                                }
                            }

                            if (capturedPages.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 2.dp)
                                ) {
                                    itemsIndexed(capturedPages, key = { _, p -> p.id }) { idx, item ->
                                        val thumbFile = if (showGrayscalePreview && item.grayscalePreviewFile != null) {
                                            item.grayscalePreviewFile
                                        } else {
                                            item.originalFile
                                        }
                                        val isSelected = idx == selectedPageIndex
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier
                                                .width(64.dp)
                                                .height(84.dp)
                                                .clickable { selectedPageIndex = idx }
                                        ) {
                                            Box {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(thumbFile)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Page ${idx + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = "${idx + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            generatedPdf?.let { pdfInfo ->
                                val sizeKb = (pdfInfo.fileSizeBytes / 1024L).coerceAtLeast(1L)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("scan_grayscale_pdf_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "PDF niveaux de gris prêt",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "${pdfInfo.pageCount} page${if (pdfInfo.pageCount > 1) "s" else ""} · ${sizeKb} Ko · Format A4 optimisé",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                PdfGenerator.shareFile(
                                                    context = context,
                                                    file = pdfInfo.pdfFile,
                                                    mimeType = "application/pdf",
                                                    chooserTitle = "Partager le PDF niveaux de gris"
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("scan_share_grayscale_pdf_btn")
                                        ) {
                                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("PDF")
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { launchCameraXScanner() },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("scan_add_page_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("+ Page")
                                }
                                Button(
                                    onClick = {
                                        if (autoConvertGrayscalePdf) {
                                            rebuildGrayscalePdfAndMaybeAnalyze(capturedPages, triggerOcrAnalysis = true)
                                        } else {
                                            onAnalyzeBitmap(page.originalBitmap, autoSaveToFirestore, null, capturedPages.size, markAsSensitive)
                                        }
                                    },
                                    modifier = Modifier.weight(1.4f).height(48.dp).testTag("scan_analyze_pdf_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Analyser & archiver")
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showRetouchPanel && activePage != null) {
                activePage?.let { page ->
                    PhotoRetouchCard(
                        sourceBitmap = page.originalBitmap,
                        params = retouchParams,
                        onParamsChange = { retouchParams = it },
                        primaryActionLabel = "Appliquer la retouche & régénérer PDF N&B",
                        onApplyRetouch = { retouched ->
                            scope.launch {
                                val updatedFile = withContext(Dispatchers.IO) {
                                    saveBitmapToScanCache(context, retouched)
                                }
                                val updatedList = capturedPages.mapIndexed { idx, existing ->
                                    if (idx == selectedPageIndex) {
                                        existing.copy(originalFile = updatedFile, originalBitmap = retouched)
                                    } else existing
                                }
                                capturedPages = updatedList
                                retouchParams = PhotoRetouchParams()
                                showRetouchPanel = false
                                if (autoConvertGrayscalePdf) {
                                    rebuildGrayscalePdfAndMaybeAnalyze(updatedList, triggerOcrAnalysis = true)
                                } else {
                                    onAnalyzeBitmap(retouched, autoSaveToFirestore, null, updatedList.size, markAsSensitive)
                                }
                            }
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = ocrState,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                label = "ocr_state_transition"
            ) { ocrState ->
            when (ocrState) {
                is OcrScanState.Idle -> Unit
                is OcrScanState.Analyzing -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                            Column {
                                Text("Analyse OCR et catégorisation en cours…", style = MaterialTheme.typography.titleSmall)
                                Text("Détection du type administratif, date, montant et catégorie.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                is OcrScanState.SavingToFirestore -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                            Column {
                                Text("Enregistrement direct sur Firestore…", style = MaterialTheme.typography.titleSmall)
                                Text("Archivage sécurisé du PDF niveaux de gris dans votre coffre-fort.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                is OcrScanState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(ocrState.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onManualEntryFallback) {
                                Text("Enregistrer manuellement")
                            }
                        }
                    }
                }
                is OcrScanState.Ready -> {
                    val extracted = ocrState.info
                    var title by remember(extracted) { mutableStateOf(extracted.title) }
                    var category by remember(extracted) { mutableStateOf(extracted.category) }
                    var docType by remember(extracted) { mutableStateOf(extracted.docType) }
                    var issuer by remember(extracted) { mutableStateOf(extracted.issuer) }
                    var docDate by remember(extracted) { mutableStateOf(extracted.docDate) }
                    var expirationDate by remember(extracted) { mutableStateOf(extracted.expirationDate) }
                    var amountText by remember(extracted) { mutableStateOf(if (extracted.amount > 0) extracted.amount.toString() else "") }
                    var refNum by remember(extracted) { mutableStateOf(extracted.referenceNumber) }
                    var ocrText by remember(extracted) { mutableStateOf(extracted.ocrText) }
                    var editableTags by remember(extracted, ocrState.isGrayscalePdf) {
                        mutableStateOf(
                            AiAssistantRepository.sanitizeTags(
                                extracted.tags + if (ocrState.isGrayscalePdf) listOf("PDF N&B") else emptyList(),
                                maxTags = 10
                            )
                        )
                    }
                    var newTagInput by remember { mutableStateOf("") }
                    var createDeadlineChecked by remember(extracted) { mutableStateOf(extracted.expirationDate.isNotBlank()) }

                    if (ocrState.autoSavedToFirestore && ocrState.savedDocumentId != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().testTag("scan_firestore_saved_banner")
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (ocrState.isGrayscalePdf) {
                                                "PDF N&B (${ocrState.pageCount} p.) enregistré · $category"
                                            } else {
                                                "Enregistré sur Firestore · Catégorie : $category"
                                            },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            if (ocrState.autoCreatedDeadline) {
                                                "Document archivé et échéance associée créée automatiquement."
                                            } else {
                                                "Document classé automatiquement et disponible dans votre coffre-fort."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { onOpenSavedDocument(ocrState.savedDocumentId) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Ouvrir la fiche")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            capturedPages = emptyList()
                                            generatedPdf = null
                                            selectedPageIndex = 0
                                            onResetScan()
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Nouveau scan")
                                    }
                                }
                            }
                        }
                    }

                    if (extracted.confidence != "high" || extracted.uncertaintyNote.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = extracted.uncertaintyNote.ifBlank {
                                    "Confiance ${extracted.confidence} : vérifiez attentivement les champs extraits avant d’enregistrer."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("scan_extracted_summary")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ExtractedKeyChip(
                                label = "Type",
                                value = docType.ifBlank { "Non détecté" },
                                modifier = Modifier.weight(1f)
                            )
                            ExtractedKeyChip(
                                label = "Date",
                                value = if (docDate.isNotBlank()) DateUtils.formatFrench(docDate) else "Non détectée",
                                modifier = Modifier.weight(1f)
                            )
                            ExtractedKeyChip(
                                label = "Montant",
                                value = amountText.replace(",", ".").toDoubleOrNull()
                                    ?.takeIf { it > 0.0 }
                                    ?.let { "${"%.2f".format(it)} €" }
                                    ?: "Aucun",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Catégorie détectée automatiquement", style = MaterialTheme.typography.titleMedium)
                            }
                            CategoryChipRow(
                                selectedCategory = category,
                                onSelectCategory = { selected -> if (selected != null) category = selected },
                                includeAllOption = false
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("scan_ai_tags_section")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Label,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.ai_tagging_title),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    if (extracted.categoryRationale.isNotBlank()) {
                                        Text(
                                            text = extracted.categoryRationale,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        editableTags.forEachIndexed { idx, tag ->
                                            InputChip(
                                                selected = true,
                                                onClick = {
                                                    editableTags = editableTags.filterNot { it.equals(tag, ignoreCase = true) }
                                                },
                                                label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Close,
                                                        contentDescription = "Retirer $tag",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                },
                                                modifier = Modifier.testTag("scan_ai_tag_$idx")
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newTagInput,
                                            onValueChange = { newTagInput = it },
                                            placeholder = { Text(stringResource(R.string.ai_tagging_add_placeholder)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("scan_add_tag_input")
                                        )
                                        FilledTonalButton(
                                            onClick = {
                                                val clean = newTagInput.trim().removePrefix("#").trim()
                                                if (clean.isNotBlank()) {
                                                    editableTags = AiAssistantRepository.sanitizeTags(editableTags + clean, maxTags = 10)
                                                    newTagInput = ""
                                                }
                                            },
                                            enabled = newTagInput.isNotBlank(),
                                            modifier = Modifier.height(48.dp).testTag("scan_add_tag_btn"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = "Ajouter tag")
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(
                                value = docType,
                                onValueChange = { docType = it },
                                label = { Text("Type de document") },
                                placeholder = { Text("Facture, Contrat, Avis d’imposition…") },
                                modifier = Modifier.fillMaxWidth().testTag("scan_extracted_doctype")
                            )
                            OutlinedTextField(value = issuer, onValueChange = { issuer = it }, label = { Text("Organisme") }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = docDate,
                                    onValueChange = { docDate = it },
                                    label = { Text("Date (AAAA-MM-JJ)") },
                                    modifier = Modifier.weight(1f).testTag("scan_extracted_date")
                                )
                                OutlinedTextField(
                                    value = expirationDate,
                                    onValueChange = { expirationDate = it },
                                    label = { Text("Expiration") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = it },
                                    label = { Text("Montant (€)") },
                                    modifier = Modifier.weight(1f).testTag("scan_extracted_amount")
                                )
                                OutlinedTextField(value = refNum, onValueChange = { refNum = it }, label = { Text("Référence") }, modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(value = ocrText, onValueChange = { ocrText = it }, label = { Text("Texte OCR") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                            if (ocrText.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { onReanalyzeText(ocrText, ocrState.encryptedPath, ocrState.savedDocumentId) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("scan_reanalyze_text_btn"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text("Ré-extraire catégorie, date, montant et type")
                                }
                            }

                            if (expirationDate.isNotBlank() && !ocrState.autoCreatedDeadline) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = createDeadlineChecked, onCheckedChange = { createDeadlineChecked = it })
                                    Text("Créer automatiquement une échéance au ${DateUtils.formatFrench(expirationDate)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Button(
                                onClick = {
                                    val doc = VaultDocument(
                                        id = ocrState.savedDocumentId.orEmpty(),
                                        title = title.ifBlank { "Document numérisé" },
                                        category = category,
                                        docType = docType.trim(),
                                        docDate = docDate.trim(),
                                        expirationDate = expirationDate.trim(),
                                        issuer = issuer.trim(),
                                        referenceNumber = refNum.trim(),
                                        amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                        tags = AiAssistantRepository.sanitizeTags(
                                            editableTags + if (ocrState.isGrayscalePdf) listOf("PDF N&B") else emptyList(),
                                            maxTags = 10
                                        ),
                                        confidentiality = if (markAsSensitive) "sensitive" else "standard",
                                        ocrText = ocrText,
                                        ocrConfidence = extracted.confidence,
                                        localFileUri = ocrState.encryptedPath
                                    )
                                    val deadline = if (createDeadlineChecked && expirationDate.isNotBlank() && !ocrState.autoCreatedDeadline) {
                                        Deadline(
                                            title = "Échéance : ${doc.title}",
                                            category = doc.category,
                                            dueDate = expirationDate.trim(),
                                            priority = "high"
                                        )
                                    } else null
                                    onSaveScannedDocument(doc, deadline)
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("scan_confirm_save_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    if (ocrState.savedDocumentId != null) "Mettre à jour sur Firestore"
                                    else "Valider et enregistrer sur Firestore"
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ExtractedKeyChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

private fun saveBitmapToScanCache(context: Context, bmp: Bitmap): File {
    val scanDir = File(context.cacheDir, "scans").apply { mkdirs() }
    val outFile = File(scanDir, "orig_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
    FileOutputStream(outFile).use { fos ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
    }
    return outFile
}

private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val input1 = context.contentResolver.openInputStream(uri) ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input1, null, opts)
        input1.close()

        var sample = 1
        val maxDim = 1600
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val input2 = context.contentResolver.openInputStream(uri) ?: return null
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeStream(input2, null, decodeOpts)
        input2.close()
        bmp
    } catch (_: Exception) {
        null
    }
}
