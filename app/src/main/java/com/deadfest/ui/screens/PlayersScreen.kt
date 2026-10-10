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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.PlayerData
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
private fun PlayerAvatar(player: User, size: Int) =
    FramedAvatar(player.playerData.avatar, player.playerData.avatarFrame, player.playerData.color, size, player.playerData.onlineNow())

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

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(
            title = "SURVIVOR NETWORK",
            subtitle = "${users.size} REGISTERED",
            trailing = { StatusPill("$onlineCount ONLINE", OnlineGreen) }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PlayerView.entries.size) { i ->
                val option = PlayerView.entries[i]
                FilterChip(
                    selected = view == option,
                    onClick = { view = option },
                    label = { Text(option.label.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardBg,
                        labelColor = TextSecondary,
                        selectedContainerColor = RedAccent,
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = view == option,
                        borderColor = Outline,
                        selectedBorderColor = RedAccent
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search survivors...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = deadFestFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        if (displayUsers.isEmpty()) {
            EmptyState(
                Icons.Default.People,
                if (users.isEmpty()) "Scanning the wasteland for survivors..." else "No survivors match \"$searchQuery\"."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(displayUsers, key = { _, u -> u.uid }) { index, player ->
                    SurvivorItem(
                        player = player,
                        isMe = player.uid == me?.uid,
                        rank = if (view == PlayerView.SURVIVORS) null else index + 1,
                        emphasizeCoins = view == PlayerView.WEALTH,
                        onClick = { selectedUid = player.uid }
                    )
                }
            }
        }
    }
}

@Composable
fun SurvivorDetailsDialog(player: User, xpCfg: XpConfig, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(player, 48)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("SURVIVOR DOSSIER", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(player.displayName?.takeIf { it.isNotBlank() } ?: "Survivor", color = TextPrimary, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(player.playerData.level, player.playerData.prestigeCount)
                        Spacer(Modifier.width(6.dp))
                        Text((player.playerData.title ?: "SURVIVOR").uppercase(), color = RedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                XpBar(player.playerData.xp, player.playerData.level, xpCfg)
                Spacer(Modifier.height(4.dp))
                SectionLabel("SURVIVAL STATS")
                DetailRow("TOTAL DEATHS", formatNumber(player.totalDeaths), RedAccent, bold = true)
                player.playerData.games.toSortedMap().forEach { (game, stats) ->
                    DetailRow(game, "${formatNumber(stats.deaths)} deaths", TextPrimary)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Outline)
                DetailRow("BANK COINS", formatNumber(player.playerData.coins), Gold, bold = true)
                DetailRow(
                    "SHIELD",
                    if (player.playerData.shield) "ACTIVE" else "NONE",
                    if (player.playerData.shield) ShieldCyan else TextMuted
                )
                DetailRow(
                    "INSURANCE",
                    if (player.playerData.insurance) "ARMED" else "NONE",
                    if (player.playerData.insurance) ShieldCyan else TextMuted
                )
                DetailRow(
                    "INSULATION",
                    if (player.playerData.insulationTier > 0) "TIER ${com.deadfest.data.model.ShopItem.roman(player.playerData.insulationTier)}" else "NONE",
                    if (player.playerData.insulationTier > 0) Gold else TextMuted
                )
                DetailRow(
                    "BANKRUPTCY",
                    if (player.playerData.bankruptcyLevel > 0) "LEVEL ${player.playerData.bankruptcyLevel}" else "SOLVENT",
                    if (player.playerData.bankruptcyLevel > 0) RedAccent else OnlineGreen
                )
                DetailRow(
                    "PENDING SPINS",
                    "${player.playerData.pendingSpins}",
                    if (player.playerData.pendingSpins > 0) RedAccent else TextMuted
                )
                DetailRow(
                    "PRESTIGE",
                    if (player.playerData.prestigeCount > 0) "★ ${player.playerData.prestigeCount}" else "NONE",
                    if (player.playerData.prestigeCount > 0) PrestigePurple else TextMuted
                )
                val online = player.playerData.onlineNow()
                DetailRow(
                    "STATUS",
                    if (online) "ONLINE" else "OFFLINE · last seen ${Presence.lastSeenLabel(player.playerData.lastSeenMs, System.currentTimeMillis())}",
                    if (online) OnlineGreen else TextMuted
                )
                if (!player.playerData.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    SectionLabel("BIO")
                    Text(player.playerData.bio!!, color = TextPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("CLOSE", fontWeight = FontWeight.Black) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.Black else FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun SurvivorItem(
    player: User,
    isMe: Boolean,
    rank: Int? = null,
    emphasizeCoins: Boolean = false,
    onClick: () -> Unit
) {
    val name = player.displayName?.takeIf { it.isNotBlank() } ?: "Survivor"
    val title = player.playerData.title?.takeIf { it.isNotBlank() } ?: "SURVIVOR"
    val themeColor = remember(player.playerData.color) { parseThemeColor(player.playerData.color) }
    val medal = when (rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(20.dp), onClick = onClick),
        color = if (medal != null) medal.copy(alpha = 0.06f) else CardBg,
        shape = RoundedCornerShape(20.dp),
        border = when {
            isMe -> BorderStroke(1.5.dp, RedAccent)
            medal != null -> BorderStroke(1.dp, medal.copy(alpha = 0.8f))
            else -> BorderStroke(1.dp, Outline)
        }
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (rank != null) {
                Text(
                    "#$rank",
                    color = medal ?: TextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(40.dp)
                )
            }
            PlayerAvatar(player, 46)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        Text("YOU", color = RedAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    if (player.playerData.shield) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Shield, contentDescription = "Shielded", tint = ShieldCyan, modifier = Modifier.size(14.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(player.playerData.level, player.playerData.prestigeCount)
                    Spacer(Modifier.width(6.dp))
                    Text(title.uppercase(), color = themeColor, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    if (player.playerData.bankruptcyLevel > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("BANKRUPT", color = RedAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (emphasizeCoins) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatNumber(player.playerData.coins), color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(4.dp))
                        CoinIcon(14)
                    }
                    Text("${formatNumber(player.totalDeaths)} DEATHS", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(formatNumber(player.totalDeaths), color = RedAccent, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("DEATHS", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
