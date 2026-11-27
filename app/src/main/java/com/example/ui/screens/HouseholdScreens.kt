package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Household
import com.example.model.HouseholdActivity
import com.example.model.HouseholdMember
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdScreen(
    householdsState: UiState<List<Household>>,
    activeHousehold: Household?,
    members: List<HouseholdMember>,
    activities: List<HouseholdActivity>,
    currentUserId: String,
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenInvite: () -> Unit,
    onCreateHousehold: (String) -> Unit,
    onSwitchHousehold: (String) -> Unit,
    onLeaveHousehold: (Household) -> Unit,
    onDeleteHousehold: (Household) -> Unit
) {
    val households = (householdsState as? UiState.Success)?.data ?: emptyList()
    var newHouseholdName by remember { mutableStateOf("") }
    var showCreateBox by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf<Household?>(null) }
    var confirmDelete by remember { mutableStateOf<Household?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Espace familial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateBox = !showCreateBox }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Créer un foyer")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column {
                        Image(
                            painter = painterResource(id = R.drawable.img_family_hub_banner_1795607684125),
                            contentDescription = "Illustration espace familial VieSimple",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Vos documents restent privés par défaut. Seuls les éléments marqués « Foyer » sont visibles par les membres.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible = showCreateBox || activeHousehold == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Créer un nouveau foyer", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = newHouseholdName,
                                onValueChange = { newHouseholdName = it },
                                label = { Text("Nom du foyer") },
                                placeholder = { Text("Ex : Famille Dupont") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("create_household_name_input")
                            )
                            Button(
                                onClick = {
                                    if (newHouseholdName.isNotBlank()) {
                                        onCreateHousehold(newHouseholdName.trim())
                                        newHouseholdName = ""
                                        showCreateBox = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("create_household_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Créer le foyer")
                            }
                        }
                    }
                }
            }

            if (households.size > 1) {
                item {
                    Text("Mes foyers", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        households.forEach { hh ->
                            FilterChip(
                                selected = activeHousehold?.id == hh.id,
                                onClick = { onSwitchHousehold(hh.id) },
                                label = { Text(hh.name) }
                            )
                        }
                    }
                }
            }

            if (activeHousehold != null) {
                val isOwner = activeHousehold.ownerId == currentUserId
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(activeHousehold.name, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "${members.size.coerceAtLeast(activeHousehold.memberUids.size)} membre(s) · Code : ${activeHousehold.inviteCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = onOpenInvite,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Inviter / Code")
                                }
                                OutlinedButton(
                                    onClick = onOpenMembers,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Membres & rôles")
                                }
                            }
                            if (isOwner) {
                                OutlinedButton(
                                    onClick = { confirmDelete = activeHousehold },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Supprimer ce foyer")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { confirmLeave = activeHousehold },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Outlined.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Quitter ce foyer")
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Journal d’activité du foyer", style = MaterialTheme.typography.titleMedium)
                }

                val hhActivities = activities.filter { it.householdId == activeHousehold.id }
                if (hhActivities.isEmpty()) {
                    item {
                        Text(
                            "Aucune activité partagée pour le moment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(hhActivities.take(25), key = { it.id }) { act ->
                        Card(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(act.message, style = MaterialTheme.typography.bodyMedium)
                                Text(act.actorName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                item {
                    OutlinedButton(onClick = onOpenInvite, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                        Text("Rejoindre un foyer avec un code d’invitation")
                    }
                }
            }
        }
    }

    confirmLeave?.let { hh ->
        ConfirmActionDialog(
            title = "Quitter le foyer « ${hh.name} » ?",
            message = "Vous n’aurez plus accès aux éléments partagés de ce foyer. Vos documents privés restent intacts.",
            confirmLabel = "Quitter",
            isDestructive = true,
            onConfirm = {
                confirmLeave = null
                onLeaveHousehold(hh)
            },
            onDismiss = { confirmLeave = null }
        )
    }

    confirmDelete?.let { hh ->
        ConfirmActionDialog(
            title = "Supprimer le foyer « ${hh.name} » ?",
            message = "Cette action dissout le foyer pour tous les membres. Les documents privés de chaque membre restent intacts.",
            confirmLabel = "Supprimer",
            isDestructive = true,
            onConfirm = {
                confirmDelete = null
                onDeleteHousehold(hh)
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdMembersScreen(
    household: Household?,
    members: List<HouseholdMember>,
    currentUserId: String,
    onBack: () -> Unit,
    onUpdateRole: (Household, String, String) -> Unit,
    onRemoveMember: (Household, String) -> Unit
) {
    var removeTarget by remember { mutableStateOf<HouseholdMember?>(null) }
    val canManage = household != null && (household.ownerId == currentUserId || currentUserId in household.adminUids)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Membres & rôles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (household == null) {
                item { Text("Aucun foyer actif.") }
            } else {
                items(members, key = { it.id }) { member ->
                    val isOwnerMember = member.userId == household.ownerId
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(roleLabel(member.role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (canManage && !isOwnerMember && member.userId != currentUserId) {
                                    IconButton(onClick = { removeTarget = member }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Retirer du foyer", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (canManage && !isOwnerMember) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("admin" to "Admin", "member" to "Membre", "guest" to "Invité").forEach { (roleKey, label) ->
                                        FilterChip(
                                            selected = member.role == roleKey,
                                            onClick = { onUpdateRole(household, member.userId, roleKey) },
                                            label = { Text(label) }
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

    removeTarget?.let { target ->
        if (household != null) {
            ConfirmActionDialog(
                title = "Retirer ${target.displayName} ?",
                message = "Ce membre n’aura plus accès aux éléments du foyer. Ses documents privés personnels ne seront pas supprimés.",
                confirmLabel = "Retirer",
                isDestructive = true,
                onConfirm = {
                    removeTarget = null
                    onRemoveMember(household, target.userId)
                },
                onDismiss = { removeTarget = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdInviteScreen(
    household: Household?,
    onBack: () -> Unit,
    onJoinWithCode: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var joinCode by remember { mutableStateOf("") }
    var confirmJoinCode by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inviter ou rejoindre") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (household != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Inviter un proche dans « ${household.name} »", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Partagez ce code uniquement avec une personne de confiance :",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                clipboard.setText(AnnotatedString(household.inviteCode))
                                onShowMessage("Code d’invitation copié.")
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                household.inviteCode,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(household.inviteCode))
                                onShowMessage("Code d’invitation copié.")
                            }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copier le code")
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Rejoindre un foyer existant", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase() },
                        label = { Text("Code d’invitation (ex : VS7K9P2M)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("join_household_code_input")
                    )
                    Button(
                        onClick = {
                            if (joinCode.trim().length >= 6) {
                                confirmJoinCode = joinCode.trim()
                            } else {
                                onShowMessage("Saisissez un code d’invitation valide.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("join_household_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Rejoindre ce foyer")
                    }
                }
            }
        }
    }

    confirmJoinCode?.let { code ->
        ConfirmActionDialog(
            title = "Rejoindre ce foyer ?",
            message = "En rejoignant le foyer ($code), les éléments marqués « Foyer » pourront être consultés avec ses membres.",
            confirmLabel = "Rejoindre",
            onConfirm = {
                confirmJoinCode = null
                onJoinWithCode(code)
                onBack()
            },
            onDismiss = { confirmJoinCode = null }
        )
    }
}

private fun roleLabel(role: String): String = when (role) {
    "owner" -> "Propriétaire"
    "admin" -> "Administrateur"
    "guest" -> "Invité (lecture limitée)"
    else -> "Membre"
}
