package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingWizardScreen(
    displayName: String,
    onComplete: (profileType: String, householdName: String?, goToScan: Boolean) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedProfile by remember { mutableStateOf("solo") }
    var householdName by remember { mutableStateOf("") }
    var notifPromptStatus by remember { mutableStateOf<String?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifPromptStatus = if (granted) "Notifications activées avec succès." else "Vous pourrez les activer plus tard dans Paramètres."
        step = 2
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (step + 1) / 3f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = "Étape ${step + 1} sur 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                when (step) {
                    0 -> {
                        Text(
                            text = "Bienvenue ${displayName.substringBefore(" ")} ! Quel est votre profil ?",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "VieSimple s’adapte à votre situation pour organiser vos documents privés et partagés.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val options = listOf(
                            Triple("solo", "Je suis seul(e)", Icons.Outlined.Person),
                            Triple("couple", "Nous sommes en couple", Icons.Outlined.FavoriteBorder),
                            Triple("family", "Nous sommes une famille", Icons.Outlined.FamilyRestroom),
                            Triple("roommates", "Nous sommes en colocation", Icons.Outlined.Group)
                        )
                        options.forEach { (key, label, icon) ->
                            val selected = selectedProfile == key
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProfile = key }
                                    .testTag("profile_option_$key"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    if (selected) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = "Sélectionné", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        if (selectedProfile != "solo") {
                            OutlinedTextField(
                                value = householdName,
                                onValueChange = { householdName = it },
                                label = { Text("Nom de votre foyer (optionnel)") },
                                placeholder = { Text("Ex : Foyer Martin") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = { step = 1 },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_next_btn"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Continuer")
                        }

                        TextButton(
                            onClick = {
                                onComplete(
                                    selectedProfile,
                                    householdName.takeIf { it.isNotBlank() },
                                    false
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onboarding_direct_dashboard_btn")
                        ) {
                            Text("Accéder directement au tableau de bord")
                        }
                    }

                    1 -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Text(
                            text = "Ne manquez plus aucune échéance",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "VieSimple vous prévient avant l’expiration d’un contrat, d’une pièce d’identité ou d’un prélèvement. Par confidentialité, nos notifications n’affichent jamais de données sensibles sur l’écran verrouillé.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    step = 2
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("enable_notifications_btn"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Activer les rappels utiles")
                        }

                        TextButton(
                            onClick = { step = 2 },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Activer plus tard")
                        }
                    }

                    else -> {
                        notifPromptStatus?.let {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                            }
                        }

                        Text(
                            text = "Ajoutez votre premier document",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Centralisez dès maintenant un premier papier important. Exemples recommandés :",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val examples = listOf(
                            "Attestation d’assurance habitation ou auto",
                            "Facture d’électricité ou de téléphonie",
                            "Carte d’identité ou passeport",
                            "Contrat de bail ou titre de propriété"
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                examples.forEach { ex ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(ex, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onComplete(
                                    selectedProfile,
                                    householdName.takeIf { it.isNotBlank() },
                                    true
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_scan_first_btn"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Scanner ou importer un document", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                onComplete(
                                    selectedProfile,
                                    householdName.takeIf { it.isNotBlank() },
                                    false
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("onboarding_finish_btn"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Accéder à mon tableau de bord")
                        }
                    }
                }
            }
        }
    }
}
