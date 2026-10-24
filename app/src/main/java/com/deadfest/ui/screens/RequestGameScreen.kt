package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.deadfest.data.model.GameRequest
import com.deadfest.data.repository.GameInfo
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.GameSearchState
import com.deadfest.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 * Community game requests (v2.0.8).
 *
 * Survivors ask for a game to become a sector; admins approve or remove from the Admin panel, so
 * this screen is read-only for everyone else. Approved games are listed openly — they are what the
 * community has already won — while the pending queue sits behind a button so it is available
 * without being the first thing on the screen.
 */
@Composable
fun RequestGameScreen(navController: NavController, viewModel: MainViewModel) {
    val requests by viewModel.gameRequests.collectAsStateWithLifecycle()
    val search by viewModel.gameSearch.collectAsStateWithLifecycle()
    var showPending by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    val pending = remember(requests) { requests.filter { !it.isApproved } }
    val approved = remember(requests) { requests.filter { it.isApproved } }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = tr(R.string.more_request_game),
            subtitle = tr(R.string.requestgame_suggest_a_sector),
            onBack = { navController.popBackStack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PrimaryAction(
                text = tr(R.string.requestgame_request_game),
                icon = Icons.Default.Add,
                onClick = { showDialog = true },
                modifier = Modifier.enterOnMount(0)
            )

            Text(tr(R.string.requestgame_approved_games), color = TextLow, style = DeadFestType.microLabel)
            if (approved.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.SportsEsports,
                    title = tr(R.string.requestgame_no_approved_games_yet),
                    message = tr(R.string.requestgame_games_the_admin_has_approved_will)
                )
            } else {
                approved.forEachIndexed { index, request ->
                    GameRequestRow(request, modifier = Modifier.enterOnMount(index + 1))
                }
            }

            Spacer(Modifier.height(Spacing.xxs))

            // The queue behind a button: visible when wanted, never in the way when not.
            GhostAction(
                text = if (showPending) tr(R.string.requestgame_hide_pending_games) else tr(R.string.requestgame_pending_games_1, pending.size),
                icon = Icons.Default.Schedule,
                onClick = { showPending = !showPending },
                modifier = Modifier.fillMaxWidth()
            )
            if (showPending) {
                if (pending.isEmpty()) {
                    EmptyState(Icons.Default.CheckCircle, tr(R.string.requestgame_nothing_waiting_for_approval))
                } else {
                    pending.forEach { GameRequestRow(it, showRequester = true) }
                }
            }

            Spacer(Modifier.height(Spacing.scrollBottom))
        }
    }

    if (showDialog) {
        RequestGameDialog(
            search = search,
            onSearch = viewModel::searchGames,
            onSubmit = { name, cover -> viewModel.submitGameRequest(name, cover) },
            onDismiss = {
                showDialog = false
                viewModel.clearGameSearch()
            }
        )
    }
}

/** One request in a list: cover, name, and who asked (pending only). */
@Composable
private fun GameRequestRow(
    request: GameRequest,
    showRequester: Boolean = false,
    modifier: Modifier = Modifier
) {
    SurfaceCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (request.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = request.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Ink3)
                )
            } else {
                IconTile(Icons.Default.SportsEsports, tint = TextLow, size = 46)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    request.name,
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showRequester) {
                    Text(
                        tr(R.string.requestgame_requested_by_1, request.requestedByName),
                        color = TextMid,
                        style = DeadFestType.body
                    )
                }
            }
            StatusPill(if (request.isApproved) tr(R.string.admin_approved) else tr(R.string.admin_pending), if (request.isApproved) Toxic else Amber)
        }
    }
}

/**
 * Submit-a-request dialog.
 *
 * The game name is looked up in the external game database so the request can carry the real box
 * art. When the lookup cannot reach the service — or finds nothing — the dialog offers manual
 * entry instead, so a game database outage never blocks a feature that otherwise runs entirely on
 * Firestore.
 */
@Composable
private fun RequestGameDialog(
    search: GameSearchState,
    onSearch: (String) -> Unit,
    onSubmit: (name: String, coverUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    val offline = search.offline
    val showManual = offline || (!search.loading && search.query.trim().length >= 2 && search.results.isEmpty())

    // Debounced search: wait for typing to settle rather than firing a request per keystroke.
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            delay(400)
            onSearch(query)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = { Text(tr(R.string.requestgame_request_a_game), color = TextHi, style = DeadFestType.cardTitle) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(tr(R.string.requestgame_search_the_game_database)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors()
                )

                if (search.loading) {
                    LinearProgressIndicator(
                        color = Ember,
                        trackColor = Ink3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                search.results.forEach { game ->
                    SurfaceCard(
                        onClick = {
                            name = game.title
                            game.coverUrl?.let { coverUrl = it }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!game.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = game.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .background(Ink3)
                                )
                            } else {
                                IconTile(Icons.Default.SportsEsports, tint = TextLow, size = 40)
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    game.title,
                                    color = TextHi,
                                    style = DeadFestType.cardTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val meta = listOfNotNull(game.platform, game.releaseDate)
                                if (meta.isNotEmpty()) {
                                    Text(
                                        meta.joinToString(" · "),
                                        color = TextMid,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (showManual) {
                    Text(
                        if (offline) tr(R.string.requestgame_the_game_database_is_unreachable_right)
                        else tr(R.string.requestgame_no_match_found_enter_the_game),
                        color = TextMid,
                        style = DeadFestType.body
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(tr(R.string.requestgame_game_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                    OutlinedTextField(
                        value = coverUrl,
                        onValueChange = { coverUrl = it },
                        label = { Text(tr(R.string.requestgame_cover_image_url_optional)) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, coverUrl) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                shape = RoundedCornerShape(Radius.sm)
            ) { Text(tr(R.string.requestgame_send_request), fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.common_cancel), color = TextMid) } }
    )
}
