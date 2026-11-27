package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiMessageRecord
import com.example.model.VieSimpleCategories
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ExtractedDocumentInfo(
    val title: String,
    val category: String,
    val docType: String,
    val docDate: String,
    val expirationDate: String,
    val amount: Double,
    val issuer: String,
    val referenceNumber: String,
    val tags: List<String>,
    val ocrText: String,
    val confidence: String,
    val uncertaintyNote: String,
    val categoryRationale: String = ""
)

data class AutoCategorizationResult(
    val category: String,
    val docType: String,
    val tags: List<String>,
    val rationale: String,
    val confidence: String
)

class AiAssistantRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Tu es l'assistant administratif et familial VieSimple, bienveillant, clair et prudent.
                    Règles strictes :
                    1. Réponds toujours en français simple, sans jargon administratif inutile.
                    2. Base-toi uniquement sur le contexte fourni (documents, échéances, abonnements et tâches auxquels l'utilisateur a accès). Ne devine jamais des données absentes.
                    3. Si une information est manquante ou incertaine, dis-le clairement ("Information incertaine ou absente de vos documents").
                    4. Ne donne JAMAIS de conseil juridique, fiscal ou médical définitif. Rappelle brièvement si pertinent qu'il s'agit d'une aide organisationnelle.
                    5. Tu ne modifies ni ne supprimes jamais rien automatiquement. Si une action est pertinente, termine ta réponse par une ligne exacte :
                       [PROPOSITION:CREATE_DEADLINE|Titre court|YYYY-MM-DD|Catégorie]
                       ou [PROPOSITION:CREATE_TASK|Titre court|YYYY-MM-DD|Catégorie]
                       ou [PROPOSITION:OPEN_LETTER|ID_MODELE] (ex: resiliation_abonnement, resiliation_assurance, reclamation, contestation_facture, changement_adresse).
                    """.trimIndent()
                )
            }
        )
    }

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Titre clair et concis du document"),
            "category" to Schema.enumeration(
                values = VieSimpleCategories.ALL,
                description = "Catégorie parmi la liste prédéfinie"
            ),
            "docType" to Schema.string(description = "Type de document (Contrat, Facture, Attestation, Pièce d'identité, Avis...)"),
            "docDate" to Schema.string(description = "Date du document au format YYYY-MM-DD, ou chaîne vide si absente"),
            "expirationDate" to Schema.string(description = "Date d'expiration ou de renouvellement au format YYYY-MM-DD, ou chaîne vide si absente"),
            "amount" to Schema.double(description = "Montant principal en euros, ou 0.0 si absent"),
            "issuer" to Schema.string(description = "Nom de l'organisme émetteur, ou chaîne vide"),
            "referenceNumber" to Schema.string(description = "Numéro de contrat, facture ou référence, ou chaîne vide"),
            "tags" to Schema.array(
                items = Schema.string(),
                description = "3 à 6 tags sémantiques automatiques basés sur le contenu : inclure d'abord le tag canonique anglais pertinent (ex: Insurance, Bill, Tax, Health, Housing, Bank, Vehicle, Work, Education, Family, Subscription, Contract, Administrative) puis des mots-clés spécifiques"
            ),
            "categoryRationale" to Schema.string(description = "Courte justification expliquant la catégorie et les tags attribués d'après l'analyse du document"),
            "ocrText" to Schema.string(description = "Transcription textuelle complète lisible (français ou anglais)"),
            "confidence" to Schema.enumeration(values = listOf("high", "medium", "low"), description = "Niveau de confiance global de l'extraction"),
            "uncertaintyNote" to Schema.string(description = "Avertissement court en français si certaines données sont floues ou incertaines")
        )
    )

    private val autoTagSchema = Schema.obj(
        properties = mapOf(
            "category" to Schema.enumeration(
                values = VieSimpleCategories.ALL,
                description = "Catégorie principale VieSimple"
            ),
            "docType" to Schema.string(description = "Type précis du document (Facture, Contrat, Attestation, Avis d'imposition...)"),
            "tags" to Schema.array(
                items = Schema.string(),
                description = "3 à 6 tags automatiques basés sur l'analyse du contenu (inclure les tags canoniques pertinents comme Insurance, Bill, Tax, Health, Housing, Bank, Vehicle, Subscription, Contract, Identity, Receipt et des mots-clés contextuels)"
            ),
            "rationale" to Schema.string(description = "Explication concise (1 phrase) du classement et des tags choisis"),
            "confidence" to Schema.enumeration(values = listOf("high", "medium", "low"), description = "Confiance du classement")
        )
    )

    private val extractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
            }
        )
    }

    private val autoTagModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = autoTagSchema
            }
        )
    }

    suspend fun analyzeDocumentImage(bitmap: Bitmap): Result<ExtractedDocumentInfo> {
        return try {
            val response = withTimeout(60_000L) {
                extractionModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            """
                            Effectue une reconnaissance optique de caractères (OCR) complète de ce document administratif (français ou anglais), puis extrais, classe et étiquette automatiquement :
                            - category : choisis OBLIGATOIREMENT la catégorie la plus pertinente parmi :
                              • Impôts (avis d'imposition, taxe foncière, taxe d'habitation, prélèvement à la source)
                              • Assurance (attestation auto/habitation/scolaire, avis d'échéance, contrat d'assurance)
                              • Santé (mutuelle, carte Vitale, CPAM, ordonnance, facture médicale)
                              • Logement (bail, quittance de loyer, facture électricité/gaz/eau, acte de propriété)
                              • Véhicule (carte grise, certificat d'immatriculation, contrôle technique, permis)
                              • Banque (RIB, relevé de compte, offre de prêt, attestation bancaire)
                              • Travail (bulletin de paie, contrat de travail, attestation France Travail / employeur)
                              • Éducation (certificat de scolarité, diplôme, bourse, inscription)
                              • Famille (livret de famille, CAF, acte de naissance/mariage)
                              • Abonnements (facture mobile, internet/fibre, forfait, streaming)
                              • Administratif (CNI, passeport, justificatif de domicile, courrier mairie/préfecture, titre officiel)
                              • Autre (uniquement si aucune catégorie ci-dessus ne correspond)
                            - tags : génère 3 à 6 tags automatiques pertinents d'après le contenu réel. Commence par les tags thématiques canoniques anglais adaptés (par exemple 'Insurance', 'Bill', 'Tax', 'Housing', 'Health', 'Bank', 'Vehicle', 'Subscription', 'Contract', 'Administrative') suivis de mots-clés précis (émetteur, année, objet).
                            - categoryRationale : explique brièvement pourquoi cette catégorie et ces tags ont été retenus d'après le texte du document.
                            - docType : type précis du document en français (ex. Facture, Contrat, Attestation, Avis d'imposition, Quittance, Pièce d'identité, Garantie, Relevé).
                            - docDate : date principale d'émission du document au format ISO YYYY-MM-DD (convertis les formats français comme JJ/MM/AAAA ou « 24 novembre 2026 »), ou "" si absente.
                            - expirationDate : date d'échéance, de validité ou de renouvellement au format ISO YYYY-MM-DD, ou "" si absente.
                            - amount : montant total TTC ou montant principal en euros (nombre décimal positif), ou 0.0 s'il n'y a aucun montant.
                            - issuer, referenceNumber, title (concis et explicite), et ocrText (transcription textuelle fidèle).
                            Si un champ est illisible ou incertain, laisse-le vide (ou 0.0 pour amount) et indique confidence='low' ou 'medium' avec une note d'incertitude en français.
                            """.trimIndent()
                        )
                    }
                )
            }
            val rawJson = response.text ?: return Result.failure(IllegalStateException("Réponse vide du service d’analyse."))
            Result.success(parseExtractedJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "OCR analysis timed out", e)
            Result.failure(IllegalStateException("L’analyse a expiré après 60 secondes. Vous pouvez enregistrer manuellement."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OCR analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeDocumentText(rawText: String): Result<ExtractedDocumentInfo> {
        return try {
            val response = withTimeout(60_000L) {
                extractionModel.generateContent(
                    """
                    Analyse ce texte extrait d'un document administratif (français ou anglais), classe-le automatiquement dans la meilleure catégorie (${VieSimpleCategories.ALL.joinToString(", ")}) et extrais :
                    - docType (type de document en français)
                    - docDate et expirationDate normalisées au format YYYY-MM-DD (ou "" si absentes)
                    - amount (montant principal en euros, ou 0.0 si absent)
                    - issuer, referenceNumber, title, category, tags (3 à 6 tags incluant des tags canoniques comme 'Insurance', 'Bill', 'Tax', etc.), categoryRationale et ocrText.
                    Indique confidence='low' ou 'medium' si un champ est incertain.

                    Texte du document :
                    $rawText
                    """.trimIndent()
                )
            }
            val rawJson = response.text ?: return Result.failure(IllegalStateException("Réponse vide du service d’analyse."))
            Result.success(parseExtractedJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("L’analyse a expiré. Vous pouvez saisir les champs manuellement."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction failed", e)
            Result.failure(e)
        }
    }

    suspend fun autoCategorizeDocument(
        title: String,
        docType: String,
        issuer: String,
        ocrText: String,
        notes: String
    ): Result<AutoCategorizationResult> {
        val analysisPayload = buildString {
            if (title.isNotBlank()) appendLine("Titre: $title")
            if (docType.isNotBlank()) appendLine("Type: $docType")
            if (issuer.isNotBlank()) appendLine("Organisme: $issuer")
            if (notes.isNotBlank()) appendLine("Notes: $notes")
            if (ocrText.isNotBlank()) appendLine("Contenu OCR:\n${ocrText.take(6000)}")
        }.trim()

        if (analysisPayload.isBlank()) {
            return Result.failure(IllegalStateException("Ajoutez un titre ou du texte OCR avant de lancer la catégorisation IA."))
        }

        return try {
            val response = withTimeout(60_000L) {
                autoTagModel.generateContent(
                    """
                    Analyse le contenu de ce document et attribue automatiquement :
                    1. category : la catégorie exacte parmi ${VieSimpleCategories.ALL.joinToString(", ")}.
                    2. docType : le type de document précis.
                    3. tags : 3 à 6 tags sémantiques pertinents basés sur le contenu, en incluant les tags canoniques anglais correspondants (ex: 'Insurance', 'Bill', 'Tax', 'Housing', 'Health', 'Bank', 'Vehicle', 'Subscription', 'Contract', 'Administrative') ainsi que des mots-clés utiles (organisme, sujet).
                    4. rationale : 1 phrase courte expliquant pourquoi cette catégorie et ces tags correspondent au contenu.

                    Document à analyser :
                    $analysisPayload
                    """.trimIndent()
                )
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Réponse vide du service de catégorisation Gemini."))
            Result.success(parseAutoCategorizationJson(rawJson, title, docType, issuer, ocrText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Auto-categorization timed out", e)
            Result.failure(IllegalStateException("La catégorisation Gemini a expiré après 60 secondes."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Auto-categorization failed", e)
            Result.failure(e)
        }
    }

    private fun safeOptString(json: JSONObject, key: String): String {
        if (!json.has(key) || json.isNull(key)) return ""
        val value = json.optString(key, "").trim()
        return if (value.equals("null", ignoreCase = true)) "" else value
    }

    private fun inferCategoryIfNeeded(
        rawCategory: String,
        docType: String,
        title: String,
        issuer: String,
        ocrText: String
    ): String {
        val validRaw = rawCategory.takeIf { it in VieSimpleCategories.ALL }
        if (validRaw != null && validRaw != "Autre") return validRaw

        val corpus = "$docType $title $issuer $ocrText".lowercase()
        return when {
            listOf("impôt", "impot", "dgfip", "taxe foncière", "taxe d'habitation", "avis d'imposition", "fiscal").any { it in corpus } -> "Impôts"
            listOf("assurance", "axa", "maif", "macif", "allianz", "matmut", "gmf", "sinistre", "responsabilité civile").any { it in corpus } -> "Assurance"
            listOf("mutuelle", "cpam", "ameli", "vitale", "santé", "sante", "ordonnance", "médecin", "hôpital").any { it in corpus } -> "Santé"
            listOf("loyer", "quittance", "bail", "edf", "engie", "veolia", "eau", "électricité", "gaz", "syndic", "habitation").any { it in corpus } -> "Logement"
            listOf("carte grise", "immatriculation", "contrôle technique", "permis de conduire", "véhicule", "vehicule").any { it in corpus } -> "Véhicule"
            listOf("rib", "iban", "banque", "crédit", "credit", "relevé bancaire", "bnp", "société générale", "boursorama").any { it in corpus } -> "Banque"
            listOf("bulletin de paie", "fiche de paie", "salaire", "contrat de travail", "employeur", "urssaf", "france travail", "pôle emploi").any { it in corpus } -> "Travail"
            listOf("scolarité", "diplôme", "diplome", "université", "école", "lycée", "bourse étudiante").any { it in corpus } -> "Éducation"
            listOf("caf", "allocation", "livret de famille", "naissance", "mariage", "enfant").any { it in corpus } -> "Famille"
            listOf("abonnement", "orange", "sfr", "bouygues", "free", "fibre", "forfait", "mobile", "internet").any { it in corpus } -> "Abonnements"
            listOf("carte nationale", "passeport", "préfecture", "mairie", "justificatif", "attestation", "cerfa", "république française").any { it in corpus } -> "Administratif"
            else -> validRaw ?: "Administratif"
        }
    }

    private fun parseExtractedJson(jsonString: String): ExtractedDocumentInfo {
        val json = JSONObject(jsonString)
        val tagsArr = json.optJSONArray("tags")
        val rawTags = mutableListOf<String>()
        if (tagsArr != null) {
            for (i in 0 until minOf(tagsArr.length(), 8)) {
                val t = tagsArr.optString(i).trim()
                if (t.isNotBlank() && !t.equals("null", ignoreCase = true)) rawTags.add(t)
            }
        }
        val docType = safeOptString(json, "docType")
        val issuer = safeOptString(json, "issuer")
        val rawTitle = safeOptString(json, "title")
        val ocrText = safeOptString(json, "ocrText")
        val inferredTitle = rawTitle.ifBlank {
            listOf(docType, issuer).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Document administratif" }
        }
        val resolvedCategory = inferCategoryIfNeeded(
            rawCategory = safeOptString(json, "category"),
            docType = docType,
            title = inferredTitle,
            issuer = issuer,
            ocrText = ocrText
        )
        return ExtractedDocumentInfo(
            title = inferredTitle,
            category = resolvedCategory,
            docType = docType,
            docDate = safeOptString(json, "docDate"),
            expirationDate = safeOptString(json, "expirationDate"),
            amount = json.optDouble("amount", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0,
            issuer = issuer,
            referenceNumber = safeOptString(json, "referenceNumber"),
            tags = sanitizeTags(rawTags),
            ocrText = ocrText,
            confidence = safeOptString(json, "confidence").ifBlank { "medium" },
            uncertaintyNote = safeOptString(json, "uncertaintyNote"),
            categoryRationale = safeOptString(json, "categoryRationale")
        )
    }

    internal fun parseAutoCategorizationJson(
        jsonString: String,
        title: String = "",
        fallbackDocType: String = "",
        issuer: String = "",
        ocrText: String = ""
    ): AutoCategorizationResult {
        val json = JSONObject(jsonString)
        val tagsArr = json.optJSONArray("tags")
        val rawTags = mutableListOf<String>()
        if (tagsArr != null) {
            for (i in 0 until minOf(tagsArr.length(), 8)) {
                val t = tagsArr.optString(i).trim()
                if (t.isNotBlank() && !t.equals("null", ignoreCase = true)) rawTags.add(t)
            }
        }
        val docType = safeOptString(json, "docType").ifBlank { fallbackDocType }
        val resolvedCategory = inferCategoryIfNeeded(
            rawCategory = safeOptString(json, "category"),
            docType = docType,
            title = title,
            issuer = issuer,
            ocrText = ocrText
        )
        return AutoCategorizationResult(
            category = resolvedCategory,
            docType = docType,
            tags = sanitizeTags(rawTags),
            rationale = safeOptString(json, "rationale"),
            confidence = safeOptString(json, "confidence").ifBlank { "medium" }
        )
    }

    fun streamAssistantReply(
        userPrompt: String,
        accessibleContextSummary: String,
        history: List<AiMessageRecord>
    ): Flow<String> = flow {
        val recentHistory = history.takeLast(8).map { msg ->
            content(if (msg.sender == "user") "user" else "model") {
                text(msg.text)
            }
        }
        val chat = chatModel.startChat(history = recentHistory)
        val fullPrompt = buildString {
            append("Contexte personnel et familial autorisé (ne rien inventer en dehors) :\n")
            append(accessibleContextSummary)
            append("\n\nQuestion de l’utilisateur :\n")
            append(userPrompt)
        }
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(fullPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("L’assistant n’a renvoyé aucune réponse.")
        }
    }

    companion object {
        private const val TAG = "AiAssistantRepo"

        fun sanitizeTags(rawTags: List<String>, maxTags: Int = 8): List<String> {
            val seen = mutableSetOf<String>()
            val result = mutableListOf<String>()
            for (raw in rawTags) {
                val clean = raw.trim().removePrefix("#").trim().take(32)
                if (clean.isBlank() || clean.equals("null", ignoreCase = true)) continue
                val key = clean.lowercase()
                if (seen.add(key)) {
                    result.add(clean)
                    if (result.size >= maxTags) break
                }
            }
            return result
        }

        fun mergeTags(existingTags: List<String>, aiTags: List<String>, maxTags: Int = 10): List<String> {
            return sanitizeTags(aiTags + existingTags, maxTags = maxTags)
        }
    }
}
