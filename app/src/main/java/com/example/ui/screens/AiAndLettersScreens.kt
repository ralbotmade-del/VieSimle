package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AiMessageRecord
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdTask
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.viewmodel.AiChatState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    messages: List<AiMessageRecord>,
    chatState: AiChatState,
    onBack: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onClearHistory: () -> Unit,
    onConfirmCreateDeadline: (Deadline) -> Unit,
    onConfirmCreateTask: (HouseholdTask) -> Unit,
    onOpenLetterTemplate: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var pendingProposition by remember { mutableStateOf<String?>(null) }

    val quickPrompts = listOf(
        "Qu’est-ce qui expire bientôt ?",
        "Quoi faire cette semaine ?",
        "Résume mes abonnements actifs",
        "Prépare un courrier de résiliation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistant VieSimple") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Effacer l’historique")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Image(
                        painter = painterResource(id = R.drawable.img_ai_banner_1795532519654),
                        contentDescription = "Assistant VieSimple",
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                        Text(
                            "Aide administrative basée uniquement sur vos documents, échéances et abonnements autorisés. Ne constitue pas un conseil juridique, fiscal ou médical personnalisé.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (messages.isEmpty()) {
                    item {
                        Text("Suggestions rapides :", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickPrompts.forEach { suggestion ->
                                OutlinedButton(
                                    onClick = { onSendPrompt(suggestion) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text(suggestion)
                                }
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.sender == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = if (isUser) 0.dp else 1.dp,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (msg.proposedAction.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { pendingProposition = msg.proposedAction },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Valider l’action proposée")
                                    }
                                }
                            }
                        }
                    }
                }

                when (chatState) {
                    is AiChatState.Loading -> item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("L’assistant analyse vos informations…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is AiChatState.Streaming -> item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Text(
                                text = chatState.partialText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    is AiChatState.Error -> item {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                            Text(
                                chatState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    else -> Unit
                }
            }

            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Posez votre question administrative…") },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).testTag("ai_prompt_input")
                    )
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendPrompt(inputText)
                                inputText = ""
                            }
                        },
                        enabled = chatState !is AiChatState.Loading && chatState !is AiChatState.Streaming,
                        modifier = Modifier.size(48.dp).testTag("ai_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    pendingProposition?.let { raw ->
        val parts = raw.split("|")
        val kind = parts.firstOrNull().orEmpty()
        val label = parts.getOrNull(1).orEmpty().ifBlank { "Action VieSimple" }
        val extra = parts.getOrNull(2).orEmpty()
        val cat = parts.getOrNull(3).orEmpty().ifBlank { "Administratif" }
        ConfirmActionDialog(
            title = "Confirmer l’action proposée ?",
            message = when (kind) {
                "CREATE_DEADLINE", "DEADLINE" -> "Créer l’échéance « $label »${if (extra.isNotBlank()) " pour le $extra" else ""} ?"
                "CREATE_TASK", "TASK" -> "Ajouter la tâche « $label » ?"
                "OPEN_LETTER", "LETTER" -> "Ouvrir le modèle de courrier correspondant ($label) ?"
                else -> "Exécuter cette action ($label) ?"
            },
            confirmLabel = "Confirmer",
            onConfirm = {
                pendingProposition = null
                when (kind) {
                    "CREATE_DEADLINE", "DEADLINE" -> onConfirmCreateDeadline(
                        Deadline(
                            title = label,
                            dueDate = if (DateUtils.parseIso(extra) != null) extra else DateUtils.addDaysIso(DateUtils.todayIso(), 7),
                            category = cat
                        )
                    )
                    "CREATE_TASK", "TASK" -> onConfirmCreateTask(
                        HouseholdTask(
                            title = label,
                            dueDate = if (DateUtils.parseIso(extra) != null) extra else DateUtils.addDaysIso(DateUtils.todayIso(), 3),
                            category = cat
                        )
                    )
                    "OPEN_LETTER", "LETTER" -> onOpenLetterTemplate(label)
                }
            },
            onDismiss = { pendingProposition = null }
        )
    }
}
