package com.deadfest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.Archive
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchivesScreen(navController: NavController, viewModel: MainViewModel) {
    val archives by viewModel.archives.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedArchive = remember(archives, selectedId) { archives.firstOrNull { it.id == selectedId } }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // System back closes the detail view first instead of leaving the screen.
    BackHandler(enabled = selectedArchive != null) { selectedId = null }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        if (selectedArchive != null) {
            ArchiveDetail(selectedArchive, dateFormat) { selectedId = null }
            return@Column
        }

        ScreenHeader(
            title = "ARCHIVES HUB",
            subtitle = "WASTELAND HISTORICAL DATABASE",
            onBack = { navController.popBackStack() }
        )

        if (archives.isEmpty()) {
            EmptyState(Icons.Default.History, "No seasons archived yet. History is still being written.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(archives, key = { it.id }) { archive ->
                    val champion = remember(archive) { archive.champion }
                    Surface(
                        modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(18.dp)) { selectedId = archive.id },
                        color = CardBg,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Outline)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.History, size = 44)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(archive.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(dateFormat.format(Date(archive.timestamp)), color = TextSecondary, fontSize = 12.sp)
                                champion?.let {
                                    Text(
                                        "👑 ${it.displayName ?: "Unknown"} · ${archive.playerSnapshots.size} survivors",
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
            }
        }
    }
}

@Composable
private fun ArchiveDetail(archive: Archive, dateFormat: SimpleDateFormat, onBack: () -> Unit) {
    val standings = remember(archive) { archive.playerSnapshots.sortedByDescending { it.coins } }

    ScreenHeader(
        title = archive.name.uppercase(),
        subtitle = "SAVED ${dateFormat.format(Date(archive.timestamp))}",
        onBack = onBack
    )
    SectionLabel("FINAL STANDINGS", Modifier.padding(horizontal = 16.dp))
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(standings, key = { i, s -> s.uid.ifEmpty { "idx$i" } }) { index, snapshot ->
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
                        Text("#${index + 1}", color = medal ?: TextMuted, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
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
                            if (snapshot.hasDetailedStats) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LevelBadge(snapshot.level, snapshot.prestigeCount)
                                    snapshot.title?.takeIf { it.isNotBlank() }?.let {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            it.uppercase(),
                                            color = RedAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        PriceTag(snapshot.coins)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${formatNumber(snapshot.totalDeaths)} total deaths", color = TextSecondary, fontSize = 12.sp)
                    if (snapshot.gameDeaths.isNotEmpty()) {
                        Text(
                            snapshot.gameDeaths.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
