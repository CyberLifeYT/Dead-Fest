package com.deadfest.ui.screens

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
        ScreenHeader(title = "UTILITY HUB", subtitle = "Advanced Terminal Systems")

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
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel("Season & History") }
            }
            item {
                HubButton(
                    label = "TEAM PROGRESS",
                    description = "Live season stats & every past Dead-Fest",
                    icon = Icons.Default.Groups,
                    accent = Plasma,
                    badge = "NEW",
                    onClick = { navController.navigate("team_progress") }
                )
            }
            item {
                HubButton(
                    label = "ARCHIVES",
                    description = "Season history & hall of fame",
                    icon = Icons.Default.History,
                    accent = Amber,
                    onClick = { navController.navigate("archives") }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel("Profile & Rewards") }
            }
            item {
                HubButton(
                    label = "PROGRESSION",
                    description = "Levels, rewards & prestige",
                    icon = Icons.Default.MilitaryTech,
                    accent = Prestige,
                    onClick = { navController.navigate("progression") }
                )
            }
            item {
                HubButton(
                    label = "SCRATCH CARDS",
                    description = "Instant-win lottery",
                    icon = Icons.Default.ConfirmationNumber,
                    accent = Toxic,
                    onClick = { navController.navigate("scratch") }
                )
            }
            item {
                HubButton(
                    label = "IMAGES",
                    description = "Photos shared by every survivor",
                    icon = Icons.Default.PhotoLibrary,
                    accent = EmberGlow,
                    onClick = { navController.navigate("images") }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel("Intel") }
            }
            item {
                HubButton(
                    label = "SURVIVAL INTEL",
                    description = "System mechanics & lore",
                    icon = Icons.Default.Info,
                    accent = Ember,
                    onClick = { navController.navigate("survival_intel") }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = Spacing.xxs)) { SectionLabel("Community") }
            }
            item {
                HubButton(
                    label = "REQUEST GAME",
                    description = "Ask for a game to become a sector",
                    icon = Icons.Default.SportsEsports,
                    accent = Prestige,
                    badge = "NEW",
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
                Text("SET UP NEXT DEAD-FEST", color = TextHi, style = DeadFestType.cardTitle)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Admin → Event: name the season, set the start time and queue the games.",
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
