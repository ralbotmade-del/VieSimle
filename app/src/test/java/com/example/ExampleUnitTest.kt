package com.example

import com.example.core.DocumentUsageStat
import com.example.data.DocumentSmartSorter
import com.example.data.DocumentSortMode
import com.example.data.local.toCachedEntity
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import org.junit.Assert.assertEquals
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
}

