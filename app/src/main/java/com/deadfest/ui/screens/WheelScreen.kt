package com.deadfest.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import kotlin.math.min
import kotlin.random.Random

private const val SPIN_MS = 3_600L
private const val SPIN_MS_PERFORMANCE = 1_600L

@Composable
fun WheelScreen(navController: NavController, viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val spin by viewModel.spin.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val haptics = LocalHapticFeedback.current

    // Disabled slices are hidden and can never win.
    val wheelItems = remember(globalState.wheelItems) { globalState.wheelItems.filter { it.enabled } }
    val spinMs = if (performanceMode) SPIN_MS_PERFORMANCE else SPIN_MS
    val rotation = remember { Animatable(0f) }

    // Animate whenever the ViewModel starts a new spin.
    LaunchedEffect(spin.spinId) {
        if (!spin.spinning || spin.targetIndex !in wheelItems.indices) return@LaunchedEffect
        val slice = 360f / wheelItems.size
        // Slice i spans [i*slice, (i+1)*slice) clockwise from the top pointer. Land
        // somewhere inside the winning slice (not always dead-centre) for a natural feel.
        val landing = spin.targetIndex * slice + slice * (0.5f + (Random.nextFloat() - 0.5f) * 0.6f)
        val desired = (360f - landing).mod(360f)
        val current = rotation.value.mod(360f)
        rotation.snapTo(current)
        val turns = if (performanceMode) 3 else 6
        val target = current + turns * 360f + (desired - current).mod(360f)
        rotation.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = spinMs.toInt(), easing = CubicBezierEasing(0.15f, 0.0f, 0.1f, 1f))
        )
    }

    // Buzz when the result lands.
    LaunchedEffect(spin.resultText) {
        if (spin.resultText != null && !spin.spinning) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "WHEEL OF FATE",
            subtitle = "TEST YOUR LUCK, SURVIVOR",
            trailing = { CoinBadge(user?.playerData?.coins ?: 0) }
        )

        when {
            globalState.lockdownActive -> NoticeCard(
                icon = Icons.Default.Lock,
                title = "SYSTEM LOCKDOWN ACTIVE",
                message = "The Wheel of Fate protocol is currently locked down by an administrator."
            )
            !globalState.wheelEnabled -> NoticeCard(
                icon = Icons.Default.Casino,
                title = "WHEEL PROTOCOL CLOSED",
                message = "Spinning is temporarily suspended by an admin until further notice."
            )
            wheelItems.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.gutter),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Default.Casino,
                    title = "WHEEL IS EMPTY",
                    message = "No rewards are loaded yet. An admin needs to add some slices."
                )
            }
            else -> {
                val pending = user?.playerData?.pendingSpins ?: 0
                val bankruptcy = user?.playerData?.bankruptcyLevel ?: 0
                val penaltyMode = pending > 0
                val resolved = spin.resultText != null && !spin.spinning
                // Win / loss / neutral colour shared by the readout and the state pill.
                val resultColor = when (spin.resultPositive) {
                    true -> Toxic
                    false -> Ember
                    null -> if (spin.resultText != null) Amber else TextLow
                }

                Box(Modifier.padding(horizontal = Spacing.gutter)) {
                    PenaltyStatusCard(bankruptcy, pending)
                }
                if (pending > 0 || bankruptcy > 0) Spacer(Modifier.height(Spacing.xs))

                if (user?.playerData?.luckyCoin == true) {
                    StatusPill(
                        "LUCKY COIN ARMED: NEXT WIN x2",
                        Amber,
                        modifier = Modifier.align(Alignment.CenterHorizontally).enterOnMount(0)
                    )
                    Spacer(Modifier.height(Spacing.xs))
                }

                // The wheel is the screen's hero: panel, live state pill and result readout.
                HeroPanel(
                    eyebrow = "Fate Protocol",
                    title = when {
                        spin.spinning -> "SPIN IN PROGRESS"
                        spin.resultText != null -> "SPIN COMPLETE"
                        penaltyMode -> "PENALTY SPINS QUEUED"
                        else -> "READY TO SPIN"
                    },
                    icon = Icons.Default.Casino,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.gutter)
                        .enterOnMount(1),
                    accent = if (penaltyMode) EmberDeep else Ember,
                    badge = {
                        StatusPill(
                            text = when {
                                spin.spinning -> "IN FLIGHT"
                                resolved -> "RESOLVED"
                                penaltyMode -> "$pending QUEUED"
                                else -> "ARMED"
                            },
                            color = when {
                                spin.spinning -> Ember
                                resolved -> resultColor
                                penaltyMode -> EmberDeep
                                else -> Toxic
                            }
                        )
                    }
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Fit the wheel to the space available, so short/small phones still see all of it.
                        val wheelSize = min(maxWidth.value, maxHeight.value - 24f).coerceIn(160f, 340f).dp
                        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.size(wheelSize + 24.dp)) {
                            FateWheel(
                                items = wheelItems,
                                modifier = Modifier
                                    .padding(top = 18.dp)
                                    .size(wheelSize)
                                    // Rotation happens in the draw layer: no recomposition per frame.
                                    .graphicsLayer { rotationZ = rotation.value }
                            )
                            WheelPointer(Modifier.size(width = 30.dp, height = 36.dp))
                        }
                    }

                    SurfaceCard(
                        accent = if (resolved) resultColor else null,
                        padding = Spacing.sm
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            val label = when {
                                spin.spinning -> "SPINNING..."
                                spin.resultText != null -> spin.resultText!!
                                else -> "AWAITING PROTOCOL"
                            }
                            AnimatedContent(
                                targetState = if (!spin.spinning && spin.penaltyNote != null) "$label\n${spin.penaltyNote}" else label,
                                transitionSpec = {
                                    if (performanceMode) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    else (fadeIn(tween(200)) + scaleIn(tween(250), initialScale = 0.85f)) togetherWith fadeOut(tween(120))
                                },
                                label = "spinResult"
                            ) { text ->
                                Text(
                                    text = text,
                                    color = if (spin.spinning) TextMid else resultColor,
                                    style = DeadFestType.cardTitle,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
                        .enterOnMount(2)
                ) {
                    PrimaryAction(
                        text = when {
                            spin.spinning -> "FATE IS DECIDING..."
                            penaltyMode -> "EXECUTE PENALTY SPIN ($pending)"
                            else -> "INITIATE SPIN"
                        },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.spinWheel(wheelItems, spinMs)
                        },
                        enabled = !spin.spinning,
                        // Penalty spins keep their pulsing ring; the pulse is skipped in Performance Mode.
                        modifier = if (penaltyMode) {
                            Modifier.penaltyRing(pulsing = !performanceMode && !spin.spinning)
                        } else {
                            Modifier
                        },
                        accent = if (penaltyMode) EmberDeep else Ember
                    )
                }
            }
        }
    }
}

/**
 * The wheel face. Everything (colours, text layouts, paths) is built once in
 * drawWithCache and only rebuilt when the items or size change. The spin itself
 * is just a layer rotation, which keeps it smooth on low-end GPUs.
 */
@Composable
private fun FateWheel(items: List<com.deadfest.data.model.WheelItem>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier.drawWithCache {
            val slice = 360f / items.size
            val radius = size.minDimension / 2f
            val colors = items.map { parseColor(it.color) }
            val labelStyle = TextStyle(
                color = Color.White,
                fontSize = if (items.size > 10) 11.sp else 14.sp,
                fontWeight = FontWeight.Black
            )
            val labels = items.map {
                textMeasurer.measure(
                    text = it.label,
                    style = labelStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = (radius * 0.62f).toInt().coerceAtLeast(1))
                )
            }
            val rim = Stroke(width = radius * 0.06f)
            val divider = Stroke(width = 2f)

            onDrawBehind {
                val c = center
                // Slice 0 starts at the top pointer (-90°) and goes clockwise.
                items.indices.forEach { i ->
                    val start = -90f + i * slice
                    drawArc(color = colors[i], startAngle = start, sweepAngle = slice, useCenter = true)
                    // Soft inner shade for depth, drawn as a flat translucent arc (no blur, cheap).
                    drawArc(
                        color = Color.Black.copy(alpha = 0.18f),
                        startAngle = start,
                        sweepAngle = slice,
                        useCenter = true,
                        topLeft = Offset(c.x - radius * 0.55f, c.y - radius * 0.55f),
                        size = androidx.compose.ui.geometry.Size(radius * 1.1f, radius * 1.1f)
                    )
                }
                // Dividers
                items.indices.forEach { i ->
                    rotate(-90f + i * slice, c) {
                        drawLine(Color.Black.copy(alpha = 0.45f), c, Offset(c.x + radius, c.y), strokeWidth = divider.width)
                    }
                }
                // Labels along each slice's centre line, reading outwards.
                items.indices.forEach { i ->
                    val layout = labels[i]
                    rotate(-90f + i * slice + slice / 2f, c) {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(c.x + radius * 0.28f, c.y - layout.size.height / 2f)
                        )
                    }
                }
                // Rim + hub
                drawCircle(Color(0xFF111111), radius - rim.width / 2f, c, style = rim)
                drawCircle(RedAccent.copy(alpha = 0.8f), radius - rim.width, c, style = Stroke(width = 2f))
                drawCircle(Color(0xFF111111), radius * 0.16f, c)
                drawCircle(RedAccent, radius * 0.16f, c, style = Stroke(width = radius * 0.025f))
                drawCircle(RedAccent, radius * 0.05f, c)
            }
        }
    ) { }
}

@Composable
private fun WheelPointer(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width / 2f, size.height)
            lineTo(0f, 0f)
            lineTo(size.width, 0f)
            close()
        }
        drawPath(path, Color.Black.copy(alpha = 0.5f))
        drawPath(Path().apply {
            moveTo(size.width / 2f, size.height - 4f)
            lineTo(4f, 2f)
            lineTo(size.width - 4f, 2f)
            close()
        }, RedAccent)
        drawCircle(Color.White, radius = size.width * 0.12f, center = Offset(size.width / 2f, size.height * 0.28f))
    }
}

/**
 * Pulsing white ring for the penalty-spin button. The animated alpha is only read in the
 * draw phase, so the pulse repaints the ring without recomposing the screen. The corner
 * radius matches PrimaryAction's shape so the ring hugs the button.
 */
@Composable
private fun Modifier.penaltyRing(pulsing: Boolean): Modifier {
    val alpha: State<Float> = if (pulsing) {
        rememberInfiniteTransition(label = "penaltyPulse").animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "penaltyRing"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    return this.drawWithContent {
        drawContent()
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = alpha.value),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(Radius.md.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

private fun parseColor(hex: String): Color =
    try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        Color.Gray
    }
