package com.deadfest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.ShopCategory
import com.deadfest.data.model.ShopItem
import com.deadfest.data.model.PlayerData
import com.deadfest.data.model.User
import com.deadfest.domain.CasualtyTax
import com.deadfest.domain.EconomyConfig
import com.deadfest.domain.TaxTier
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListScope
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

private data class TitleOffer(val title: String, val price: Long, val description: String)

private val TITLES = listOf(
    TitleOffer("SURVIVOR", 0L, "Default badge of survival"),
    TitleOffer("UNDEAD LEGEND", 250L, "Feared across all dead zones"),
    TitleOffer("WARLORD", 500L, "Master of wasteland combat"),
    TitleOffer("SHADOW ASSASSIN", 750L, "Strikes unseen from the dark"),
    TitleOffer("APOCALYPSE EMPEROR", 1500L, "Ruler of the fallen world")
)

private fun ShopItem.icon(): ImageVector = when (this) {
    ShopItem.SHIELD -> Icons.Default.Shield
    ShopItem.CURSE -> Icons.Default.AutoFixHigh
    ShopItem.DOUBLE_CURSE -> Icons.Default.Whatshot
    ShopItem.REVIVE -> Icons.Default.MedicalServices
    ShopItem.LUCKY_COIN -> Icons.Default.MonetizationOn
    ShopItem.INSURANCE -> Icons.Default.HealthAndSafety
    ShopItem.RATION_UPGRADE -> Icons.Default.Inventory2
    ShopItem.INSULATION_1, ShopItem.INSULATION_2, ShopItem.INSULATION_3, ShopItem.INSULATION_4 -> Icons.Default.Savings
}

/** Description with live admin-configured numbers filled in. */
private fun ShopItem.liveDescription(cfg: EconomyConfig): String = when (this) {
    ShopItem.INSURANCE -> "Absorbs ${cfg.insurancePct}% of your next casualty tax. One use."
    ShopItem.RATION_UPGRADE -> "Daily rations ${formatNumber(cfg.dailyBase)} → ${formatNumber(cfg.dailyUpgraded)} coins. Permanent."
    else -> description
}

private const val TAB_ALL = 0
private const val TAB_COMBAT = 1
private const val TAB_ECONOMY = 2
private const val TAB_INSULATION = 3
private const val TAB_TITLES = 4

@Composable
fun ShopScreen(navController: NavController, viewModel: MainViewModel) {
    var selectedTab by rememberSaveableInt(0)
    val tabs = listOf("All", "Combat", "Economy", "Insulation", "Titles")
    val user by viewModel.user.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val coins = user?.playerData?.coins ?: 0L
    val economy = globalState.economy

    var confirmItem by remember { mutableStateOf<ShopItem?>(null) }
    var curseItem by remember { mutableStateOf<ShopItem?>(null) }
    var confirmTitle by remember { mutableStateOf<TitleOffer?>(null) }
    var confirmTier by remember { mutableStateOf<ShopItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(
            title = "BLACK MARKET",
            subtitle = "ILLEGAL GEAR & CURSES",
            trailing = { CoinBadge(coins) }
        )

        when {
            globalState.lockdownActive -> NoticeCard(
                icon = Icons.Default.Lock,
                title = "SYSTEM LOCKDOWN ACTIVE",
                message = "The Black Market is currently locked down by an administrator."
            )
            !globalState.marketEnabled -> NoticeCard(
                icon = Icons.Default.Storefront,
                title = "SHOP CLOSED BY ADMIN",
                message = "All transactions and illegal gear acquisitions are temporarily suspended."
            )
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs.size) { index ->
                        FilterChip(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = { Text(tabs[index].uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = CardBg,
                                labelColor = TextSecondary,
                                selectedContainerColor = RedAccent,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedTab == index,
                                borderColor = Outline,
                                selectedBorderColor = RedAccent
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == TAB_INSULATION) {
                        insulationTab(
                            data = user?.playerData,
                            cfg = economy,
                            onUpgrade = { confirmTier = it }
                        )
                    } else if (selectedTab == TAB_TITLES) {
                        items(TITLES, key = { it.title }) { offer ->
                            val owned = offer.price == 0L || user?.playerData?.ownedTitles?.contains(offer.title) == true
                            val equipped = user?.playerData?.title.equals(offer.title, ignoreCase = true)
                            TitleShopRow(
                                offer = offer,
                                isOwned = owned,
                                isEquipped = equipped,
                                affordable = coins >= offer.price,
                                onEquip = { viewModel.equipTitle(offer.title) },
                                onBuy = { confirmTitle = offer }
                            )
                        }
                    } else {
                        if (selectedTab != TAB_COMBAT && economy.scratchEnabled) {
                            item(key = "scratch") {
                                ScratchEntryCard(economy.scratchCost) { navController.navigate("scratch") }
                            }
                        }
                        // Insulation tiers live in their own tab; admin-disabled items are hidden.
                        val catalog = ShopItem.entries.filter {
                            !it.isInsulation && it.enabledIn(economy) && when (selectedTab) {
                                TAB_COMBAT -> it.category == ShopCategory.COMBAT
                                TAB_ECONOMY -> it.category == ShopCategory.ECONOMY
                                else -> true
                            }
                        }
                        items(catalog, key = { it.id }) { item ->
                            val data = user?.playerData
                            val status: String? = when (item) {
                                ShopItem.SHIELD -> if (data?.shield == true) "ACTIVE" else null
                                ShopItem.LUCKY_COIN -> if (data?.luckyCoin == true) "ARMED" else null
                                ShopItem.INSURANCE -> if (data?.insurance == true) "ARMED" else null
                                ShopItem.RATION_UPGRADE -> if (data?.dailyUpgradeOwned == true) "ACTIVE" else null
                                ShopItem.REVIVE -> if (!globalState.reviveEnabled) "DISABLED" else null
                                else -> null
                            }
                            val price = item.priceIn(economy)
                            ShopItemRow(
                                item = item,
                                description = item.liveDescription(economy),
                                price = price,
                                affordable = coins >= price,
                                status = status,
                                onBuy = { if (item.isCurse) curseItem = item else confirmItem = item }
                            )
                        }
                    }
                }
            }
        }
    }

    confirmItem?.let { item ->
        val price = item.priceIn(economy)
        val permanent = if (item.oneTime) "\n\nThis is a permanent, one-time upgrade." else ""
        ConfirmDialog(
            title = "BUY ${item.displayName}?",
            message = "${item.liveDescription(economy)}$permanent\n\nCost: ${formatNumber(price)} coins. You'll have ${formatNumber(coins - price)} left.",
            confirmText = "BUY",
            onConfirm = { viewModel.buyShopItem(item) },
            onDismiss = { confirmItem = null }
        )
    }

    confirmTitle?.let { offer ->
        ConfirmDialog(
            title = "UNLOCK ${offer.title}?",
            message = "Cost: ${formatNumber(offer.price)} coins. The title is equipped straight away.",
            confirmText = "UNLOCK",
            onConfirm = { viewModel.buyTitle(offer.title, offer.price) },
            onDismiss = { confirmTitle = null }
        )
    }

    confirmTier?.let { tierItem ->
        val price = tierItem.priceIn(economy)
        val current = user?.playerData?.insulationTier ?: 0
        val replaces = ShopItem.forInsulationTier(current)?.let {
            "\n\nThis replaces ${it.displayName} (Tier ${ShopItem.roman(current)}). Lower tiers are consumed: no refund."
        } ?: ""
        ConfirmDialog(
            title = "UPGRADE TO TIER ${ShopItem.roman(tierItem.tier)}?",
            message = "${tierItem.displayName}: -${tierItem.reductionIn(economy)}% casualty tax, permanently.$replaces\n\nCost: ${formatNumber(price)} coins. You'll have ${formatNumber(coins - price)} left.",
            confirmText = "UPGRADE",
            onConfirm = { viewModel.buyShopItem(tierItem) },
            onDismiss = { confirmTier = null }
        )
    }

    curseItem?.let { item ->
        val unlockedSectors = remember(globalState.games, globalState.lockedGames) {
            globalState.games.filter { it !in globalState.lockedGames }
        }
        CurseTargetDialog(
            item = item,
            price = item.priceIn(economy),
            survivors = users.filter { it.uid != user?.uid },
            sectors = unlockedSectors,
            onCurse = { target, sector -> viewModel.buyShopItem(item, target, sector) },
            onDismiss = { curseItem = null }
        )
    }
}

@Composable
private fun rememberSaveableInt(initial: Int): MutableIntState =
    androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(initial) }

@Composable
private fun CurseTargetDialog(
    item: ShopItem,
    price: Long,
    survivors: List<User>,
    sectors: List<String>,
    onCurse: (User, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember { mutableStateOf<User?>(null) }
    var sector by remember(sectors) { mutableStateOf(sectors.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text("CAST ${item.displayName}", color = RedAccent, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text(
                    "${item.description} · ${formatNumber(price)} coins",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sectors.isNotEmpty()) {
                    SectionLabel("SECTOR")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sectors) { s ->
                            FilterChip(
                                selected = sector == s,
                                onClick = { sector = s },
                                label = { Text(s, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CardBgRaised,
                                    labelColor = TextSecondary,
                                    selectedContainerColor = RedAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                SectionLabel("TARGET")
                if (survivors.isEmpty()) {
                    Text("No other survivors to curse.", color = TextMuted, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(survivors, key = { it.uid }) { s ->
                            val selected = target?.uid == s.uid
                            Surface(
                                modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(12.dp)) { target = s },
                                color = if (selected) RedAccent.copy(alpha = 0.18f) else CardBgRaised,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (selected) RedAccent else Color.Transparent)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.playerData.avatar?.takeIf { it.isNotBlank() } ?: "💀", fontSize = 20.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        s.displayName?.takeIf { it.isNotBlank() } ?: "Survivor",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (s.playerData.shield) {
                                        Icon(Icons.Default.Shield, contentDescription = "Shielded", tint = ShieldCyan, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (target?.playerData?.shield == true) {
                        Text(
                            "Heads up: ${target?.displayName} has a Shield. It will block this curse and break, and you still pay.",
                            color = ShieldCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { target?.let { onCurse(it, sector) }; onDismiss() },
                enabled = target != null,
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("CURSE", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun TitleShopRow(
    offer: TitleOffer,
    isOwned: Boolean,
    isEquipped: Boolean,
    affordable: Boolean,
    onEquip: () -> Unit,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isEquipped) RedAccent else Outline)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.MilitaryTech, tint = if (isEquipped) RedAccent else Gold)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(offer.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(offer.description, color = TextSecondary, fontSize = 12.sp)
                if (!isOwned) {
                    Spacer(Modifier.height(4.dp))
                    PriceTag(offer.price, affordable)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                isEquipped -> StatusPill("EQUIPPED", RedAccent)
                isOwned -> OutlinedButton(
                    onClick = onEquip,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Outline),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) { Text("EQUIP", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                else -> BuyButton(affordable, onBuy)
            }
        }
    }
}

@Composable
private fun ShopItemRow(item: ShopItem, description: String, price: Long, affordable: Boolean, status: String?, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(item.icon(), tint = if (item.category == ShopCategory.ECONOMY) Gold else RedAccent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(description, color = TextSecondary, fontSize = 12.sp)
                if (status == null || !item.oneTime) {
                    Spacer(Modifier.height(4.dp))
                    PriceTag(price, affordable)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (status != null) {
                StatusPill(
                    status,
                    when {
                        status == "DISABLED" -> TextMuted
                        item == ShopItem.SHIELD || item == ShopItem.INSURANCE -> ShieldCyan
                        else -> Gold
                    }
                )
            } else {
                BuyButton(affordable, onBuy)
            }
        }
    }
}

@Composable
private fun BuyButton(affordable: Boolean, onBuy: () -> Unit) {
    Button(
        onClick = onBuy,
        enabled = affordable,
        modifier = Modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RedAccent,
            disabledContainerColor = CardBgRaised,
            disabledContentColor = TextMuted
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text("BUY", fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

// ================================================================== insulation tab

/** Wealth Insulation: tier tracker, live effective tax rate, and the four tier cards. */
private fun LazyListScope.insulationTab(data: PlayerData?, cfg: EconomyConfig, onUpgrade: (ShopItem) -> Unit) {
    if (!cfg.insulationEnabled) {
        item { EmptyState(Icons.Default.Savings, "Wealth Insulation is switched off by an admin.") }
        return
    }
    val tier = data?.insulationTier ?: 0
    val coins = data?.coins ?: 0L
    item(key = "insulation-header") { InsulationHeader(tier, coins, data?.insurance == true, cfg) }
    items(ShopItem.insulationTiers, key = { it.id }) { item ->
        val state = when {
            item.tier < tier -> "OWNED"
            item.tier == tier -> "ACTIVE"
            item.tier == tier + 1 -> null           // next in line: buyable
            else -> "LOCKED"
        }
        val price = item.priceIn(cfg)
        val enabled = item.enabledIn(cfg)
        InsulationTierCard(
            item = item,
            reduction = item.reductionIn(cfg),
            price = price,
            state = if (!enabled && state == null) "DISABLED" else state,
            affordable = coins >= price,
            balance = coins,
            onBuy = { onUpgrade(item) }
        )
    }
}

@Composable
private fun InsulationHeader(tier: Int, coins: Long, insured: Boolean, cfg: EconomyConfig) {
    val reduction = ShopItem.insulationReduction(tier, cfg)
    val bracket = TaxTier.forBalance(coins)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WEALTH INSULATION", color = Gold, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    if (tier > 0) "TIER ${ShopItem.roman(tier)} / IV" else "NO TIER / IV",
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }
            TierTracker(tier)
            HorizontalDivider(color = Outline)
            Text("CURRENT TAX RATE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(
                CasualtyTax.effectiveRateLabel(coins, reduction).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            Text(
                "${bracket.label} bracket at ${formatNumber(coins)} coins" +
                    (if (reduction > 0) " · insulation -$reduction%" else "") +
                    (if (insured) " · insurance armed (-${cfg.insurancePct}% of what's left)" else ""),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/** I / II / III / IV segments; fills animate on upgrade (instant in Performance Mode). */
@Composable
private fun TierTracker(tier: Int) {
    val reduced = LocalPerformanceMode.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..4).forEach { t ->
            val color by animateColorAsState(
                if (t <= tier) Gold else Outline,
                animationSpec = if (reduced) snap() else tween(500, delayMillis = (t - 1) * 120),
                label = "tier$t"
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(color, RoundedCornerShape(50))
                )
                Spacer(Modifier.height(4.dp))
                Text(ShopItem.roman(t), color = if (t <= tier) Gold else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun InsulationTierCard(
    item: ShopItem,
    reduction: Int,
    price: Long,
    state: String?,
    affordable: Boolean,
    balance: Long,
    onBuy: () -> Unit
) {
    val accent = when (state) {
        "ACTIVE" -> Gold
        "OWNED" -> TextSecondary
        null -> RedAccent
        else -> Outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (state == "ACTIVE") Gold.copy(alpha = 0.07f) else CardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (state == "LOCKED" || state == "DISABLED") Outline else accent)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(Gold.copy(alpha = if (state == "LOCKED") 0.05f else 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(ShopItem.roman(item.tier), color = if (state == "LOCKED") TextMuted else Gold, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, color = if (state == "LOCKED") TextMuted else TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("-$reduction% casualty tax", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "At your balance: ${CasualtyTax.effectiveRateLabel(balance, reduction)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (state == null || state == "LOCKED" || state == "DISABLED") {
                    Spacer(Modifier.height(4.dp))
                    PriceTag(price, affordable && state == null)
                }
            }
            Spacer(Modifier.width(8.dp))
            when (state) {
                null -> BuyButton(affordable, onBuy)
                "ACTIVE" -> StatusPill("ACTIVE", Gold)
                "OWNED" -> StatusPill("OWNED", TextSecondary)
                "DISABLED" -> StatusPill("DISABLED", TextMuted)
                else -> Icon(Icons.Default.Lock, contentDescription = "Locked", tint = TextMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ScratchEntryCard(cost: Long, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(20.dp), onClick = onClick),
        color = Gold.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.ConfirmationNumber, tint = Gold)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("SCRATCH CARDS", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Instant prizes: coins, Shields, Lucky Coins", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                PriceTag(cost)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Gold)
        }
    }
}
