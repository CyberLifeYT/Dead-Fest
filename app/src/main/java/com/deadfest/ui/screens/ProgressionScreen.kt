package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
            title = tr(R.string.progression_enter_prestige_1, prestige + 1),
            message = tr(R.string.progression_your_level_and_xp_reset_to),
            confirmText = tr(R.string.progression_prestige),
            onConfirm = viewModel::prestige,
            onDismiss = { confirmPrestige = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = tr(R.string.more_progression),
            subtitle = tr(R.string.progression_levels_rewards_prestige),
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

            item { SectionLabel(tr(R.string.progression_how_to_earn_xp), Modifier.enterOnMount(1)) }
            item {
                SurfaceCard(modifier = Modifier.enterOnMount(2)) {
                    XpRow(Icons.Default.Warning, tr(R.string.progression_recording_a_death), xpCfg.death)
                    XpRow(Icons.Default.Shield, tr(R.string.progression_surviving_a_full_day_claim_rations), xpCfg.survivalDay)
                    XpRow(Icons.Default.ShoppingCart, tr(R.string.progression_black_market_purchase), xpCfg.purchase)
                    XpRow(Icons.Default.Casino, tr(R.string.progression_wheel_spin), xpCfg.spin)
                    XpRow(Icons.Default.ConfirmationNumber, tr(R.string.progression_scratch_card), xpCfg.scratch)
                    XpRow(Icons.Default.Cyclone, tr(R.string.progression_clearing_a_penalty_spin_bonus), xpCfg.penaltySpin)
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

            // ---- achievements (v2.1.0)
            item {
                val owned = p?.achievements ?: emptyList()
                AchievementsPanel(owned = owned, modifier = Modifier.enterOnMount(4))
            }

            item { SectionLabel(tr(R.string.progression_reward_track), Modifier.enterOnMount(4)) }
            if (rewards.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.MilitaryTech,
                        title = tr(R.string.progression_reward_track_offline),
                        message = tr(R.string.progression_cosmetics_unlocked_by_levelling_will_be),
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
                        LevelReward(xpCfg.maxLevel, "frame_prestige", "frame", tr(R.string.progression_prestige_frame), "prestige"),
                        unlocked = true,
                        equipped = p?.avatarFrame == "prestige"
                    ) { viewModel.equipFrame("prestige") }
                }
            }
            item {
                GhostAction(
                    text = tr(R.string.progression_remove_avatar_frame),
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
        eyebrow = tr(R.string.progression_progression),
        title = tr(R.string.progression_level_1_of_2, level, cfg.maxLevel),
        icon = Icons.Default.MilitaryTech,
        modifier = modifier,
        accent = Ember,
        badge = { if (prestige > 0) PrestigeBadge(prestige) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FramedAvatar(avatar, frame, badgeColor, 56)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                // Each stat line is one item: the label and its value are announced together.
                KeyValueRow(
                    tr(R.string.progression_total_xp),
                    formatNumber(xp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    valueColor = Ember
                )
                KeyValueRow(
                    label = tr(R.string.progression_to_next_level),
                    value = if (maxed) tr(R.string.progression_max_level) else tr(R.string.progression_1_xp, formatNumber(toNext)),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    valueColor = if (maxed) Amber else TextHi
                )
                KeyValueRow(
                    tr(R.string.progression_prestiges),
                    "$prestige",
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    valueColor = Prestige
                )
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
            value = tr(R.string.resultdialogs_1_xp, formatNumber(xp)),
            valueColor = Amber,
            // Decorative icon; the row itself is one item: "label, +N XP".
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}
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
                // Prestige is its own block on this screen, so its title reads as a heading.
                Text(
                    tr(R.string.progression_prestige),
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (ready) tr(R.string.progression_you_ve_hit_the_cap_reset)
                    else tr(R.string.progression_reach_level_1_to_prestige_prestiges, maxLevel, prestige),
                    color = TextMid,
                    style = DeadFestType.body
                )
            }
            StatusPill(if (ready) tr(R.string.images_ready) else tr(R.string.home_locked), if (ready) Prestige else TextLow)
        }
        if (ready) {
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(tr(R.string.progression_enter_prestige_1_2, prestige + 1), onPrestige, accent = Prestige)
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
                    when (reward.type) { "frame" -> tr(R.string.progression_avatar_frame); "title" -> tr(R.string.progression_title); else -> tr(R.string.progression_badge_colour) },
                    color = TextLow,
                    style = DeadFestType.body
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            when {
                equipped -> StatusPill(tr(R.string.progression_equipped), Ember)
                unlocked -> GhostAction(tr(R.string.progression_equip), onEquip, modifier = Modifier.width(104.dp))
                else -> Icon(Icons.Default.Lock, contentDescription = tr(R.string.admin_locked), tint = TextLow, modifier = Modifier.size(16.dp))
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
