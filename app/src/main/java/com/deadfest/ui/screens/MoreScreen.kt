package com.deadfest.ui.screens

import com.deadfest.util.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.res.stringResource
import com.deadfest.R
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

/**
 * Utility hub.
 *
 * Everything that isn't a primary tab lives here, so the tab is laid out as a colour-coded grid:
 * each destination owns one accent hue, which makes the grid scannable at a glance instead of
 * being a wall of identical cards.
 */
@Composable
fun MoreScreen(navController: NavController, viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val isAdmin = user?.admin == true
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val unreadAlerts = alerts.count { !it.read }
    val latestChat by viewModel.latestChat.collectAsStateWithLifecycle()
    val week = viewModel.currentWeekKey()
    val claimableChallenges = user?.playerData?.let {
        com.deadfest.domain.Challenges.claimable(week, it.weekKey, it.weeklyStats, it.weeklyClaimed).size
    } ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = tr(R.string.more_utility_hub), subtitle = tr(R.string.more_advanced_terminal_systems))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.sm,
                bottom = Spacing.scrollBottom
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // ---- Next Dead-Fest: the headline panel of this tab ----
            if (globalState.hasNextEvent()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NextEventHero(
                        seasonName = globalState.nextEventDisplayName(),
                        games = globalState.nextEventGames,
                        notes = globalState.nextEventNotes,
                        startsAt = globalState.nextEventStartOrNull(),
                        modifier = Modifier.enterOnMount(0)
                    )
                }
            } else if (isAdmin) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SetupEventCard { navController.navigate("admin") { launchSingleTop = true } }
                }
            }

            // ---- Social (v2.1.0)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel(stringResource(R.string.hub_section_social)) }
            }
            item {
                HubButton(
                    label = stringResource(R.string.hub_chat),
                    description = stringResource(R.string.hub_chat_desc),
                    icon = Icons.Default.Forum,
                    accent = Plasma,
                    badge = if (viewModel.hasUnreadChat(latestChat)) "\u25CF" else null,
                    onClick = { navController.navigate("chat") }
                )
            }
            item {
                HubButton(
                    label = stringResource(R.string.hub_challenges),
                    description = stringResource(R.string.hub_challenges_desc),
                    icon = Icons.Default.EmojiEvents,
                    accent = Amber,
                    badge = if (claimableChallenges > 0) "$claimableChallenges" else stringResource(R.string.badge_new),
                    onClick = { navController.navigate("challenges") }
                )
            }
            item {
                HubButton(
                    label = stringResource(R.string.hub_alerts),
                    description = stringResource(R.string.hub_alerts_desc),
                    icon = Icons.Default.Notifications,
                    accent = Ember,
                    badge = if (unreadAlerts > 0) "$unreadAlerts" else null,
                    onClick = { navController.navigate("alerts") }
                )
            }
            item {
                HubButton(
                    label = stringResource(R.string.hub_stats),
                    description = stringResource(R.string.hub_stats_desc),
                    icon = Icons.Default.BarChart,
                    accent = Amber,
                    badge = stringResource(R.string.badge_new),
                    onClick = { navController.navigate("stats") }
                )
            }
            item {
                HubButton(
                    label = stringResource(R.string.hub_profile),
                    description = stringResource(R.string.hub_profile_desc),
                    icon = Icons.Default.Face,
                    accent = Toxic,
                    onClick = { navController.navigate("edit_profile") }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel(tr(R.string.more_season_history)) }
            }
            item {
                HubButton(
                    label = tr(R.string.more_team_progress),
                    description = tr(R.string.more_live_season_stats_every_past_dead),
                    icon = Icons.Default.Groups,
                    accent = Plasma,
                    badge = tr(R.string.more_new),
                    onClick = { navController.navigate("team_progress") }
                )
            }
            item {
                HubButton(
                    label = tr(R.string.more_archives),
                    description = tr(R.string.more_season_history_hall_of_fame),
                    icon = Icons.Default.History,
                    accent = Amber,
                    onClick = { navController.navigate("archives") }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel(tr(R.string.more_profile_rewards)) }
            }
            item {
                HubButton(
                    label = tr(R.string.more_progression),
                    description = tr(R.string.more_levels_rewards_prestige),
                    icon = Icons.Default.MilitaryTech,
                    accent = Prestige,
                    onClick = { navController.navigate("progression") }
                )
            }
            item {
                HubButton(
                    label = tr(R.string.more_scratch_cards),
                    description = tr(R.string.more_instant_win_lottery),
                    icon = Icons.Default.ConfirmationNumber,
                    accent = Toxic,
                    onClick = { navController.navigate("scratch") }
                )
            }
            item {
                HubButton(
                    label = "IMAGES",
                    description = tr(R.string.more_photos_shared_by_every_survivor),
                    icon = Icons.Default.PhotoLibrary,
                    accent = EmberGlow,
                    onClick = { navController.navigate("images") }
                )
            }
            item {
                HubButton(
                    label = stringResource(R.string.hub_deaths_images),
                    description = stringResource(R.string.hub_deaths_images_desc),
                    icon = Icons.Default.PhotoCamera,
                    accent = Ember,
                    badge = stringResource(R.string.badge_new),
                    onClick = { navController.navigate("death_images") }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel(tr(R.string.more_intel)) }
            }
            item {
                HubButton(
                    label = tr(R.string.more_survival_intel),
                    description = tr(R.string.more_system_mechanics_lore),
                    icon = Icons.Default.Info,
                    accent = Ember,
                    onClick = { navController.navigate("survival_intel") }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel(tr(R.string.more_community)) }
            }
            item {
                HubButton(
                    label = tr(R.string.more_request_game),
                    description = tr(R.string.more_ask_for_a_game_to_become),
                    icon = Icons.Default.SportsEsports,
                    accent = Prestige,
                    badge = tr(R.string.more_new),
                    onClick = { navController.navigate("request_game") }
                )
            }
        }
    }
}

/** Admin nudge shown until a Next Dead-Fest is configured. */
@Composable
private fun SetupEventCard(onClick: () -> Unit) {
    SurfaceCard(accent = Amber, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Add, tint = Amber, size = 44)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.more_set_up_next_dead_fest), color = TextHi, style = DeadFestType.cardTitle)
                Spacer(Modifier.height(2.dp))
                Text(
                    tr(R.string.more_admin_event_name_the_season_set),
                    color = TextMid,
                    style = DeadFestType.body
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextLow, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Grid tile for a hub destination.
 *
 * Sized for two columns on a small phone and coloured by meaning, with a gradient wash so the
 * tiles read as a set rather than as flat buttons.
 */
@Composable
fun HubButton(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accent: Color = Ember,
    badge: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                )
            )
            .background(Ink2)
            .border(BorderStroke(Stroke.hair, accent.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.xl))
            .pressClick(RoundedCornerShape(Radius.xl), onClick = onClick)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint = accent, size = 46)
            Spacer(Modifier.weight(1f))
            if (badge != null) StatusPill(badge, accent)
        }
        Column {
            Text(
                label,
                color = TextHi,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = TextMid,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
