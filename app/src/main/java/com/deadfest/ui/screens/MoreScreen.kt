package com.deadfest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.ui.components.IconTile
import com.deadfest.ui.components.NextEventHero
import com.deadfest.ui.components.ScreenHeader
import com.deadfest.ui.components.SectionLabel
import com.deadfest.ui.components.StatusPill
import com.deadfest.ui.components.pressClick
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

@Composable
fun MoreScreen(navController: NavController, viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val isAdmin = viewModel.user.collectAsStateWithLifecycle().value?.admin == true

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "UTILITY HUB", subtitle = "ADVANCED TERMINAL SYSTEMS")

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Next Dead-Fest: the headline panel of this tab ----
            if (globalState.hasNextEvent()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NextEventHero(
                        seasonName = globalState.nextEventDisplayName(),
                        games = globalState.nextEventGames,
                        notes = globalState.nextEventNotes,
                        startsAt = globalState.nextEventStartOrNull()
                    )
                }
            } else if (isAdmin) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SetupEventCard { navController.navigate("admin") { launchSingleTop = true } }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("SEASON & HISTORY", Modifier.padding(top = 4.dp))
            }
            item {
                HubButton(
                    label = "TEAM PROGRESS",
                    description = "Live season stats & every past Dead-Fest",
                    icon = Icons.Default.Groups,
                    badge = "NEW"
                ) { navController.navigate("team_progress") }
            }
            item {
                HubButton(
                    label = "ARCHIVES",
                    description = "Season history & hall of fame",
                    icon = Icons.Default.History
                ) { navController.navigate("archives") }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("PROFILE & REWARDS", Modifier.padding(top = 4.dp))
            }
            item {
                HubButton(
                    label = "PROGRESSION",
                    description = "Levels, rewards & prestige",
                    icon = Icons.Default.MilitaryTech
                ) { navController.navigate("progression") }
            }
            item {
                HubButton(
                    label = "SCRATCH CARDS",
                    description = "Instant-win lottery",
                    icon = Icons.Default.ConfirmationNumber
                ) { navController.navigate("scratch") }
            }
            item {
                HubButton(
                    label = "IMAGES",
                    description = "Photos shared by every survivor",
                    icon = Icons.Default.PhotoLibrary
                ) { navController.navigate("images") }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("INTEL", Modifier.padding(top = 4.dp))
            }
            item {
                HubButton(
                    label = "SURVIVAL INTEL",
                    description = "System mechanics & lore",
                    icon = Icons.Default.Info
                ) { navController.navigate("survival_intel") }
            }
        }
    }
}

/** Admin nudge shown until a Next Dead-Fest is configured. */
@Composable
private fun SetupEventCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(20.dp), onClick = onClick),
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Add, tint = Gold, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("SET UP NEXT DEAD-FEST", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(
                    "Admin → Event: name the season, set the start time and queue the games.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
fun HubButton(
    label: String,
    description: String,
    icon: ImageVector,
    accent: Color = RedAccent,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .pressClick(RoundedCornerShape(22.dp), onClick = onClick),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
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
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}