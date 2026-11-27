package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class CoachMarkStep {
    SCAN,
    AI_ASSISTANT
}

@Composable
fun CoachMarkOverlay(
    step: CoachMarkStep,
    scanTargetBounds: Rect?,
    aiTargetBounds: Rect?,
    onStepChange: (CoachMarkStep) -> Unit,
    onTryScan: () -> Unit,
    onTryAi: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val density = LocalDensity.current
    val spotlightPadPx = with(density) { 8.dp.toPx() }
    val cornerRadiusPx = with(density) { 18.dp.toPx() }

    val rawTarget = when (step) {
        CoachMarkStep.SCAN -> scanTargetBounds
        CoachMarkStep.AI_ASSISTANT -> aiTargetBounds
    }

    val pulseTransition = rememberInfiniteTransition(label = "coach_spotlight_pulse")
    val pulseProgress by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("coach_mark_overlay")
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        val fallbackTarget = when (step) {
            CoachMarkStep.SCAN -> Rect(
                left = containerWidthPx * 0.06f,
                top = containerHeightPx * 0.36f,
                right = containerWidthPx * 0.48f,
                bottom = containerHeightPx * 0.44f
            )
            CoachMarkStep.AI_ASSISTANT -> Rect(
                left = containerWidthPx * 0.52f,
                top = containerHeightPx * 0.36f,
                right = containerWidthPx * 0.94f,
                bottom = containerHeightPx * 0.44f
            )
        }

        val targetRect = rawTarget ?: fallbackTarget
        val animatedRect by animateRectAsState(
            targetValue = targetRect,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "spotlight_rect"
        )

        val paddedRect = Rect(
            left = (animatedRect.left - spotlightPadPx).coerceAtLeast(0f),
            top = (animatedRect.top - spotlightPadPx).coerceAtLeast(0f),
            right = (animatedRect.right + spotlightPadPx).coerceAtMost(containerWidthPx),
            bottom = (animatedRect.bottom + spotlightPadPx).coerceAtMost(containerHeightPx)
        )

        // Scrim + spotlight cutout; tapping directly on the highlighted button executes the target action
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(step, paddedRect) {
                    detectTapGestures { offset ->
                        if (paddedRect.contains(offset)) {
                            if (step == CoachMarkStep.SCAN) onTryScan() else onTryAi()
                        }
                    }
                }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.74f))

            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(paddedRect.left, paddedRect.top),
                size = Size(paddedRect.width, paddedRect.height),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                blendMode = BlendMode.Clear
            )

            val ringExpand = (4.dp.toPx()) * pulseProgress
            val ringColor = if (step == CoachMarkStep.SCAN) primaryColor else tertiaryColor
            drawRoundRect(
                color = ringColor.copy(alpha = 0.92f - 0.35f * pulseProgress),
                topLeft = Offset(paddedRect.left - ringExpand, paddedRect.top - ringExpand),
                size = Size(paddedRect.width + ringExpand * 2f, paddedRect.height + ringExpand * 2f),
                cornerRadius = CornerRadius(cornerRadiusPx + ringExpand, cornerRadiusPx + ringExpand),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Compute tooltip card placement cleanly below or above spotlighted button
        val placeBelow = paddedRect.center.y < containerHeightPx * 0.56f
        val gapPx = with(density) { 16.dp.toPx() }
        val cardApproxHeightPx = with(density) { 250.dp.toPx() }
        val cardTopPx = if (placeBelow) {
            (paddedRect.bottom + gapPx).coerceAtMost((containerHeightPx - cardApproxHeightPx - gapPx).coerceAtLeast(gapPx))
        } else {
            (paddedRect.top - cardApproxHeightPx - gapPx).coerceAtLeast(gapPx)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, cardTopPx.roundToInt()) }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240))) togetherWith
                        fadeOut(tween(160))
                },
                label = "coach_mark_card_content"
            ) { currentStep ->
                val isScan = currentStep == CoachMarkStep.SCAN
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .testTag("coach_mark_card_${currentStep.name.lowercase()}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = if (isScan) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isScan) Icons.Outlined.DocumentScanner else Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isScan) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(9.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (isScan) "Découverte · 1 / 2" else "Découverte · 2 / 2",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isScan) "Scanner un document" else "Assistant IA VieSimple",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("coach_mark_close_btn")
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Fermer le tutoriel")
                            }
                        }

                        Text(
                            text = if (isScan) {
                                "Touchez « Scanner » pour photographier un justificatif, une facture ou un contrat. L’OCR détecte automatiquement la date, le montant et la catégorie avant le chiffrement dans votre coffre-fort."
                            } else {
                                "Touchez « Assistant IA » pour poser vos questions administratives, résumer vos obligations et créer des échéances ou courriers en un geste."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Step progress dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoachMarkStep.entries.forEach { entry ->
                                val active = entry == currentStep
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .size(width = if (active) 20.dp else 6.dp, height = 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Touchez le bouton illuminé pour l’essayer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isScan) {
                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("coach_mark_skip_btn")
                                ) {
                                    Text("Passer")
                                }
                                Spacer(Modifier.weight(1f))
                                FilledTonalButton(
                                    onClick = onTryScan,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("coach_mark_try_scan_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Essayer")
                                }
                                Button(
                                    onClick = { onStepChange(CoachMarkStep.AI_ASSISTANT) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("coach_mark_next_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Suivant")
                                }
                            } else {
                                TextButton(
                                    onClick = { onStepChange(CoachMarkStep.SCAN) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("coach_mark_prev_btn")
                                ) {
                                    Text("Précédent")
                                }
                                Spacer(Modifier.weight(1f))
                                FilledTonalButton(
                                    onClick = onTryAi,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("coach_mark_try_ai_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Ouvrir l’IA")
                                }
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("coach_mark_done_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Terminer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
