package com.deadfest.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.PlayerData
import com.deadfest.data.model.ShopItem
import com.deadfest.domain.DailyClock
import com.deadfest.domain.EconomyConfig
import com.deadfest.domain.onlineNow
import com.deadfest.ui.components.*
import kotlinx.coroutines.delay
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val userState by viewModel.user.collectAsStateWithLifecycle()
    val eventLogs by viewModel.eventLogs.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val onlineCount = remember(users) { users.count { it.playerData.onlineNow() } }
    val wheelPref by viewModel.prefs.wheelAlerts.collectAsStateWithLifecycle()
    val marketPref by viewModel.prefs.marketAlerts.collectAsStateWithLifecycle()
    val globalPref by viewModel.prefs.wastelandAlerts.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val haptics = LocalHapticFeedback.current
    var showSheet by remember { mutableStateOf(false) }

    val latestLog = remember(eventLogs, wheelPref, marketPref, globalPref) {
        eventLogs.firstOrNull { log ->
            when (log.category) {
                "WHEEL_SPIN" -> wheelPref
                "BLACK_MARKET" -> marketPref
                "WASTELAND_ALERT", "ADMIN_BROADCAST", "FIRMWARE_UPDATE" -> globalPref
                else -> true
            }
        }
    }
    val unlockedGames = remember(globalState.games, globalState.lockedGames) {
        globalState.games.filter { it !in globalState.lockedGames }
    }
    val player = userState?.playerData
    val totalDeaths = remember(player?.games) { player?.games?.values?.sumOf { it.deaths } ?: 0L }
    val canRecordDeath = !globalState.lockdownActive && unlockedGames.isNotEmpty()
    val economy = globalState.economy
    val overloadLive = globalState.overloadActive()

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // Scrollable content so nothing gets cut off on small screens; the
        // Record Death button stays pinned at the bottom.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenHeader(
                title = "COMMAND CENTER",
                subtitle = "SECURE ACCESS // LIVE FEED",
                trailing = { StatusPill("$onlineCount ONLINE", OnlineGreen) }
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live ticker
                if (latestLog != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.35f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(RedAccent, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            AnimatedContent(
                                targetState = latestLog.message ?: "Awaiting wasteland transmission...",
                                transitionSpec = {
                                    val ms = if (performanceMode) 0 else 250
                                    fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                                },
                                label = "ticker",
                                modifier = Modifier.weight(1f)
                            ) { message ->
                                Text(message, color = Color(0xFFD6D6D6), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (globalState.lockdownActive) {
                    AlertBanner(
                        icon = Icons.Default.Lock,
                        title = "DEAD-FEST IS IN LOCKDOWN",
                        message = "Core protocol actions are restricted by an administrator."
                    )
                }

                // Bankruptcy / Forced Spin Protocol: impossible to miss, tap to go spin.
                if (player != null) {
                    PenaltyStatusCard(player.bankruptcyLevel, player.pendingSpins) {
                        navController.navigate("wheel") { launchSingleTop = true }
                    }
                }

                if (overloadLive) {
                    val left = if (globalState.overloadEndsAt > 0) " · ${remainingLabel(globalState.overloadEndsAt)} left" else ""
                    AlertBanner(
                        icon = Icons.Default.Bolt,
                        title = "SECTOR OVERLOAD: ${globalState.overloadedSector.uppercase()}",
                        message = "x${trimDouble(globalState.multiplier)} multiplier is active in this sector$left.",
                        accent = Gold
                    )
                }

                if (player != null) {
                    DailyRationsCard(player, economy, onClaim = viewModel::claimDaily)
                }

                // Global eliminations hero card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(RedAccent.copy(alpha = 0.16f), Color.Transparent)))
                            .padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("GLOBAL ELIMINATIONS", color = RedAccent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(formatNumber(globalState.grandTotal), color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Black)
                                Text(
                                    "$onlineCount SURVIVORS ONLINE",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconTile(Icons.Default.BarChart, size = 56)
                        }
                    }
                }

                // Survivor profile
                SectionLabel("SURVIVOR PROFILE")
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FramedAvatar(player?.avatar, player?.avatarFrame, player?.color, 54)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    userState?.displayName?.takeIf { it.isNotBlank() } ?: "Survivor",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LevelBadge(player?.level ?: 1, player?.prestigeCount ?: 0)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        (player?.title?.takeIf { it.isNotBlank() } ?: "SURVIVOR").uppercase(),
                                        color = RedAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            CoinBadge(player?.coins ?: 0)
                        }
                        Spacer(Modifier.height(12.dp))
                        XpBar(player?.xp ?: 0, player?.level ?: 1, economy.xp)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStat("DEATHS", formatNumber(totalDeaths), RedAccent, Modifier.weight(1f))
                            MiniStat(
                                "SHIELD",
                                if (player?.shield == true) "ACTIVE" else "NONE",
                                if (player?.shield == true) ShieldCyan else TextMuted,
                                Modifier.weight(1f)
                            )
                            MiniStat(
                                "LUCKY",
                                if (player?.luckyCoin == true) "ARMED" else "NONE",
                                if (player?.luckyCoin == true) Gold else TextMuted,
                                Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStat(
                                "INSURANCE",
                                if (player?.insurance == true) "ARMED" else "NONE",
                                if (player?.insurance == true) ShieldCyan else TextMuted,
                                Modifier.weight(1f)
                            )
                            val tier = player?.insulationTier ?: 0
                            MiniStat(
                                "INSULATION",
                                if (tier > 0) "TIER ${ShopItem.roman(tier)}" else "NONE",
                                if (tier > 0) Gold else TextMuted,
                                Modifier.weight(1f)
                            )
                            MiniStat(
                                "SPINS DUE",
                                "${player?.pendingSpins ?: 0}",
                                if ((player?.pendingSpins ?: 0) > 0) RedAccent else TextMuted,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Sectors
                SectionLabel("ACTIVE SECTORS")
            }

            Spacer(Modifier.height(10.dp))
            if (globalState.games.isEmpty()) {
                Text("No sectors configured yet.", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(globalState.games) { sector ->
                        val locked = sector in globalState.lockedGames
                        val overloaded = overloadLive && sector == globalState.overloadedSector
                        val deaths = player?.games?.get(sector)?.deaths ?: 0L
                        SectorChip(sector, deaths, locked, overloaded)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Pinned Record Death button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pressClick(RoundedCornerShape(16.dp), enabled = canRecordDeath) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSheet = true
                },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (canRecordDeath) RedAccent else Outline),
            color = if (canRecordDeath) RedAccent.copy(alpha = 0.14f) else CardBg
        ) {
            Row(
                modifier = Modifier.padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (canRecordDeath) Icons.Default.Warning else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (canRecordDeath) RedAccent else TextMuted
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        globalState.lockdownActive -> "DEATH RECORDING LOCKED"
                        unlockedGames.isEmpty() -> "NO OPEN SECTORS"
                        else -> "RECORD DEATH"
                    },
                    color = if (canRecordDeath) TextPrimary else TextMuted,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = CardBg,
            contentColor = TextPrimary,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                Text("SELECT SECTOR", color = RedAccent, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                Text("Where did you fall, survivor?", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(unlockedGames) { game ->
                        val overloaded = overloadLive && game == globalState.overloadedSector
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressClick(RoundedCornerShape(12.dp)) {
                                    viewModel.addDeath(game)
                                    showSheet = false
                                },
                            color = CardBgRaised,
                            shape = RoundedCornerShape(12.dp),
                            border = if (overloaded) BorderStroke(1.dp, Gold) else null
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(game.uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (overloaded) StatusPill("OVERLOAD", Gold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(CardBgRaised, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun SectorChip(name: String, deaths: Long, locked: Boolean, overloaded: Boolean) {
    val border = when {
        overloaded -> Gold
        locked -> Outline
        else -> RedAccent.copy(alpha = 0.35f)
    }
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (locked) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                } else if (overloaded) {
                    Icon(Icons.Default.Bolt, contentDescription = "Overload", tint = Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(name, color = if (locked) TextMuted else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                if (locked) "LOCKED" else "${formatNumber(deaths)} YOUR DEATHS",
                color = if (locked) TextMuted else TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Daily rations: big CLAIM button when available, otherwise a live countdown to midnight.
 * The ticking lives in its own composable so only this card recomposes each second.
 */
@Composable
private fun DailyRationsCard(player: PlayerData, economy: EconomyConfig, onClaim: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val canClaim = DailyClock.canClaim(player.lastDailyClaim, now)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val amount = if (player.dailyUpgradeOwned) economy.dailyUpgraded else economy.dailyBase
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (canClaim) OnlineGreen.copy(alpha = 0.08f) else CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (canClaim) OnlineGreen.copy(alpha = 0.7f) else Outline)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Inventory2, tint = if (canClaim) OnlineGreen else TextMuted, size = 46)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("DAILY RATIONS", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(
                    if (canClaim) "${formatNumber(amount)} coins ready to collect"
                    else "Next drop in ${DailyClock.countdown(now)}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (player.dailyUpgradeOwned) {
                    Text("RATION UPGRADE ACTIVE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
            if (canClaim) {
                Button(
                    onClick = onClaim,
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) { Text("CLAIM", fontWeight = FontWeight.Black) }
            }
        }
    }
}

internal fun remainingLabel(endsAt: Long, now: Long = System.currentTimeMillis()): String {
    val mins = ((endsAt - now) / 60_000).coerceAtLeast(0)
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

internal fun trimDouble(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
