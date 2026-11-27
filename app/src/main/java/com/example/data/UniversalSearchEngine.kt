package com.example.data

import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdMember
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import java.text.Normalizer
import java.util.Locale

enum class SearchScopeTab(val label: String, val testTag: String) {
    ALL("Tout", "search_tab_all"),
    DOCUMENTS("Documents", "search_tab_docs"),
    TASKS("Tâches", "search_tab_tasks"),
    SUBSCRIPTIONS("Abonnements", "search_tab_subs"),
    TAGS("Tags", "search_tab_tags"),
    DEADLINES("Échéances", "search_tab_deadlines"),
    MEMBERS("Membres", "search_tab_members")
}

data class UniversalSearchFilterState(
    val query: String = "",
    val activeTab: SearchScopeTab = SearchScopeTab.ALL,
    val selectedCategory: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val onlyActiveOrOpen: Boolean = false,
    val onlyHouseholdShared: Boolean = false,
    val onlyFavorites: Boolean = false
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() ||
            selectedCategory != null ||
            selectedTags.isNotEmpty() ||
            onlyActiveOrOpen ||
            onlyHouseholdShared ||
            onlyFavorites
}

data class ScoredSearchMatch<T>(
    val item: T,
    val score: Int,
    val matchReasons: List<String> = emptyList(),
    val matchedSnippet: String? = null
)

data class TagSearchSummary(
    val displayTag: String,
    val normalizedTag: String,
    val documentCount: Int,
    val relatedItemCount: Int,
    val categoryHints: List<String>,
    val sampleDocumentTitles: List<String>,
    val score: Int = 0
)

data class UniversalSearchResults(
    val filter: UniversalSearchFilterState = UniversalSearchFilterState(),
    val allTags: List<TagSearchSummary> = emptyList(),
    val matchingTags: List<TagSearchSummary> = emptyList(),
    val matchingDocuments: List<ScoredSearchMatch<VaultDocument>> = emptyList(),
    val matchingTasks: List<ScoredSearchMatch<HouseholdTask>> = emptyList(),
    val matchingSubscriptions: List<ScoredSearchMatch<SubscriptionItem>> = emptyList(),
    val matchingDeadlines: List<ScoredSearchMatch<Deadline>> = emptyList(),
    val matchingMembers: List<HouseholdMember> = emptyList(),
    val tabCounts: Map<SearchScopeTab, Int> = emptyMap(),
    val isSyncing: Boolean = false
) {
    val totalAllMatches: Int
        get() = matchingDocuments.size +
            matchingTasks.size +
            matchingSubscriptions.size +
            matchingDeadlines.size +
            matchingTags.size +
            matchingMembers.size

    val visibleCount: Int
        get() = when (filter.activeTab) {
            SearchScopeTab.ALL -> totalAllMatches
            SearchScopeTab.DOCUMENTS -> matchingDocuments.size
            SearchScopeTab.TASKS -> matchingTasks.size
            SearchScopeTab.SUBSCRIPTIONS -> matchingSubscriptions.size
            SearchScopeTab.TAGS -> matchingTags.size
            SearchScopeTab.DEADLINES -> matchingDeadlines.size
            SearchScopeTab.MEMBERS -> matchingMembers.size
        }
}

object UniversalSearchEngine {

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val whitespaceRegex = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
        return diacriticsRegex.replace(decomposed, "")
            .lowercase(Locale.FRENCH)
    }

    fun tokenize(query: String): List<String> {
        val norm = normalize(query)
        if (norm.isBlank()) return emptyList()
        return norm.split(whitespaceRegex).filter { it.isNotBlank() }.distinct()
    }

    fun search(
        documents: List<VaultDocument>,
        tasks: List<HouseholdTask>,
        subscriptions: List<SubscriptionItem>,
        deadlines: List<Deadline>,
        members: List<HouseholdMember>,
        filter: UniversalSearchFilterState,
        isSyncing: Boolean = false
    ): UniversalSearchResults {
        val tokens = tokenize(filter.query)
        val normalizedQuery = normalize(filter.query)
        val normalizedSelectedTags = filter.selectedTags.map { normalize(it) }.filter { it.isNotBlank() }.toSet()

        val allTags = buildTagSummaries(
            documents = documents,
            tasks = tasks,
            subscriptions = subscriptions,
            deadlines = deadlines
        )

        val matchedDocs = documents.mapNotNull { doc ->
            scoreDocument(
                doc = doc,
                tokens = tokens,
                normalizedQuery = normalizedQuery,
                selectedCategory = filter.selectedCategory,
                selectedTagsNormalized = normalizedSelectedTags,
                onlyActive = filter.onlyActiveOrOpen,
                onlyHousehold = filter.onlyHouseholdShared,
                onlyFavorites = filter.onlyFavorites
            )
        }.sortedWith(compareByDescending<ScoredSearchMatch<VaultDocument>> { it.score }
            .thenByDescending { it.item.favorite }
            .thenBy { normalize(it.item.title) })

        val matchedTasks = if (filter.onlyFavorites) {
            emptyList()
        } else {
            tasks.mapNotNull { task ->
                scoreTask(
                    task = task,
                    tokens = tokens,
                    normalizedQuery = normalizedQuery,
                    selectedCategory = filter.selectedCategory,
                    selectedTagsNormalized = normalizedSelectedTags,
                    onlyOpen = filter.onlyActiveOrOpen,
                    onlyHousehold = filter.onlyHouseholdShared
                )
            }.sortedWith(compareByDescending<ScoredSearchMatch<HouseholdTask>> { it.score }
                .thenBy { it.item.status != "todo" }
                .thenBy { DateUtils.daysUntil(it.item.dueDate) ?: Long.MAX_VALUE })
        }

        val matchedSubs = if (filter.onlyFavorites) {
            emptyList()
        } else {
            subscriptions.mapNotNull { sub ->
                scoreSubscription(
                    sub = sub,
                    tokens = tokens,
                    normalizedQuery = normalizedQuery,
                    selectedCategory = filter.selectedCategory,
                    selectedTagsNormalized = normalizedSelectedTags,
                    onlyActive = filter.onlyActiveOrOpen,
                    onlyHousehold = filter.onlyHouseholdShared
                )
            }.sortedWith(compareByDescending<ScoredSearchMatch<SubscriptionItem>> { it.score }
                .thenByDescending { it.item.monthlyEquivalent() })
        }

        val matchedDeadlines = if (filter.onlyFavorites) {
            emptyList()
        } else {
            deadlines.mapNotNull { dl ->
                scoreDeadline(
                    dl = dl,
                    tokens = tokens,
                    normalizedQuery = normalizedQuery,
                    selectedCategory = filter.selectedCategory,
                    selectedTagsNormalized = normalizedSelectedTags,
                    onlyPending = filter.onlyActiveOrOpen,
                    onlyHousehold = filter.onlyHouseholdShared
                )
            }.sortedWith(compareByDescending<ScoredSearchMatch<Deadline>> { it.score }
                .thenBy { it.item.status != "pending" }
                .thenBy { DateUtils.daysUntil(it.item.dueDate) ?: Long.MAX_VALUE })
        }

        val matchedTags = allTags.mapNotNull { summary ->
            scoreTagSummary(
                summary = summary,
                tokens = tokens,
                normalizedQuery = normalizedQuery,
                selectedCategory = filter.selectedCategory,
                selectedTagsNormalized = normalizedSelectedTags
            )
        }.sortedWith(compareByDescending<TagSearchSummary> { it.score }
            .thenByDescending { it.documentCount }
            .thenBy { it.normalizedTag })

        val matchedMembers = if (
            filter.selectedCategory != null ||
            normalizedSelectedTags.isNotEmpty() ||
            filter.onlyFavorites
        ) {
            emptyList()
        } else {
            members.filter { member ->
                tokens.isEmpty() || tokens.all { token ->
                    normalize(member.displayName).contains(token) ||
                        normalize(memberRoleFrench(member.role)).contains(token)
                }
            }
        }

        val counts = mapOf(
            SearchScopeTab.ALL to (matchedDocs.size + matchedTasks.size + matchedSubs.size + matchedDeadlines.size + matchedTags.size + matchedMembers.size),
            SearchScopeTab.DOCUMENTS to matchedDocs.size,
            SearchScopeTab.TASKS to matchedTasks.size,
            SearchScopeTab.SUBSCRIPTIONS to matchedSubs.size,
            SearchScopeTab.TAGS to matchedTags.size,
            SearchScopeTab.DEADLINES to matchedDeadlines.size,
            SearchScopeTab.MEMBERS to matchedMembers.size
        )

        return UniversalSearchResults(
            filter = filter,
            allTags = allTags,
            matchingTags = matchedTags,
            matchingDocuments = matchedDocs,
            matchingTasks = matchedTasks,
            matchingSubscriptions = matchedSubs,
            matchingDeadlines = matchedDeadlines,
            matchingMembers = matchedMembers,
            tabCounts = counts,
            isSyncing = isSyncing
        )
    }

    private fun buildTagSummaries(
        documents: List<VaultDocument>,
        tasks: List<HouseholdTask>,
        subscriptions: List<SubscriptionItem>,
        deadlines: List<Deadline>
    ): List<TagSearchSummary> {
        data class Bucket(
            var display: String,
            var docCount: Int = 0,
            val categories: LinkedHashSet<String> = linkedSetOf(),
            val sampleTitles: MutableList<String> = mutableListOf()
        )

        val buckets = linkedMapOf<String, Bucket>()
        documents.forEach { doc ->
            val seenInDoc = hashSetOf<String>()
            doc.tags.forEach { rawTag ->
                val clean = rawTag.trim()
                val norm = normalize(clean)
                if (norm.isNotBlank() && seenInDoc.add(norm)) {
                    val bucket = buckets.getOrPut(norm) { Bucket(display = clean) }
                    bucket.docCount += 1
                    if (doc.category.isNotBlank()) bucket.categories.add(doc.category)
                    if (doc.title.isNotBlank() && bucket.sampleTitles.size < 3) {
                        bucket.sampleTitles.add(doc.title)
                    }
                }
            }
        }

        return buckets.entries.map { (norm, bucket) ->
            val extraRelated =
                tasks.count { normalize(it.category) == norm || normalize(it.checklistTemplate).contains(norm) || normalize(it.title).contains(norm) } +
                    subscriptions.count { normalize(it.category) == norm || normalize(it.name).contains(norm) } +
                    deadlines.count { normalize(it.category) == norm || normalize(it.title).contains(norm) }
            TagSearchSummary(
                displayTag = bucket.display,
                normalizedTag = norm,
                documentCount = bucket.docCount,
                relatedItemCount = bucket.docCount + extraRelated,
                categoryHints = bucket.categories.toList(),
                sampleDocumentTitles = bucket.sampleTitles
            )
        }.sortedWith(compareByDescending<TagSearchSummary> { it.documentCount }.thenBy { it.normalizedTag })
    }

    private fun scoreTagSummary(
        summary: TagSearchSummary,
        tokens: List<String>,
        normalizedQuery: String,
        selectedCategory: String?,
        selectedTagsNormalized: Set<String>
    ): TagSearchSummary? {
        if (selectedCategory != null && summary.categoryHints.none { it == selectedCategory }) {
            return null
        }
        if (selectedTagsNormalized.isNotEmpty() && summary.normalizedTag !in selectedTagsNormalized) {
            return null
        }
        if (tokens.isEmpty()) return summary.copy(score = summary.documentCount)

        val norm = summary.normalizedTag
        val hintsNorm = summary.categoryHints.joinToString(" ") { normalize(it) }
        val titlesNorm = summary.sampleDocumentTitles.joinToString(" ") { normalize(it) }
        val combined = "$norm $hintsNorm $titlesNorm"
        if (!tokens.all { combined.contains(it) }) return null

        var score = 0
        if (norm == normalizedQuery) score += 120
        else if (norm.startsWith(normalizedQuery)) score += 90
        else if (tokens.all { norm.contains(it) }) score += 70
        else score += 30
        score += summary.documentCount.coerceAtMost(20)
        return summary.copy(score = score)
    }

    private fun scoreDocument(
        doc: VaultDocument,
        tokens: List<String>,
        normalizedQuery: String,
        selectedCategory: String?,
        selectedTagsNormalized: Set<String>,
        onlyActive: Boolean,
        onlyHousehold: Boolean,
        onlyFavorites: Boolean
    ): ScoredSearchMatch<VaultDocument>? {
        if (selectedCategory != null && doc.category != selectedCategory) return null
        if (onlyActive && doc.status != "active") return null
        if (onlyHousehold && doc.visibility != "household") return null
        if (onlyFavorites && !doc.favorite) return null

        val normalizedDocTags = doc.tags.map { normalize(it) }
        if (selectedTagsNormalized.isNotEmpty() && !selectedTagsNormalized.all { sel -> normalizedDocTags.any { it == sel } }) {
            return null
        }

        if (tokens.isEmpty()) {
            return ScoredSearchMatch(item = doc, score = if (doc.favorite) 10 else 1)
        }

        val normTitle = normalize(doc.title)
        val normIssuer = normalize(doc.issuer)
        val normCategory = normalize(doc.category)
        val normDocType = normalize(doc.docType)
        val normFolder = normalize(doc.folder)
        val normRef = normalize(doc.referenceNumber)
        val normNotes = normalize(doc.notes)
        val normOcr = normalize(doc.ocrText)
        val normTagsJoined = normalizedDocTags.joinToString(" ")
        val formattedAmount = if (doc.amount > 0) "%.2f".format(Locale.US, doc.amount) else ""
        val combined = listOf(
            normTitle, normIssuer, normCategory, normDocType, normFolder,
            normRef, normNotes, normOcr, normTagsJoined,
            normalize(doc.docDate), normalize(doc.expirationDate), formattedAmount
        ).joinToString(" ")

        if (!tokens.all { combined.contains(it) }) return null

        var score = 0
        val reasons = linkedSetOf<String>()

        if (normTitle == normalizedQuery) score += 120
        else if (normTitle.startsWith(normalizedQuery)) score += 90

        tokens.forEach { token ->
            if (normTitle.contains(token)) {
                score += 45
                reasons.add("Titre")
            }
            if (normalizedDocTags.any { it == token || it.contains(token) }) {
                score += 38
                val matchedTag = doc.tags.firstOrNull { normalize(it).contains(token) }
                reasons.add(if (matchedTag != null) "Tag #$matchedTag" else "Tag")
            }
            if (normIssuer.contains(token)) {
                score += 32
                reasons.add("Organisme")
            }
            if (normCategory.contains(token) || normDocType.contains(token) || normFolder.contains(token)) {
                score += 24
                reasons.add("Catégorie")
            }
            if (normRef.contains(token)) {
                score += 28
                reasons.add("Référence")
            }
            if (normNotes.contains(token)) {
                score += 18
                reasons.add("Notes")
            }
            if (normOcr.contains(token)) {
                score += 14
                reasons.add("Texte OCR")
            }
        }

        val snippet = when {
            tokens.any { normOcr.contains(it) } -> extractSnippet(doc.ocrText, tokens)
            tokens.any { normNotes.contains(it) } -> extractSnippet(doc.notes, tokens)
            else -> null
        }

        return ScoredSearchMatch(
            item = doc,
            score = score.coerceAtLeast(1),
            matchReasons = reasons.toList(),
            matchedSnippet = snippet
        )
    }

    private fun scoreTask(
        task: HouseholdTask,
        tokens: List<String>,
        normalizedQuery: String,
        selectedCategory: String?,
        selectedTagsNormalized: Set<String>,
        onlyOpen: Boolean,
        onlyHousehold: Boolean
    ): ScoredSearchMatch<HouseholdTask>? {
        if (selectedCategory != null && task.category != selectedCategory) return null
        if (onlyOpen && task.status != "todo") return null
        if (onlyHousehold && task.visibility != "household") return null

        val normTitle = normalize(task.title)
        val normDesc = normalize(task.description)
        val normCat = normalize(task.category)
        val normAssignee = normalize(task.assigneeName)
        val normTemplate = normalize(task.checklistTemplate)
        val cleanSubtasks = task.subtasks.map { it.removePrefix("0|").removePrefix("1|").trim() }
        val normSubtasks = normalize(cleanSubtasks.joinToString(" "))
        val normPriority = normalize(priorityFrench(task.priority))
        val combined = listOf(
            normTitle, normDesc, normCat, normAssignee, normTemplate,
            normSubtasks, normPriority, normalize(task.dueDate)
        ).joinToString(" ")

        if (selectedTagsNormalized.isNotEmpty() && !selectedTagsNormalized.all { combined.contains(it) }) {
            return null
        }

        if (tokens.isEmpty()) {
            return ScoredSearchMatch(item = task, score = if (task.status == "todo") 5 else 1)
        }

        if (!tokens.all { combined.contains(it) }) return null

        var score = 0
        val reasons = linkedSetOf<String>()
        if (normTitle == normalizedQuery) score += 110
        else if (normTitle.startsWith(normalizedQuery)) score += 80

        tokens.forEach { token ->
            if (normTitle.contains(token)) {
                score += 45
                reasons.add("Titre")
            }
            if (normCat.contains(token) || normTemplate.contains(token)) {
                score += 24
                reasons.add("Catégorie")
            }
            if (normAssignee.contains(token)) {
                score += 22
                reasons.add("Assigné à")
            }
            if (normSubtasks.contains(token)) {
                score += 20
                reasons.add("Sous-tâche")
            }
            if (normDesc.contains(token)) {
                score += 16
                reasons.add("Description")
            }
        }

        val matchedSubtask = cleanSubtasks.firstOrNull { sub ->
            val n = normalize(sub)
            tokens.any { n.contains(it) }
        }
        val snippet = when {
            matchedSubtask != null -> "Sous-tâche : $matchedSubtask"
            tokens.any { normDesc.contains(it) } -> extractSnippet(task.description, tokens)
            else -> null
        }

        return ScoredSearchMatch(
            item = task,
            score = score.coerceAtLeast(1),
            matchReasons = reasons.toList(),
            matchedSnippet = snippet
        )
    }

    private fun scoreSubscription(
        sub: SubscriptionItem,
        tokens: List<String>,
        normalizedQuery: String,
        selectedCategory: String?,
        selectedTagsNormalized: Set<String>,
        onlyActive: Boolean,
        onlyHousehold: Boolean
    ): ScoredSearchMatch<SubscriptionItem>? {
        if (selectedCategory != null && sub.category != selectedCategory) return null
        if (onlyActive && sub.status != "active" && sub.status != "to_cancel") return null
        if (onlyHousehold && sub.visibility != "household") return null

        val normName = normalize(sub.name)
        val normCat = normalize(sub.category)
        val normNotes = normalize(sub.notes)
        val normPayment = normalize(sub.paymentMethod)
        val normFreq = normalize(frequencyFrench(sub.frequency))
        val normStatus = normalize(subscriptionStatusFrench(sub.status))
        val amountStr = "%.2f".format(Locale.US, sub.amount)
        val combined = listOf(
            normName, normCat, normNotes, normPayment, normFreq,
            normStatus, normalize(sub.nextBillingDate), amountStr
        ).joinToString(" ")

        if (selectedTagsNormalized.isNotEmpty() && !selectedTagsNormalized.all { combined.contains(it) }) {
            return null
        }

        if (tokens.isEmpty()) {
            return ScoredSearchMatch(item = sub, score = if (sub.status == "active" || sub.status == "to_cancel") 5 else 1)
        }

        if (!tokens.all { combined.contains(it) }) return null

        var score = 0
        val reasons = linkedSetOf<String>()
        if (normName == normalizedQuery) score += 115
        else if (normName.startsWith(normalizedQuery)) score += 85

        tokens.forEach { token ->
            if (normName.contains(token)) {
                score += 48
                reasons.add("Nom")
            }
            if (normCat.contains(token)) {
                score += 24
                reasons.add("Catégorie")
            }
            if (normFreq.contains(token) || normStatus.contains(token)) {
                score += 18
                reasons.add("Formule")
            }
            if (normPayment.contains(token)) {
                score += 18
                reasons.add("Paiement")
            }
            if (normNotes.contains(token)) {
                score += 16
                reasons.add("Notes")
            }
        }

        val snippet = if (tokens.any { normNotes.contains(it) }) extractSnippet(sub.notes, tokens) else null

        return ScoredSearchMatch(
            item = sub,
            score = score.coerceAtLeast(1),
            matchReasons = reasons.toList(),
            matchedSnippet = snippet
        )
    }

    private fun scoreDeadline(
        dl: Deadline,
        tokens: List<String>,
        normalizedQuery: String,
        selectedCategory: String?,
        selectedTagsNormalized: Set<String>,
        onlyPending: Boolean,
        onlyHousehold: Boolean
    ): ScoredSearchMatch<Deadline>? {
        if (selectedCategory != null && dl.category != selectedCategory) return null
        if (onlyPending && dl.status != "pending") return null
        if (onlyHousehold && dl.visibility != "household") return null

        val normTitle = normalize(dl.title)
        val normDesc = normalize(dl.description)
        val normCat = normalize(dl.category)
        val normAssignee = normalize(dl.assigneeName)
        val combined = listOf(
            normTitle, normDesc, normCat, normAssignee, normalize(dl.dueDate)
        ).joinToString(" ")

        if (selectedTagsNormalized.isNotEmpty() && !selectedTagsNormalized.all { combined.contains(it) }) {
            return null
        }

        if (tokens.isEmpty()) {
            return ScoredSearchMatch(item = dl, score = if (dl.status == "pending") 5 else 1)
        }

        if (!tokens.all { combined.contains(it) }) return null

        var score = 0
        val reasons = linkedSetOf<String>()
        if (normTitle == normalizedQuery) score += 110
        else if (normTitle.startsWith(normalizedQuery)) score += 80

        tokens.forEach { token ->
            if (normTitle.contains(token)) {
                score += 45
                reasons.add("Titre")
            }
            if (normCat.contains(token)) {
                score += 24
                reasons.add("Catégorie")
            }
            if (normDesc.contains(token)) {
                score += 18
                reasons.add("Description")
            }
        }

        val snippet = if (tokens.any { normDesc.contains(it) }) extractSnippet(dl.description, tokens) else null

        return ScoredSearchMatch(
            item = dl,
            score = score.coerceAtLeast(1),
            matchReasons = reasons.toList(),
            matchedSnippet = snippet
        )
    }

    fun extractSnippet(source: String, tokens: List<String>, maxLength: Int = 92): String? {
        val clean = source.replace('\n', ' ').replace(whitespaceRegex, " ").trim()
        if (clean.isBlank()) return null
        val norm = normalize(clean)
        val firstMatchIdx = tokens.mapNotNull { token ->
            val idx = norm.indexOf(token)
            if (idx >= 0) idx else null
        }.minOrNull() ?: return clean.take(maxLength)

        val start = (firstMatchIdx - 28).coerceAtLeast(0)
        val end = (start + maxLength).coerceAtMost(clean.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < clean.length) "…" else ""
        return prefix + clean.substring(start, end).trim() + suffix
    }

    fun frequencyFrench(freq: String): String = when (freq) {
        "yearly" -> "Annuel"
        "quarterly" -> "Trimestriel"
        else -> "Mensuel"
    }

    fun subscriptionStatusFrench(status: String): String = when (status) {
        "to_cancel" -> "À résilier"
        "cancelled" -> "Résilié"
        else -> "Actif"
    }

    fun priorityFrench(priority: String): String = when (priority) {
        "urgent" -> "Urgente"
        "high" -> "Haute"
        "low" -> "Basse"
        else -> "Normale"
    }

    fun memberRoleFrench(role: String): String = when (role) {
        "owner" -> "Propriétaire"
        "admin" -> "Administrateur"
        else -> "Membre"
    }
}
