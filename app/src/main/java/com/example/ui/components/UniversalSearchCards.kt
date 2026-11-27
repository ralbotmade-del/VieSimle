package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ScoredSearchMatch
import com.example.data.TagSearchSummary
import com.example.data.UniversalSearchEngine
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdMember
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.UrgentRedContainer
import java.text.Normalizer
import java.util.Locale

private val combiningMarksRegex = Regex("\\p{InCombiningDiacriticalMarks}+")

@Composable
fun rememberHighlightedText(
    text: String,
    query: String,
    highlightBg: Color = MaterialTheme.colorScheme.primaryContainer,
    highlightFg: Color = MaterialTheme.colorScheme.onPrimaryContainer
): AnnotatedString {
    return remember(text, query, highlightBg, highlightFg) {
        buildHighlightedAnnotatedString(text, query, highlightBg, highlightFg)
    }
}

fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    highlightBg: Color,
    highlightFg: Color
): AnnotatedString {
    val tokens = UniversalSearchEngine.tokenize(query)
    if (text.isEmpty() || tokens.isEmpty()) return AnnotatedString(text)

    val normChars = StringBuilder()
    val origIndexMap = mutableListOf<Int>()
    for (i in text.indices) {
        val decomposed = Normalizer.normalize(text[i].toString(), Normalizer.Form.NFD)
        val stripped = combiningMarksRegex.replace(decomposed, "").lowercase(Locale.FRENCH)
        for (c in stripped) {
            normChars.append(c)
            origIndexMap.add(i)
        }
    }
    val normText = normChars.toString()
    if (normText.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        tokens.forEach { token ->
            var searchFrom = 0
            while (searchFrom < normText.length) {
                val matchIdx = normText.indexOf(token, startIndex = searchFrom)
                if (matchIdx < 0) break
                val matchEndNorm = (matchIdx + token.length - 1).coerceAtMost(origIndexMap.lastIndex)
                val startOrig = origIndexMap[matchIdx]
                val endOrig = (origIndexMap[matchEndNorm] + 1).coerceAtMost(text.length)
                if (startOrig < endOrig) {
                    addStyle(
                        style = SpanStyle(
                            background = highlightBg,
                            color = highlightFg,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = startOrig,
                        end = endOrig
                    )
                }
                searchFrom = matchIdx + token.length.coerceAtLeast(1)
            }
        }
    }
}

@Composable
fun SearchTagResultCard(
    tagSummary: TagSearchSummary,
    query: String,
    isSelected: Boolean,
    onToggleTag: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleTag)
            .testTag("search_result_tag_${tagSummary.normalizedTag}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Label,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = rememberHighlightedText("#${tagSummary.displayTag}", query),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val hints = buildString {
                    append("${tagSummary.documentCount} document${if (tagSummary.documentCount > 1) "s" else ""}")
                    if (tagSummary.categoryHints.isNotEmpty()) {
                        append(" · ${tagSummary.categoryHints.take(2).joinToString(", ")}")
                    }
                }
                Text(
                    text = hints,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tagSummary.sampleDocumentTitles.isNotEmpty()) {
                    Text(
                        text = tagSummary.sampleDocumentTitles.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isSelected) "Actif" else "Filtrer",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchDocumentResultCard(
    match: ScoredSearchMatch<VaultDocument>,
    query: String,
    selectedTagsNormalized: Set<String>,
    onOpen: () -> Unit,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val doc = match.item
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("search_result_doc_${doc.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = rememberHighlightedText(doc.title.ifBlank { "Document sans titre" }, query),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (doc.favorite) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = "Favori",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    val subtitle = buildString {
                        append(doc.category)
                        if (doc.issuer.isNotBlank()) append(" · ${doc.issuer}")
                        if (doc.docDate.isNotBlank()) append(" · ${DateUtils.formatFrench(doc.docDate)}")
                        if (doc.amount > 0) append(" · ${"%.2f".format(doc.amount)} €")
                    }
                    Text(
                        text = rememberHighlightedText(subtitle, query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                VisibilityBadge(doc.visibility, doc.confidentiality)
            }

            if (!match.matchedSnippet.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = rememberHighlightedText(match.matchedSnippet, query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (doc.tags.isNotEmpty() || match.matchReasons.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    doc.tags.take(5).forEach { tag ->
                        val isTagActive = UniversalSearchEngine.normalize(tag) in selectedTagsNormalized
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleTag(tag) },
                            color = if (isTagActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = rememberHighlightedText(
                                    text = "#$tag",
                                    query = query,
                                    highlightBg = if (isTagActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    highlightFg = if (isTagActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTagActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTaskResultCard(
    match: ScoredSearchMatch<HouseholdTask>,
    query: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val task = match.item
    val isDone = task.status == "done"
    val days = DateUtils.daysUntil(task.dueDate)
    val isUrgent = !isDone && (task.priority == "urgent" || task.priority == "high" || (days != null && days <= 1L))
    val completedSubtasks = task.subtasks.count { it.startsWith("1|") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("search_result_task_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (isDone) "Terminée" else "À faire",
                    tint = when {
                        isDone -> MaterialTheme.colorScheme.primary
                        isUrgent -> UrgentRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = rememberHighlightedText(task.title, query),
                        style = MaterialTheme.typography.titleSmall.copy(
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildString {
                        append(task.category)
                        if (task.assigneeName.isNotBlank()) append(" · ${task.assigneeName}")
                        if (task.dueDate.isNotBlank()) append(" · ${DateUtils.formatFrench(task.dueDate)}")
                        if (task.subtasks.isNotEmpty()) append(" · $completedSubtasks/${task.subtasks.size} étapes")
                    }
                    Text(
                        text = rememberHighlightedText(meta, query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = when {
                        isDone -> MaterialTheme.colorScheme.primaryContainer
                        isUrgent -> UrgentRedContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isDone) "Terminée" else UniversalSearchEngine.priorityFrench(task.priority),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isDone -> MaterialTheme.colorScheme.onPrimaryContainer
                            isUrgent -> UrgentRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (!match.matchedSnippet.isNullOrBlank()) {
                Text(
                    text = rememberHighlightedText(match.matchedSnippet, query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SearchSubscriptionResultCard(
    match: ScoredSearchMatch<SubscriptionItem>,
    query: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sub = match.item
    val isToCancel = sub.status == "to_cancel"
    val isCancelled = sub.status == "cancelled"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("search_result_sub_${sub.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (isToCancel) UrgentRedContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Subscriptions,
                        contentDescription = null,
                        tint = if (isToCancel) UrgentRed else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = rememberHighlightedText(sub.name, query),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildString {
                        append(sub.category)
                        append(" · ${UniversalSearchEngine.frequencyFrench(sub.frequency)}")
                        if (sub.nextBillingDate.isNotBlank()) {
                            append(" · Prélèvement ${DateUtils.formatFrench(sub.nextBillingDate)}")
                        }
                    }
                    Text(
                        text = rememberHighlightedText(meta, query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${"%.2f".format(sub.monthlyEquivalent())} €/mois",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = when {
                            isToCancel -> UrgentRedContainer
                            isCancelled -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = UniversalSearchEngine.subscriptionStatusFrench(sub.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isToCancel -> UrgentRed
                                isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (!match.matchedSnippet.isNullOrBlank()) {
                Text(
                    text = rememberHighlightedText(match.matchedSnippet, query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SearchDeadlineResultCard(
    match: ScoredSearchMatch<Deadline>,
    query: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dl = match.item
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("search_result_deadline_${dl.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = rememberHighlightedText(dl.title, query),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = rememberHighlightedText("${dl.category} · ${DateUtils.formatFrench(dl.dueDate)}", query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DeadlineBadge(dl.dueDate, dl.status)
            }
            if (!match.matchedSnippet.isNullOrBlank()) {
                Text(
                    text = rememberHighlightedText(match.matchedSnippet, query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SearchMemberResultCard(
    member: HouseholdMember,
    query: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("search_result_member_${member.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rememberHighlightedText(member.displayName, query),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = UniversalSearchEngine.memberRoleFrench(member.role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
