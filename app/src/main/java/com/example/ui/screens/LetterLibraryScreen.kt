package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.UserSecuritySettings
import com.example.export.PdfGenerator
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.LetterFillData
import com.example.model.LetterTemplate
import com.example.model.LetterTemplates
import com.example.model.SubscriptionItem
import com.example.model.UserProfile
import com.example.model.VaultDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterTemplatesScreen(
    initialTemplateId: String? = null,
    prefillOrg: String = "",
    prefillContract: String = "",
    prefillAmount: String = "",
    prefillRef: String = "",
    profile: UserProfile?,
    securitySettings: UserSecuritySettings = UserSecuritySettings(),
    documents: List<VaultDocument>,
    subscriptions: List<SubscriptionItem>,
    deadlines: List<Deadline> = emptyList(),
    hasHousehold: Boolean = false,
    onBack: () -> Unit,
    onSaveSenderDefaults: (address: String, city: String, phone: String) -> Unit = { _, _, _ -> },
    onSaveGeneratedLetterToVault: (
        template: LetterTemplate,
        subject: String,
        body: String,
        recipientOrg: String,
        referenceNumber: String,
        amount: Double,
        pdfFile: File,
        shareWithHousehold: Boolean,
        onSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onOpenSavedDocument: (String) -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTemplate by remember(initialTemplateId) {
        mutableStateOf(initialTemplateId?.let { id -> LetterTemplates.ALL.firstOrNull { it.id == id } })
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val availableCategories = remember {
        LetterTemplates.ALL.map { it.category }.distinct()
    }
    val filteredTemplates = remember(searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        LetterTemplates.ALL.filter { tpl ->
            val matchesCat = selectedCategory == null || tpl.category == selectedCategory
            val matchesQuery = q.isBlank() ||
                tpl.title.lowercase().contains(q) ||
                tpl.description.lowercase().contains(q) ||
                tpl.category.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }

    var senderName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var senderEmail by remember(profile?.email) { mutableStateOf(profile?.email.orEmpty()) }
    var senderAddress by remember(securitySettings.senderAddress) { mutableStateOf(securitySettings.senderAddress) }
    var senderCity by remember(securitySettings.senderCity) { mutableStateOf(securitySettings.senderCity) }
    var senderPhone by remember(securitySettings.senderPhone) { mutableStateOf(securitySettings.senderPhone) }

    var orgName by remember(prefillOrg) { mutableStateOf(prefillOrg) }
    var orgAddress by remember { mutableStateOf("") }
    var contractName by remember(prefillContract) { mutableStateOf(prefillContract) }
    var refNum by remember(prefillRef) { mutableStateOf(prefillRef) }
    var amountText by remember(prefillAmount) { mutableStateOf(prefillAmount) }
    var reasonText by remember { mutableStateOf("") }

    var shareWithHousehold by remember { mutableStateOf(false) }
    var showSenderSection by remember { mutableStateOf(securitySettings.senderAddress.isBlank()) }
    var manualTextEdited by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf("preview") } // "preview" or "edit"

    var editedSubject by remember { mutableStateOf("") }
    var editedBody by remember { mutableStateOf("") }

    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var previewPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isRenderingPdf by remember { mutableStateOf(false) }
    var isSavingVault by remember { mutableStateOf(false) }
    var savedVaultDocId by remember { mutableStateOf<String?>(null) }

    fun currentFillData(): LetterFillData = LetterFillData(
        senderName = senderName,
        senderEmail = senderEmail,
        senderAddress = senderAddress,
        senderCity = senderCity,
        senderPhone = senderPhone,
        orgName = orgName,
        orgAddress = orgAddress,
        contractName = contractName,
        referenceNumber = refNum,
        amountText = amountText,
        reasonText = reasonText,
        dateLabel = DateUtils.todayNumeric()
    )

    fun syncFromFields(tpl: LetterTemplate) {
        val (subj, body) = LetterTemplates.fillTemplate(tpl, currentFillData())
        editedSubject = subj
        editedBody = body
        manualTextEdited = false
        savedVaultDocId = null
    }

    LaunchedEffect(
        selectedTemplate,
        senderName,
        senderEmail,
        senderAddress,
        senderCity,
        senderPhone,
        orgName,
        orgAddress,
        contractName,
        refNum,
        amountText,
        reasonText
    ) {
        val tpl = selectedTemplate ?: return@LaunchedEffect
        if (!manualTextEdited) {
            val (subj, body) = LetterTemplates.fillTemplate(tpl, currentFillData())
            editedSubject = subj
            editedBody = body
        }
    }

    LaunchedEffect(
        selectedTemplate,
        editedSubject,
        editedBody,
        senderName,
        senderAddress,
        senderEmail,
        senderPhone,
        senderCity,
        orgName,
        orgAddress
    ) {
        val tpl = selectedTemplate ?: return@LaunchedEffect
        if (editedBody.isBlank()) return@LaunchedEffect
        delay(220L)
        isRenderingPdf = true
        val generated = withContext(Dispatchers.IO) {
            runCatching {
                val file = PdfGenerator.generateLetterPdf(
                    context = context,
                    subject = editedSubject,
                    body = editedBody,
                    senderName = senderName.ifBlank { profile?.displayName.orEmpty() },
                    senderAddress = senderAddress,
                    senderEmail = senderEmail.ifBlank { profile?.email.orEmpty() },
                    senderPhone = senderPhone,
                    senderCity = senderCity,
                    recipientOrg = orgName,
                    recipientAddress = orgAddress,
                    filePrefix = "courrier_${tpl.id}"
                )
                val bitmaps = PdfGenerator.renderPdfPagesToBitmaps(file, maxPages = 4)
                file to bitmaps
            }.getOrNull()
        }
        if (generated != null) {
            previewPdfFile = generated.first
            previewPages = generated.second
        }
        isRenderingPdf = false
    }

    if (selectedTemplate != null && initialTemplateId == null) {
        BackHandler {
            selectedTemplate = null
            savedVaultDocId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedTemplate?.title ?: "Bibliothèque de courriers PDF",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTemplate != null && initialTemplateId == null) {
                            selectedTemplate = null
                            savedVaultDocId = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentTpl = selectedTemplate
        if (currentTpl == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val heroBrush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(heroBrush)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(9.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        "${LetterTemplates.ALL.size} modèles officiels prêts à l’emploi",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Pré-remplissage automatique avec votre profil et vos documents",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Effacer")
                                }
                            }
                        },
                        placeholder = { Text("Rechercher : résiliation, impôts, bail, mutuelle…") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("letter_library_search")
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Tous (${LetterTemplates.ALL.size})") }
                        )
                        availableCategories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                if (filteredTemplates.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.MailOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("Aucun modèle correspondant", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Essayez un autre mot-clé ou réinitialisez le filtre.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTemplates, key = { it.id }) { tpl ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTemplate = tpl
                                    savedVaultDocId = null
                                    syncFromFields(tpl)
                                }
                                .testTag("letter_tpl_${tpl.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Icon(
                                        imageVector = categoryIconForTemplate(tpl.category),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(11.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = tpl.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = tpl.deliveryHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = tpl.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = tpl.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = "Choisir ce modèle",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            if (editedBody.isBlank()) {
                syncFromFields(currentTpl)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pre-fill banner / quick picker from vault, subscriptions & deadlines
                if (documents.isNotEmpty() || subscriptions.isNotEmpty() || deadlines.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Pré-remplir automatiquement depuis vos données :",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                subscriptions.take(6).forEach { sub ->
                                    FilterChip(
                                        selected = orgName == sub.name && contractName == sub.name,
                                        onClick = {
                                            orgName = sub.name
                                            contractName = sub.name
                                            amountText = "%.2f".format(sub.amount)
                                            manualTextEdited = false
                                            savedVaultDocId = null
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Subscriptions, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text(sub.name) }
                                    )
                                }
                                documents.take(6).forEach { doc ->
                                    FilterChip(
                                        selected = refNum.isNotBlank() && refNum == doc.referenceNumber,
                                        onClick = {
                                            orgName = doc.issuer.ifBlank { doc.title }
                                            contractName = doc.title
                                            refNum = doc.referenceNumber
                                            if (doc.amount > 0) amountText = "%.2f".format(doc.amount)
                                            manualTextEdited = false
                                            savedVaultDocId = null
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text(doc.title.take(20)) }
                                    )
                                }
                                deadlines.filter { it.status == "pending" }.take(4).forEach { dl ->
                                    FilterChip(
                                        selected = contractName == dl.title,
                                        onClick = {
                                            contractName = dl.title
                                            if (orgName.isBlank()) orgName = dl.category
                                            reasonText = "échéance prévue le ${DateUtils.formatFrench(dl.dueDate)}"
                                            manualTextEdited = false
                                            savedVaultDocId = null
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.EventNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text(dl.title.take(18)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Sender coordinates (auto-prefilled from profile + DataStore)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSenderSection = !showSenderSection },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Vos coordonnées (pré-remplies)", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOf(senderName, senderCity, senderEmail)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                            .ifBlank { "À compléter une seule fois" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                if (showSenderSection) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (showSenderSection) "Masquer" else "Afficher"
                            )
                        }

                        AnimatedVisibility(visible = showSenderSection) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = senderName,
                                    onValueChange = { senderName = it; savedVaultDocId = null },
                                    label = { Text("Nom complet") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = senderAddress,
                                    onValueChange = { senderAddress = it; savedVaultDocId = null },
                                    label = { Text("Adresse postale (rue, code postal)") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = senderCity,
                                        onValueChange = { senderCity = it; savedVaultDocId = null },
                                        label = { Text("Ville") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = senderPhone,
                                        onValueChange = { senderPhone = it; savedVaultDocId = null },
                                        label = { Text("Téléphone") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = senderEmail,
                                    onValueChange = { senderEmail = it; savedVaultDocId = null },
                                    label = { Text("E-mail") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedButton(
                                    onClick = { onSaveSenderDefaults(senderAddress, senderCity, senderPhone) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Mémoriser mes coordonnées pour les prochains courriers")
                                }
                            }
                        }
                    }
                }

                // Request & Recipient fields
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Détails de la demande (${currentTpl.category})", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = orgName,
                                onValueChange = { orgName = it; savedVaultDocId = null },
                                label = { Text("Organisme destinataire") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("letter_org_input")
                            )
                            OutlinedTextField(
                                value = refNum,
                                onValueChange = { refNum = it; savedVaultDocId = null },
                                label = { Text("N° contrat / réf.") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("letter_ref_input")
                            )
                        }
                        OutlinedTextField(
                            value = contractName,
                            onValueChange = { contractName = it; savedVaultDocId = null },
                            label = { Text("Intitulé du contrat / dossier") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (currentTpl.supportsAmount || amountText.isNotBlank()) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it; savedVaultDocId = null },
                                label = { Text("Montant (€)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = orgAddress,
                            onValueChange = { orgAddress = it; savedVaultDocId = null },
                            label = { Text("Adresse de l’organisme (optionnel)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = reasonText,
                            onValueChange = { reasonText = it; savedVaultDocId = null },
                            label = { Text("Motif ou précisions") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (hasHousehold) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Partager ce courrier avec le foyer", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = shareWithHousehold,
                                    onCheckedChange = { shareWithHousehold = it }
                                )
                            }
                        }
                    }
                }

                // Mode Switch: Aperçu PDF vs Édition texte
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = editorMode == "preview",
                        onClick = { editorMode = "preview" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text("Aperçu PDF")
                    }
                    SegmentedButton(
                        selected = editorMode == "edit",
                        onClick = { editorMode = "edit" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text("Éditer le texte")
                    }
                }

                if (editorMode == "edit") {
                    if (manualTextEdited) {
                        OutlinedButton(
                            onClick = { syncFromFields(currentTpl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Réactualiser depuis les champs ci-dessus")
                        }
                    }
                    OutlinedTextField(
                        value = editedSubject,
                        onValueChange = {
                            editedSubject = it
                            manualTextEdited = true
                            savedVaultDocId = null
                        },
                        label = { Text("Objet du courrier") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedBody,
                        onValueChange = {
                            editedBody = it
                            manualTextEdited = true
                            savedVaultDocId = null
                        },
                        label = { Text("Corps du courrier (modifiable)") },
                        minLines = 11,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("letter_body_editor")
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (previewPages.isNotEmpty()) "Aperçu A4 (${previewPages.size} page${if (previewPages.size > 1) "s" else ""})"
                                    else "Aperçu du document PDF",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (isRenderingPdf) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }

                            if (previewPages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Génération de l’aperçu PDF…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                previewPages.forEachIndexed { idx, pageBitmap ->
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shadowElevation = 2.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Image(
                                            bitmap = pageBitmap.asImageBitmap(),
                                            contentDescription = "Aperçu page ${idx + 1} du courrier PDF",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(pageBitmap.width.toFloat() / pageBitmap.height.toFloat().coerceAtLeast(1f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Success banner after vault save
                savedVaultDocId?.let { docId ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("letter_vault_saved_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Courrier PDF enregistré dans le coffre-fort",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Classé dans « Courriers générés » (${currentTpl.category}) avec chiffrement local.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { onOpenSavedDocument(docId) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Ouvrir la fiche dans le coffre-fort")
                            }
                        }
                    }
                }

                // Primary Save PDF to Vault CTA
                Button(
                    onClick = {
                        if (isSavingVault) return@Button
                        isSavingVault = true
                        scope.launch {
                            val finalPdf = withContext(Dispatchers.IO) {
                                PdfGenerator.generateLetterPdf(
                                    context = context,
                                    subject = editedSubject,
                                    body = editedBody,
                                    senderName = senderName.ifBlank { profile?.displayName.orEmpty() },
                                    senderAddress = senderAddress,
                                    senderEmail = senderEmail.ifBlank { profile?.email.orEmpty() },
                                    senderPhone = senderPhone,
                                    senderCity = senderCity,
                                    recipientOrg = orgName,
                                    recipientAddress = orgAddress,
                                    filePrefix = "courrier_${currentTpl.id}"
                                )
                            }
                            previewPdfFile = finalPdf
                            val parsedAmount = amountText.replace(",", ".").replace("€", "").trim().toDoubleOrNull() ?: 0.0
                            onSaveGeneratedLetterToVault(
                                currentTpl,
                                editedSubject,
                                editedBody,
                                orgName,
                                refNum,
                                parsedAmount,
                                finalPdf,
                                shareWithHousehold,
                                { savedId ->
                                    isSavingVault = false
                                    savedVaultDocId = savedId
                                },
                                {
                                    isSavingVault = false
                                }
                            )
                        }
                    },
                    enabled = !isSavingVault && editedBody.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_letter_vault_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isSavingVault) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Outlined.BookmarkAdded, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (savedVaultDocId != null) "Générer une nouvelle version au coffre-fort"
                        else "Générer PDF et enregistrer au coffre-fort"
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString("Objet : $editedSubject\n\n$editedBody"))
                            onShowMessage("Courrier copié dans le presse-papiers.")
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Copier")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val file = previewPdfFile ?: withContext(Dispatchers.IO) {
                                    PdfGenerator.generateLetterPdf(
                                        context = context,
                                        subject = editedSubject,
                                        body = editedBody,
                                        senderName = senderName.ifBlank { profile?.displayName.orEmpty() },
                                        senderAddress = senderAddress,
                                        senderEmail = senderEmail.ifBlank { profile?.email.orEmpty() },
                                        senderPhone = senderPhone,
                                        senderCity = senderCity,
                                        recipientOrg = orgName,
                                        recipientAddress = orgAddress,
                                        filePrefix = "courrier_${currentTpl.id}"
                                    )
                                }
                                PdfGenerator.shareFile(context, file, "application/pdf", "Exporter le courrier en PDF")
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Partager PDF")
                    }
                }
            }
        }
    }
}

private fun categoryIconForTemplate(category: String): ImageVector = when (category) {
    "Abonnements" -> Icons.Outlined.Subscriptions
    "Assurance" -> Icons.Outlined.Shield
    "Banque", "Impôts" -> Icons.Outlined.AccountBalance
    "Logement" -> Icons.Outlined.Home
    "Santé" -> Icons.Outlined.LocalHospital
    "Travail" -> Icons.Outlined.WorkOutline
    else -> Icons.Outlined.MailOutline
}
