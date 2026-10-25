@file:OptIn(ExperimentalLayoutApi::class)

package com.deadfest.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.R
import com.deadfest.data.model.DeathEvidence
import com.deadfest.data.model.DeathEvidenceAudit
import com.deadfest.data.model.User
import com.deadfest.data.repository.FirebaseRepository.EvidenceFilter
import com.deadfest.domain.DeathEventType
import com.deadfest.domain.DeathEvidenceStatus
import com.deadfest.domain.DeathGames
import com.deadfest.domain.DeathModeration
import com.deadfest.domain.ModerationAction
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.EvidenceBrowser
import com.deadfest.ui.viewmodel.MainViewModel
import com.deadfest.util.ThumbnailCache
import com.deadfest.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Xbox death evidence (v2.1.3): More -> Deaths Images for every survivor, Admin -> Deaths for
 * review. Both read the same shared records; the screenshots themselves were captured on a PC by
 * the Dead-Fest Xbox Death Detector and uploaded only if that PC's owner chose to share them.
 */

// ---------------------------------------------------------------- labels

internal fun evidenceStatusColor(status: String): Color = when (status) {
    DeathEvidenceStatus.VERIFIED -> Toxic
    DeathEvidenceStatus.PENDING -> Amber
    DeathEvidenceStatus.REJECTED -> Ember
    else -> TextLow
}

internal fun evidenceStatusLabel(status: String): String = when (status) {
    DeathEvidenceStatus.VERIFIED -> tr(R.string.evidence_status_verified)
    DeathEvidenceStatus.PENDING -> tr(R.string.evidence_status_pending)
    DeathEvidenceStatus.REJECTED -> tr(R.string.evidence_status_rejected)
    DeathEvidenceStatus.REMOVED -> tr(R.string.evidence_status_removed)
    else -> status
}

internal fun evidenceTypeLabel(type: String): String = when (type) {
    DeathEventType.SELF -> tr(R.string.evidence_type_self)
    DeathEventType.TEAMMATE -> tr(R.string.evidence_type_teammate)
    DeathEventType.OTHER -> tr(R.string.evidence_type_other)
    else -> tr(R.string.evidence_type_unknown)
}

private fun auditLabel(action: String): String = when (action) {
    "submitted" -> tr(R.string.evidence_audit_submitted)
    "verified" -> tr(R.string.evidence_audit_verified)
    "rejected" -> tr(R.string.evidence_audit_rejected)
    "corrected" -> tr(R.string.evidence_audit_corrected)
    "removed" -> tr(R.string.evidence_audit_removed)
    "restored" -> tr(R.string.evidence_audit_restored)
    "deleted" -> tr(R.string.evidence_audit_deleted)
    else -> action
}

private val EVENT_ID = Regex("^[A-Za-z0-9_-]{16,64}$")
private const val DAY_MS = 86_400_000L

// ---------------------------------------------------------------- More -> Deaths Images

@Composable
fun DeathImagesScreen(navController: NavController, viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val browser = remember { viewModel.evidenceBrowser(scope, admin = false) }
    val state by browser.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<DeathEvidence?>(null) }
    LaunchedEffect(browser) { if (!state.loadedOnce) browser.refresh() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = tr(R.string.evidence_title),
            subtitle = tr(R.string.evidence_subtitle),
            onBack = { navController.popBackStack() },
            trailing = {
                IconButton(onClick = { browser.refresh() }, enabled = !state.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = tr(R.string.evidence_refresh), tint = TextHi)
                }
            }
        )
        EvidenceGrid(
            state = state,
            onOpen = { open = it },
            onLoadMore = browser::loadMore,
            onRetry = browser::refresh,
            emptyTitle = tr(R.string.evidence_empty_title),
            emptyMessage = tr(R.string.evidence_empty_message),
            header = {
                EvidenceFilterBar(
                    filter = state.filter,
                    statuses = listOf(null) + DeathEvidenceStatus.VISIBLE,
                    hiddenWhenAll = DeathEvidenceStatus.VISIBLE,
                    onChange = browser::setFilter
                )
            }
        )
    }

    open?.let { ev ->
        EvidenceViewer(ev, viewModel, admin = false, onClose = { open = null }, onChanged = {})
    }
}

// ---------------------------------------------------------------- Admin -> Deaths

@Composable
internal fun AdminDeathEvidence(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val browser = remember { viewModel.evidenceBrowser(scope, admin = true) }
    val state by browser.state.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<DeathEvidence?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<DeathEvidence?>(null) }
    var searched by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf<Set<String>>(emptySet()) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(browser) { if (!state.loadedOnce) browser.refresh() }

    val q = query.trim()
    val shown = when {
        found != null -> listOf(found!!)
        q.isEmpty() || EVENT_ID.matches(q) -> state.items
        else -> state.items.filter { ev ->
            val linked = users.firstOrNull { it.uid == ev.subjectUid }?.handle.orEmpty()
            listOf(ev.playerLabel, ev.submitterName, linked, ev.id).any { it.contains(q, ignoreCase = true) }
        }
    }

    // Whole lookups by event id go to the server; anything else filters what is loaded.
    LaunchedEffect(q) {
        found = null
        searched = false
        if (EVENT_ID.matches(q)) {
            found = viewModel.findEvidence(q)
            searched = true
        }
    }

    EvidenceGrid(
        state = state.copy(items = shown),
        onOpen = { open = it },
        onLoadMore = { if (found == null) browser.loadMore() },
        onRetry = browser::refresh,
        emptyTitle = if (searched && found == null) tr(R.string.evidence_search_no_match_title) else tr(R.string.evidence_admin_empty_title),
        emptyMessage = if (searched && found == null) tr(R.string.evidence_search_no_match, q) else tr(R.string.evidence_admin_empty_message),
        missing = missing,
        bottomPadding = Spacing.md,
        header = {
            AdminActionCard(
                title = tr(R.string.evidence_admin_title),
                subtitle = tr(R.string.evidence_admin_subtitle),
                accent = Ember
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMid) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = tr(R.string.evidence_clear_search), tint = TextMid)
                            }
                        }
                    },
                    placeholder = { Text(tr(R.string.evidence_search_hint), color = TextLow) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    colors = deadFestFieldColors()
                )
                Spacer(Modifier.height(Spacing.sm))
                EvidenceFilterBar(
                    filter = state.filter,
                    statuses = listOf(null) + DeathEvidenceStatus.ALL,
                    hiddenWhenAll = null,
                    onChange = browser::setFilter
                )
                Spacer(Modifier.height(Spacing.xs))
                AdminEvidenceExtraFilters(state.filter, users, onChange = browser::setFilter)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    GhostAction(
                        text = if (checking) tr(R.string.evidence_checking_images) else tr(R.string.evidence_check_images),
                        icon = Icons.Default.BrokenImage,
                        enabled = !checking && shown.isNotEmpty(),
                        onClick = {
                            checking = true
                            scope.launch {
                                viewModel.missingEvidenceImages(shown)?.let { missing = it }
                                checking = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GhostAction(
                        text = tr(R.string.evidence_refresh),
                        icon = Icons.Default.Refresh,
                        enabled = !state.loading,
                        onClick = { missing = emptySet(); browser.refresh() },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(tr(R.string.evidence_missing_count, missing.size), color = Ember, style = DeadFestType.body)
                }
            }
        }
    )

    open?.let { ev ->
        EvidenceViewer(
            ev, viewModel, admin = true,
            onClose = { open = null },
            onChanged = { updated ->
                if (updated == null) {
                    browser.remove(ev.id)
                    open = null
                } else {
                    browser.replace(updated)
                    if (found?.id == updated.id) found = updated
                    open = updated
                }
            }
        )
    }
}

// ---------------------------------------------------------------- filters

@Composable
private fun EvidenceFilterBar(
    filter: EvidenceFilter,
    statuses: List<String?>,
    hiddenWhenAll: List<String>?,
    onChange: (EvidenceFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            PillChip(tr(R.string.evidence_all_games), filter.gameId == null, onClick = { onChange(filter.copy(gameId = null)) })
            DeathGames.ALL.forEach { g ->
                PillChip(g.sectorName, filter.gameId == g.id, onClick = { onChange(filter.copy(gameId = g.id)) }, accent = Plasma)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            statuses.forEach { s ->
                val selected = filter.status == s
                PillChip(
                    text = if (s == null) tr(R.string.evidence_all_statuses) else evidenceStatusLabel(s),
                    selected = selected,
                    accent = if (s == null) Ember else evidenceStatusColor(s),
                    onClick = { onChange(filter.copy(status = s, statuses = if (s == null) hiddenWhenAll else null)) }
                )
            }
            PillChip(
                text = if (filter.newestFirst) tr(R.string.evidence_sort_newest) else tr(R.string.evidence_sort_oldest),
                selected = true,
                accent = Amber,
                onClick = { onChange(filter.copy(newestFirst = !filter.newestFirst)) }
            )
        }
    }
}

@Composable
private fun AdminEvidenceExtraFilters(filter: EvidenceFilter, users: List<User>, onChange: (EvidenceFilter) -> Unit) {
    var playerMenu by remember { mutableStateOf(false) }
    val player = users.firstOrNull { it.uid == filter.subjectUid }
    val ranges = listOf(null to tr(R.string.evidence_range_all), 1 to tr(R.string.evidence_range_day), 7 to tr(R.string.evidence_range_week), 30 to tr(R.string.evidence_range_month))
    val currentDays = filter.fromMs?.let { from -> ranges.mapNotNull { it.first }.minByOrNull { d -> kotlin.math.abs((System.currentTimeMillis() - from) - d * DAY_MS) } }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            PillChip(
                text = player?.handle ?: tr(R.string.evidence_all_players),
                selected = player != null,
                accent = Toxic,
                onClick = { playerMenu = true }
            )
            DropdownMenu(expanded = playerMenu, onDismissRequest = { playerMenu = false }) {
                DropdownMenuItem(text = { Text(tr(R.string.evidence_all_players)) }, onClick = {
                    playerMenu = false
                    onChange(filter.copy(subjectUid = null))
                })
                users.sortedBy { it.handle.lowercase() }.forEach { u ->
                    DropdownMenuItem(text = { Text(u.handle) }, onClick = {
                        playerMenu = false
                        onChange(filter.copy(subjectUid = u.uid))
                    })
                }
            }
        }
        ranges.forEach { (days, label) ->
            PillChip(
                text = label,
                selected = if (days == null) filter.fromMs == null else currentDays == days,
                accent = Plasma,
                onClick = { onChange(filter.copy(fromMs = days?.let { System.currentTimeMillis() - it * DAY_MS })) }
            )
        }
    }
}

// ---------------------------------------------------------------- grid

@Composable
private fun EvidenceGrid(
    state: EvidenceBrowser.State,
    onOpen: (DeathEvidence) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    emptyTitle: String,
    emptyMessage: String,
    missing: Set<String> = emptySet(),
    bottomPadding: androidx.compose.ui.unit.Dp = Spacing.scrollBottom,
    header: @Composable () -> Unit
) {
    val grid = rememberLazyGridState()
    // Load the next page as the end comes into view.
    val nearEnd by remember(state.items.size) {
        derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.items.size - 3 }
    }
    LaunchedEffect(nearEnd, state.items.size, state.endReached) {
        if (nearEnd && state.items.isNotEmpty() && !state.endReached) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        when {
            state.items.isEmpty() && (state.loading || !state.loadedOnce) -> item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingState(tr(R.string.evidence_loading))
            }
            state.items.isEmpty() && state.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(message = tr(R.string.evidence_load_failed, state.error), onRetry = onRetry)
            }
            state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyStateCard(icon = Icons.Default.PhotoCamera, title = emptyTitle, message = emptyMessage)
            }
            else -> {
                items(state.items, key = { it.id }) { ev ->
                    EvidenceTile(ev, missing = ev.id in missing || !ev.hasImage, onClick = { onOpen(ev) })
                }
                if (state.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Ember, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (state.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorState(message = tr(R.string.evidence_load_failed, state.error), onRetry = onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberEvidenceThumb(ev: DeathEvidence): Bitmap? {
    val key = "ev_${ev.id}"
    val state = produceState(initialValue = ThumbnailCache.get(key), ev.id) {
        val bytes = ev.thumb
        if (value == null && bytes != null) value = withContext(Dispatchers.Default) { ThumbnailCache.decode(key, bytes) }
    }
    return state.value
}

@Composable
private fun EvidenceTile(ev: DeathEvidence, missing: Boolean, onClick: () -> Unit) {
    val game = DeathGames.name(ev.gameId)
    val time = formatEventTime(ev.detectedAt)
    val status = evidenceStatusLabel(ev.status)
    val thumb = rememberEvidenceThumb(ev)
    val description = tr(R.string.evidence_tile_description, game, time, ev.playerLabel.ifBlank { ev.submitterName }, status)
    SurfaceCard(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        onClick = onClick,
        padding = 0.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
                .background(Ink0),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    bitmap = remember(thumb) { thumb.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.HideImage, contentDescription = null, tint = TextLow, modifier = Modifier.size(28.dp))
            }
            if (missing) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(Spacing.xs)
                        .background(Ink0.copy(alpha = 0.75f), RoundedCornerShape(Radius.pill))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(tr(R.string.evidence_image_missing_short), color = Ember, style = DeadFestType.microLabel)
                }
            }
        }
        Column(Modifier.padding(Spacing.sm)) {
            Text(game, color = TextHi, style = DeadFestType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, color = TextMid, style = DeadFestType.microLabel, maxLines = 1)
            val who = ev.playerLabel.ifBlank { if (ev.eventType == DeathEventType.SELF) ev.submitterName else "" }
            if (who.isNotBlank()) {
                Text(who, color = TextMid, style = DeadFestType.microLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(Spacing.xs))
            StatusPill(status, evidenceStatusColor(ev.status))
        }
    }
}

// ---------------------------------------------------------------- viewer

@Composable
private fun EvidenceViewer(
    evidence: DeathEvidence,
    viewModel: MainViewModel,
    admin: Boolean,
    onClose: () -> Unit,
    onChanged: (DeathEvidence?) -> Unit
) {
    val screenPx = with(LocalDensity.current) {
        val cfg = LocalConfiguration.current
        maxOf(cfg.screenWidthDp, cfg.screenHeightDp).dp.roundToPx()
    }
    val users by viewModel.users.collectAsStateWithLifecycle()
    val global by viewModel.globalState.collectAsStateWithLifecycle()
    var loadFailed by remember(evidence.id) { mutableStateOf(false) }
    val full by produceState<Bitmap?>(initialValue = null, evidence.id) {
        if (!evidence.hasImage) {
            loadFailed = true
            return@produceState
        }
        val bytes = viewModel.evidenceImage(evidence.id)
        value = bytes?.let { withContext(Dispatchers.Default) { ThumbnailCache.decodeFull(it, screenPx) } }
        if (value == null) loadFailed = true
    }
    val thumb = rememberEvidenceThumb(evidence)
    var scale by remember(evidence.id) { mutableFloatStateOf(1f) }
    var offset by remember(evidence.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val game = DeathGames.name(evidence.gameId)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink0)) {
            Row(
                Modifier.fillMaxWidth().background(Ink1).padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = tr(R.string.images_close), tint = TextHi) }
                Column(Modifier.weight(1f)) {
                    Text(game, color = TextHi, style = DeadFestType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatEventTime(evidence.detectedAt), color = TextMid, style = DeadFestType.body)
                }
                StatusPill(evidenceStatusLabel(evidence.status), evidenceStatusColor(evidence.status))
                Spacer(Modifier.width(Spacing.xs))
            }

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val shown = full ?: thumb
                if (shown != null) {
                    Image(
                        bitmap = remember(shown) { shown.asImageBitmap() },
                        contentDescription = tr(R.string.evidence_image_description, game, formatEventTime(evidence.detectedAt)),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(evidence.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
                if (full == null && !loadFailed) {
                    LoadingState(tr(R.string.evidence_loading_image), Modifier.align(Alignment.Center))
                }
                if (loadFailed) {
                    // Deliberately distinct from "rejected": the record is fine, the picture is gone.
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(Spacing.sm)
                            .background(Ink0.copy(alpha = 0.85f), RoundedCornerShape(Radius.md))
                            .padding(Spacing.sm)
                    ) {
                        Text(
                            if (evidence.hasImage) tr(R.string.evidence_image_missing) else tr(R.string.evidence_image_not_uploaded),
                            color = Ember,
                            style = DeadFestType.body
                        )
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
                    .background(Ink1)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(Spacing.md)
            ) {
                EvidenceDetails(evidence, users)
                if (admin) {
                    Spacer(Modifier.height(Spacing.md))
                    EvidenceAdminActions(evidence, viewModel, users, global.games, onChanged)
                    Spacer(Modifier.height(Spacing.md))
                    EvidenceAuditHistory(evidence, viewModel)
                }
            }
        }
    }
}

@Composable
private fun EvidenceDetails(ev: DeathEvidence, users: List<User>) {
    val linked = users.firstOrNull { it.uid == ev.subjectUid }?.handle
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        KeyValueRow(tr(R.string.evidence_field_classification), evidenceTypeLabel(ev.eventType))
        if (ev.playerLabel.isNotBlank()) KeyValueRow(tr(R.string.evidence_field_player_label), ev.playerLabel)
        KeyValueRow(tr(R.string.evidence_field_counts_for), linked ?: tr(R.string.evidence_not_linked))
        KeyValueRow(tr(R.string.evidence_field_submitted_by), ev.submitterName.ifBlank { ev.uid.take(8) })
        KeyValueRow(tr(R.string.evidence_field_confidence), tr(R.string.evidence_percent, (ev.confidence * 100).toInt()))
        KeyValueRow(
            tr(R.string.evidence_field_counted),
            if (ev.counted) tr(R.string.evidence_counted_in, ev.countedSector) else tr(R.string.evidence_not_counted),
            valueColor = if (ev.counted) Toxic else TextMid
        )
        if (ev.reviewedAt > 0) {
            KeyValueRow(tr(R.string.evidence_field_reviewed), tr(R.string.evidence_reviewed_by, ev.reviewerName, formatEventTime(ev.reviewedAt)))
        }
        if (ev.rejectionReason.isNotBlank()) {
            KeyValueRow(tr(R.string.evidence_field_reason), ev.rejectionReason, valueColor = Ember)
        }
        KeyValueRow(tr(R.string.evidence_field_event_id), ev.id, valueColor = TextLow)
        if (ev.localRelativePath.isNotBlank()) {
            KeyValueRow(tr(R.string.evidence_field_pc_file), ev.localRelativePath, valueColor = TextLow)
        }
    }
}

@Composable
private fun EvidenceAuditHistory(ev: DeathEvidence, viewModel: MainViewModel) {
    val history by produceState<List<DeathEvidenceAudit>?>(initialValue = null, ev.id, ev.updatedAt) {
        value = viewModel.evidenceAudit(ev.id) ?: emptyList()
    }
    SectionLabel(tr(R.string.evidence_history))
    Spacer(Modifier.height(Spacing.xs))
    val list = history
    when {
        list == null -> Text(tr(R.string.evidence_loading), color = TextLow, style = DeadFestType.body)
        list.isEmpty() -> Text(tr(R.string.evidence_history_empty), color = TextLow, style = DeadFestType.body)
        else -> list.forEach { a ->
            Column(Modifier.padding(vertical = Spacing.xxs)) {
                Text("${auditLabel(a.action)} · ${a.actorName.ifBlank { a.actor.take(8) }}", color = TextHi, style = DeadFestType.body)
                Text(
                    buildString {
                        append(formatEventTime(a.at))
                        if (a.note.isNotBlank()) append(" · ").append(a.note)
                    },
                    color = TextMid,
                    style = DeadFestType.microLabel
                )
            }
        }
    }
}

// ---------------------------------------------------------------- admin actions

private enum class EvidenceDialog { VERIFY, REJECT, CORRECT, REMOVE, RESTORE, DELETE }

@Composable
private fun EvidenceAdminActions(
    ev: DeathEvidence,
    viewModel: MainViewModel,
    users: List<User>,
    sectors: List<String>,
    onChanged: (DeathEvidence?) -> Unit
) {
    var dialog by remember(ev.id) { mutableStateOf<EvidenceDialog?>(null) }
    var busy by remember(ev.id) { mutableStateOf(false) }
    fun run(action: ModerationAction) {
        busy = true
        dialog = null
        viewModel.moderateEvidence(ev, action) { fresh ->
            busy = false
            if (fresh != null) onChanged(fresh)
        }
    }

    SectionLabel(tr(R.string.evidence_actions))
    Spacer(Modifier.height(Spacing.xxs))
    Text(tr(R.string.evidence_coins_note), color = TextMid, style = DeadFestType.microLabel)
    Spacer(Modifier.height(Spacing.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when {
            ev.isPending -> {
                PrimaryAction(tr(R.string.evidence_verify), onClick = { dialog = EvidenceDialog.VERIFY }, enabled = !busy, icon = Icons.Default.Verified, accent = Toxic)
                GhostAction(tr(R.string.evidence_reject), onClick = { dialog = EvidenceDialog.REJECT }, enabled = !busy, icon = Icons.Default.Block, accent = Ember)
            }
            ev.isVerified -> {
                GhostAction(tr(R.string.evidence_reject), onClick = { dialog = EvidenceDialog.REJECT }, enabled = !busy, icon = Icons.Default.Block, accent = Ember)
            }
            else -> {
                PrimaryAction(tr(R.string.evidence_restore), onClick = { dialog = EvidenceDialog.RESTORE }, enabled = !busy, icon = Icons.Default.Restore, accent = Amber)
            }
        }
        if (!ev.isRemoved) {
            GhostAction(tr(R.string.evidence_correct), onClick = { dialog = EvidenceDialog.CORRECT }, enabled = !busy, icon = Icons.Default.Edit, accent = Plasma)
            GhostAction(tr(R.string.evidence_remove), onClick = { dialog = EvidenceDialog.REMOVE }, enabled = !busy, icon = Icons.Default.RemoveCircleOutline, accent = Ember)
        }
        if (!ev.counted && (ev.isRejected || ev.isRemoved)) {
            GhostAction(tr(R.string.evidence_delete_forever), onClick = { dialog = EvidenceDialog.DELETE }, enabled = !busy, icon = Icons.Default.DeleteForever, accent = Ember)
        }
    }
    if (busy) {
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(color = Ember, trackColor = Ink2, modifier = Modifier.fillMaxWidth())
    }

    when (dialog) {
        EvidenceDialog.VERIFY -> VerifyDialog(ev, users, sectors, onDismiss = { dialog = null }) { sector, subject ->
            run(ModerationAction.Verify(sector, subject))
        }
        EvidenceDialog.REJECT -> ReasonDialog(
            title = tr(R.string.evidence_reject_title),
            message = if (ev.counted) tr(R.string.evidence_reject_counted_message, ev.countedSector) else tr(R.string.evidence_reject_message),
            confirm = tr(R.string.evidence_reject),
            onDismiss = { dialog = null }
        ) { run(ModerationAction.Reject(it)) }
        EvidenceDialog.REMOVE -> ReasonDialog(
            title = tr(R.string.evidence_remove_title),
            message = if (ev.counted) tr(R.string.evidence_remove_counted_message, ev.countedSector) else tr(R.string.evidence_remove_message),
            confirm = tr(R.string.evidence_remove),
            onDismiss = { dialog = null }
        ) { run(ModerationAction.Remove(it)) }
        EvidenceDialog.RESTORE -> ConfirmDialog(
            title = tr(R.string.evidence_restore_title),
            message = if (ev.countedSector.isNotBlank() && ev.subjectUid.isNotBlank()) {
                tr(R.string.evidence_restore_counted_message, ev.countedSector)
            } else tr(R.string.evidence_restore_pending_message),
            confirmText = tr(R.string.evidence_restore),
            onConfirm = { run(ModerationAction.Restore) },
            onDismiss = { dialog = null }
        )
        EvidenceDialog.CORRECT -> CorrectDialog(ev, users, sectors, onDismiss = { dialog = null }) { run(it) }
        EvidenceDialog.DELETE -> ConfirmDialog(
            title = tr(R.string.evidence_delete_title),
            message = tr(R.string.evidence_delete_message),
            confirmText = tr(R.string.evidence_delete_forever),
            destructive = true,
            onConfirm = {
                dialog = null
                busy = true
                viewModel.deleteEvidencePermanently(ev) { ok ->
                    busy = false
                    if (ok) onChanged(null)
                }
            },
            onDismiss = { dialog = null }
        )
        null -> Unit
    }
}

@Composable
private fun SectorPicker(sectors: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (sectors + selected).filter { it.isNotBlank() }.distinct().forEach { s ->
            PillChip(s, selected == s, onClick = { onSelect(s) }, accent = Plasma)
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    OutlinedTextField(
        value = selected,
        onValueChange = { onSelect(it.take(DeathModeration.MAX_SECTOR)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(tr(R.string.evidence_sector)) },
        colors = deadFestFieldColors()
    )
}

@Composable
private fun PlayerPicker(users: List<User>, selected: String, allowNone: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (allowNone) PillChip(tr(R.string.evidence_not_linked), selected.isBlank(), onClick = { onSelect("") })
            users.sortedBy { it.handle.lowercase() }.forEach { u ->
                PillChip(u.handle, selected == u.uid, onClick = { onSelect(u.uid) }, accent = Toxic)
            }
        }
    }
}

@Composable
private fun VerifyDialog(
    ev: DeathEvidence,
    users: List<User>,
    sectors: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (sector: String, subjectUid: String) -> Unit
) {
    var sector by remember { mutableStateOf(DeathGames.defaultSector(ev.gameId, sectors)) }
    var subject by remember { mutableStateOf(ev.subjectUid) }
    val who = users.firstOrNull { it.uid == subject }?.handle
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink1,
        title = { Text(tr(R.string.evidence_verify_title), color = TextHi) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr(R.string.evidence_verify_player), color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.xs))
                PlayerPicker(users, subject, allowNone = false) { subject = it }
                Spacer(Modifier.height(Spacing.sm))
                Text(tr(R.string.evidence_verify_sector), color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.xs))
                SectorPicker(sectors, sector) { sector = it }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    if (who != null && sector.isNotBlank()) tr(R.string.evidence_verify_summary, who, sector.trim())
                    else tr(R.string.evidence_verify_incomplete),
                    color = if (who != null && sector.isNotBlank()) Toxic else Amber,
                    style = DeadFestType.body
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sector.trim(), subject) }, enabled = who != null && sector.isNotBlank()) {
                Text(tr(R.string.evidence_verify_confirm), color = if (who != null && sector.isNotBlank()) Toxic else TextLow)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.evidence_cancel), color = TextMid) } }
    )
}

@Composable
private fun ReasonDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink1,
        title = { Text(title, color = TextHi) },
        text = {
            Column {
                Text(message, color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(DeathModeration.MAX_REASON) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr(R.string.evidence_reason)) },
                    supportingText = { Text("${reason.length}/${DeathModeration.MAX_REASON}", color = TextLow) },
                    colors = deadFestFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = reason.isNotBlank()) {
                Text(confirm, color = if (reason.isNotBlank()) Ember else TextLow)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.evidence_cancel), color = TextMid) } }
    )
}

@Composable
private fun CorrectDialog(
    ev: DeathEvidence,
    users: List<User>,
    sectors: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (ModerationAction.Correct) -> Unit
) {
    var game by remember { mutableStateOf(ev.gameId) }
    var type by remember { mutableStateOf(ev.eventType) }
    var label by remember { mutableStateOf(ev.playerLabel) }
    var subject by remember { mutableStateOf(ev.subjectUid) }
    var sector by remember { mutableStateOf(ev.countedSector) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink1,
        title = { Text(tr(R.string.evidence_correct_title), color = TextHi) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr(R.string.evidence_field_game), color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DeathGames.ALL.forEach { g -> PillChip(g.sectorName, game == g.id, onClick = { game = g.id }, accent = Plasma) }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(tr(R.string.evidence_field_classification), color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DeathEventType.ALL.forEach { t -> PillChip(evidenceTypeLabel(t), type == t, onClick = { type = t }, accent = Amber) }
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(DeathModeration.MAX_LABEL) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr(R.string.evidence_field_player_label)) },
                    colors = deadFestFieldColors()
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(tr(R.string.evidence_field_counts_for), color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.xs))
                PlayerPicker(users, subject, allowNone = !ev.counted) { subject = it }
                if (ev.counted) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(tr(R.string.evidence_verify_sector), color = TextMid, style = DeadFestType.body)
                    Spacer(Modifier.height(Spacing.xs))
                    SectorPicker(sectors, sector) { sector = it }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(tr(R.string.evidence_correct_counted_note), color = TextMid, style = DeadFestType.microLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    ModerationAction.Correct(
                        gameId = game.takeIf { it != ev.gameId },
                        eventType = type.takeIf { it != ev.eventType },
                        playerLabel = label.trim().takeIf { it != ev.playerLabel },
                        subjectUid = subject.takeIf { it != ev.subjectUid },
                        sector = sector.trim().takeIf { ev.counted && it != ev.countedSector }
                    )
                )
            }) { Text(tr(R.string.evidence_save), color = Plasma) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.evidence_cancel), color = TextMid) } }
    )
}
