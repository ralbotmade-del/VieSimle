package com.example

import com.example.core.DocumentUsageStat
import com.example.data.AiAssistantRepository
import com.example.data.DocumentSmartSorter
import com.example.data.DocumentSortMode
import com.example.data.UniversalSearchEngine
import com.example.data.UniversalSearchFilterState
import com.example.data.local.toCachedEntity
import com.example.model.Deadline
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import com.example.widget.WidgetSummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun smartSorter_ranksMostUsedAndFrequentTagDocumentsFirst() {
        val now = 1_750_000_000_000L
        val docA = VaultDocument(id = "a", title = "Attestation", tags = listOf("impôts", "caf"))
        val docB = VaultDocument(id = "b", title = "Facture EDF", tags = listOf("énergie", "impots"))
        val docC = VaultDocument(id = "c", title = "Note diverse", tags = listOf("divers"))

        val usage = mapOf(
            "b" to DocumentUsageStat(openCount = 6, lastOpenedAtMillis = now - 3_600_000L),
            "a" to DocumentUsageStat(openCount = 1, lastOpenedAtMillis = now - 72_000_000L)
        )

        val frequentTags = DocumentSmartSorter.computeFrequentTags(listOf(docA, docB, docC), usage)
        assertEquals("impôts", frequentTags.first().normalizedTag.let {
            assertEquals(2, frequentTags.first().documentCount)
            "impôts"
        })

        val autoSorted = DocumentSmartSorter.sortDocuments(
            documents = listOf(docC, docA, docB),
            usageStats = usage,
            sortMode = DocumentSortMode.SMART_AUTO,
            nowMillis = now
        )
        assertEquals(listOf("b", "a", "c"), autoSorted.map { it.document.id })
        assertTrue(autoSorted.first().reasonBadge?.contains("6") == true)

        val mostUsed = DocumentSmartSorter.sortDocuments(
            documents = listOf(docC, docA, docB),
            usageStats = usage,
            sortMode = DocumentSortMode.MOST_USED,
            nowMillis = now
        )
        assertEquals(listOf("b", "a", "c"), mostUsed.map { it.document.id })

        val byTitle = DocumentSmartSorter.sortDocuments(
            documents = listOf(docC, docB, docA),
            usageStats = usage,
            sortMode = DocumentSortMode.TITLE,
            nowMillis = now
        )
        assertEquals(listOf("a", "b", "c"), byTitle.map { it.document.id })
    }

    @Test
    fun cachedDocumentEntity_roundTripPreservesFieldsAndAccountScope() {
        val doc = VaultDocument(
            id = "doc_123",
            userId = "uid_alpha",
            ownerName = "Camille",
            householdId = "hh_1",
            visibility = "household",
            sharedWithUids = listOf("uid_alpha", "uid_beta"),
            title = "Avis d'imposition",
            category = "Impôts",
            docType = "Fiscalité",
            folder = "Déclarations",
            docDate = "2026-05-01",
            expirationDate = "2027-05-01",
            issuer = "DGFiP",
            referenceNumber = "REF-9988",
            amount = 1420.5,
            notes = "Conserver 3 ans",
            tags = listOf("impôts", "urgent", "2026"),
            confidentiality = "sensitive",
            status = "active",
            favorite = true,
            ocrText = "Texte extrait",
            ocrConfidence = "high",
            localFileUri = "file:///secure/doc.jpg",
            versionNumber = 2L,
            versionHistory = listOf("v1", "v2")
        )

        val entity = doc.toCachedEntity(ownerScopeUid = "uid_alpha", cachedAtMillis = 1_750_000_000_000L)
        assertEquals("uid_alpha_doc_123", entity.cacheKey)
        assertEquals("uid_alpha", entity.ownerScopeUid)

        val restored = entity.toVaultDocument()
        assertEquals(doc.id, restored.id)
        assertEquals(doc.userId, restored.userId)
        assertEquals(doc.title, restored.title)
        assertEquals(doc.amount, restored.amount, 0.001)
        assertEquals(doc.tags, restored.tags)
        assertEquals(doc.sharedWithUids, restored.sharedWithUids)
        assertEquals(doc.confidentiality, restored.confidentiality)
        assertEquals(doc.favorite, restored.favorite)
        assertEquals(doc.versionHistory, restored.versionHistory)
    }

    @Test
    fun cachedTaskEntity_roundTripPreservesSubtasksAndLinks() {
        val task = HouseholdTask(
            id = "task_42",
            userId = "uid_alpha",
            householdId = "hh_1",
            visibility = "household",
            sharedWithUids = listOf("uid_alpha"),
            title = "Renouveler passeport",
            description = "Prendre rendez-vous en mairie",
            category = "Administratif",
            dueDate = "2026-12-10",
            priority = "high",
            assigneeUid = "uid_alpha",
            assigneeName = "Camille",
            status = "todo",
            subtasks = listOf("Photo d'identité", "Timbre fiscal", "Justificatif domicile"),
            checklistTemplate = "papiers",
            linkedDocumentId = "doc_123",
            linkedDeadlineId = "dl_7",
            confidentiality = "standard"
        )

        val entity = task.toCachedEntity(ownerScopeUid = "uid_alpha", cachedAtMillis = 1_750_000_000_000L)
        assertEquals("uid_alpha_task_42", entity.cacheKey)

        val restored = entity.toHouseholdTask()
        assertEquals(task.id, restored.id)
        assertEquals(task.title, restored.title)
        assertEquals(task.subtasks, restored.subtasks)
        assertEquals(task.linkedDocumentId, restored.linkedDocumentId)
        assertEquals(task.linkedDeadlineId, restored.linkedDeadlineId)
        assertEquals(task.priority, restored.priority)
        assertEquals(task.status, restored.status)
    }

    @Test
    fun cachedSubscriptionEntity_roundTripPreservesNameCostAndRenewalDate() {
        val sub = SubscriptionItem(
            id = "sub_99",
            userId = "uid_alpha",
            householdId = "hh_1",
            visibility = "household",
            sharedWithUids = listOf("uid_alpha", "uid_beta"),
            name = "Fibre Orange",
            category = "Logement",
            amount = 39.99,
            currency = "EUR",
            frequency = "monthly",
            nextBillingDate = "2026-12-05",
            paymentMethod = "Prélèvement SEPA",
            status = "active",
            cancellationStatus = "none",
            notes = "Sans engagement",
            linkedDocumentId = "doc_123",
            alertDaysBefore = 3L
        )

        val entity = sub.toCachedEntity(ownerScopeUid = "uid_alpha", cachedAtMillis = 1_750_000_000_000L)
        assertEquals("uid_alpha_sub_99", entity.cacheKey)
        assertEquals("Fibre Orange", entity.name)
        assertEquals(39.99, entity.amount, 0.001)
        assertEquals("2026-12-05", entity.nextBillingDate)

        val restored = entity.toSubscriptionItem()
        assertEquals(sub.id, restored.id)
        assertEquals(sub.name, restored.name)
        assertEquals(sub.amount, restored.amount, 0.001)
        assertEquals(39.99, restored.monthlyEquivalent(), 0.001)
        assertEquals(sub.nextBillingDate, restored.nextBillingDate)
        assertEquals(sub.frequency, restored.frequency)
        assertEquals(sub.status, restored.status)
        assertEquals(sub.sharedWithUids, restored.sharedWithUids)
    }

    @Test
    fun widgetSummaryCalculator_picksNextSubscriptionAndNearestPendingDeadline() {
        val today = "2026-11-27"
        val subs = listOf(
            SubscriptionItem(id = "s_cancelled", name = "Old Gym", amount = 29.0, nextBillingDate = "2026-11-28", status = "cancelled"),
            SubscriptionItem(id = "s_later", name = "Assurance Auto", amount = 54.0, nextBillingDate = "2026-12-10", status = "active"),
            SubscriptionItem(id = "s_next", name = "Fibre Orange", amount = 39.99, nextBillingDate = "2026-11-30", status = "active"),
            SubscriptionItem(id = "s_past", name = "Streaming", amount = 12.99, nextBillingDate = "2026-11-20", status = "active")
        )
        val deadlines = listOf(
            Deadline(id = "d_done", title = "Facture payée", category = "Logement", dueDate = "2026-11-28", status = "done"),
            Deadline(id = "d_next", title = "Déclaration Taxe Foncière", category = "Impôts", dueDate = "2026-11-29", status = "pending", priority = "high"),
            Deadline(id = "d_later", title = "Renouvellement Mutuelle", category = "Santé", dueDate = "2026-12-15", status = "pending")
        )

        val summary = WidgetSummaryCalculator.computeWidgetSummary(
            subscriptions = subs,
            deadlines = deadlines,
            todayIso = today,
            nowMillis = 12345L,
            isAuthenticated = true
        )

        assertTrue(summary.isAuthenticated)
        assertEquals("s_next", summary.nextSubscription?.id)
        assertEquals("Fibre Orange", summary.nextSubscription?.name)
        assertEquals(3L, summary.nextSubscription?.daysUntil)

        assertEquals("d_next", summary.nearestDeadline?.id)
        assertEquals("Déclaration Taxe Foncière", summary.nearestDeadline?.title)
        assertEquals(2L, summary.nearestDeadline?.daysUntil)

        val signedOut = WidgetSummaryCalculator.computeWidgetSummary(
            subscriptions = subs,
            deadlines = deadlines,
            todayIso = today,
            isAuthenticated = false
        )
        assertFalse(signedOut.isAuthenticated)
        assertNull(signedOut.nextSubscription)
        assertNull(signedOut.nearestDeadline)
    }

    @Test
    fun aiTagging_sanitizesDeduplicatesAndCapsToFirestoreRulesLimit() {
        val raw = listOf("#Insurance", "insurance", "  Bill ", "", "null", "Tax", "MAIF", "2026", "Contrat", "Auto", "PDF", "Extra1", "Extra2")
        val sanitized = AiAssistantRepository.sanitizeTags(raw, maxTags = 8)
        assertEquals(listOf("Insurance", "Bill", "Tax", "MAIF", "2026", "Contrat", "Auto", "PDF"), sanitized)

        val merged = AiAssistantRepository.mergeTags(
            existingTags = listOf("PDF N&B", "bill", "Archive"),
            aiTags = listOf("Insurance", "Bill", "Tax"),
            maxTags = 10
        )
        assertEquals(listOf("Insurance", "Bill", "Tax", "PDF N&B", "Archive"), merged)
        assertTrue(merged.size <= 10)
    }

    @Test
    fun fullTextDocumentSearch_matchesKeywordsDateRangeAndCategoryWithSnippets() {
        val docTax = VaultDocument(
            id = "doc_tax",
            title = "Avis d'imposition sur le revenu",
            category = "Impôts",
            issuer = "DGFiP",
            docDate = "2026-05-14",
            tags = listOf("Tax", "2026"),
            ocrText = "Direction générale des finances publiques. Revenu fiscal de référence 32450 euros."
        )
        val docIns = VaultDocument(
            id = "doc_ins",
            title = "Attestation habitation",
            category = "Assurance",
            issuer = "MAIF",
            docDate = "2026-11-02",
            expirationDate = "2027-11-01",
            tags = listOf("Insurance", "Logement"),
            ocrText = "Contrat multirisque habitation numéro MH-4490 valable pour le logement principal."
        )
        val docBill = VaultDocument(
            id = "doc_bill",
            title = "Facture électricité",
            category = "Logement",
            issuer = "EDF",
            docDate = "2025-12-20",
            tags = listOf("Bill"),
            ocrText = "Consommation décembre 2025."
        )

        // 1. Keyword full-text search across OCR text (accent-insensitive)
        val ocrMatch = UniversalSearchEngine.scoreDocumentMatch(
            doc = docTax,
            query = "fiscal reference"
        )
        assertTrue(ocrMatch != null)
        assertTrue(ocrMatch!!.matchReasons.contains("Texte OCR"))
        assertTrue(ocrMatch.matchedSnippet?.contains("fiscal", ignoreCase = true) == true)

        // 2. Category + Date range filtering
        val results = UniversalSearchEngine.search(
            documents = listOf(docTax, docIns, docBill),
            tasks = emptyList(),
            subscriptions = emptyList(),
            deadlines = emptyList(),
            members = emptyList(),
            filter = UniversalSearchFilterState(
                query = "habitation",
                selectedCategory = "Assurance",
                dateFrom = "2026-11",
                dateTo = "2026-11"
            )
        )
        assertEquals(listOf("doc_ins"), results.matchingDocuments.map { it.item.id })

        // 3. Year / partial date query matching
        assertTrue(
            UniversalSearchEngine.matchesDateCriteria(
                primaryIso = "2026-05-14",
                secondaryIso = "",
                dateQuery = "2026-05",
                dateFrom = "",
                dateTo = ""
            )
        )
        assertFalse(
            UniversalSearchEngine.matchesDateCriteria(
                primaryIso = "2025-12-20",
                secondaryIso = "",
                dateQuery = "",
                dateFrom = "2026-01-01",
                dateTo = "2026-12-31"
            )
        )
    }
}


