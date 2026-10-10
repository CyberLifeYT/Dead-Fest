package com.deadfest.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(
            title = "SCRATCH CARDS",
            subtitle = "INSTANT WASTELAND LOTTERY",
            onBack = { navController.popBackStack() },
            trailing = { CoinBadge(coins) }
        )

        when {
            globalState.lockdownActive -> NoticeCard(Icons.Default.Lock, "SYSTEM LOCKDOWN ACTIVE", "Scratch Cards are frozen by an administrator.")
            !economy.scratchEnabled -> NoticeCard(Icons.Default.ConfirmationNumber, "SCRATCH CARDS OFFLINE", "An admin has switched Scratch Cards off for now.")
            pool.isEmpty() -> NoticeCard(Icons.Default.ConfirmationNumber, "NO PRIZES LOADED", "An admin needs to stock the prize pool first.")
            else -> {
                val chances = remember(pool) { Weighted.chances(pool.map { it.weight }) }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ScratchCardView(
                            state = scratch,
                            cost = economy.scratchCost,
                            onRevealed = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                        )
                    }
                    item {
                        val affordable = coins >= economy.scratchCost
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.scratchCard(pool)
                            },
                            enabled = !scratch.busy && affordable,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedAccent, disabledContainerColor = CardBgRaised),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(
                                when {
                                    scratch.busy -> "SCRATCHING..."
                                    !affordable -> "NEED ${formatNumber(economy.scratchCost)} COINS"
                                    else -> "SCRATCH A CARD · ${formatNumber(economy.scratchCost)} COINS"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                    item { SectionLabel("PRIZE POOL & ODDS", Modifier.padding(top = 8.dp)) }
                    items(pool.indices.toList(), key = { pool[it].id.ifEmpty { "p$it" } }) { i ->
                        val p = pool[i]
                        Surface(color = CardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Outline)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(14.dp).background(parseThemeColor(p.color), CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(prizeDescription(p), color = TextSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    "${CasualtyFormat.pct(chances[i])}%",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
        ScratchOutcome.ITEM -> Gold
        ScratchOutcome.WIN -> OnlineGreen
        ScratchOutcome.LOSS -> RedAccent
        null -> TextMuted
    }
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
    ) {
        // Prize layer
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (result == null) {
                Text("?", color = TextMuted, fontSize = 48.sp, fontWeight = FontWeight.Black)
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
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    when {
                        result.convertedItem -> "Already owned: cashed in for ${formatNumber(result.coinsWon)} coins"
                        result.outcome == ScratchOutcome.ITEM -> "Added to your gear"
                        result.coinsWon > 0 -> "+${formatNumber(result.coinsWon)} coins (card cost ${formatNumber(cost)})"
                        else -> "Better luck next card"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
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
