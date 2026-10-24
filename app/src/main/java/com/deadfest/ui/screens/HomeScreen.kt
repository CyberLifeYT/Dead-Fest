package com.deadfest.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.ui.semantics.contentDescription
import com.deadfest.domain.OverloadRules
import androidx.compose.ui.res.stringResource
import com.deadfest.R
import com.deadfest.data.model.PlayerData
import com.deadfest.data.model.ShopItem
import com.deadfest.domain.DailyClock
import com.deadfest.domain.EconomyConfig
import com.deadfest.domain.onlineNow
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

/**
 * Command Center.
 *
 * Layout order is deliberate: anything time-critical or actionable that the player might be
 * forced to care about (lockdown, forced spins, sector overload, unclaimed rations) sits above the
 * fold, the big global number anchors the middle, and the profile/roster detail follows. The
 * Record Death action is pinned outside the scroll so it can never be scrolled out of reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val userState by viewModel.user.collectAsStateWithLifecycle()
    val eventLogs by viewModel.eventLogs.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val unreadAlerts = alerts.count { !it.read }
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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenHeader(
                title = "COMMAND CENTER",
                subtitle = "Sector Net // Live Feed",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlertsBell(unreadAlerts) { navController.navigate("alerts") }
                        StatusPill("$onlineCount ONLINE", OnlineGreen)
                    }
                }
            )

            Column(
                modifier = Modifier.padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                LiveFeedStrip(latestLog?.message, performanceMode, Modifier.enterOnMount(0))

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
                        message = "x${trimDouble(globalState.multiplier)} multiplier is active in this sector$left. " +
                            stringResource(R.string.overload_payout, trimDouble(globalState.multiplier.coerceAtMost(OverloadRules.MAX_MULTIPLIER))),
                        accent = Gold
                    )
                }

                if (player != null) {
                    DailyRationsCard(player, economy, onClaim = viewModel::claimDaily)
                }

                EliminationsHero(
                    total = globalState.grandTotal,
                    online = onlineCount,
                    sectors = globalState.games.size,
                    yourDeaths = totalDeaths,
                    modifier = Modifier.enterOnMount(1)
                )

                Spacer(Modifier.height(Spacing.xxs))
                SectionLabel("Survivor Profile")
                SurvivorProfileCard(userState?.displayName, player, economy, totalDeaths, onEdit = { navController.navigate("edit_profile") })

                Spacer(Modifier.height(Spacing.xxs))
                SectionLabel("Active Sectors")
            }

            Spacer(Modifier.height(Spacing.sm))
            if (globalState.games.isEmpty()) {
                Text(
                    "No sectors configured yet.",
                    color = TextMid,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = Spacing.gutter)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    contentPadding = PaddingValues(horizontal = Spacing.gutter)
                ) {
                    items(globalState.games) { sector ->
                        val locked = sector in globalState.lockedGames
                        val overloaded = overloadLive && sector == globalState.overloadedSector
                        val deaths = player?.games?.get(sector)?.deaths ?: 0L
                        SectorChip(sector, deaths, locked, overloaded)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        // Pinned action. Gradient + haptic feedback so it feels like committing to something.
        RecordDeathBar(
            enabled = canRecordDeath,
            label = when {
                globalState.lockdownActive -> "Death recording locked"
                unlockedGames.isEmpty() -> "No open sectors"
                else -> "Record Death"
            },
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                showSheet = true
            }
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Ink2,
            contentColor = TextHi,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMarkBadge(size = 42)
                    Spacer(Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            "SELECT SECTOR",
                            color = TextHi,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text("Where did you fall, survivor?", color = TextMid, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                GradientRule()
                Spacer(Modifier.height(Spacing.md))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(unlockedGames) { game ->
                        val overloaded = overloadLive && game == globalState.overloadedSector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .background(if (overloaded) Gold.copy(alpha = Tint.wash) else Ink3)
                                .border(
                                    BorderStroke(Stroke.hair, if (overloaded) Gold.copy(alpha = Tint.edge) else InkLine),
                                    RoundedCornerShape(Radius.md)
                                )
                                .pressClick(RoundedCornerShape(Radius.md)) {
                                    viewModel.addDeath(game)
                                    showSheet = false
                                }
                                .padding(horizontal = Spacing.md, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = if (overloaded) Gold else TextLow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                game.uppercase(),
                                color = TextHi,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (overloaded) StatusPill("OVERLOAD", Gold)
                            else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextLow, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                GhostAction("Cancel", { showSheet = false }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

// ==================================================================== live feed

/** One-line broadcast strip. Cross-fades between messages unless Performance Mode is on. */
@Composable
private fun LiveFeedStrip(message: String?, performanceMode: Boolean, modifier: Modifier = Modifier) {
    val text = message ?: "Awaiting wasteland transmission..."
    Row(
        modifier = modifier
            .fillMaxWidth()
            // One ticker line: "LIVE" and the message are read as a single item.
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(Radius.md))
            .background(Ink2)
            .border(BorderStroke(Stroke.hair, Ember.copy(alpha = 0.3f)), RoundedCornerShape(Radius.md))
            .padding(horizontal = Spacing.sm, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Ember)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text("LIVE", color = Ember, style = DeadFestType.microLabel)
        Spacer(Modifier.width(Spacing.xs))
        if (performanceMode) {
            Text(
                text,
                color = TextMid,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "ticker",
                modifier = Modifier.weight(1f)
            ) { msg ->
                Text(
                    msg,
                    color = TextMid,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==================================================================== hero

/** The number that defines the season. Skull watermark instead of a stock icon. */
@Composable
private fun EliminationsHero(
    total: Long,
    online: Int,
    sectors: Int,
    yourDeaths: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(
                Brush.linearGradient(
                    listOf(
                        Ember.copy(alpha = 0.22f),
                        EmberDeep.copy(alpha = 0.07f),
                        Color.Transparent
                    )
                )
            )
            .background(Ink2)
            .border(BorderStroke(Stroke.hair, Ember.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.xl))
    ) {
        Image(
            painter = painterResource(R.drawable.ic_deadfest_mark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Ember.copy(alpha = 0.07f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 16.dp)
                .size(132.dp)
        )
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Ember))
                Spacer(Modifier.width(6.dp))
                Text("Global Eliminations", color = Ember, style = DeadFestType.microLabel)
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                formatNumber(total),
                color = TextHi,
                style = DeadFestType.statHero.copy(fontSize = 44.sp, lineHeight = 46.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.sm))
            GradientRule()
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                HeroStat("Survivors", "$online")
                HeroStat("Sectors", "$sectors")
                HeroStat("Your deaths", formatNumber(yourDeaths))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    // Label and value are one data point; keep them in a single announcement.
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, color = TextLow, style = DeadFestType.microLabel, maxLines = 1)
        Text(value, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

// ==================================================================== profile

@Composable
private fun SurvivorProfileCard(
    displayName: String?,
    player: PlayerData?,
    economy: EconomyConfig,
    totalDeaths: Long,
    onEdit: () -> Unit = {}
) {
    SurfaceCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FramedAvatar(player?.avatar, player?.avatarFrame, player?.color, 52)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    displayName?.takeIf { it.isNotBlank() } ?: "Survivor",
                    color = TextHi,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(player?.level ?: 1, player?.prestigeCount ?: 0)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (player?.title?.takeIf { it.isNotBlank() } ?: "SURVIVOR").uppercase(),
                        color = Ember,
                        style = DeadFestType.microLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            CoinBadge(player?.coins ?: 0)
        }
        Spacer(Modifier.height(Spacing.sm))
        XpBar(player?.xp ?: 0, player?.level ?: 1, economy.xp)
        Spacer(Modifier.height(Spacing.sm))
        val shielded = player?.shield == true
        val lucky = player?.luckyCoin == true
        val insured = player?.insurance == true
        val tier = player?.insulationTier ?: 0
        val spins = player?.pendingSpins ?: 0
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MiniStat("Deaths", formatNumber(totalDeaths), Ember, Modifier.weight(1f))
            MiniStat("Shield", if (shielded) "ACTIVE" else "NONE", if (shielded) Plasma else TextLow, Modifier.weight(1f))
            MiniStat("Lucky", if (lucky) "ARMED" else "NONE", if (lucky) Amber else TextLow, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MiniStat("Insurance", if (insured) "ARMED" else "NONE", if (insured) Plasma else TextLow, Modifier.weight(1f))
            MiniStat("Insulation", if (tier > 0) "TIER ${ShopItem.roman(tier)}" else "NONE", if (tier > 0) Amber else TextLow, Modifier.weight(1f))
            MiniStat("Spins due", "$spins", if (spins > 0) Ember else TextLow, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            // A stat tile is one item: label and value are announced together.
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(Radius.sm))
            .background(Ink3)
            .border(BorderStroke(Stroke.hair, InkLine), RoundedCornerShape(Radius.sm))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(label, color = TextLow, style = DeadFestType.microLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ==================================================================== sectors

@Composable
private fun SectorChip(name: String, deaths: Long, locked: Boolean, overloaded: Boolean) {
    val accent = when {
        overloaded -> Gold
        locked -> TextLow
        else -> Ember
    }
    Column(
        modifier = Modifier
            // One chip is one item: name and status are read together, not as separate stops.
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(Radius.md))
            .background(if (overloaded) Gold.copy(alpha = Tint.wash) else Ink2)
            .border(BorderStroke(Stroke.hair, accent.copy(alpha = if (locked) 0.18f else Tint.edge)), RoundedCornerShape(Radius.md))
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                locked -> Icon(Icons.Default.Lock, contentDescription = null, tint = TextLow, modifier = Modifier.size(11.dp))
                // The bolt is not repeated by any text, so it keeps its description; it is what makes
                // the overload state audible instead of amber-only.
                overloaded -> Icon(Icons.Default.Bolt, contentDescription = "Overload", tint = Gold, modifier = Modifier.size(13.dp))
                else -> Box(Modifier.size(5.dp).clip(CircleShape).background(Ember))
            }
            Spacer(Modifier.width(5.dp))
            Text(
                name,
                color = if (locked) TextLow else TextHi,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (locked) "LOCKED" else "${formatNumber(deaths)} YOUR DEATHS",
            color = if (locked) TextLow else TextMid,
            style = DeadFestType.microLabel
        )
    }
}

// ==================================================================== pinned action

@Composable
private fun RecordDeathBar(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xs)
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Ember, EmberDeep))
                else Brush.horizontalGradient(listOf(Ink3, Ink3))
            )
            .border(
                BorderStroke(Stroke.hair, if (enabled) EmberGlow.copy(alpha = 0.5f) else InkLine),
                RoundedCornerShape(Radius.lg)
            )
            .pressClick(RoundedCornerShape(Radius.lg), enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (enabled) Icons.Default.Warning else Icons.Default.Lock,
                contentDescription = null,
                tint = if (enabled) Color.White else TextLow,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                label.uppercase(),
                color = if (enabled) Color.White else TextLow,
                style = DeadFestType.action
            )
        }
    }
}

// ==================================================================== daily rations

/**
 * Daily rations: big CLAIM button when available, otherwise a live countdown to the next drop.
 *
 * The previous implementation ran a 1 Hz timer that never stopped — it kept waking the CPU in the
 * background and every second recomposed this whole card. Now the clock is lifecycle-aware, stops
 * permanently the moment the drop becomes claimable, and only the status line / button read it,
 * so the avatar tile and title never recompose.
 */
@Composable
private fun DailyRationsCard(player: PlayerData, economy: EconomyConfig, onClaim: () -> Unit) {
    val amount = if (player.dailyUpgradeOwned) economy.dailyUpgraded else economy.dailyBase
    // Only tick while a drop is still pending; once it can be claimed there is nothing to wait for.
    val pending = remember(player.lastDailyClaim) {
        !DailyClock.canClaim(player.lastDailyClaim, System.currentTimeMillis())
    }
    val now = rememberLiveNow(
        intervalMs = 1_000L,
        running = pending,
        key = player.lastDailyClaim,
        stopWhen = { DailyClock.canClaim(player.lastDailyClaim, it) }
    )
    DailyRationsBody(player, amount, now, onClaim)
}

@Composable
private fun DailyRationsBody(
    player: PlayerData,
    amount: Long,
    now: State<Long>,
    onClaim: () -> Unit
) {
    val canClaim = DailyClock.canClaim(player.lastDailyClaim, now.value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Ink2)
            .then(
                if (canClaim) Modifier.background(
                    Brush.horizontalGradient(
                        listOf(OnlineGreen.copy(alpha = 0.14f), Color.Transparent)
                    )
                ) else Modifier
            )
            .border(
                BorderStroke(Stroke.hair, if (canClaim) OnlineGreen.copy(alpha = Tint.edgeStrong) else InkLine),
                RoundedCornerShape(Radius.lg)
            )
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(Icons.Default.Inventory2, tint = if (canClaim) OnlineGreen else TextLow, size = 44)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text("DAILY RATIONS", color = TextHi, style = DeadFestType.cardTitle)
            Text(
                if (canClaim) "${formatNumber(amount)} coins ready to collect"
                else "Next drop in ${DailyClock.countdown(now.value)}",
                color = TextMid,
                fontSize = 12.sp
            )
            if (player.dailyUpgradeOwned) {
                Text("RATION UPGRADE ACTIVE", color = Amber, style = DeadFestType.microLabel)
            }
        }
        if (canClaim) {
            Button(
                onClick = onClaim,
                colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen, contentColor = Color(0xFF04210F)),
                shape = RoundedCornerShape(Radius.sm),
                elevation = null,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("CLAIM", style = DeadFestType.chip)
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

/** Bell with an unread count; 48dp touch target and a spoken count for screen readers. */
@Composable
private fun AlertsBell(unread: Int, onClick: () -> Unit) {
    val cd = if (unread > 0) stringResource(R.string.cd_alerts_unread, unread) else stringResource(R.string.cd_alerts)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = cd }) {
        BadgedBox(badge = { if (unread > 0) Badge(containerColor = Ember) { Text(if (unread > 9) "9+" else "$unread") } }) {
            Icon(
                if (unread > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                contentDescription = null,
                tint = if (unread > 0) Ember else TextMid
            )
        }
    }
}
