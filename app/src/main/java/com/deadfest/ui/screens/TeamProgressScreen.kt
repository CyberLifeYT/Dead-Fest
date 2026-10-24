package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedArchive != null) {
            SeasonDetail(
                archive = selectedArchive,
                archives = archives,
                dateFormat = dateFormat,
                onSelect = { selectedId = it },
                onBack = { selectedId = null }
            )
            return@Column
        }

        ScreenHeader(
            title = tr(R.string.more_team_progress),
            subtitle = tr(R.string.teamprogress_live_season_dead_fest_history),
            onBack = { navController.popBackStack() }
        )

        val stats = remember(users, globalState.games) { LiveStats.of(users, globalState.games) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.sm,
                bottom = Spacing.scrollBottom
            )
        ) {
            item {
                CurrentSeasonHero(globalState.currentSeasonName, stats, Modifier.enterOnMount(0))
            }
            item { SectionLabel(tr(R.string.archives_eliminations_by_sector), Modifier.enterOnMount(1)) }
            item {
                if (stats.deathsPerSector.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.Warning,
                        title = tr(R.string.teamprogress_no_eliminations_recorded),
                        message = tr(R.string.teamprogress_deaths_recorded_this_season_appear_here),
                        modifier = Modifier.enterOnMount(2)
                    )
                } else {
                    SectorBreakdown(stats.deathsPerSector, Modifier.enterOnMount(2))
                }
            }

            item { SectionLabel(tr(R.string.teamprogress_current_standings), Modifier.enterOnMount(3)) }
            item {
                SurvivorLeaders(stats.deadliest, stats.richest, Modifier.enterOnMount(4))
            }

            item { SectionLabel(tr(R.string.teamprogress_past_dead_fests), Modifier.enterOnMount(5)) }
            if (archives.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.History,
                        title = tr(R.string.teamprogress_no_dead_fest_archived),
                        message = tr(R.string.teamprogress_every_past_season_will_be_listed)
                    )
                }
            } else {
                items(archives, key = { it.id }) { archive ->
                    PastSeasonCard(archive, dateFormat) { selectedId = archive.id }
                }
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
                deadliest = players.sortedByDescending { u -> u.playerData.games.values.sumOf { s -> s.deaths } }.take(3),
                richest = players.sortedByDescending { u -> u.playerData.coins }.take(3)
            )
        }
    }
}

@Composable
private fun CurrentSeasonHero(seasonName: String, stats: LiveStats, modifier: Modifier = Modifier) {
    HeroPanel(
        eyebrow = tr(R.string.teamprogress_current_dead_fest),
        title = seasonName,
        icon = Icons.Default.Groups,
        modifier = modifier,
        accent = Ember,
        badge = { StatusPill(tr(R.string.home_live), Toxic) }
    ) {
        StatGrid(
            listOf(
                StatSpec(tr(R.string.archives_survivors), formatNumber(stats.survivors)),
                StatSpec(tr(R.string.teamprogress_total_deaths), formatNumber(stats.totalDeaths), Ember),
                StatSpec(tr(R.string.teamprogress_coins_in_play), formatNumber(stats.totalCoins), Amber),
                StatSpec(tr(R.string.teamprogress_avg_level), "${stats.averageLevel}"),
                StatSpec(tr(R.string.teamprogress_top_level), "${stats.topLevel}"),
                StatSpec(tr(R.string.teamprogress_prestiges), "${stats.prestigeCount}", Prestige),
                StatSpec(tr(R.string.teamprogress_bankrupt), "${stats.bankrupt}", if (stats.bankrupt > 0) Ember else TextPrimary),
                StatSpec(tr(R.string.teamprogress_spins_due), "${stats.pendingSpins}", if (stats.pendingSpins > 0) Ember else TextPrimary),
                StatSpec(tr(R.string.teamprogress_insulated), "${stats.insulations}", Plasma)
            )
        )
    }
}

@Composable
private fun SectorBreakdown(sectors: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val peak = sectors.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    SurfaceCard(modifier = modifier) {
        sectors.take(8).forEachIndexed { index, (name, deaths) ->
            if (index > 0) Spacer(Modifier.height(Spacing.sm))
            Column {
                // One sector line: the sector name and its count read as one item.
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        color = TextHi,
                        style = DeadFestType.chip,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(formatNumber(deaths), color = Ember, style = DeadFestType.chip)
                }
                Spacer(Modifier.height(Spacing.xxs))
                ProportionBar(deaths.toFloat() / peak.toFloat())
            }
        }
    }
}

@Composable
private fun SurvivorLeaders(deadliest: List<User>, richest: List<User>, modifier: Modifier = Modifier) {
    if (deadliest.isEmpty() && richest.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Default.Groups,
            title = tr(R.string.teamprogress_no_standings_yet),
            message = tr(R.string.teamprogress_standings_fill_in_as_survivors_record),
            modifier = modifier
        )
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LeaderColumn(
            title = tr(R.string.teamprogress_deadliest),
            accent = Ember,
            icon = Icons.Default.LocalFireDepartment,
            entries = deadliest.map { it to it.playerData.games.values.sumOf { s -> s.deaths } },
            modifier = Modifier.weight(1f)
        )
        LeaderColumn(
            title = tr(R.string.teamprogress_richest),
            accent = Amber,
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
    icon: ImageVector,
    entries: List<Pair<User, Long>>,
    modifier: Modifier = Modifier
) {
    SurfaceCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(Spacing.xxs))
            SectionLabel(title, accent = accent)
        }
        Spacer(Modifier.height(Spacing.xs))
        if (entries.none { it.second > 0 }) {
            Text(tr(R.string.teamprogress_nothing_recorded_yet), color = TextLow, style = DeadFestType.body)
        } else {
            entries.forEachIndexed { index, (user, value) ->
                if (index > 0) Spacer(Modifier.height(Spacing.xs))
                // One leaderboard entry: rank, name and value read as a single item.
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RankBadge(index + 1)
                    Spacer(Modifier.width(Spacing.xs))
                    // Two lines: half-width columns ellipsize a name + value on one line.
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.admin_unknown),
                            color = TextHi,
                            style = DeadFestType.chip,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(formatNumber(value), color = accent, style = DeadFestType.chip, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- past seasons

@Composable
private fun PastSeasonCard(archive: Archive, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.History, tint = Amber, size = 44)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    archive.name,
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(dateFormat.format(Date(archive.timestamp)), color = TextMid, style = DeadFestType.body)
                Text(
                    tr(R.string.archives_1_deaths_2_survivors, formatNumber(archive.totalDeaths), formatNumber(archive.survivorCount)),
                    color = Ember,
                    style = DeadFestType.chip
                )
                archive.champion?.let { champ ->
                    Text(
                        "👑 ${champ.displayName ?: "Unknown"}",
                        color = Amber,
                        style = DeadFestType.chip,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (archive.schemaVersion < 2) {
                    Text(tr(R.string.archives_pre_v2_0_7_levels_not), color = TextLow, style = DeadFestType.microLabel)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextLow)
        }
    }
}

@Composable
private fun SeasonDetail(
    archive: Archive,
    archives: List<Archive>,
    dateFormat: SimpleDateFormat,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
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
            subtitle = tr(R.string.teamprogress_dead_fest_archive),
            onBack = onBack
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.sm,
                bottom = Spacing.scrollBottom
            )
        ) {
            if (archives.size > 1) {
                item {
                    Column {
                        SectionLabel(tr(R.string.archives_season_index))
                        Spacer(Modifier.height(Spacing.xs))
                        // Horizontal selector: jump between records without going back to the list.
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            items(archives, key = { it.id }) { entry ->
                                PillChip(
                                    text = entry.name,
                                    selected = entry.id == archive.id,
                                    onClick = { onSelect(entry.id) },
                                    accent = Amber,
                                    // Small pill, but it still needs the 48dp minimum touch target.
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }
                    }
                }
            }

            item {
                SurfaceCard(accent = Ember, modifier = Modifier.enterOnMount(0)) {
                    SectionLabel(tr(R.string.archives_season_summary), accent = Amber)
                    Spacer(Modifier.height(Spacing.sm))
                    StatGrid(
                        listOf(
                            StatSpec(tr(R.string.archives_survivors), formatNumber(archive.survivorCount)),
                            StatSpec(tr(R.string.teamprogress_total_deaths), formatNumber(archive.totalDeaths), Ember),
                            StatSpec(tr(R.string.archives_coins_at_close), formatNumber(archive.totalCoins), Amber),
                            StatSpec(tr(R.string.archives_eliminations), formatNumber(archive.grandTotalAtEnd)),
                            StatSpec(tr(R.string.archives_archived), dateFormat.format(Date(archive.timestamp)).substringBefore(" "))
                        )
                    )
                    archive.champion?.let { champ ->
                        Spacer(Modifier.height(Spacing.xs))
                        // Label and value stay in one announcement, and this card is not clickable.
                        KeyValueRow(
                            tr(R.string.archives_champion),
                            champ.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.admin_unknown),
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                            valueColor = Amber
                        )
                    }
                    if (archive.notes.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(archive.notes, color = TextMid, style = DeadFestType.body)
                    }
                    if (archive.schemaVersion < 2) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            tr(R.string.archives_archived_before_v2_0_7_levels),
                            color = TextLow,
                            style = DeadFestType.body
                        )
                    }
                }
            }

            if (sectors.isNotEmpty()) {
                item { SectionLabel(tr(R.string.archives_eliminations_by_sector), Modifier.enterOnMount(1)) }
                item {
                    SurfaceCard(modifier = Modifier.enterOnMount(2)) {
                        sectors.forEachIndexed { index, (name, deaths) ->
                            if (index > 0) Spacer(Modifier.height(Spacing.sm))
                            Column {
                                // One sector line: the sector name and its count read as one item.
                                Row(
                                    modifier = Modifier.semantics(mergeDescendants = true) {},
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        name,
                                        color = TextHi,
                                        style = DeadFestType.chip,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(formatNumber(deaths), color = Ember, style = DeadFestType.chip)
                                }
                                Spacer(Modifier.height(Spacing.xxs))
                                ProportionBar(deaths.toFloat() / peak.toFloat())
                            }
                        }
                    }
                }
            }

            item { SectionLabel(tr(R.string.archives_final_standings), Modifier.enterOnMount(3)) }
            if (standings.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.History,
                        title = tr(R.string.archives_no_roster_recorded),
                        message = tr(R.string.archives_this_season_was_sealed_before_any),
                        modifier = Modifier.enterOnMount(4)
                    )
                }
            } else {
                itemsIndexed(standings, key = { i, s -> s.uid.ifEmpty { "idx$i" } }) { index, snapshot ->
                    ArchiveStandingsRow(index, snapshot)
                }
            }
        }
    }
}

@Composable
private fun ArchiveStandingsRow(index: Int, snapshot: PlayerSnapshot) {
    val medal = when (index) {
        0 -> Amber
        1 -> Silver
        2 -> Bronze
        else -> null
    }
    // One leaderboard row: rank, survivor details and coins read as a single item.
    SurfaceCard(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        accent = medal,
        padding = Spacing.sm
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RankBadge(index + 1)
            Spacer(Modifier.width(Spacing.xs))
            FramedAvatar(snapshot.avatar, snapshot.avatarFrame, snapshot.color, 34)
            Spacer(Modifier.width(Spacing.xs))
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot.displayName ?: tr(R.string.archives_unknown_survivor),
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (snapshot.hasDetailedStats) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(snapshot.level, snapshot.prestigeCount)
                        snapshot.title?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.width(Spacing.xxs))
                            Text(
                                it.uppercase(),
                                color = Ember,
                                style = DeadFestType.microLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        tr(R.string.archives_level_title_not_recorded),
                        color = TextLow,
                        style = DeadFestType.microLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PriceTag(snapshot.coins)
        }
        Spacer(Modifier.height(Spacing.xs))
        KeyValueRow(tr(R.string.archives_season_deaths), formatNumber(snapshot.totalDeaths), valueColor = Ember)
        if (snapshot.gameDeaths.isNotEmpty()) {
            Text(
                snapshot.gameDeaths.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .joinToString(" · ") { "${it.key}: ${it.value}" }
                    .ifBlank { tr(R.string.teamprogress_no_deaths_recorded) },
                color = TextLow,
                style = DeadFestType.body
            )
        }
    }
}

// ---------------------------------------------------------------- shared bits

private data class StatSpec(val label: String, val value: String, val accent: Color = TextPrimary)

@Composable
private fun StatGrid(stats: List<StatSpec>, columns: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        stats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { spec ->
                    StatTile(
                        label = spec.label,
                        value = spec.value,
                        accent = spec.accent,
                        // A tile is one item: its label is announced with the value it names.
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
