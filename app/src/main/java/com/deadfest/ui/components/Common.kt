package com.deadfest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadfest.ui.theme.*
import java.text.NumberFormat

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

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = RedAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp
    )
}

@Composable
fun CoinIcon(size: Int = 18) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(Gold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("$", color = Color.Black, fontSize = (size * 0.6f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CoinBadge(coins: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = CardBgRaised,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoinIcon(18)
            Spacer(Modifier.width(8.dp))
            Text(formatNumber(coins), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun PriceTag(price: Long, affordable: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoinIcon(14)
        Spacer(Modifier.width(6.dp))
        Text(
            formatNumber(price),
            color = if (affordable) TextPrimary else TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
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
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(RedAccent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = RedAccent, modifier = Modifier.size(36.dp))
                }
                Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Big icon tile used on list rows (shop items, titles, hub buttons). */
@Composable
fun IconTile(icon: ImageVector, tint: Color = RedAccent, size: Int = 52) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun deadFestFieldColors() =OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = RedAccent,
    unfocusedBorderColor = Outline,
    focusedContainerColor = CardBg,
    unfocusedContainerColor = CardBg,
    focusedLabelColor = RedAccent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = RedAccent,
    focusedLeadingIconColor = RedAccent,
    unfocusedLeadingIconColor = TextSecondary,
    focusedTrailingIconColor = TextSecondary,
    unfocusedTrailingIconColor = TextSecondary
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
        "gold" -> Brush.sweepGradient(listOf(Gold, Color(0xFFFFF59D), Gold, Color(0xFFFFA000), Gold))
        "inferno" -> Brush.sweepGradient(listOf(RedAccent, Color(0xFFFF9800), Gold, RedAccent))
        "prestige" -> Brush.sweepGradient(listOf(Color(0xFFB388FF), ShieldCyan, Gold, Color(0xFFB388FF)))
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

val PrestigePurple = Color(0xFFB388FF)

@Composable
fun PrestigeBadge(count: Int) {
    Surface(
        color = PrestigePurple.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PrestigePurple.copy(alpha = 0.7f))
    ) {
        Text(
            "★ P$count",
            color = Color(0xFFD1C4E9),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    val accent = if (bankruptcyLevel >= 1) RedAccent else Gold
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressClick(RoundedCornerShape(16.dp), onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = if (bankruptcyLevel >= 2) 0.2f else 0.1f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (bankruptcyLevel >= 2) 2.dp else 1.dp, accent)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (bankruptcyLevel >= 1) Icons.Default.MoneyOff else Icons.Default.Casino,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(message, color = TextSecondary, fontSize = 12.sp)
            }
            if (pendingSpins > 0) {
                Spacer(Modifier.width(10.dp))
                PendingSpinsChip(pendingSpins)
            }
        }
    }
}

@Composable
fun PendingSpinsChip(count: Int) {
    Column(
        modifier = Modifier
            .background(RedAccent, RoundedCornerShape(12.dp))
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            SectionLabel(title)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
