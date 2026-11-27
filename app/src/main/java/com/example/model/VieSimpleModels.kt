package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object VieSimpleCategories {
    val ALL = listOf(
        "Assurance",
        "Santé",
        "Logement",
        "Véhicule",
        "Impôts",
        "Banque",
        "Famille",
        "Abonnements",
        "Travail",
        "Éducation",
        "Administratif",
        "Autre"
    )
}

data class ChecklistTemplateDef(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val items: List<String>
)

object ChecklistTemplates {
    val ALL = listOf(
        ChecklistTemplateDef(
            id = "demenagement",
            title = "Déménagement",
            category = "Logement",
            description = "Préparer son départ et l’installation dans le nouveau logement.",
            items = listOf(
                "0|Envoyer le préavis de départ par lettre recommandée",
                "0|Résilier ou transférer le contrat électricité / gaz",
                "0|Mettre à jour l’assurance habitation",
                "0|Déclarer le changement d’adresse aux impôts et à la CAF",
                "0|Réaliser l’état des lieux de sortie",
                "0|Mettre en place le suivi de courrier La Poste"
            )
        ),
        ChecklistTemplateDef(
            id = "rentree_scolaire",
            title = "Rentrée scolaire",
            category = "Éducation",
            description = "Documents et démarches indispensables pour la rentrée.",
            items = listOf(
                "0|Télécharger l’attestation d’assurance scolaire",
                "0|Fournir le certificat médical pour les activités sportives",
                "0|Mettre à jour la fiche sanitaire et contacts d’urgence",
                "0|Renouveler la carte de transport scolaire",
                "0|Inscrire à la cantine et au périscolaire"
            )
        ),
        ChecklistTemplateDef(
            id = "voyage",
            title = "Voyage",
            category = "Famille",
            description = "Vérifications avant le départ en vacances ou à l’étranger.",
            items = listOf(
                "0|Vérifier la date d’expiration des passeports / cartes d’identité",
                "0|Télécharger la carte européenne d’assurance maladie",
                "0|Sauvegarder les billets et réservations dans le coffre-fort",
                "0|Vérifier la couverture d’assurance voyage / carte bancaire",
                "0|Activer l’option internationale si nécessaire"
            )
        ),
        ChecklistTemplateDef(
            id = "assurance",
            title = "Assurance",
            category = "Assurance",
            description = "Réviser et optimiser les contrats d’assurance du foyer.",
            items = listOf(
                "0|Rassembler les échéanciers annuels",
                "0|Vérifier les dates limites de résiliation (loi Hamon / Chatel)",
                "0|Comparer les garanties et franchises",
                "0|Archiver les attestations à jour dans le coffre-fort"
            )
        ),
        ChecklistTemplateDef(
            id = "impots",
            title = "Impôts",
            category = "Impôts",
            description = "Préparer sereinement la déclaration annuelle de revenus.",
            items = listOf(
                "0|Rassembler les attestations fiscales et reçus de dons",
                "0|Vérifier les justificatifs de garde d’enfants ou emploi à domicile",
                "0|Contrôler les montants pré-remplis",
                "0|Sauvegarder l’avis d’imposition en PDF"
            )
        ),
        ChecklistTemplateDef(
            id = "naissance",
            title = "Naissance",
            category = "Famille",
            description = "Démarches essentielles après l’arrivée d’un enfant.",
            items = listOf(
                "0|Déclarer la naissance en mairie (dans les 5 jours)",
                "0|Rattacher l’enfant à l’Assurance Maladie et à la mutuelle",
                "0|Mettre à jour le dossier CAF / Pajemploi",
                "0|Informer l’employeur et l’administration fiscale",
                "0|Archiver l’acte de naissance dans le coffre-fort"
            )
        ),
        ChecklistTemplateDef(
            id = "changement_adresse",
            title = "Changement d’adresse",
            category = "Administratif",
            description = "Prévenir tous les organismes officiels et prestataires.",
            items = listOf(
                "0|Mettre à jour la carte grise (dans le mois suivant)",
                "0|Prévenir la banque et les assureurs",
                "0|Mettre à jour l’adresse sur service-public.fr",
                "0|Informer l’employeur et la mutuelle"
            )
        ),
        ChecklistTemplateDef(
            id = "renouvellement_docs",
            title = "Renouvellement de documents",
            category = "Administratif",
            description = "Anticiper le renouvellement des titres officiels.",
            items = listOf(
                "0|Faire une pré-demande en ligne sur ANTS",
                "0|Réaliser une photo d’identité conforme",
                "0|Préparer un justificatif de domicile de moins de 6 mois",
                "0|Prendre rendez-vous en mairie",
                "0|Acheter le timbre fiscal si requis"
            )
        )
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val profileType: String = "solo",
    val planTier: String = "free",
    val onboardingCompleted: Boolean = false,
    val activeHouseholdId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(80).ifBlank { "Utilisateur VieSimple" },
        "email" to email.take(160),
        "profileType" to profileType,
        "planTier" to planTier,
        "onboardingCompleted" to onboardingCompleted,
        "activeHouseholdId" to activeHouseholdId.take(128),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Household(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val lastInviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HouseholdMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val householdId: String = "",
    val displayName: String = "",
    val role: String = "member",
    val status: String = "active",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VaultDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerName: String = "",
    val householdId: String = "",
    val visibility: String = "private",
    val sharedWithUids: List<String> = emptyList(),
    val title: String = "",
    val category: String = "Autre",
    val docType: String = "",
    val folder: String = "Général",
    val docDate: String = "",
    val expirationDate: String = "",
    val issuer: String = "",
    val referenceNumber: String = "",
    val amount: Double = 0.0,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val confidentiality: String = "standard",
    val status: String = "active",
    val favorite: Boolean = false,
    val ocrText: String = "",
    val ocrConfidence: String = "manual",
    val localFileUri: String = "",
    val versionNumber: Long = 1L,
    val versionHistory: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Deadline(
    @DocumentId val id: String = "",
    val userId: String = "",
    val householdId: String = "",
    val visibility: String = "private",
    val sharedWithUids: List<String> = emptyList(),
    val title: String = "",
    val description: String = "",
    val category: String = "Administratif",
    val dueDate: String = "",
    val recurrence: String = "none",
    val customRecurrenceDays: Long = 0L,
    val reminders: List<Long> = listOf(7L, 3L, 1L, 0L),
    val status: String = "pending",
    val priority: String = "medium",
    val linkedDocumentId: String = "",
    val linkedTaskId: String = "",
    val assigneeUid: String = "",
    val assigneeName: String = "",
    val confidentiality: String = "standard",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SubscriptionItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val householdId: String = "",
    val visibility: String = "private",
    val sharedWithUids: List<String> = emptyList(),
    val name: String = "",
    val category: String = "Abonnements",
    val amount: Double = 0.0,
    val currency: String = "EUR",
    val frequency: String = "monthly",
    val nextBillingDate: String = "",
    val paymentMethod: String = "",
    val status: String = "active",
    val cancellationStatus: String = "none",
    val notes: String = "",
    val linkedDocumentId: String = "",
    val alertDaysBefore: Long = 3L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun monthlyEquivalent(): Double = when (frequency) {
        "yearly" -> amount / 12.0
        "quarterly" -> amount / 3.0
        else -> amount
    }

    fun annualCost(): Double = monthlyEquivalent() * 12.0
}

@Keep
@IgnoreExtraProperties
data class HouseholdTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val householdId: String = "",
    val visibility: String = "private",
    val sharedWithUids: List<String> = emptyList(),
    val title: String = "",
    val description: String = "",
    val category: String = "Famille",
    val dueDate: String = "",
    val priority: String = "medium",
    val assigneeUid: String = "",
    val assigneeName: String = "",
    val status: String = "todo",
    val subtasks: List<String> = emptyList(),
    val checklistTemplate: String = "",
    val linkedDocumentId: String = "",
    val linkedDeadlineId: String = "",
    val confidentiality: String = "standard",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HouseholdActivity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorName: String = "",
    val householdId: String = "",
    val sharedWithUids: List<String> = emptyList(),
    val message: String = "",
    val actionType: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SecurityLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val action: String = "",
    val detail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AiMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = "user",
    val text: String = "",
    val proposedAction: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
