package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Staggered entrance animation wrapper for dashboard and list items.
 * Reveals content with a smooth fade + vertical spring slide-in.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    visible: Boolean = true,
    delayStepMs: Long = 55L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible, index) {
        if (visible) {
            val cappedDelay = (index.coerceIn(0, 8)) * delayStepMs
            if (cappedDelay > 0L) delay(cappedDelay)
            shown = true
        } else {
            shown = false
        }
    }

    AnimatedVisibility(
        visible = shown && visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialOffsetY = { fullHeight -> (fullHeight / 3).coerceAtLeast(18) }
        ) + expandVertically(
            animationSpec = tween(durationMillis = 220),
            expandFrom = androidx.compose.ui.Alignment.Top
        ),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
    ) {
        content()
    }
}

/**
 * Animated numeric counter for KPI badges and highlight pills.
 */
@Composable
fun rememberAnimatedCount(target: Int, animationDurationMs: Int = 450): Int {
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = animationDurationMs, easing = FastOutSlowInEasing),
        label = "animated_count"
    )
    return animated
}

/**
 * Gentle pulsing loading placeholder surface for skeletons.
 */
@Composable
fun ShimmerPulseBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}
