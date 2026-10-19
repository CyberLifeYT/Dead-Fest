package com.deadfest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.PlayerData
import com.deadfest.data.model.ShopItem
import com.deadfest.data.model.User
import com.deadfest.domain.Presence
import com.deadfest.domain.XpConfig
import com.deadfest.domain.onlineNow
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

private enum class PlayerView(val label: String) { SURVIVORS("Survivors"), WEALTH("Wealth"), PROWESS("Prowess") }

private val User.totalDeaths: Long get() = playerData.games.values.sumOf { it.deaths }

@Composable
private fun PlayerAvatar(player: User, size: Int) {
    val online = player.playerData.onlineNow()
    // The green presence dot is colour-only, so state it: TalkBack hears "Online" with the row.
    val presence = if (online) Modifier.semantics { contentDescription = "Online" } else Modifier
    Box(presence) {
        FramedAvatar(player.playerData.avatar, player.playerData.avatarFrame, player.playerData.color, size, online)
    }
}

/**
 * Survivor network.
 *
 * Three lenses on the same roster — presence, wealth and survival prowess. The list is the
 * product here, so each row is built as a dense but legible dossier line: identity and rank on
 * the left, the metric that the current lens cares about on the right, and a colour wash for the
 * top three so standings are readable without scanning the numbers.
 */
@Composable
fun PlayersScreen(navController: NavController, viewModel: MainViewModel) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val me by viewModel.user.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()

    var view by rememberSaveable { mutableStateOf(PlayerView.SURVIVORS) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedUid by remember { mutableStateOf<String?>(null) }

    // Only recomputed when the data, tab or search actually changes, not on every recomposition.
    val displayUsers = remember(users, view, searchQuery) {
        val filtered = if (searchQuery.isBlank()) users
        else users.filter { it.displayName?.contains(searchQuery.trim(), ignoreCase = true) == true }
        when (view) {
            PlayerView.SURVIVORS -> filtered.sortedWith(
                compareByDescending<User> { it.playerData.onlineNow() }.thenBy { it.displayName?.lowercase() ?: "" }
            )
            PlayerView.WEALTH -> filtered.sortedByDescending { it.playerData.coins }
            // Survival prowess: the fewest deaths ranks highest.
            PlayerView.PROWESS -> filtered.sortedWith(compareBy<User> { it.totalDeaths }.thenByDescending { it.playerData.coins })
        }
    }
    val onlineCount = remember(users) { users.count { it.playerData.onlineNow() } }

    // Look the selected player up in the live list, so the dialog updates in real time.
    val selectedPlayer = remember(users, selectedUid) { users.firstOrNull { it.uid == selectedUid } }
    selectedPlayer?.let { SurvivorDetailsDialog(player = it, xpCfg = globalState.economy.xp, onDismiss = { selectedUid = null }) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "SURVIVOR NETWORK",
            subtitle = "${users.size} registered // leaderboard",
            trailing = { StatusPill("$onlineCount ONLINE", OnlineGreen) }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(PlayerView.entries.size) { i ->
                val option = PlayerView.entries[i]
                PillChip(
                    text = option.label,
                    selected = view == option,
                    onClick = { view = option },
                    // ~28dp pills: reserve a 48dp touch target and announce the active lens.
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { selected = view == option }
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
            placeholder = { Text("Search survivors...", color = TextLow) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextLow) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextLow)
                    }
                }
            },
            shape = RoundedCornerShape(Radius.md),
            colors = deadFestFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        Spacer(Modifier.height(Spacing.sm))

        if (displayUsers.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.People,
                title = if (users.isEmpty()) "Scanning the wasteland" else "No matches",
                message = if (users.isEmpty()) "Survivors will appear here as they register."
                else "No survivor matches \"$searchQuery\".",
                modifier = Modifier.padding(horizontal = Spacing.gutter)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.xxs,
                    bottom = Spacing.scrollBottom
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                itemsIndexed(displayUsers, key = { _, u -> u.uid }) { index, player ->
                    SurvivorItem(
                        player = player,
                        isMe = player.uid == me?.uid,
                        rank = if (view == PlayerView.SURVIVORS) null else index + 1,
                        emphasizeCoins = view == PlayerView.WEALTH,
                        onClick = { selectedUid = player.uid },
                        modifier = Modifier.enterOnMount(index)
                    )
                }
            }
        }
    }
}

@Composable
fun SurvivorDetailsDialog(player: User, xpCfg: XpConfig, onDismiss: () -> Unit) {
    val data = player.playerData
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(player, 48)
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text("SURVIVOR DOSSIER", color = Ember, style = DeadFestType.microLabel)
                    Text(
                        player.displayName?.takeIf { it.isNotBlank() } ?: "Survivor",
                        color = TextHi,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(data.level, data.prestigeCount)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (data.title ?: "SURVIVOR").uppercase(),
                            color = Ember,
                            style = DeadFestType.microLabel
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                XpBar(data.xp, data.level, xpCfg)
                Spacer(Modifier.height(Spacing.xxs))
                SectionLabel("Survival Stats")
                KeyValueRow("Total deaths", formatNumber(player.totalDeaths), valueColor = Ember)
                data.games.toSortedMap().forEach { (game, stats) ->
                    KeyValueRow(game, "${formatNumber(stats.deaths)} deaths")
                }
                Spacer(Modifier.height(Spacing.xs))
                GradientRule()
                Spacer(Modifier.height(Spacing.xs))
                SectionLabel("Standing")
                KeyValueRow("Bank coins", formatNumber(data.coins), valueColor = Amber)
                KeyValueRow("Shield", if (data.shield) "ACTIVE" else "NONE", valueColor = if (data.shield) Plasma else TextLow)
                KeyValueRow("Insurance", if (data.insurance) "ARMED" else "NONE", valueColor = if (data.insurance) Plasma else TextLow)
                KeyValueRow(
                    "Insulation",
                    if (data.insulationTier > 0) "TIER ${ShopItem.roman(data.insulationTier)}" else "NONE",
                    valueColor = if (data.insulationTier > 0) Amber else TextLow
                )
                KeyValueRow(
                    "Bankruptcy",
                    if (data.bankruptcyLevel > 0) "LEVEL ${data.bankruptcyLevel}" else "SOLVENT",
                    valueColor = if (data.bankruptcyLevel > 0) Ember else OnlineGreen
                )
                KeyValueRow(
                    "Pending spins",
                    "${data.pendingSpins}",
                    valueColor = if (data.pendingSpins > 0) Ember else TextLow
                )
                KeyValueRow(
                    "Prestige",
                    if (data.prestigeCount > 0) "★ ${data.prestigeCount}" else "NONE",
                    valueColor = if (data.prestigeCount > 0) Prestige else TextLow
                )
                val online = data.onlineNow()
                KeyValueRow(
                    "Status",
                    if (online) "ONLINE" else "OFFLINE · ${Presence.lastSeenLabel(data.lastSeenMs, System.currentTimeMillis())}",
                    valueColor = if (online) OnlineGreen else TextLow
                )
                if (!data.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    SectionLabel("Bio")
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(data.bio!!, color = TextMid, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
                shape = RoundedCornerShape(Radius.md),
                elevation = null
            ) { Text("CLOSE", style = DeadFestType.chip) }
        }
    )
}

/**
 * One roster row.
 *
 * `modifier` was added with a default so existing callers keep compiling; it exists so the list
 * can pass a staggered entrance without wrapping the row in another layout node.
 */
@Composable
fun SurvivorItem(
    player: User,
    isMe: Boolean,
    rank: Int? = null,
    emphasizeCoins: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = player.displayName?.takeIf { it.isNotBlank() } ?: "Survivor"
    val title = player.playerData.title?.takeIf { it.isNotBlank() } ?: "SURVIVOR"
    val themeColor = remember(player.playerData.color) { parseThemeColor(player.playerData.color) }
    val medal = when (rank) {
        1 -> Amber
        2 -> Silver
        3 -> Bronze
        else -> null
    }
    val accent = when {
        isMe -> Ember
        medal != null -> medal
        else -> InkLine
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Ink2)
            .then(
                if (medal != null) Modifier.background(
                    Brush.horizontalGradient(listOf(medal.copy(alpha = 0.10f), Color.Transparent))
                ) else Modifier
            )
            .border(BorderStroke(Stroke.hair, accent), RoundedCornerShape(Radius.lg))
            .pressClick(RoundedCornerShape(Radius.lg), onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            RankBadge(rank)
            Spacer(Modifier.width(Spacing.xs))
        }
        PlayerAvatar(player, 44)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = TextHi,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(Ember.copy(alpha = Tint.fill))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("YOU", color = Ember, style = DeadFestType.microLabel)
                    }
                }
                if (player.playerData.shield) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.Shield, contentDescription = "Shielded", tint = Plasma, modifier = Modifier.size(13.dp))
                }
                if (player.playerData.bankruptcyLevel > 0) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.Whatshot, contentDescription = "Bankrupt", tint = Ember, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(player.playerData.level, player.playerData.prestigeCount)
                Spacer(Modifier.width(6.dp))
                Text(title.uppercase(), color = themeColor, style = DeadFestType.microLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(horizontalAlignment = Alignment.End) {
            if (emphasizeCoins) {
                Text(formatNumber(player.playerData.coins), color = Amber, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("${formatNumber(player.totalDeaths)} DEATHS", color = TextLow, style = DeadFestType.microLabel)
            } else {
                Text(formatNumber(player.totalDeaths), color = Ember, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("DEATHS", color = TextLow, style = DeadFestType.microLabel)
            }
        }
    }
}
