package com.deadfest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.LevelReward
import com.deadfest.data.model.LevelRewardCatalog
import com.deadfest.domain.Leveling
import com.deadfest.domain.XpConfig
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

/**
 * Progression: the survivor's own level track.
 *
 * Ordering follows the screen's job — level and XP readout first because that is what changes when
 * XP is earned, then the reference material (how XP is earned, prestige, the reward track).
 */
@Composable
fun ProgressionScreen(navController: NavController, viewModel: MainViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val rewards by viewModel.levelRewards.collectAsStateWithLifecycle()
    val p = user?.playerData
    val xpCfg = globalState.economy.xp
    val level = p?.level ?: 1
    val prestige = p?.prestigeCount ?: 0
    var confirmPrestige by remember { mutableStateOf(false) }
    val ready = Leveling.canPrestige(level, xpCfg)

    if (confirmPrestige) {
        ConfirmDialog(
            title = "ENTER PRESTIGE ${prestige + 1}?",
            message = "Your level and XP reset to 1 / 0. You keep every reward you've unlocked, gain a permanent prestige badge and the Prestige frame.",
            confirmText = "PRESTIGE",
            onConfirm = viewModel::prestige,
            onDismiss = { confirmPrestige = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "PROGRESSION",
            subtitle = "LEVELS, REWARDS & PRESTIGE",
            onBack = { navController.popBackStack() }
        )
        LazyColumn(
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.sm,
                bottom = Spacing.scrollBottom
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                LevelHeroCard(
                    avatar = p?.avatar,
                    frame = p?.avatarFrame,
                    badgeColor = p?.color,
                    xp = p?.xp ?: 0,
                    level = level,
                    prestige = prestige,
                    cfg = xpCfg,
                    modifier = Modifier.enterOnMount(0)
                )
            }

            item { SectionLabel("How to earn XP", Modifier.enterOnMount(1)) }
            item {
                SurfaceCard(modifier = Modifier.enterOnMount(2)) {
                    XpRow(Icons.Default.Warning, "Recording a death", xpCfg.death)
                    XpRow(Icons.Default.Shield, "Surviving a full day (claim rations)", xpCfg.survivalDay)
                    XpRow(Icons.Default.ShoppingCart, "Black Market purchase", xpCfg.purchase)
                    XpRow(Icons.Default.Casino, "Wheel spin", xpCfg.spin)
                    XpRow(Icons.Default.ConfirmationNumber, "Scratch card", xpCfg.scratch)
                    XpRow(Icons.Default.Cyclone, "Clearing a penalty spin (bonus)", xpCfg.penaltySpin)
                }
            }

            item {
                PrestigeCard(
                    ready = ready,
                    prestige = prestige,
                    maxLevel = xpCfg.maxLevel,
                    onPrestige = { confirmPrestige = true },
                    modifier = Modifier.enterOnMount(3)
                )
            }

            item { SectionLabel("Reward track", Modifier.enterOnMount(4)) }
            if (rewards.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.MilitaryTech,
                        title = "Reward track offline",
                        message = "Cosmetics unlocked by levelling will be listed here.",
                        modifier = Modifier.enterOnMount(5)
                    )
                }
            } else {
                items(rewards, key = { "${it.level}-${it.rewardId}" }) { reward ->
                    val unlocked = LevelRewardCatalog.isUnlocked(reward, level, prestige)
                    val equipped = when (reward.type) {
                        "color" -> p?.color.equals(reward.value, ignoreCase = true)
                        "frame" -> p?.avatarFrame == reward.value
                        "title" -> p?.title.equals(reward.value, ignoreCase = true)
                        else -> false
                    }
                    RewardRow(reward, unlocked, equipped) {
                        when (reward.type) {
                            "color" -> viewModel.equipColor(reward.value)
                            "frame" -> viewModel.equipFrame(reward.value)
                            "title" -> viewModel.equipTitle(reward.value)
                        }
                    }
                }
            }
            if (prestige > 0) {
                item {
                    RewardRow(
                        LevelReward(xpCfg.maxLevel, "frame_prestige", "frame", "Prestige frame", "prestige"),
                        unlocked = true,
                        equipped = p?.avatarFrame == "prestige"
                    ) { viewModel.equipFrame("prestige") }
                }
            }
            item {
                GhostAction(
                    text = "Remove avatar frame",
                    onClick = { viewModel.equipFrame("none") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------------------------------------------------- level hero

/** The one block that changes when XP is earned: avatar, level, XP to the next level. */
@Composable
private fun LevelHeroCard(
    avatar: String?,
    frame: String?,
    badgeColor: String?,
    xp: Long,
    level: Int,
    prestige: Int,
    cfg: XpConfig,
    modifier: Modifier = Modifier
) {
    val maxed = level >= cfg.maxLevel
    val toNext = (Leveling.xpForLevel(level, cfg) - xp).coerceAtLeast(0)

    HeroPanel(
        eyebrow = "Progression",
        title = "Level $level of ${cfg.maxLevel}",
        icon = Icons.Default.MilitaryTech,
        modifier = modifier,
        accent = Ember,
        badge = { if (prestige > 0) PrestigeBadge(prestige) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FramedAvatar(avatar, frame, badgeColor, 56)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                KeyValueRow("Total XP", formatNumber(xp), valueColor = Ember)
                KeyValueRow(
                    label = "To next level",
                    value = if (maxed) "MAX LEVEL" else "${formatNumber(toNext)} XP",
                    valueColor = if (maxed) Amber else TextHi
                )
                KeyValueRow("Prestiges", "$prestige", valueColor = Prestige)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        XpBar(xp, level, cfg)
    }
}

@Composable
private fun XpRow(icon: ImageVector, label: String, xp: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextLow, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(Spacing.sm))
        KeyValueRow(
            label = label,
            value = "+${formatNumber(xp)} XP",
            valueColor = Amber,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------- prestige

@Composable
private fun PrestigeCard(
    ready: Boolean,
    prestige: Int,
    maxLevel: Int,
    onPrestige: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(accent = if (ready) Prestige else null, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.AutoAwesome, tint = if (ready) Prestige else TextLow, size = 44)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text("PRESTIGE", color = TextHi, style = DeadFestType.cardTitle)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (ready) "You've hit the cap. Reset to level 1 for a permanent badge."
                    else "Reach level $maxLevel to prestige. Prestiges so far: $prestige",
                    color = TextMid,
                    style = DeadFestType.body
                )
            }
            StatusPill(if (ready) "READY" else "LOCKED", if (ready) Prestige else TextLow)
        }
        if (ready) {
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction("Enter prestige ${prestige + 1}", onPrestige, accent = Prestige)
        }
    }
}

// ---------------------------------------------------------------- reward track

@Composable
private fun RewardRow(reward: LevelReward, unlocked: Boolean, equipped: Boolean, onEquip: () -> Unit) {
    SurfaceCard(accent = if (equipped) Ember else null, padding = Spacing.sm) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Unlock level marker: the number is why the row is available or not.
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) Amber.copy(alpha = Tint.wash) else Ink3)
                    .border(
                        BorderStroke(Stroke.hair, if (unlocked) Amber.copy(alpha = Tint.edge) else InkLine),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("${reward.level}", color = if (unlocked) Amber else TextLow, style = DeadFestType.chip)
            }
            Spacer(Modifier.width(Spacing.sm))
            RewardPreview(reward)
            Spacer(Modifier.width(Spacing.xs))
            Column(Modifier.weight(1f)) {
                Text(
                    reward.label,
                    color = if (unlocked) TextHi else TextLow,
                    style = DeadFestType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (reward.type) { "frame" -> "Avatar frame"; "title" -> "Title"; else -> "Badge colour" },
                    color = TextLow,
                    style = DeadFestType.body
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            when {
                equipped -> StatusPill("EQUIPPED", Ember)
                unlocked -> GhostAction("Equip", onEquip, modifier = Modifier.width(104.dp))
                else -> Icon(Icons.Default.Lock, contentDescription = "Locked", tint = TextLow, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Preview of the cosmetic itself, so the row shows what is being unlocked. */
@Composable
private fun RewardPreview(reward: LevelReward) {
    when (reward.type) {
        "color" -> Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(parseThemeColor(reward.value))
        )
        // null colour resolves to the default badge tint (parseThemeColor's fallback).
        "frame" -> FramedAvatar("☠", reward.value, null, 28)
        else -> IconTile(Icons.Default.MilitaryTech, tint = Amber, size = 28)
    }
}
