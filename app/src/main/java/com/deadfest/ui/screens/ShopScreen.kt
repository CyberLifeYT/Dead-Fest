package com.deadfest.ui.screens

import com.deadfest.util.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.PlayerData
import com.deadfest.data.model.ShopCategory
import com.deadfest.data.model.ShopItem
import com.deadfest.data.model.User
import com.deadfest.domain.CasualtyTax
import com.deadfest.domain.EconomyConfig
import com.deadfest.domain.TaxTier
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import com.deadfest.R
import com.deadfest.domain.FlashSale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

private data class TitleOffer(val title: String, val price: Long, val description: String)

// A getter so the descriptions follow the current language; the titles are stored values.
private val TITLES: List<TitleOffer>
    get() = listOf(
    TitleOffer("SURVIVOR", 0L, tr(R.string.shop_title_desc_survivor)),
    TitleOffer("UNDEAD LEGEND", 250L, tr(R.string.shop_title_desc_undead_legend)),
    TitleOffer("WARLORD", 500L, tr(R.string.shop_title_desc_warlord)),
    TitleOffer("SHADOW ASSASSIN", 750L, tr(R.string.shop_title_desc_shadow_assassin)),
    TitleOffer("APOCALYPSE EMPEROR", 1500L, tr(R.string.shop_title_desc_apocalypse_emperor))
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

/**
 * Each item owns a hue so the market is scannable by colour before you read a word:
 * cyan = defensive, purple/red = curses, amber = economy, green = a second chance.
 */
private fun ShopItem.accent(): Color = when (this) {
    ShopItem.SHIELD -> Plasma
    ShopItem.INSURANCE -> Plasma
    ShopItem.REVIVE -> Toxic
    ShopItem.LUCKY_COIN -> Amber
    ShopItem.RATION_UPGRADE -> Amber
    ShopItem.CURSE -> Prestige
    ShopItem.DOUBLE_CURSE -> Ember
    ShopItem.INSULATION_1, ShopItem.INSULATION_2, ShopItem.INSULATION_3, ShopItem.INSULATION_4 -> Amber
}

/** Description with live admin-configured numbers filled in. */
private fun ShopItem.liveDescription(cfg: EconomyConfig): String = when (this) {
    ShopItem.INSURANCE -> tr(R.string.shop_absorbs_1_of_your_next_casualty, cfg.insurancePct)
    ShopItem.RATION_UPGRADE -> tr(R.string.shop_daily_rations_1_2_coins_permanent, formatNumber(cfg.dailyBase), formatNumber(cfg.dailyUpgraded))
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
    val tabs = listOf("All", tr(R.string.shop_combat), tr(R.string.admineconomy_economy), tr(R.string.admin_insulation), tr(R.string.shop_titles))
    val user by viewModel.user.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val coins = user?.playerData?.coins ?: 0L
    // Flash sale: flips off by itself when the timer runs out, so prices snap back without
    // recomposing the whole shop every second (only the banner ticks).
    val sale = globalState.economy.flashSale
    var saleLive by remember(sale) { mutableStateOf(sale.isLive()) }
    LaunchedEffect(sale) {
        if (sale.isLive() && sale.endsAt > 0L) {
            kotlinx.coroutines.delay((sale.endsAt - System.currentTimeMillis()).coerceAtLeast(0L))
            saleLive = false
        }
    }
    val economy = if (saleLive) globalState.economy else globalState.economy.copy(flashSale = FlashSale())

    var confirmItem by remember { mutableStateOf<ShopItem?>(null) }
    var curseItem by remember { mutableStateOf<ShopItem?>(null) }
    var confirmTitle by remember { mutableStateOf<TitleOffer?>(null) }
    var confirmTier by remember { mutableStateOf<ShopItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = tr(R.string.shop_black_market),
            subtitle = tr(R.string.shop_illegal_gear_curses),
            trailing = { CoinBadge(coins) }
        )

        when {
            globalState.lockdownActive -> NoticeCard(
                icon = Icons.Default.Lock,
                title = tr(R.string.scratch_system_lockdown_active),
                message = tr(R.string.shop_the_black_market_is_currently_locked)
            )
            !globalState.marketEnabled -> NoticeCard(
                icon = Icons.Default.Storefront,
                title = tr(R.string.shop_shop_closed_by_admin),
                message = tr(R.string.shop_all_transactions_and_illegal_gear_acquisitions)
            )
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(tabs.size) { index ->
                        // Filter pills are ~28dp tall: reserve the 48dp touch target and announce the
                        // active tab, which is otherwise signalled by tint alone.
                        PillChip(
                            text = tabs[index],
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { selected = selectedTab == index }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = Spacing.xxs,
                        bottom = Spacing.scrollBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (saleLive) {
                        item(key = "flash") { FlashSaleBanner(sale) }
                    }
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
                            val titlePrice = economy.flashSale.apply(offer.price)
                            TitleShopRow(
                                offer = offer,
                                price = titlePrice,
                                isOwned = owned,
                                isEquipped = equipped,
                                affordable = coins >= titlePrice,
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
                                ShopItem.SHIELD -> if (data?.shield == true) tr(R.string.home_active) else null
                                ShopItem.LUCKY_COIN -> if (data?.luckyCoin == true) tr(R.string.home_armed) else null
                                ShopItem.INSURANCE -> if (data?.insurance == true) tr(R.string.home_armed) else null
                                ShopItem.RATION_UPGRADE -> if (data?.dailyUpgradeOwned == true) tr(R.string.home_active) else null
                                ShopItem.REVIVE -> if (!globalState.reviveEnabled) tr(R.string.shop_disabled) else null
                                else -> null
                            }
                            val price = item.priceIn(economy)
                            ShopItemRow(
                                item = item,
                                description = item.liveDescription(economy),
                                price = price,
                                basePrice = item.basePriceIn(economy),
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
        val permanent = if (item.oneTime) tr(R.string.shop_this_is_a_permanent_one_time) else ""
        ConfirmDialog(
            title = tr(R.string.shop_buy_1, item.displayName),
            message = tr(R.string.shop_1_2_cost_3_coins_you, item.liveDescription(economy), permanent, formatNumber(price), formatNumber(coins - price)),
            confirmText = tr(R.string.shop_buy),
            onConfirm = { viewModel.buyShopItem(item) },
            onDismiss = { confirmItem = null }
        )
    }

    confirmTitle?.let { offer ->
        ConfirmDialog(
            title = tr(R.string.shop_unlock_1, offer.title),
            message = tr(R.string.shop_cost_1_coins_the_title_is, formatNumber(economy.flashSale.apply(offer.price))),
            confirmText = tr(R.string.shop_unlock),
            onConfirm = { viewModel.buyTitle(offer.title, economy.flashSale.apply(offer.price)) },
            onDismiss = { confirmTitle = null }
        )
    }

    confirmTier?.let { tierItem ->
        val price = tierItem.priceIn(economy)
        val current = user?.playerData?.insulationTier ?: 0
        val replaces = ShopItem.forInsulationTier(current)?.let {
            tr(R.string.shop_this_replaces_1_tier_2_lower, it.displayName, ShopItem.roman(current))
        } ?: ""
        ConfirmDialog(
            title = tr(R.string.shop_upgrade_to_tier_1, ShopItem.roman(tierItem.tier)),
            message = tr(R.string.shop_1_2_casualty_tax_permanently_3, tierItem.displayName, tierItem.reductionIn(economy), replaces, formatNumber(price), formatNumber(coins - price)),
            confirmText = tr(R.string.shop_upgrade),
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

/**
 * Curse targeting.
 *
 * Two-step choice (who, then where) with a live warning when the target is shielded — that warning
 * exists because casting at a shielded survivor still costs coins, so it needs to be unmissable.
 */
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
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = {
            Column {
                Text(tr(R.string.shop_cast_1, item.displayName), color = Ember, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    tr(R.string.shop_1_2_coins, item.description, formatNumber(price)),
                    color = TextMid,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (sectors.isNotEmpty()) {
                    SectionLabel(tr(R.string.shop_sector))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        items(sectors) { s ->
                            PillChip(
                                text = s,
                                selected = sector == s,
                                onClick = { sector = s },
                                // Same 48dp touch target and spoken selection as the shop's filter pills.
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .semantics { selected = sector == s },
                                accent = Prestige
                            )
                        }
                    }
                }
                SectionLabel(tr(R.string.shop_target))
                if (survivors.isEmpty()) {
                    Text(tr(R.string.shop_no_other_survivors_to_curse), color = TextMid, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(survivors, key = { it.uid }) { s ->
                            val isSelected = target?.uid == s.uid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.md))
                                    .background(if (isSelected) Prestige.copy(alpha = Tint.fill) else Ink3)
                                    .border(
                                        BorderStroke(Stroke.hair, if (isSelected) Prestige.copy(alpha = Tint.edgeStrong) else InkLine),
                                        RoundedCornerShape(Radius.md)
                                    )
                                    // The chosen curse target is shown by the purple tint alone.
                                    .semantics { selected = isSelected }
                                    .pressClick(RoundedCornerShape(Radius.md)) { target = s },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    s.playerData.avatar?.takeIf { it.isNotBlank() } ?: "💀",
                                    fontSize = 20.sp,
                                    modifier = Modifier.padding(start = Spacing.sm, top = 10.dp, bottom = 10.dp)
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(
                                    s.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.admineconomy_survivor),
                                    color = TextHi,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (s.playerData.shield) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = tr(R.string.players_shielded),
                                        tint = Plasma,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(Modifier.width(Spacing.sm))
                            }
                        }
                    }
                    if (target?.playerData?.shield == true) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(Plasma.copy(alpha = Tint.wash))
                                .border(BorderStroke(Stroke.hair, Plasma.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.sm))
                                .padding(Spacing.xs),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = Plasma, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tr(R.string.shop_1_has_a_shield_it_will, target?.displayName),
                                color = Plasma,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { target?.let { onCurse(it, sector) }; onDismiss() },
                enabled = target != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Prestige,
                    contentColor = Color(0xFF1A0B33),
                    disabledContainerColor = Ink4,
                    disabledContentColor = TextLow
                ),
                shape = RoundedCornerShape(Radius.md),
                elevation = null
            ) { Text(tr(R.string.shop_curse), style = DeadFestType.chip) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(R.string.common_cancel), color = TextMid, style = DeadFestType.chip) }
        }
    )
}

@Composable
private fun TitleShopRow(
    offer: TitleOffer,
    price: Long,
    isOwned: Boolean,
    isEquipped: Boolean,
    affordable: Boolean,
    onEquip: () -> Unit,
    onBuy: () -> Unit
) {
    val accent = if (isEquipped) Ember else Amber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Ink2)
            .then(
                if (isEquipped) Modifier.background(
                    Brush.horizontalGradient(listOf(Ember.copy(alpha = 0.12f), Color.Transparent))
                ) else Modifier
            )
            .border(
                BorderStroke(Stroke.hair, accent.copy(alpha = if (isEquipped) Tint.edgeStrong else Tint.edge)),
                RoundedCornerShape(Radius.lg)
            )
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemEmblem(Icons.Default.MilitaryTech, accent = accent, size = 52.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(offer.title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(offer.description, color = TextMid, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!isOwned) {
                Spacer(Modifier.height(Spacing.xxs))
                SalePriceTag(price, offer.price, affordable)
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        when {
            isEquipped -> StatusPill(tr(R.string.progression_equipped), Ember)
            isOwned -> GhostAction(tr(R.string.progression_equip), onEquip, accent = TextHi)
            else -> BuyButton(affordable, onBuy)
        }
    }
}

@Composable
private fun ShopItemRow(
    item: ShopItem,
    description: String,
    price: Long,
    basePrice: Long,
    affordable: Boolean,
    status: String?,
    onBuy: () -> Unit
) {
    val accent = item.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Ink2)
            .background(
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.09f), Color.Transparent))
            )
            .border(BorderStroke(Stroke.hair, accent.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.lg))
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemEmblem(item.icon(), accent = accent, size = 54.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                item.displayName,
                color = TextHi,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(description, color = TextMid, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (status == null || !item.oneTime) {
                Spacer(Modifier.height(Spacing.xxs))
                SalePriceTag(price, basePrice, affordable)
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        if (status != null) {
            StatusPill(
                status,
                when {
                    status == tr(R.string.shop_disabled) -> TextLow
                    item == ShopItem.SHIELD || item == ShopItem.INSURANCE -> Plasma
                    else -> Amber
                }
            )
        } else {
            BuyButton(affordable, onBuy, accent)
        }
    }
}

@Composable
private fun BuyButton(affordable: Boolean, onBuy: () -> Unit, accent: Color = Ember) {
    Button(
        onClick = onBuy,
        enabled = affordable,
        // 40dp is the visual height; the interactive touch target stays at the 48dp minimum.
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White,
            disabledContainerColor = Ink4,
            disabledContentColor = TextLow
        ),
        shape = RoundedCornerShape(Radius.sm),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text(tr(R.string.shop_buy), style = DeadFestType.chip)
    }
}

// ================================================================== insulation tab

/** Wealth Insulation: tier tracker, live effective tax rate, and the four tier cards. */
private fun LazyListScope.insulationTab(data: PlayerData?, cfg: EconomyConfig, onUpgrade: (ShopItem) -> Unit) {
    if (!cfg.insulationEnabled) {
        item {
            EmptyStateCard(
                icon = Icons.Default.Savings,
                title = tr(R.string.shop_insulation_disabled),
                message = tr(R.string.shop_wealth_insulation_is_switched_off_by),
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
        return
    }
    val tier = data?.insulationTier ?: 0
    val coins = data?.coins ?: 0L
    item(key = "insulation-header") { InsulationHeader(tier, coins, data?.insurance == true, cfg) }
    items(ShopItem.insulationTiers, key = { it.id }) { item ->
        val state = when {
            item.tier < tier -> tr(R.string.shop_owned)
            item.tier == tier -> tr(R.string.home_active)
            item.tier == tier + 1 -> null           // next in line: buyable
            else -> tr(R.string.home_locked)
        }
        val price = item.priceIn(cfg)
        val enabled = item.enabledIn(cfg)
        InsulationTierCard(
            item = item,
            reduction = item.reductionIn(cfg),
            price = price,
            state = if (!enabled && state == null) tr(R.string.shop_disabled) else state,
            affordable = coins >= price,
            balance = coins,
            onBuy = { onUpgrade(item) }
        )
    }
}

/** The single most important block on this tab: what you have, and what it saves you right now. */
@Composable
private fun InsulationHeader(tier: Int, coins: Long, insured: Boolean, cfg: EconomyConfig) {
    val reduction = ShopItem.insulationReduction(tier, cfg)
    val bracket = TaxTier.forBalance(coins)
    HeroPanel(
        eyebrow = tr(R.string.shop_wealth_insulation),
        title = if (tier > 0) tr(R.string.shop_tier_1_of_iv, ShopItem.roman(tier)) else tr(R.string.shop_no_tier_yet),
        icon = Icons.Default.Savings,
        accent = Amber
    ) {
        TierTracker(tier)
        Spacer(Modifier.height(Spacing.md))
        // Plain-Text group label (the other group headers on this screen use SectionLabel).
        Text(
            tr(R.string.shop_current_tax_rate),
            color = TextLow,
            style = DeadFestType.microLabel,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            CasualtyTax.effectiveRateLabel(coins, reduction).uppercase(),
            color = TextHi,
            style = DeadFestType.statHero,
            maxLines = 1
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            tr(R.string.shop_1_bracket_at_2_coins, bracket.label, formatNumber(coins)) +
                (if (reduction > 0) tr(R.string.shop_insulation_1, reduction) else "") +
                (if (insured) tr(R.string.shop_insurance_armed_1_of_what_s, cfg.insurancePct) else ""),
            color = TextMid,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

/** I / II / III / IV segments; fills animate on upgrade (instant in Performance Mode). */
@Composable
private fun TierTracker(tier: Int) {
    val reduced = LocalPerformanceMode.current
    // Reached tiers are shown by an amber fill and nothing else; the tracker says the progress in
    // words so it isn't colour-only, and merging keeps it one announcement instead of four numerals.
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription =
                if (tier > 0) tr(R.string.shop_wealth_insulation_tier_1_of_4, tier) else tr(R.string.shop_wealth_insulation_no_tiers_unlocked_yet)
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        (1..4).forEach { t ->
            val color by animateColorAsState(
                if (t <= tier) Amber else Ink4,
                animationSpec = if (reduced) snap() else tween(500, delayMillis = (t - 1) * 120),
                label = "tier$t"
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(color)
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    ShopItem.roman(t),
                    color = if (t <= tier) Amber else TextLow,
                    style = DeadFestType.microLabel
                )
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
        tr(R.string.home_active) -> Amber
        tr(R.string.shop_owned) -> TextMid
        null -> Ember
        else -> TextLow
    }
    val dim = state == tr(R.string.home_locked) || state == tr(R.string.shop_disabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Ink2)
            .then(
                if (state == tr(R.string.home_active)) Modifier.background(
                    Brush.horizontalGradient(listOf(Amber.copy(alpha = 0.12f), Color.Transparent))
                ) else Modifier
            )
            .border(
                BorderStroke(Stroke.hair, if (dim) InkLine else accent.copy(alpha = Tint.edge)),
                RoundedCornerShape(Radius.lg)
            )
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The tier numeral is the artwork here — no glyph reads "level" better than the numeral.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(
                    Brush.verticalGradient(
                        listOf(Amber.copy(alpha = if (dim) 0.06f else 0.30f), Color.Transparent)
                    )
                )
                .background(Ink3)
                .border(BorderStroke(Stroke.hair, if (dim) InkLine else Amber.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ShopItem.roman(item.tier),
                color = if (dim) TextLow else Amber,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                item.displayName,
                color = if (dim) TextLow else TextHi,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(tr(R.string.shop_1_casualty_tax, reduction), color = if (dim) TextLow else Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                tr(R.string.shop_at_your_balance_1, CasualtyTax.effectiveRateLabel(balance, reduction)),
                color = TextMid,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state == null || dim) {
                Spacer(Modifier.height(Spacing.xxs))
                PriceTag(price, affordable && state == null)
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        when (state) {
            null -> BuyButton(affordable, onBuy, accent = Amber)
            tr(R.string.home_active) -> StatusPill(tr(R.string.home_active), Amber)
            tr(R.string.shop_owned) -> StatusPill(tr(R.string.shop_owned), TextMid)
            tr(R.string.shop_disabled) -> StatusPill(tr(R.string.shop_disabled), TextLow)
            else -> Icon(Icons.Default.Lock, contentDescription = tr(R.string.admin_locked), tint = TextLow, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ScratchEntryCard(cost: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                Brush.horizontalGradient(listOf(Toxic.copy(alpha = 0.12f), Color.Transparent))
            )
            .background(Ink2)
            .border(BorderStroke(Stroke.hair, Toxic.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.lg))
            .pressClick(RoundedCornerShape(Radius.lg), onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemEmblem(Icons.Default.ConfirmationNumber, accent = Toxic, size = 54.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(tr(R.string.more_scratch_cards), color = TextHi, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(tr(R.string.shop_instant_prizes_coins_shields_lucky_coins), color = TextMid, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(Spacing.xxs))
            PriceTag(cost)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Toxic, modifier = Modifier.size(20.dp))
    }
}

// ================================================================== flash sale (v2.1.0)

/** Price with the pre-sale price struck through next to it while a flash sale runs. */
@Composable
private fun SalePriceTag(price: Long, basePrice: Long, affordable: Boolean) {
    if (basePrice <= price) {
        PriceTag(price, affordable)
        return
    }
    val pct = (100 - price * 100 / basePrice).toInt()
    val cd = stringResource(R.string.cd_flash_price, formatNumber(price), formatNumber(basePrice))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = cd }
    ) {
        PriceTag(price, affordable)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            formatNumber(basePrice),
            color = TextLow,
            fontSize = 12.sp,
            style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
        )
        Spacer(Modifier.width(Spacing.xs))
        StatusPill(stringResource(R.string.flash_off, pct), Toxic)
    }
}

/** Top-of-shop banner with a live countdown. Only this composable ticks every second. */
@Composable
private fun FlashSaleBanner(sale: FlashSale) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sale) {
        while (sale.endsAt > 0L) {
            kotlinx.coroutines.delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    SurfaceCard(accent = Toxic) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Bolt, tint = Toxic, size = 44)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.flash_title), color = Toxic, style = DeadFestType.microLabel)
                Text(stringResource(R.string.flash_banner, sale.pct), color = TextHi, style = DeadFestType.cardTitle)
                if (sale.endsAt <= 0L) {
                    Text(stringResource(R.string.flash_until_ended), color = TextMid, style = DeadFestType.body)
                }
            }
        }
        if (sale.endsAt > 0L) {
            Spacer(Modifier.height(Spacing.xs))
            CountdownTiles(remainingMs = (sale.endsAt - now).coerceAtLeast(0L))
        }
    }
}
