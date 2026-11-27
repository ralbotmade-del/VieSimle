package com.example.data

import com.example.core.DocumentUsageStat
import com.example.model.VaultDocument

enum class DocumentSortMode(val label: String) {
    SMART_AUTO("Tri auto"),
    MOST_USED("Plus consultés"),
    TOP_TAGS("Tags fréquents"),
    RECENT("Récents"),
    TITLE("A → Z")
}

data class FrequentTagStat(
    val normalizedTag: String,
    val displayTag: String,
    val documentCount: Int,
    val weightedUsageCount: Int
)

data class RankedVaultDocument(
    val document: VaultDocument,
    val score: Double,
    val openCount: Int,
    val topFrequentTag: String?,
    val reasonBadge: String?
)

object DocumentSmartSorter {

    fun computeFrequentTags(
        documents: List<VaultDocument>,
        usageStats: Map<String, DocumentUsageStat>
    ): List<FrequentTagStat> {
        if (documents.isEmpty()) return emptyList()

        data class MutableTagAcc(
            val displayCounts: MutableMap<String, Int> = LinkedHashMap(),
            var docCount: Int = 0,
            var usageWeight: Int = 0
        )

        val accMap = LinkedHashMap<String, MutableTagAcc>()
        documents.forEach { doc ->
            val docOpens = usageStats[doc.id]?.openCount ?: 0
            val seenNormsInDoc = HashSet<String>()
            doc.tags.forEach { rawTag ->
                val clean = rawTag.trim().removePrefix("#")
                if (clean.isBlank()) return@forEach
                val norm = UniversalSearchEngine.normalize(clean)
                if (norm.isBlank() || !seenNormsInDoc.add(norm)) return@forEach

                val acc = accMap.getOrPut(norm) { MutableTagAcc() }
                acc.docCount += 1
                acc.usageWeight += 1 + docOpens
                acc.displayCounts[clean] = (acc.displayCounts[clean] ?: 0) + 1
            }
        }

        return accMap.entries
            .map { (norm, acc) ->
                val bestDisplay = acc.displayCounts.maxByOrNull { it.value }?.key ?: norm
                FrequentTagStat(
                    normalizedTag = norm,
                    displayTag = bestDisplay,
                    documentCount = acc.docCount,
                    weightedUsageCount = acc.usageWeight
                )
            }
            .sortedWith(
                compareByDescending<FrequentTagStat> { it.documentCount }
                    .thenByDescending { it.weightedUsageCount }
                    .thenBy { it.displayTag.lowercase() }
            )
    }

    fun sortDocuments(
        documents: List<VaultDocument>,
        usageStats: Map<String, DocumentUsageStat>,
        sortMode: DocumentSortMode,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RankedVaultDocument> {
        if (documents.isEmpty()) return emptyList()

        val tagStatsByNorm = computeFrequentTags(documents, usageStats).associateBy { it.normalizedTag }

        val ranked = documents.map { doc ->
            val stat = usageStats[doc.id]
            val openCount = stat?.openCount ?: 0
            val lastOpened = stat?.lastOpenedAtMillis ?: 0L

            val recencyAccessBoost = if (lastOpened > 0L) {
                val ageHours = ((nowMillis - lastOpened).coerceAtLeast(0L)) / 3_600_000L
                when {
                    ageHours <= 24L -> 14.0
                    ageHours <= 24L * 7L -> 8.0
                    ageHours <= 24L * 30L -> 3.0
                    else -> 1.0
                }
            } else {
                0.0
            }

            val usageScore = (openCount * 16.0) + recencyAccessBoost

            val docNormTags = doc.tags
                .map { UniversalSearchEngine.normalize(it.removePrefix("#")) }
                .filter { it.isNotBlank() }
                .distinct()

            var tagScore = 0.0
            var topTagStat: FrequentTagStat? = null
            docNormTags.forEach { normTag ->
                val tagStat = tagStatsByNorm[normTag] ?: return@forEach
                // Tags shared across multiple documents carry higher navigational weight
                val sharedBonus = (tagStat.documentCount - 1).coerceAtLeast(0) * 5.0
                val usageAffinity = tagStat.weightedUsageCount * 1.25
                tagScore += sharedBonus + usageAffinity + 2.0
                if (topTagStat == null ||
                    tagStat.documentCount > topTagStat!!.documentCount ||
                    (tagStat.documentCount == topTagStat!!.documentCount &&
                        tagStat.weightedUsageCount > topTagStat!!.weightedUsageCount)
                ) {
                    topTagStat = tagStat
                }
            }

            val favoriteScore = if (doc.favorite) 12.0 else 0.0
            val docTimestampMillis = (doc.updatedAt?.toDate()?.time ?: doc.createdAt?.toDate()?.time ?: 0L)
            val freshnessScore = if (docTimestampMillis > 0L) {
                val ageDays = ((nowMillis - docTimestampMillis).coerceAtLeast(0L)) / 86_400_000L
                when {
                    ageDays <= 3L -> 6.0
                    ageDays <= 14L -> 3.5
                    ageDays <= 45L -> 1.5
                    else -> 0.0
                }
            } else {
                0.0
            }

            val totalSmartScore = usageScore + tagScore + favoriteScore + freshnessScore
            val reasonBadge = when (sortMode) {
                DocumentSortMode.SMART_AUTO -> when {
                    openCount > 0 && topTagStat != null && topTagStat!!.documentCount >= 2 ->
                        "Consulté $openCount× · #${topTagStat!!.displayTag}"
                    openCount > 0 ->
                        "Consulté $openCount fois"
                    topTagStat != null && topTagStat!!.documentCount >= 2 ->
                        "Tag fréquent : #${topTagStat!!.displayTag} (${topTagStat!!.documentCount})"
                    doc.favorite ->
                        "Favori"
                    else -> null
                }
                DocumentSortMode.MOST_USED ->
                    if (openCount > 0) "Consulté $openCount fois" else "Pas encore consulté"
                DocumentSortMode.TOP_TAGS ->
                    topTagStat?.let { "#${it.displayTag} (${it.documentCount} docs)" }
                DocumentSortMode.RECENT, DocumentSortMode.TITLE -> null
            }

            RankedVaultDocument(
                document = doc,
                score = when (sortMode) {
                    DocumentSortMode.SMART_AUTO -> totalSmartScore
                    DocumentSortMode.MOST_USED -> usageScore
                    DocumentSortMode.TOP_TAGS -> tagScore
                    DocumentSortMode.RECENT -> docTimestampMillis.toDouble()
                    DocumentSortMode.TITLE -> 0.0
                },
                openCount = openCount,
                topFrequentTag = topTagStat?.displayTag,
                reasonBadge = reasonBadge
            )
        }

        return when (sortMode) {
            DocumentSortMode.SMART_AUTO -> ranked.sortedWith(
                compareByDescending<RankedVaultDocument> { it.score }
                    .thenByDescending { it.openCount }
                    .thenByDescending { it.document.favorite }
                    .thenByDescending { it.document.updatedAt?.seconds ?: it.document.createdAt?.seconds ?: 0L }
                    .thenBy { it.document.title.lowercase() }
            )
            DocumentSortMode.MOST_USED -> ranked.sortedWith(
                compareByDescending<RankedVaultDocument> { it.openCount }
                    .thenByDescending { usageStats[it.document.id]?.lastOpenedAtMillis ?: 0L }
                    .thenByDescending { it.document.favorite }
                    .thenBy { it.document.title.lowercase() }
            )
            DocumentSortMode.TOP_TAGS -> ranked.sortedWith(
                compareByDescending<RankedVaultDocument> { it.score }
                    .thenByDescending { it.openCount }
                    .thenBy { it.document.title.lowercase() }
            )
            DocumentSortMode.RECENT -> ranked.sortedWith(
                compareByDescending<RankedVaultDocument> {
                    it.document.updatedAt?.seconds ?: it.document.createdAt?.seconds ?: 0L
                }.thenBy { it.document.title.lowercase() }
            )
            DocumentSortMode.TITLE -> ranked.sortedBy { it.document.title.lowercase() }
        }
    }
}
