package com.deadfest.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.ScratchOutcome
import com.deadfest.data.model.ScratchPrize
import com.deadfest.domain.Weighted
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import com.deadfest.ui.viewmodel.ScratchState

private const val REVEAL_MS = 1_200

@Composable
fun ScratchScreen(navController: NavController, viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val prizes by viewModel.scratchPrizes.collectAsStateWithLifecycle()
    val scratch by viewModel.scratch.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val economy = globalState.economy
    val pool = remember(prizes) { prizes.filter { it.enabled } }
    val coins = user?.playerData?.coins ?: 0L

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "SCRATCH CARDS",
            subtitle = "INSTANT WASTELAND LOTTERY",
            onBack = { navController.popBackStack() },
            trailing = { CoinBadge(coins) }
        )

        when {
            globalState.lockdownActive -> NoticeCard(Icons.Default.Lock, "SYSTEM LOCKDOWN ACTIVE", "Scratch Cards are frozen by an administrator.")
            !economy.scratchEnabled -> NoticeCard(Icons.Default.ConfirmationNumber, "SCRATCH CARDS OFFLINE", "An admin has switched Scratch Cards off for now.")
            pool.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.gutter),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Default.ConfirmationNumber,
                    title = "NO PRIZES LOADED",
                    message = "An admin needs to stock the prize pool first."
                )
            }
            else -> {
                val chances = remember(pool) { Weighted.chances(pool.map { it.weight }) }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        top = Spacing.md,
                        end = Spacing.gutter,
                        bottom = Spacing.lg
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        val result = scratch.result
                        // The card is the hero: hero panel carries the state, the card carries the reveal.
                        HeroPanel(
                            eyebrow = "Instant Lottery",
                            title = when {
                                scratch.busy -> "STAMPING A FRESH CARD"
                                result != null -> "CARD RESOLVED"
                                else -> "READY TO SCRATCH"
                            },
                            icon = Icons.Default.ConfirmationNumber,
                            modifier = Modifier.enterOnMount(0),
                            accent = when (result?.outcome) {
                                ScratchOutcome.ITEM -> Amber
                                ScratchOutcome.WIN -> Toxic
                                ScratchOutcome.LOSS -> Ember
                                null -> Ember
                            },
                            badge = {
                                StatusPill(
                                    text = when {
                                        scratch.busy -> "PRINTING"
                                        result?.outcome == ScratchOutcome.ITEM -> "RARE FIND"
                                        result?.outcome == ScratchOutcome.WIN -> "WIN"
                                        result?.outcome == ScratchOutcome.LOSS -> if (result.coinsWon > 0) "SMALL RETURN" else "NO LUCK"
                                        else -> "READY"
                                    },
                                    color = when {
                                        scratch.busy -> Ember
                                        result?.outcome == ScratchOutcome.ITEM -> Amber
                                        result?.outcome == ScratchOutcome.WIN -> Toxic
                                        result?.outcome == ScratchOutcome.LOSS -> if (result.coinsWon > 0) Amber else Ember
                                        else -> Toxic
                                    }
                                )
                            }
                        ) {
                            ScratchCardView(
                                state = scratch,
                                cost = economy.scratchCost,
                                onRevealed = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                            )
                        }
                    }
                    item {
                        val affordable = coins >= economy.scratchCost
                        PrimaryAction(
                            text = when {
                                scratch.busy -> "SCRATCHING..."
                                !affordable -> "NEED ${formatNumber(economy.scratchCost)} COINS"
                                else -> "SCRATCH A CARD · ${formatNumber(economy.scratchCost)} COINS"
                            },
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.scratchCard(pool)
                            },
                            enabled = !scratch.busy && affordable,
                            modifier = Modifier.enterOnMount(1)
                        )
                    }
                    item { SectionLabel("Prize Pool & Odds", Modifier.padding(top = Spacing.xs).enterOnMount(2)) }
                    items(pool.indices.toList(), key = { pool[it].id.ifEmpty { "p$it" } }) { i ->
                        PrizeRow(prize = pool[i], pct = chances[i], modifier = Modifier.enterOnMount(3))
                    }
                }
            }
        }
    }
}

/**
 * One prize-pool row. Hierarchy: tinted icon carries the prize's own colour, the name is the
 * loudest text, the payout description supports it and the coin value + odds sit right-aligned.
 */
@Composable
private fun PrizeRow(prize: ScratchPrize, pct: Double, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier, padding = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = prize.icon(), tint = parseThemeColor(prize.color), size = 40)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    prize.label,
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    prizeDescription(prize),
                    color = TextMid,
                    style = DeadFestType.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Column(horizontalAlignment = Alignment.End) {
                if (prize.coinAmount > 0) {
                    PriceTag(prize.coinAmount)
                    Spacer(Modifier.height(Spacing.xxs))
                }
                Text("${CasualtyFormat.pct(pct)}%", color = TextMid, style = DeadFestType.chip)
                Text("ODDS", color = TextLow, style = DeadFestType.microLabel)
            }
        }
    }
}

private fun ScratchPrize.icon(): ImageVector = when (type) {
    ScratchPrize.TYPE_SHIELD -> Icons.Default.Shield
    ScratchPrize.TYPE_LUCKY -> Icons.Default.MonetizationOn
    ScratchPrize.TYPE_COINS -> Icons.Default.Paid
    else -> Icons.Default.MoneyOff
}

private object CasualtyFormat {
    fun pct(p: Double): String = com.deadfest.domain.CasualtyTax.formatPct(p)
}

private fun prizeDescription(p: ScratchPrize): String = when (p.type) {
    ScratchPrize.TYPE_COINS -> if (p.coinAmount > 0) "${formatNumber(p.coinAmount)} coins" else "No win"
    ScratchPrize.TYPE_SHIELD -> "Free Shield (cashed in if you already have one)"
    ScratchPrize.TYPE_LUCKY -> "Free Lucky Coin (cashed in if you already have one)"
    else -> "No win"
}

/**
 * The card: prize underneath, foil on top. A zig-zag "scratch" stroke clears the foil
 * (BlendMode.Clear on an offscreen layer). The progress is read in the draw phase only,
 * so the reveal never recomposes; in Performance Mode it's instant.
 */
@Composable
private fun ScratchCardView(state: ScratchState, cost: Long, onRevealed: () -> Unit) {
    val reduced = LocalPerformanceMode.current
    val reveal = remember { Animatable(if (state.result != null) 1f else 0f) }
    var shownId by remember { mutableIntStateOf(state.scratchId) }

    LaunchedEffect(state.busy) {
        if (state.busy) reveal.snapTo(0f)   // fresh foil while the card is being bought
    }
    LaunchedEffect(state.scratchId) {
        if (state.scratchId == shownId || state.result == null) return@LaunchedEffect
        reveal.snapTo(0f)
        if (reduced) reveal.snapTo(1f) else reveal.animateTo(1f, tween(REVEAL_MS, easing = LinearEasing))
        shownId = state.scratchId
        onRevealed()
    }

    val result = state.result
    val accent = when (result?.outcome) {
        ScratchOutcome.ITEM -> Amber
        ScratchOutcome.WIN -> Toxic
        ScratchOutcome.LOSS -> Ember
        null -> TextLow
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(Radius.xl))
            .background(accent.copy(alpha = Tint.wash))
    ) {
        // Prize layer
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (result == null) {
                Text("?", color = TextLow, fontSize = 48.sp, fontWeight = FontWeight.Black)
            } else {
                Text(
                    when (result.outcome) {
                        ScratchOutcome.ITEM -> "RARE FIND"
                        ScratchOutcome.WIN -> "WINNER"
                        ScratchOutcome.LOSS -> if (result.coinsWon > 0) "SMALL RETURN" else "NO LUCK"
                    },
                    color = accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    result.prize.label.uppercase(),
                    color = TextHi,
                    style = DeadFestType.statHero,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    when {
                        result.convertedItem -> "Already owned: cashed in for ${formatNumber(result.coinsWon)} coins"
                        result.outcome == ScratchOutcome.ITEM -> "Added to your gear"
                        result.coinsWon > 0 -> "+${formatNumber(result.coinsWon)} coins (card cost ${formatNumber(cost)})"
                        else -> "Better luck next card"
                    },
                    color = TextMid,
                    style = DeadFestType.body,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Foil layer
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(-0.05f * w, 0.08f * h)
                        lineTo(1.05f * w, 0.28f * h)
                        lineTo(-0.05f * w, 0.48f * h)
                        lineTo(1.05f * w, 0.68f * h)
                        lineTo(-0.05f * w, 0.88f * h)
                        lineTo(1.05f * w, 1.08f * h)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val length = measure.length
                    val segment = Path()
                    val foil = Brush.linearGradient(
                        listOf(Color(0xFF8E8E8E), Color(0xFFD6D6D6), Color(0xFF7A7A7A), Color(0xFFC4C4C4)),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                    val stripe = Color.White.copy(alpha = 0.08f)
                    val brushWidth = h * 0.34f
                    onDrawBehind {
                        val p = reveal.value
                        if (p >= 0.999f) return@onDrawBehind
                        drawRect(foil)
                        var x = -h
                        while (x < w) {
                            drawLine(stripe, Offset(x, h), Offset(x + h, 0f), strokeWidth = 6f)
                            x += 24f
                        }
                        if (p > 0f) {
                            segment.reset()
                            measure.getSegment(0f, length * p, segment, true)
                            drawPath(
                                segment,
                                Color.Black,
                                style = Stroke(width = brushWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                }
        )
    }
}
