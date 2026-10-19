package com.deadfest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.Archive
import com.deadfest.data.model.PlayerSnapshot
import com.deadfest.data.model.User
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Team Progress: everything about the season that is running right now, plus a browsable
 * library of every Dead-Fest that has been archived.
 *
 * The live numbers are aggregated from the same user documents the rest of the app reads, so
 * this screen needs no extra collections and no extra Firestore writes.
 */
@Composable
fun TeamProgressScreen(navController: NavController, viewModel: MainViewModel) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val archives by viewModel.archives.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedArchive = remember(archives, selectedId) { archives.firstOrNull { it.id == selectedId } }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    BackHandler(enabled = selectedArchive != null) { selectedId = null }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        if (selectedArchive != null) {
            SeasonDetail(selectedArchive, dateFormat) { selectedId = null }
            return@Column
        }

        ScreenHeader(
            title = "TEAM PROGRESS",
            subtitle = "LIVE SEASON & DEAD-FEST HISTORY",
            onBack = { navController.popBackStack() }
        )

        val stats = remember(users, globalState.games) { LiveStats.of(users, globalState.games) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            item {
                CurrentSeasonCard(
                    seasonName = globalState.currentSeasonName,
                    stats = stats
                )
            }
            item { SectionLabel("ELIMINATIONS BY SECTOR") }
            item {
                if (stats.deathsPerSector.isEmpty()) {
                    EmptyState(Icons.Default.Warning, "No deaths recorded this season yet.")
                } else {
                    SectorBreakdown(stats.deathsPerSector)
                }
            }

            item { SectionLabel("CURRENT STANDINGS") }
            item {
                SurvivorLeaders(stats.deadliest, stats.richest)
            }

            item { SectionLabel("PAST DEAD-FESTS", Modifier.padding(top = 6.dp)) }
            if (archives.isEmpty()) {
                item { EmptyState(Icons.Default.History, "No Dead-Fest has been archived yet. The first one is still being written.") }
            }
            items(archives, key = { it.id }) { archive ->
                PastSeasonCard(archive, dateFormat) { selectedId = archive.id }
            }
        }
    }
}

// ---------------------------------------------------------------- live season

private data class LiveStats(
    val survivors: Int,
    val totalDeaths: Long,
    val totalCoins: Long,
    val averageLevel: Int,
    val topLevel: Int,
    val prestigeCount: Int,
    val bankrupt: Int,
    val pendingSpins: Int,
    val shields: Int,
    val insulations: Int,
    val deathsPerSector: List<Pair<String, Long>>,
    val deadliest: List<User>,
    val richest: List<User>
) {
    companion object {
        fun of(users: List<User>, sectorOrder: List<String>): LiveStats {
            val players = users.filter { it.playerData.games.isNotEmpty() || it.playerData.coins > 0 }
            val rosterIndex = sectorOrder.withIndex().associate { (i, name) -> name to i }
            val deathsPerSector = players
                .flatMap { it.playerData.games.entries }
                .groupBy({ it.key }, { it.value.deaths })
                .mapValues { (_, values) -> values.sum() }
                .toList()
                .sortedWith(
                    compareByDescending<Pair<String, Long>> { it.second }
                        .thenBy { rosterIndex[it.first] ?: Int.MAX_VALUE }
                )
            return LiveStats(
                survivors = users.size,
                totalDeaths = players.sumOf { u -> u.playerData.games.values.sumOf { it.deaths } },
                totalCoins = players.sumOf { it.playerData.coins },
                averageLevel = if (players.isEmpty()) 0 else players.sumOf { it.playerData.level } / players.size,
                topLevel = players.maxOfOrNull { it.playerData.level } ?: 0,
                prestigeCount = players.sumOf { it.playerData.prestigeCount },
                bankrupt = players.count { it.playerData.bankruptcyLevel > 0 },
                pendingSpins = players.sumOf { it.playerData.pendingSpins },
                shields = players.count { it.playerData.shield },
                insulations = players.count { it.playerData.insulationTier > 0 },
                deathsPerSector = deathsPerSector,
                deadliest = players.sortedByDescending { u -> u.playerData.games.values.sumOf { it.deaths } }.take(3),
                richest = players.sortedByDescending { it.playerData.coins }.take(3)
            )
        }
    }
}

@Composable
private fun CurrentSeasonCard(seasonName: String, stats: LiveStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.Groups, size = 46)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("CURRENT DEAD-FEST", color = RedAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(
                        seasonName,
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill("LIVE", OnlineGreen)
            }
            Spacer(Modifier.height(16.dp))
            StatGrid(
                listOf(
                    StatSpec("SURVIVORS", formatNumber(stats.survivors)),
                    StatSpec("TOTAL DEATHS", formatNumber(stats.totalDeaths), RedAccent),
                    StatSpec("COINS IN PLAY", formatNumber(stats.totalCoins), Gold),
                    StatSpec("AVG LEVEL", "${stats.averageLevel}"),
                    StatSpec("TOP LEVEL", "${stats.topLevel}"),
                    StatSpec("PRESTIGES", "${stats.prestigeCount}", PrestigePurple),
                    StatSpec("BANKRUPT", "${stats.bankrupt}", if (stats.bankrupt > 0) RedAccent else TextPrimary),
                    StatSpec("SPINS DUE", "${stats.pendingSpins}", if (stats.pendingSpins > 0) RedAccent else TextPrimary),
                    StatSpec("INSULATED", "${stats.insulations}", ShieldCyan)
                )
            )
        }
    }
}

@Composable
private fun SectorBreakdown(sectors: List<Pair<String, Long>>) {
    val peak = sectors.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sectors.take(8).forEach { (name, deaths) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(formatNumber(deaths), color = RedAccent, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    ProportionBar(deaths.toFloat() / peak.toFloat())
                }
            }
        }
    }
}

@Composable
private fun SurvivorLeaders(deadliest: List<User>, richest: List<User>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        LeaderColumn(
            title = "DEADLIEST",
            accent = RedAccent,
            icon = Icons.Default.LocalFireDepartment,
            entries = deadliest.map { it to it.playerData.games.values.sumOf { s -> s.deaths } },
            modifier = Modifier.weight(1f)
        )
        LeaderColumn(
            title = "RICHEST",
            accent = Gold,
            icon = Icons.Default.Paid,
            entries = richest.map { it to it.playerData.coins },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LeaderColumn(
    title: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    entries: List<Pair<User, Long>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (entries.none { it.second > 0 }) {
                Text("Nothing recorded yet.", color = TextMuted, fontSize = 12.sp)
            } else {
                entries.forEachIndexed { index, (user, value) ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            user.playerData.avatar?.takeIf { it.isNotBlank() } ?: "💀",
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            user.displayName?.takeIf { it.isNotBlank() } ?: "Unknown",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(formatNumber(value), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- past seasons

@Composable
private fun PastSeasonCard(archive: Archive, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressClick(RoundedCornerShape(18.dp), onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.History, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(archive.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateFormat.format(Date(archive.timestamp)), color = TextSecondary, fontSize = 12.sp)
                Text(
                    "${formatNumber(archive.totalDeaths)} deaths · ${formatNumber(archive.survivorCount)} survivors",
                    color = RedAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                archive.champion?.let { champ ->
                    Text(
                        "👑 ${champ.displayName ?: "Unknown"}",
                        color = Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
private fun SeasonDetail(archive: Archive, dateFormat: SimpleDateFormat, onBack: () -> Unit) {
    val standings = remember(archive) {
        archive.playerSnapshots.sortedWith(
            compareByDescending<PlayerSnapshot> { it.totalDeaths }
                .thenByDescending { it.coins }
        )
    }
    val sectors = remember(archive) { archive.deathsPerSector() }
    val peak = sectors.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = archive.name.uppercase(),
            subtitle = "DEAD-FEST ARCHIVE",
            onBack = onBack
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        StatGrid(
                            listOf(
                                StatSpec("SURVIVORS", formatNumber(archive.survivorCount)),
                                StatSpec("TOTAL DEATHS", formatNumber(archive.totalDeaths), RedAccent),
                                StatSpec("COINS AT CLOSE", formatNumber(archive.totalCoins), Gold),
                                StatSpec("ELIMINATIONS", formatNumber(archive.grandTotalAtEnd)),
                                StatSpec("ARCHIVED", dateFormat.format(Date(archive.timestamp)).substringBefore(" "))
                            )
                        )
                        if (archive.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(archive.notes, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        if (archive.schemaVersion < 2) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Archived before v2.0.7: levels, XP and titles weren't captured for this season.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            if (sectors.isNotEmpty()) {
                item { SectionLabel("ELIMINATIONS BY SECTOR") }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Outline)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sectors.forEach { (name, deaths) ->
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(formatNumber(deaths), color = RedAccent, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    ProportionBar(deaths.toFloat() / peak.toFloat())
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("FINAL STANDINGS") }
            itemsIndexed(standings, key = { i, s -> s.uid.ifEmpty { "idx$i" } }) { index, snapshot ->
                StandingsRow(index, snapshot)
            }
        }
    }
}

@Composable
private fun StandingsRow(index: Int, snapshot: PlayerSnapshot) {
    val medal = when (index) {
        0 -> Gold
        1 -> Silver
        2 -> Bronze
        else -> null
    }
    Surface(
        color = medal?.copy(alpha = 0.06f) ?: CardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, medal?.copy(alpha = 0.7f) ?: Outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${index + 1}", color = medal ?: TextMuted, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
                Text(snapshot.avatar?.takeIf { it.isNotBlank() } ?: "💀", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot.displayName ?: "Unknown Survivor",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (snapshot.hasDetailedStats) {
                            LevelBadge(snapshot.level, snapshot.prestigeCount)
                            Spacer(Modifier.width(6.dp))
                        }
                        snapshot.title?.takeIf { it.isNotBlank() }?.let {
                            Text(it.uppercase(), color = RedAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                PriceTag(snapshot.coins)
            }
            Spacer(Modifier.height(6.dp))
            Text("${formatNumber(snapshot.totalDeaths)} deaths this season", color = TextSecondary, fontSize = 12.sp)
            if (snapshot.gameDeaths.isNotEmpty()) {
                Text(
                    snapshot.gameDeaths.entries
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .joinToString(" · ") { "${it.key}: ${it.value}" }
                        .ifBlank { "No deaths recorded" },
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

private data class StatSpec(val label: String, val value: String, val accent: Color = TextPrimary)

@Composable
private fun StatGrid(stats: List<StatSpec>, columns: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { spec ->
                    StatTile(
                        label = spec.label,
                        value = spec.value,
                        accent = spec.accent,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}