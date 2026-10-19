package com.deadfest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.deadfest.R
import com.deadfest.ui.theme.*
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Performance mode: fewer/shorter animations for low-end phones. */
val LocalPerformanceMode = staticCompositionLocalOf { false }

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()
fun formatNumber(n: Long): String = numberFormat.format(n)
fun formatNumber(n: Int): String = numberFormat.format(n.toLong())

/**
 * Click with a subtle "press in" scale. It runs entirely in the draw layer
 * (graphicsLayer), so it never triggers recomposition and stays cheap on slow phones.
 */
@Composable
fun Modifier.pressClick(
    shape: Shape = RoundedCornerShape(20.dp),
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalPerformanceMode.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) 0.97f else 1f,
        animationSpec = spring(stiffness = 800f),
        label = "pressScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * The brand mark in a tinted tile. Sits at the left of every screen header so the app reads as
 * one product rather than a set of unrelated tabs.
 */
@Composable
fun BrandMarkBadge(size: Int = 38, tint: Color = Ember) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.22f), EmberDeep.copy(alpha = 0.10f))
                )
            )
            .border(BorderStroke(Stroke.hair, tint.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.sm)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_deadfest_mark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size((size * 0.58f).dp)
        )
    }
}

/**
 * Screen header: brand mark (or a back button), title, accent subtitle and a gradient rule.
 *
 * Used by every screen, so the app has exactly one header design.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                // The tile is 38dp because that is what looks right in the header, but 38dp is under
                // the 48dp minimum touch target. minimumInteractiveComponentSize widens the hit area
                // without changing how the tile looks.
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(38.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Ink3)
                        .border(BorderStroke(Stroke.hair, InkLine), RoundedCornerShape(Radius.sm))
                        .pressClick(RoundedCornerShape(Radius.sm), onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextHi,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                BrandMarkBadge()
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = TextHi,
                    style = DeadFestType.screenTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle.uppercase(),
                    color = Ember,
                    style = DeadFestType.screenSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(Spacing.xs))
                trailing()
            }
        }
        GradientRule()
    }
}

/** Group heading with a leading ember bar. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, accent: Color = Ember) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text.uppercase(),
            color = TextMid,
            style = DeadFestType.section,
            // Section titles are the skeleton of every screen. Marking them as headings is what lets
            // TalkBack jump section to section instead of reading the screen as one wall of text.
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
fun CoinIcon(size: Int = 18) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                Brush.verticalGradient(listOf(Amber, AmberDeep)),
                CircleShape
            )
            .border(BorderStroke(Stroke.hair, AmberDeep.copy(alpha = 0.6f)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("$", color = Color(0xFF2B1B00), fontSize = (size * 0.6f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CoinBadge(coins: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            // A bare number is ambiguous out loud — "1,240" of what? Named, and via
            // clearAndSetSemantics rather than a merge, so it still folds into an enclosing
            // clickable card instead of becoming a second stop inside it.
            .clearAndSetSemantics { contentDescription = "${formatNumber(coins)} coins" },
        color = Ink3,
        shape = RoundedCornerShape(Radius.pill),
        border = BorderStroke(Stroke.hair, Amber.copy(alpha = Tint.edge))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoinIcon(16)
            Spacer(Modifier.width(Spacing.xs))
            Text(formatNumber(coins), color = Amber, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun PriceTag(price: Long, affordable: Boolean = true, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            // A price with no unit is just a number out loud, and it is the one thing a shop row
            // most needs to convey. Cleared rather than merged, so the enclosing shop card stays the
            // single stop for the whole item.
            .clearAndSetSemantics { contentDescription = "${formatNumber(price)} coins" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoinIcon(14)
        Spacer(Modifier.width(6.dp))
        Text(
            formatNumber(price),
            color = if (affordable) Amber else TextLow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = Tint.fill),
        shape = RoundedCornerShape(Radius.pill),
        border = BorderStroke(Stroke.hair, color.copy(alpha = Tint.edge))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text.uppercase(), color = color, style = DeadFestType.microLabel)
        }
    }
}

/** Full-width notice used for lockdown / closed-by-admin states. */
@Composable
fun NoticeCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.xl))
                .background(
                    Brush.verticalGradient(
                        listOf(Ember.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .background(Ink2)
                .border(BorderStroke(Stroke.hair, Ember.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.xl))
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Ember.copy(alpha = Tint.wash))
                    .border(BorderStroke(Stroke.hair, Ember.copy(alpha = Tint.edge)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Ember, modifier = Modifier.size(34.dp))
            }
            Text(title.uppercase(), color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(message, color = TextMid, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Ink3)
                .border(BorderStroke(Stroke.hair, InkLine), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextLow, modifier = Modifier.size(26.dp))
        }
        Text(text, color = TextMid, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
    }
}

/** Big icon tile used on list rows (shop items, titles, hub buttons). */
@Composable
fun IconTile(icon: ImageVector, tint: Color = RedAccent, size: Int = 52) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = Tint.fill), tint.copy(alpha = 0.05f))
                )
            )
            .border(BorderStroke(Stroke.hair, tint.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.md)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun deadFestFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi,
    unfocusedTextColor = TextHi,
    focusedBorderColor = Ember,
    unfocusedBorderColor = InkLine,
    focusedContainerColor = Ink2,
    unfocusedContainerColor = Ink2,
    focusedLabelColor = Ember,
    unfocusedLabelColor = TextMid,
    cursorColor = Ember,
    focusedLeadingIconColor = Ember,
    unfocusedLeadingIconColor = TextLow,
    focusedTrailingIconColor = TextMid,
    unfocusedTrailingIconColor = TextLow,
    focusedPlaceholderColor = TextLow,
    unfocusedPlaceholderColor = TextLow,
    errorBorderColor = Ember,
    errorContainerColor = Ink2
)

/** Consistent confirm dialog for anything that spends coins or changes data. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = if (destructive) RedAccent else TextPrimary, fontWeight = FontWeight.Black, fontSize = 19.sp) },
        text = { Text(message, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text(confirmText, fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold) }
        }
    )
}

// ================================================================== v2.0.5 shared pieces

fun parseThemeColor(hex: String?): Color =
    try {
        Color(android.graphics.Color.parseColor(hex?.trim()?.takeIf { it.isNotEmpty() } ?: "#536DFE"))
    } catch (_: Exception) {
        RedAccent
    }

/** Avatar frames unlocked through levels (and prestige). */
object AvatarFrames {
    val labels = linkedMapOf(
        "none" to "No frame",
        "rust" to "Rust",
        "neon" to "Neon",
        "toxic" to "Toxic",
        "gold" to "Gold",
        "inferno" to "Inferno",
        "prestige" to "Prestige"
    )

    fun brush(frame: String?): Brush? = when (frame) {
        "rust" -> SolidColor(Bronze)
        "neon" -> SolidColor(RedAccent)
        "toxic" -> SolidColor(OnlineGreen)
        "gold" -> Brush.sweepGradient(listOf(Amber, Color(0xFFFFF9C4), Amber, AmberDeep, Amber))
        "inferno" -> Brush.sweepGradient(listOf(Ember, EmberGlow, Amber, Ember))
        "prestige" -> Brush.sweepGradient(listOf(Prestige, Plasma, Amber, Prestige))
        else -> null
    }
}

/** Avatar with badge-colour ring, optional level frame and online dot. Static brushes only (cheap). */
@Composable
fun FramedAvatar(
    avatar: String?,
    frame: String?,
    color: String?,
    size: Int,
    online: Boolean = false
) {
    val themeColor = remember(color) { parseThemeColor(color) }
    val frameBrush = remember(frame) { AvatarFrames.brush(frame) }
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(themeColor.copy(alpha = 0.18f), CircleShape)
                .then(
                    if (frameBrush != null) Modifier.border(3.dp, frameBrush, CircleShape)
                    else Modifier.border(1.5.dp, themeColor, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(avatar?.takeIf { it.isNotBlank() } ?: "💀", fontSize = (size * 0.45f).sp)
        }
        if (online) {
            Box(
                Modifier
                    .size((size * 0.28f).dp)
                    .background(DarkBg, CircleShape)
                    .padding(2.dp)
                    .background(OnlineGreen, CircleShape)
            )
        }
    }
}

@Composable
fun LevelBadge(level: Int, prestige: Int = 0, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(color = RedAccent.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
            Text(
                "LV $level",
                color = RedAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (prestige > 0) {
            Spacer(Modifier.width(4.dp))
            PrestigeBadge(prestige)
        }
    }
}

/** Kept as a named alias so existing call sites keep working; the value now comes from the scale. */
val PrestigePurple = Prestige

@Composable
fun PrestigeBadge(count: Int) {
    Surface(
        color = Prestige.copy(alpha = Tint.fill),
        shape = RoundedCornerShape(Radius.xs),
        border = BorderStroke(Stroke.hair, Prestige.copy(alpha = Tint.edgeStrong))
    ) {
        Text(
            "★ P$count",
            color = Prestige,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

/** XP progress towards the next level. Animated fill unless Performance Mode is on. */
@Composable
fun XpBar(xp: Long, level: Int, cfg: com.deadfest.domain.XpConfig, modifier: Modifier = Modifier) {
    val maxed = level >= cfg.maxLevel
    val need = remember(level, cfg) { com.deadfest.domain.Leveling.xpForLevel(level, cfg) }
    val target = com.deadfest.domain.Leveling.progress(xp, level, cfg)
    val reduced = LocalPerformanceMode.current
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) snap() else tween(600),
        label = "xp"
    )
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // "XP" and "1,240 / 5,000" are one fact, not two stops.
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            Text("XP", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (maxed) "MAX LEVEL · PRESTIGE READY" else "${formatNumber(xp)} / ${formatNumber(need)}",
                color = if (maxed) Gold else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                // A hand-drawn bar carries no semantics at all, so without this the progress is
                // simply absent for anyone not looking at it. Reports the target rather than the
                // animated value, so it does not narrate the animation.
                .progressSemantics(target)
                .background(Outline, RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(if (maxed) Gold else RedAccent, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
fun AlertBanner(icon: ImageVector, title: String, message: String, accent: Color = RedAccent, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressClick(RoundedCornerShape(16.dp), onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(message, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

/**
 * High-visibility bankruptcy / Forced Spin Protocol warning (Dashboard and Wheel).
 * Shows nothing when the survivor is solvent with no pending spins.
 */
@Composable
fun PenaltyStatusCard(bankruptcyLevel: Int, pendingSpins: Int, onClick: (() -> Unit)? = null) {
    if (bankruptcyLevel <= 0 && pendingSpins <= 0) return
    val (title, message) = when {
        bankruptcyLevel >= 2 -> "BANKRUPTCY LEVEL 2" to
            "Clear every pending spin and get your balance above 0 to escape. Survival payouts are halved."
        bankruptcyLevel == 1 -> "BANKRUPTCY LEVEL 1" to
            "Balance hit 0. Survival payouts are halved. Another death means Level 2."
        else -> "FORCED SPIN PROTOCOL" to "Penalty spins are waiting for you on the Wheel of Fate."
    }
    val accent = if (bankruptcyLevel >= 1) Ember else Amber
    val critical = bankruptcyLevel >= 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = if (critical) 0.24f else 0.13f), Color.Transparent)
                )
            )
            .background(Ink2)
            .border(
                BorderStroke(
                    if (critical) Stroke.emphasis else Stroke.hair,
                    accent.copy(alpha = if (critical) 1f else Tint.edgeStrong)
                ),
                RoundedCornerShape(Radius.lg)
            )
            .then(
                if (onClick != null) Modifier.pressClick(RoundedCornerShape(Radius.lg), onClick = onClick)
                else Modifier
            )
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (bankruptcyLevel >= 1) Icons.Default.MoneyOff else Icons.Default.Casino,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (critical) accent else TextHi, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(message, color = TextMid, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (pendingSpins > 0) {
            Spacer(Modifier.width(Spacing.xs))
            PendingSpinsChip(pendingSpins)
        }
    }
}

@Composable
fun PendingSpinsChip(count: Int) {
    Column(
        modifier = Modifier
            .background(Ember, RoundedCornerShape(Radius.sm))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$count", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "PENDING\nSPINS",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            lineHeight = 9.sp
        )
    }
}

/** Elevated panel used by the Live Event Dashboard. */
@Composable
fun DashboardPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Ink2)
            .border(BorderStroke(Stroke.hair, InkLine), RoundedCornerShape(Radius.lg))
            .padding(Spacing.sm)
    ) {
        SectionLabel(title)
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

// ================================================================== v2.0.7 shared pieces

private val countdownFormat = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault())

/** "3D 04H 12M" style label for anything that is not a full countdown tile row. */
fun formatCountdown(ms: Long): String {
    if (ms <= 0L) return "LIVE NOW"
    val s = ms / 1000
    val d = s / 86_400
    val h = (s % 86_400) / 3_600
    val m = (s % 3_600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "${d}D ${h}H ${m}M"
        h > 0 -> "${h}H ${m}M ${sec}S"
        m > 0 -> "${m}M ${sec}S"
        else -> "${sec}S"
    }
}

/** Friendly date/time for a scheduled event. Returns [fallback] when no time is set. */
fun formatEventTime(millis: Long, fallback: String = "Date to be announced"): String =
    if (millis > 0L) countdownFormat.format(Date(millis)) else fallback

/**
 * Native date -> time picker chain. Uses the platform dialogs (available since API 1),
 * so it works on Android 7 without pulling in the Material 3 date picker or core library
 * desugaring. [onPicked] receives epoch millis at minute precision.
 */
fun showDateTimePicker(context: android.content.Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply {
        timeInMillis = initialMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

/** Countdown display precision. Performance Mode drops seconds so the timer can idle. */
private enum class CountdownPrecision { Seconds, Minutes }

/** Tick interval for each precision: 1s for a live seconds readout, 30s when seconds are hidden. */
private fun CountdownPrecision.intervalMs(): Long = when (this) {
    CountdownPrecision.Seconds -> 1_000L
    CountdownPrecision.Minutes -> 30_000L
}

/**
 * A lifecycle-aware wall clock, shared by every live readout in the app.
 *
 * This is the only place in Dead-Fest where a repeating timer is allowed to exist. What it
 * guarantees:
 *  - **Lifecycle aware.** The loop runs inside `repeatOnLifecycle(RESUMED)`, so it is cancelled
 *    the moment the app is backgrounded and restarted the moment it returns — immediately
 *    re-reading the wall clock, so nothing shows a stale value after a resume.
 *  - **Self-cancelling.** `LaunchedEffect` tears the coroutine down when the composable leaves the
 *    composition, so navigating between tabs can never leak a timer.
 *  - **Free when idle.** `running = false` schedules no coroutine at all, so screens with nothing
 *    to count down to cost nothing.
 *  - **A State, not a snapshot.** Callers read `.value` inside the smallest composable that needs
 *    it, which scopes recomposition to those pixels instead of the whole screen.
 *  - **Timezone-safe.** Epoch millis only, never local-time arithmetic.
 *
 * @param intervalMs how often to re-read the clock while running.
 * @param running false to suspend the timer entirely.
 * @param key restart the timer if this identity changes (e.g. an admin edits the target date).
 * @param stopWhen called with the freshly read clock; returning true stops the timer for good.
 */
@Composable
fun rememberLiveNow(
    intervalMs: Long,
    running: Boolean = true,
    key: Any? = null,
    stopWhen: (Long) -> Boolean = { false }
): State<Long> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs, running, lifecycleOwner, key) {
        if (!running) {
            now.value = System.currentTimeMillis()
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val t = System.currentTimeMillis()
                now.value = t
                if (stopWhen(t)) break
                delay(intervalMs)
            }
        }
    }
    return now
}

/**
 * Live clock for the Dead-Fest countdown.
 *
 * Normally 1 Hz so seconds tick smoothly. In Performance Mode it drops to one tick every 30s,
 * which is exactly why [CountdownTiles] hides the seconds tile in that mode — a 30s-stale seconds
 * digit would be a lie, whereas DAYS/HRS/MIN stay correct at that rate. Does nothing at all when
 * there is no target, or the target has already passed.
 */
@Composable
private fun rememberCountdownNow(target: Long?): State<Long> {
    val reduced = LocalPerformanceMode.current
    val precision = if (reduced) CountdownPrecision.Minutes else CountdownPrecision.Seconds
    return rememberLiveNow(
        intervalMs = precision.intervalMs(),
        running = target != null && target > System.currentTimeMillis(),
        key = target,
        stopWhen = { target != null && it >= target }
    )
}

/**
 * Day/hour/minute/second breakdown, shared by the tiles and the compact label.
 *
 * `internal` so the unit tests can pin the arithmetic: this is the function that decides what the
 * survivor actually reads, so a subtle off-by-one here would be a wrong countdown on screen.
 */
internal fun countdownParts(totalSeconds: Long, withSeconds: Boolean): List<Pair<String, Long>> = buildList {
    add("DAYS" to totalSeconds / 86_400)
    add("HRS" to (totalSeconds % 86_400) / 3_600)
    add("MIN" to (totalSeconds % 3_600) / 60)
    if (withSeconds) add("SEC" to totalSeconds % 60)
}

/**
 * Spoken form of a countdown, rounded down to whole minutes.
 *
 * The tiles tick every second because that is satisfying to watch; a spoken reading that changed
 * that often would be useless. "Starts in 2 days 5 hours 30 minutes" is what someone listening
 * actually needs, and it holds still for a whole minute.
 */
internal fun spokenCountdown(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val days = safe / 86_400
    val hours = (safe % 86_400) / 3_600
    val minutes = (safe % 3_600) / 60
    val spoken = buildList {
        if (days > 0) add(if (days == 1L) "1 day" else "$days days")
        if (hours > 0) add(if (hours == 1L) "1 hour" else "$hours hours")
        if (minutes > 0) add(if (minutes == 1L) "1 minute" else "$minutes minutes")
    }
    return if (spoken.isEmpty()) "Less than a minute to go" else "Starts in " + spoken.joinToString(" ")
}

/**
 * DAYS / HRS / MIN / SEC readout. In Performance Mode the SEC tile is omitted so the display
 * stays truthful at the reduced 30s tick rate. Plain text + solid fills: no bitmaps, no shadows.
 */
@Composable
fun CountdownTiles(
    remainingMs: Long,
    modifier: Modifier = Modifier,
    showSeconds: Boolean = !LocalPerformanceMode.current
) {
    val total = (remainingMs / 1000).coerceAtLeast(0)
    val parts = countdownParts(total, showSeconds)
    Row(
        modifier = modifier
            .fillMaxWidth()
            // One stop for the whole readout, spoken to the minute.
            //
            // Left as loose text this is eight separate nodes — "zero zero", "one two", "DAYS" — and
            // the listener has to reassemble the time in their head while the values shift under
            // them. There is deliberately no live region: a countdown that interrupts every few
            // seconds would be worse than one you can choose to go and read.
            .clearAndSetSemantics { contentDescription = spokenCountdown(total) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        parts.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(
                        Brush.verticalGradient(listOf(Ink4, Ink3))
                    )
                    .border(Stroke.hair, Amber.copy(alpha = if (label == "SEC") 0.34f else 0.16f), RoundedCornerShape(Radius.md))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    value.toString().padStart(2, '0'),
                    color = if (label == "SEC") Amber else TextHi,
                    style = DeadFestType.stat,
                    maxLines = 1
                )
                Text(label, color = TextLow, style = DeadFestType.microLabel)
            }
        }
    }
}

/**
 * "Next Dead-Fest" hero panel: season name, a live countdown, the games lined up for it and an
 * admin blurb.
 *
 * Only the two sub-composables that actually read the clock ([CountdownStatusPill] and
 * [CountdownReadout]) recompose each tick — the season name, game chips and blurb are static and
 * never re-run.
 */
@Composable
fun NextEventHero(
    seasonName: String,
    games: List<String>,
    notes: String,
    startsAt: Long?,
    modifier: Modifier = Modifier
) {
    val nowState = rememberCountdownNow(startsAt)
    // Formatted once per target instead of once per tick.
    val startLabel = remember(startsAt) {
        if (startsAt != null && startsAt > 0L) formatEventTime(startsAt) else null
    }

    HeroPanel(
        eyebrow = "Next Dead-Fest",
        title = seasonName.uppercase(),
        icon = Icons.Default.Celebration,
        modifier = modifier,
        accent = Amber,
        badge = { CountdownStatusPill(startsAt, nowState) }
    ) {
        CountdownReadout(startsAt, nowState, startLabel)

        if (games.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Ember, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("IN THE LINE-UP", color = TextLow, style = DeadFestType.section)
            }
            Spacer(Modifier.height(Spacing.xs))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(games, key = { it }) { game ->
                    Surface(
                        color = Ink4,
                        shape = RoundedCornerShape(Radius.pill),
                        border = BorderStroke(Stroke.hair, Amber.copy(alpha = 0.3f))
                    ) {
                        Text(
                            game.uppercase(),
                            color = TextHi,
                            style = DeadFestType.chip,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        if (notes.isNotBlank()) {
            Spacer(Modifier.height(Spacing.md))
            GradientRule()
            Spacer(Modifier.height(Spacing.sm))
            Text(notes, color = TextMid, style = DeadFestType.body)
        }
    }
}

/** Live state pill. Isolated so a tick repaints only this pill. */
@Composable
private fun CountdownStatusPill(target: Long?, now: State<Long>) {
    when {
        target == null -> StatusPill("TBA", TextLow)
        now.value >= target -> StatusPill("LIVE NOW", OnlineGreen)
        else -> StatusPill("UPCOMING", Amber)
    }
}

/** Live countdown body. Isolated so a tick repaints only the digits. */
@Composable
private fun CountdownReadout(target: Long?, now: State<Long>, startLabel: String?) {
    if (target == null) {
        Text(
            "Start time hasn't been locked in yet.",
            color = TextMid,
            fontSize = 13.sp
        )
        return
    }
    val remaining = target - now.value
    if (remaining <= 0L) {
        Text(
            "It's on. Good luck out there.",
            color = OnlineGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    } else {
        CountdownTiles(remaining)
    }
    if (startLabel != null) {
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = TextLow, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (remaining <= 0L) "Started $startLabel" else "Starts $startLabel",
                color = TextMid,
                fontSize = 12.sp
            )
        }
    }
}

/** Compact label/value tile used across the progress and season screens. */
@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color = TextPrimary,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        // Label and value are one fact. Merged, TalkBack reads "TOTAL DEATHS, 42"; unmerged it reads
        // the label, then the number, as two unrelated stops.
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = CardBgRaised,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Horizontal proportion bar used for per-sector death breakdowns. */
@Composable
fun ProportionBar(fraction: Float, accent: Color = RedAccent, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Outline, RoundedCornerShape(50))
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(50))
            )
        }
    }
}
