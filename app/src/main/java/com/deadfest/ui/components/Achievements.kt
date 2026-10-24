package com.deadfest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.deadfest.R
import com.deadfest.domain.Achievement
import com.deadfest.domain.Achievements
import com.deadfest.ui.theme.*

/** Icon for each achievement badge. */
fun achievementIcon(id: String): ImageVector = when (id) {
    "first_blood" -> Icons.Default.WaterDrop
    "centurion" -> Icons.Default.MilitaryTech
    "survivor_7" -> Icons.Default.EventAvailable
    "bankrupt_back" -> Icons.AutoMirrored.Filled.TrendingUp
    "jackpot" -> Icons.Default.Diamond
    "high_roller" -> Icons.Default.Savings
    "level_10", "level_25" -> Icons.Default.Star
    "prestige" -> Icons.Default.AutoAwesome
    "ghost_economy" -> Icons.Default.AccountBalance
    "hex_master" -> Icons.Default.AutoFixHigh
    "deflector" -> Icons.Default.Shield
    "covered" -> Icons.Default.HealthAndSafety
    "lucky_strike" -> Icons.Default.MonetizationOn
    "photographer" -> Icons.Default.PhotoCamera
    "comms_online" -> Icons.Default.Forum
    else -> Icons.Default.EmojiEvents
}

/** Badge grid: earned in colour, locked greyed out with the requirement still readable. */
@Composable
fun AchievementsPanel(owned: List<String>, modifier: Modifier = Modifier) {
    val earned = Achievements.ALL.count { it.id in owned }
    DashboardPanel(stringResource(R.string.achievements_title), modifier) {
        Text(
            stringResource(R.string.achievements_progress, earned, Achievements.ALL.size),
            color = TextMid,
            style = DeadFestType.body
        )
        Spacer(Modifier.height(Spacing.xs))
        ProportionBar(earned / Achievements.ALL.size.toFloat(), accent = Amber)
        Spacer(Modifier.height(Spacing.sm))
        Achievements.ALL.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { a -> AchievementBadge(a, a.id in owned, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun AchievementBadge(a: Achievement, unlocked: Boolean, modifier: Modifier = Modifier) {
    val accent = if (unlocked) Amber else TextLow
    val state = if (unlocked) a.description else stringResource(R.string.achievement_locked) + ": " + a.description
    val cd = stringResource(R.string.cd_achievement, a.title, state)
    Row(
        modifier
            .background(if (unlocked) Amber.copy(alpha = 0.08f) else Ink3, androidx.compose.foundation.shape.RoundedCornerShape(Radius.md))
            .padding(Spacing.xs)
            .clearAndSetSemantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(accent.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (unlocked) achievementIcon(a.id) else Icons.Default.Lock, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(a.title, color = if (unlocked) TextHi else TextMid, style = DeadFestType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.description, color = TextLow, style = DeadFestType.microLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pop-up when an achievement unlocks. The bounce is skipped in Performance Mode. */
@Composable
fun AchievementUnlockedDialog(a: Achievement, onDismiss: () -> Unit) {
    val reduced = LocalPerformanceMode.current
    val scale = remember(a.id) { Animatable(if (reduced) 1f else 0.5f) }
    LaunchedEffect(a.id) {
        if (!reduced) scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 320f))
    }
    Dialog(onDismissRequest = onDismiss) {
        SurfaceCard(accent = Amber, padding = Spacing.lg) {
            Text(
                stringResource(R.string.achievement_unlocked),
                color = Amber,
                style = DeadFestType.microLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .size(84.dp)
                    .background(Amber.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(achievementIcon(a.id), contentDescription = null, tint = Amber, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(a.title, color = TextHi, style = DeadFestType.screenTitle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(a.description, color = TextMid, style = DeadFestType.body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.md))
            PrimaryAction(text = stringResource(R.string.action_close), onClick = onDismiss)
        }
    }
}
