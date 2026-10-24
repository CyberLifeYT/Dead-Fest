package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.Archive
import com.deadfest.data.model.PlayerSnapshot
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Archives Hub: the sealed record of every Dead-Fest.
 *
 * Archives arrive newest-first, so the newest one leads the screen as the season most people
 * came to look up; the rest stay a plain list. Selecting any season opens its full record, with
 * a season index for jumping straight to another one.
 */
@Composable
fun ArchivesScreen(navController: NavController, viewModel: MainViewModel) {
    val archives by viewModel.archives.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedArchive = remember(archives, selectedId) { archives.firstOrNull { it.id == selectedId } }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // System back closes the detail view first instead of leaving the screen.
    BackHandler(enabled = selectedArchive != null) { selectedId = null }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedArchive != null) {
            ArchiveDetail(
                archive = selectedArchive,
                archives = archives,
                dateFormat = dateFormat,
                onSelect = { selectedId = it },
                onBack = { selectedId = null }
            )
            return@Column
        }

        ScreenHeader(
            title = tr(R.string.archives_archives_hub),
            subtitle = tr(R.string.archives_wasteland_historical_database),
            onBack = { navController.popBackStack() }
        )

        if (archives.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Spacing.gutter)) {
                EmptyStateCard(
                    icon = Icons.Default.History,
                    title = tr(R.string.archives_no_archived_seasons),
                    message = tr(R.string.archives_once_a_dead_fest_is_sealed),
                    modifier = Modifier.enterOnMount(0)
                )
            }
        } else {
            val latest = archives.first()
            val earlier = archives.drop(1)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.sm,
                    bottom = Spacing.scrollBottom
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    LatestSeasonHero(latest, dateFormat, Modifier.enterOnMount(0)) { selectedId = latest.id }
                }
                if (earlier.isNotEmpty()) {
                    item { SectionLabel(tr(R.string.archives_earlier_seasons), Modifier.enterOnMount(1)) }
                    items(earlier, key = { it.id }) { archive ->
                        ArchiveRow(archive, dateFormat) { selectedId = archive.id }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- hub

@Composable
private fun LatestSeasonHero(
    archive: Archive,
    dateFormat: SimpleDateFormat,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val detailed = archive.schemaVersion >= 2
    HeroPanel(
        eyebrow = tr(R.string.archives_latest_archive),
        title = archive.name.uppercase(),
        icon = Icons.Default.History,
        modifier = modifier.pressClick(RoundedCornerShape(Radius.xl), onClick = onOpen),
        accent = Amber,
        badge = { StatusPill(if (detailed) tr(R.string.archives_detailed) else tr(R.string.archives_legacy), if (detailed) Toxic else TextLow) }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StatTile(tr(R.string.archives_survivors), formatNumber(archive.survivorCount), TextHi, Modifier.weight(1f))
            StatTile(tr(R.string.archives_deaths), formatNumber(archive.totalDeaths), Ember, Modifier.weight(1f))
            StatTile(tr(R.string.admineconomy_coins), formatNumber(archive.totalCoins), Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.sm))
        KeyValueRow(tr(R.string.archives_sealed), dateFormat.format(Date(archive.timestamp)))
        archive.champion?.let { champ ->
            KeyValueRow(tr(R.string.archives_champion), champ.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.admin_unknown), valueColor = Amber)
        }
        if (!detailed) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                tr(R.string.archives_archived_before_v2_0_7_levels),
                color = TextLow,
                style = DeadFestType.body
            )
        }
    }
}

@Composable
private fun ArchiveRow(archive: Archive, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
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

// ---------------------------------------------------------------- season record

@Composable
private fun ArchiveDetail(
    archive: Archive,
    archives: List<Archive>,
    dateFormat: SimpleDateFormat,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    val standings = remember(archive) { archive.playerSnapshots.sortedByDescending { it.coins } }
    val sectors = remember(archive) { archive.deathsPerSector() }
    val peak = sectors.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = archive.name.uppercase(),
            subtitle = tr(R.string.archives_saved_1, dateFormat.format(Date(archive.timestamp))),
            onBack = onBack
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.sm,
                bottom = Spacing.scrollBottom
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            if (archives.size > 1) {
                item {
                    Column {
                        SectionLabel(tr(R.string.archives_season_index))
                        Spacer(Modifier.height(Spacing.xs))
                        // Horizontal selector: seasons stay one tap away without leaving the record.
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
                    ArchiveStatGrid(
                        listOf(
                            ArchiveStat(tr(R.string.archives_survivors), formatNumber(archive.survivorCount)),
                            ArchiveStat(tr(R.string.archives_losses), formatNumber(archive.totalDeaths), Ember),
                            ArchiveStat(tr(R.string.archives_coins_at_close), formatNumber(archive.totalCoins), Amber),
                            ArchiveStat(tr(R.string.archives_eliminations), formatNumber(archive.grandTotalAtEnd)),
                            ArchiveStat(tr(R.string.archives_archived), dateFormat.format(Date(archive.timestamp)).substringBefore(" "))
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
                snapshot.gameDeaths.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                color = TextLow,
                style = DeadFestType.body
            )
        }
    }
}

// ---------------------------------------------------------------- stats grid

private data class ArchiveStat(val label: String, val value: String, val accent: Color = TextPrimary)

@Composable
private fun ArchiveStatGrid(stats: List<ArchiveStat>, columns: Int = 3) {
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
