package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DateUtils
import com.example.model.SubscriptionItem
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class MonthlyProjectionPoint(
    val shortLabel: String,
    val fullLabel: String,
    val totalAmount: Double,
    val dueNames: List<String>
)

private data class CategoryExpenseSlice(
    val category: String,
    val monthlyAmount: Double,
    val sharePercent: Int,
    val subNames: List<String>,
    val color: Color
)

@Composable
fun SubscriptionExpenseChartCard(
    subscriptionsState: UiState<List<SubscriptionItem>>,
    onOpenSubscriptions: () -> Unit,
    onAddSubscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chartMode by remember { mutableStateOf("monthly") } // "monthly" | "category"
    var selectedMonthIndex by remember { mutableIntStateOf(0) }

    val allSubs = (subscriptionsState as? UiState.Success)?.data ?: emptyList()
    val activeSubs = remember(allSubs) {
        allSubs.filter { it.status == "active" || it.status == "to_cancel" }
    }
    val monthlyEquivalentTotal = remember(activeSubs) {
        activeSubs.sumOf { it.monthlyEquivalent() }
    }
    val annualTotal = remember(activeSubs) {
        activeSubs.sumOf { it.annualCost() }
    }

    val monthlyProjection = remember(activeSubs) {
        buildSixMonthProjection(activeSubs)
    }

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        Color(0xFF00897B),
        Color(0xFF5C6BC0),
        Color(0xFFD97706)
    )

    val categorySlices = remember(activeSubs, monthlyEquivalentTotal) {
        if (monthlyEquivalentTotal <= 0.0) {
            emptyList()
        } else {
            activeSubs.groupBy { it.category.ifBlank { "Abonnements" } }
                .entries
                .mapIndexed { idx, (cat, items) ->
                    val sum = items.sumOf { it.monthlyEquivalent() }
                    val pct = ((sum / monthlyEquivalentTotal) * 100.0).toInt().coerceIn(1, 100)
                    CategoryExpenseSlice(
                        category = cat,
                        monthlyAmount = sum,
                        sharePercent = pct,
                        subNames = items.map { it.name },
                        color = palette[idx % palette.size]
                    )
                }
                .sortedByDescending { it.monthlyAmount }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_subscriptions_chart_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dépenses mensuelles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (activeSubs.isEmpty()) "Abonnements synchronisés"
                        else "${activeSubs.size} abonnement(s) actif(s) · ${"%.0f".format(annualTotal)} €/an",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeSubs.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${"%.2f".format(monthlyEquivalentTotal)} €/mois",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            when (subscriptionsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = subscriptionsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    if (activeSubs.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Subscriptions,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = "Aucun abonnement actif pour le graphique",
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Ajoutez vos abonnements (forfait, streaming, assurance, sport) pour suivre vos dépenses mensuelles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onAddSubscription,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("chart_add_sub_btn")
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Ajouter un abonnement")
                                }
                            }
                        }
                    } else {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = chartMode == "monthly",
                                onClick = { chartMode = "monthly" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = { Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text("6 prochains mois")
                            }
                            SegmentedButton(
                                selected = chartMode == "category",
                                onClick = { chartMode = "category" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = { Icon(Icons.Outlined.PieChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text("Par catégorie")
                            }
                        }

                        if (chartMode == "monthly") {
                            val safeIndex = selectedMonthIndex.coerceIn(0, (monthlyProjection.size - 1).coerceAtLeast(0))
                            val maxVal = maxOf(
                                monthlyProjection.maxOfOrNull { it.totalAmount } ?: 0.0,
                                monthlyEquivalentTotal,
                                1.0
                            )
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val mutedBarColor = MaterialTheme.colorScheme.primaryContainer
                            val guideColor = MaterialTheme.colorScheme.outlineVariant
                            val averageColor = MaterialTheme.colorScheme.tertiary

                            val animatedRatios = monthlyProjection.map { pt ->
                                val target = (pt.totalAmount / maxVal).toFloat().coerceIn(0.04f, 1f)
                                animateFloatAsState(
                                    targetValue = target,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "bar_${pt.shortLabel}"
                                ).value
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .padding(top = 8.dp)
                                ) {
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val barAreaHeight = h - 8.dp.toPx()

                                        // Horizontal grid guides (50% and 100%)
                                        listOf(0.5f, 1f).forEach { frac ->
                                            val yPos = barAreaHeight * (1f - frac) + 4.dp.toPx()
                                            drawLine(
                                                color = guideColor.copy(alpha = 0.55f),
                                                start = Offset(0f, yPos),
                                                end = Offset(w, yPos),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }

                                        // Smoothed monthly average reference line
                                        val avgRatio = (monthlyEquivalentTotal / maxVal).toFloat().coerceIn(0f, 1f)
                                        val avgY = barAreaHeight * (1f - avgRatio) + 4.dp.toPx()
                                        drawLine(
                                            color = averageColor.copy(alpha = 0.85f),
                                            start = Offset(0f, avgY),
                                            end = Offset(w, avgY),
                                            strokeWidth = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                        )

                                        val count = monthlyProjection.size.coerceAtLeast(1)
                                        val slotWidth = w / count
                                        val barWidth = (slotWidth * 0.48f).coerceAtMost(38.dp.toPx())

                                        animatedRatios.forEachIndexed { idx, ratio ->
                                            val barH = (barAreaHeight * ratio).coerceAtLeast(6.dp.toPx())
                                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                                            val top = barAreaHeight - barH + 4.dp.toPx()
                                            drawRoundRect(
                                                color = if (idx == safeIndex) primaryColor else mutedBarColor,
                                                topLeft = Offset(left, top),
                                                size = Size(barWidth, barH),
                                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                            )
                                        }
                                    }
                                }

                                // Interactive month labels & amounts row (48dp touch targets)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    monthlyProjection.forEachIndexed { idx, pt ->
                                        val isSelected = idx == safeIndex
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { selectedMonthIndex = idx }
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = pt.shortLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${"%.0f".format(pt.totalAmount)}€",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                val selectedPoint = monthlyProjection.getOrNull(safeIndex)
                                if (selectedPoint != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${selectedPoint.fullLabel} : ${"%.2f".format(selectedPoint.totalAmount)} €",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Moy. ${"%.2f".format(monthlyEquivalentTotal)} €/mois",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                            Text(
                                                text = if (selectedPoint.dueNames.isEmpty()) "Aucune échéance prévue ce mois-ci"
                                                else "Échéances : ${selectedPoint.dueNames.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Category Breakdown Bars
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Segmented stacked proportion bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    categorySlices.forEach { slice ->
                                        val weight = slice.monthlyAmount.toFloat().coerceAtLeast(0.01f)
                                        Box(
                                            modifier = Modifier
                                                .weight(weight)
                                                .fillMaxHeight()
                                                .background(slice.color)
                                        )
                                    }
                                }

                                categorySlices.take(5).forEach { slice ->
                                    val barFraction by animateFloatAsState(
                                        targetValue = (slice.monthlyAmount / monthlyEquivalentTotal).toFloat().coerceIn(0.05f, 1f),
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "cat_${slice.category}"
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(slice.color)
                                                )
                                                Text(
                                                    text = "${slice.category} (${slice.subNames.joinToString(", ")})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "${"%.2f".format(slice.monthlyAmount)} € · ${slice.sharePercent}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(barFraction)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(slice.color)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onOpenSubscriptions) {
                                Text("Gérer tous les abonnements")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildSixMonthProjection(activeSubs: List<SubscriptionItem>): List<MonthlyProjectionPoint> {
    val shortFmt = SimpleDateFormat("MMM", Locale.FRENCH)
    val fullFmt = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)
    val baseCal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    return (0 until 6).map { offset ->
        val monthCal = (baseCal.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
        val targetYear = monthCal.get(Calendar.YEAR)
        val targetMonth = monthCal.get(Calendar.MONTH)
        val targetTotalMonths = targetYear * 12 + targetMonth

        var monthTotal = 0.0
        val dueNames = mutableListOf<String>()

        activeSubs.forEach { sub ->
            val nextDate = DateUtils.parseIso(sub.nextBillingDate)
            val anchorTotalMonths = if (nextDate != null) {
                val c = Calendar.getInstance().apply { time = nextDate }
                c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
            } else {
                baseCal.get(Calendar.YEAR) * 12 + baseCal.get(Calendar.MONTH)
            }

            val isChargedThisMonth = when (sub.frequency) {
                "yearly" -> (targetTotalMonths - anchorTotalMonths) % 12 == 0
                "quarterly" -> (targetTotalMonths - anchorTotalMonths) % 3 == 0
                else -> true // "monthly"
            }

            if (isChargedThisMonth) {
                monthTotal += sub.amount
                dueNames.add(sub.name)
            }
        }

        val shortLabel = shortFmt.format(monthCal.time)
            .replace(".", "")
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
        val fullLabel = fullFmt.format(monthCal.time)
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }

        MonthlyProjectionPoint(
            shortLabel = shortLabel,
            fullLabel = fullLabel,
            totalAmount = monthTotal,
            dueNames = dueNames
        )
    }
}
