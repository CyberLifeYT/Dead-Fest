package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deadfest.data.model.EventLog
import com.deadfest.data.model.GlobalState
import com.deadfest.data.model.ScratchPrize
import com.deadfest.data.model.ShopItem
import com.deadfest.data.model.User
import com.deadfest.domain.TaxTier
import com.deadfest.domain.Weighted
import com.deadfest.domain.XpConfig
import com.deadfest.domain.onlineNow
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

// ================================================================== live event dashboard

/**
 * Real-time event health. Everything comes from existing snapshot listeners (users, global
 * state) plus one windowed eventLog listener that only runs while this tab is open.
 */
@Composable
internal fun LiveEventDashboard(viewModel: MainViewModel, onPlayerClick: (String) -> Unit) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val recent by viewModel.recentEvents.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()

    // Time-windowed stats ("online now", "deaths in the last 10 min") re-read the clock every
    // 10s. rememberLiveNow is lifecycle-aware and schedules nothing while the tab is away,
    // unlike the hand-rolled loop this dashboard used to run.
    val now = rememberLiveNow(intervalMs = 10_000L)

    val pendingPlayers = remember(users) { users.filter { it.playerData.pendingSpins > 0 }.sortedByDescending { it.playerData.pendingSpins } }
    val pendingTotal = pendingPlayers.sumOf { it.playerData.pendingSpins }
    val tiers = remember(users) { TaxTier.entries.associateWith { t -> users.count { TaxTier.forBalance(it.playerData.coins) == t } } }
    val bankrupt1 = users.count { it.playerData.bankruptcyLevel == 1 }
    val bankrupt2 = users.count { it.playerData.bankruptcyLevel >= 2 }
    val insulated = remember(users) { users.filter { it.playerData.insulationTier > 0 }.sortedByDescending { it.playerData.insulationTier } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().enterOnMount(0),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.scrollBottom)
    ) {
        item { OnlineStatTile(users, now) }
        item { DeathRateStatTile(recent, now) }
        item {
            StatPanel(tr(R.string.admineconomy_pending_spins), formatNumber(pendingTotal), tr(R.string.admineconomy_1_survivor_s_owe_spins, pendingPlayers.size), Amber)
        }
        item {
            SurfaceCard {
                SectionLabel(tr(R.string.admineconomy_bankruptcy), accent = Ember)
                Spacer(Modifier.height(Spacing.xxs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$bankrupt1", color = TextHi, style = DeadFestType.statHero)
                    Text(" L1", color = TextMid, style = DeadFestType.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Spacing.xxs))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("$bankrupt2", color = Ember, style = DeadFestType.statHero)
                    Text(" L2", color = TextMid, style = DeadFestType.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Spacing.xxs))
                }
                Text(tr(R.string.admineconomy_survivors_in_bankruptcy), color = TextLow, style = DeadFestType.body)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { OverloadPanel(globalState, now) }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SurfaceCard {
                SectionLabel(tr(R.string.admineconomy_wealth_distribution), accent = Amber)
                Spacer(Modifier.height(Spacing.xs))
                WealthChart(tiers)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SurfaceCard {
                SectionLabel(tr(R.string.admineconomy_pending_spins_by_survivor))
                Spacer(Modifier.height(Spacing.xs))
                if (pendingPlayers.isEmpty()) {
                    EmptyState(Icons.Default.Casino, tr(R.string.admineconomy_nobody_owes_the_wheel_right_now))
                } else {
                    pendingPlayers.take(12).forEach { u -> PlayerLine(u, "${u.playerData.pendingSpins} spins", Ember) { onPlayerClick(u.uid) } }
                    if (pendingPlayers.size > 12) {
                        Text(tr(R.string.admineconomy_1_more, pendingPlayers.size - 12), color = TextLow, style = DeadFestType.body)
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SurfaceCard {
                SectionLabel(tr(R.string.admineconomy_wealth_insulation_ownership), accent = Plasma)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    (1..4).forEach { t ->
                        val count = users.count { it.playerData.insulationTier == t }
                        StatTile(
                            label = tr(R.string.admineconomy_tier_1, ShopItem.roman(t)),
                            value = "$count",
                            accent = if (count > 0) Amber else TextMid,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                if (insulated.isNotEmpty()) {
                    insulated.take(12).forEach { u ->
                        PlayerLine(u, tr(R.string.admineconomy_tier_1_2, ShopItem.roman(u.playerData.insulationTier)), Amber) { onPlayerClick(u.uid) }
                    }
                } else {
                    Text(tr(R.string.admineconomy_no_survivor_owns_an_insulation_tier), color = TextLow, style = DeadFestType.body)
                }
            }
        }
    }
}

/**
 * Compact dashboard tile: label, jumbo value and a caption. Reads the clock outside this
 * composable so a 10s tick rebuilds only the tiles that actually depend on it.
 */
@Composable
private fun StatPanel(title: String, value: String, caption: String, accent: Color) {
    SurfaceCard {
        SectionLabel(title, accent = accent)
        Spacer(Modifier.height(Spacing.xxs))
        Text(value, color = accent, style = DeadFestType.statHero, maxLines = 1)
        Text(caption, color = TextLow, style = DeadFestType.body, maxLines = 2)
    }
}

/** Online count. Isolated so the clock tick recomposes only this tile. */
@Composable
private fun OnlineStatTile(users: List<User>, now: State<Long>) {
    val online = remember(users, now.value) { users.count { it.playerData.onlineNow(now.value) } }
    StatPanel(tr(R.string.admineconomy_online_now), "$online", tr(R.string.admineconomy_in_the_app_right_now_1, users.size), Toxic)
}

/** Deaths in the last 10 minutes, normalised to deaths per minute. Isolated like [OnlineStatTile]. */
@Composable
private fun DeathRateStatTile(recent: List<EventLog>, now: State<Long>) {
    val deaths10 = remember(recent, now.value) {
        recent.count { it.category == "DEATH" && (it.timestamp?.toDate()?.time ?: 0L) >= now.value - 10 * 60_000L }
    }
    StatPanel(
        tr(R.string.admineconomy_death_rate),
        com.deadfest.domain.CasualtyTax.formatPct(deaths10 / 10.0),
        tr(R.string.admineconomy_deaths_min_1_in_last_10, deaths10),
        Ember
    )
}

/** Active overload readout. Isolated so the remaining-time label updates without a full refresh. */
@Composable
private fun OverloadPanel(globalState: GlobalState, now: State<Long>) {
    SurfaceCard {
        SectionLabel(tr(R.string.admineconomy_active_overloads), accent = Amber)
        Spacer(Modifier.height(Spacing.xs))
        if (globalState.overloadActive(now.value)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(globalState.overloadedSector, color = TextHi, style = DeadFestType.cardTitle, modifier = Modifier.weight(1f))
                Text(
                    "x${trimDouble(globalState.multiplier)} · " +
                        if (globalState.overloadEndsAt > 0) "${remainingLabel(globalState.overloadEndsAt, now.value)} left" else tr(R.string.admineconomy_no_time_limit),
                    color = Amber,
                    style = DeadFestType.body,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(tr(R.string.admineconomy_no_sector_is_overloaded), color = TextLow, style = DeadFestType.body)
        }
    }
}

@Composable
private fun PlayerLine(user: User, value: String, valueColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressClick(RoundedCornerShape(Radius.sm), onClick = onClick).padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FramedAvatar(user.playerData.avatar, user.playerData.avatarFrame, user.playerData.color, 26)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            user.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.admineconomy_survivor),
            color = TextHi,
            style = DeadFestType.body,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(value, color = valueColor, style = DeadFestType.body, fontWeight = FontWeight.Black)
    }
}

/**
 * Single-series horizontal bar chart: survivors per tax tier. One hue, thin bars with a
 * rounded data end anchored to the baseline, counts labelled in text colour (no legend).
 */
@Composable
private fun WealthChart(counts: Map<TaxTier, Int>) {
    val max = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val ranges = mapOf(
        TaxTier.ELITE to "> 5,000 · 30%",
        TaxTier.HIGH to "> 1,000 · 20%",
        TaxTier.MID to "> 500 · 15%",
        TaxTier.BASE to tr(R.string.admineconomy_500_flat_25)
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TaxTier.entries.forEach { tier ->
            val count = counts[tier] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    Text(tier.label, color = TextHi, style = DeadFestType.body, fontWeight = FontWeight.Black)
                    Text(ranges[tier] ?: "", color = TextLow, style = DeadFestType.microLabel)
                }
                Box(Modifier.weight(1f).height(12.dp)) {
                    if (count > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(count / max.toFloat())
                                .fillMaxHeight()
                                .background(Ember, RoundedCornerShape(topEnd = Radius.xs, bottomEnd = Radius.xs))
                        )
                    }
                }
                Text(
                    "$count",
                    color = TextHi,
                    style = DeadFestType.body,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}

// ================================================================== shop config

@Composable
internal fun ShopAdmin(viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val economy = globalState.economy

    fun save(field: String, value: Any, msg: String = tr(R.string.admineconomy_saved)) =
        viewModel.updateGlobalState(mapOf(field to value), success = msg)

    LazyColumn(
        modifier = Modifier.fillMaxSize().enterOnMount(1),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.scrollBottom)
    ) {
        item {
            AdminActionCard(
                title = tr(R.string.admineconomy_economy),
                subtitle = tr(R.string.admineconomy_ration_and_survival_payouts_insurance_cover)
            ) {
                NumberSetting(tr(R.string.admineconomy_daily_rations), economy.dailyBase) { save("dailyBase", it, tr(R.string.admineconomy_daily_rations_set_to_1, it)) }
                NumberSetting(tr(R.string.admineconomy_daily_rations_with_ration_upgrade), economy.dailyUpgraded) { save("dailyUpgraded", it, tr(R.string.admineconomy_upgraded_rations_set_to_1, it)) }
                NumberSetting(tr(R.string.admineconomy_mutual_survival_reward_per_death_0), economy.survivalReward) { save("survivalReward", it, tr(R.string.admineconomy_survival_reward_set_to_1, it)) }
                NumberSetting(tr(R.string.admineconomy_insurance_protection), economy.insurancePct.toLong(), max = 100) { save("insurancePct", it, tr(R.string.admineconomy_insurance_now_covers_1, it)) }
                HorizontalDivider(color = InkLine)
                AdminSwitch(tr(R.string.admineconomy_wealth_insulation_system), tr(R.string.admineconomy_show_and_sell_the_four_insulation), economy.insulationEnabled) {
                    save("insulationEnabled", it, if (it) tr(R.string.admineconomy_wealth_insulation_enabled) else tr(R.string.admineconomy_wealth_insulation_disabled))
                }
            }
        }
        item { FlashSaleAdminCard(economy.flashSale, viewModel::startFlashSale, viewModel::endFlashSale) }
        item {
            Column {
                SectionLabel(tr(R.string.admineconomy_black_market_items))
                Spacer(Modifier.height(Spacing.xxs))
                Text(tr(R.string.admineconomy_enable_disable_and_re_price_every), color = TextMid, style = DeadFestType.body)
            }
        }
        items(ShopItem.entries, key = { it.id }) { item ->
            ShopItemAdminRow(
                item = item,
                enabled = economy.shop[item.id]?.enabled ?: true,
                price = item.basePriceIn(economy),
                reduction = item.reductionIn(economy)
            ) { enabled, price, reduction ->
                val patch = mutableMapOf<String, Any>("enabled" to enabled, "price" to price)
                if (item.isInsulation && reduction != null) patch["reductionPct"] = reduction
                viewModel.updateGlobalState(mapOf("shopConfig" to mapOf(item.id to patch)), success = "${item.displayName} updated")
            }
        }
    }
}

@Composable
private fun ShopItemAdminRow(
    item: ShopItem,
    enabled: Boolean,
    price: Long,
    reduction: Int,
    onSave: (enabled: Boolean, price: Long, reduction: Int?) -> Unit
) {
    var priceText by remember(price) { mutableStateOf(price.toString()) }
    var reductionText by remember(reduction) { mutableStateOf(reduction.toString()) }
    val priceValue = priceText.toLongOrNull()
    val reductionValue = reductionText.toIntOrNull()?.takeIf { it in 0..100 }
    val dirty = priceValue != price || (item.isInsulation && reductionValue != reduction)

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.displayName, color = if (enabled) TextHi else TextLow, style = DeadFestType.cardTitle)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (item.isInsulation) tr(R.string.admineconomy_insulation_tier_1, ShopItem.roman(item.tier)) else item.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = TextMid,
                    style = DeadFestType.body
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onSave(it, priceValue ?: price, reductionValue) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Ember, uncheckedTrackColor = Ink3)
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedTextField(
                value = priceText,
                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 9) priceText = v },
                label = { Text(tr(R.string.admineconomy_price)) },
                singleLine = true,
                isError = priceValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors(),
                modifier = Modifier.weight(1f)
            )
            if (item.isInsulation) {
                OutlinedTextField(
                    value = reductionText,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) reductionText = v },
                    label = { Text(tr(R.string.admineconomy_tax_cut)) },
                    singleLine = true,
                    isError = reductionValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            PrimaryAction(
                text = tr(R.string.admineconomy_save),
                onClick = { onSave(enabled, priceValue!!, reductionValue) },
                enabled = dirty && priceValue != null && (!item.isInsulation || reductionValue != null),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Whole-number setting with its own SAVE button. */
@Composable
internal fun NumberSetting(label: String, value: Long, max: Long = 1_000_000_000, onSave: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toLongOrNull()?.takeIf { it in 0..max }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 10) text = v },
            label = { Text(label) },
            singleLine = true,
            isError = parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(Radius.md),
            colors = deadFestFieldColors(),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Spacing.xs))
        IconButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed != value) {
            Icon(Icons.Default.Save, contentDescription = tr(R.string.admineconomy_save), tint = if (parsed != null && parsed != value) Ember else TextLow)
        }
    }
}

@Composable
private fun DecimalSetting(label: String, value: Double, onSave: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(trimDouble(value)) }
    val parsed = text.toDoubleOrNull()?.takeIf { it > 0 }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) text = v },
            label = { Text(label) },
            singleLine = true,
            isError = parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(Radius.md),
            colors = deadFestFieldColors(),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Spacing.xs))
        IconButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed != value) {
            Icon(Icons.Default.Save, contentDescription = tr(R.string.admineconomy_save), tint = if (parsed != null && parsed != value) Ember else TextLow)
        }
    }
}

// ================================================================== XP config (Global tab)

@Composable
internal fun XpConfigCard(xp: XpConfig, onSave: (key: String, value: Any) -> Unit) {
    AdminActionCard(
        title = tr(R.string.admineconomy_xp_levels),
        subtitle = tr(R.string.admineconomy_rewards_granted_for_each_action_level)
    ) {
        NumberSetting(tr(R.string.admineconomy_xp_recording_a_death), xp.death) { onSave("death", it) }
        NumberSetting(tr(R.string.admineconomy_xp_surviving_a_full_day), xp.survivalDay) { onSave("survivalDay", it) }
        NumberSetting(tr(R.string.admineconomy_xp_shop_purchase), xp.purchase) { onSave("purchase", it) }
        NumberSetting(tr(R.string.admineconomy_xp_wheel_spin), xp.spin) { onSave("spin", it) }
        NumberSetting(tr(R.string.admineconomy_xp_scratch_card), xp.scratch) { onSave("scratch", it) }
        NumberSetting(tr(R.string.admineconomy_xp_clearing_a_penalty_spin), xp.penaltySpin) { onSave("penaltySpin", it) }
        HorizontalDivider(color = InkLine, modifier = Modifier.padding(vertical = Spacing.xxs))
        DecimalSetting(tr(R.string.admineconomy_curve_base), xp.curveBase) { onSave("curveBase", it) }
        DecimalSetting(tr(R.string.admineconomy_curve_exponent), xp.curveExponent) { onSave("curveExponent", it) }
        NumberSetting(tr(R.string.admineconomy_max_level_prestige_unlocks_here), xp.maxLevel.toLong(), max = 999) { onSave("maxLevel", it) }
    }
}

// ================================================================== scratch config

@Composable
internal fun ScratchAdmin(viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val prizes by viewModel.scratchPrizes.collectAsStateWithLifecycle()
    val economy = globalState.economy
    var editing by remember { mutableStateOf<ScratchPrize?>(null) }
    var deleting by remember { mutableStateOf<ScratchPrize?>(null) }
    val chances = remember(prizes) {
        val enabled = prizes.filter { it.enabled }
        val pct = Weighted.chances(enabled.map { it.weight })
        enabled.mapIndexed { i, p -> p.id to pct[i] }.toMap()
    }

    editing?.let { prize ->
        ScratchPrizeDialog(prize, onSave = { viewModel.saveScratchPrize(it); editing = null }, onCancel = { editing = null })
    }
    deleting?.let { prize ->
        ConfirmDialog(
            title = tr(R.string.admineconomy_delete_prize),
            message = tr(R.string.admineconomy_remove_1_from_the_scratch_card, prize.label),
            confirmText = tr(R.string.admineconomy_delete),
            destructive = true,
            onConfirm = { viewModel.deleteScratchPrize(prize.id) },
            onDismiss = { deleting = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().enterOnMount(2),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.scrollBottom)
    ) {
        item {
            AdminActionCard(
                title = tr(R.string.admineconomy_scratch_cards),
                subtitle = tr(R.string.admineconomy_global_switch_and_the_price_of)
            ) {
                AdminSwitch(tr(R.string.admineconomy_scratch_card_system), tr(R.string.admineconomy_global_on_off_switch), economy.scratchEnabled) {
                    viewModel.updateGlobalState(mapOf("scratchEnabled" to it), success = if (it) tr(R.string.admineconomy_scratch_cards_enabled) else tr(R.string.admineconomy_scratch_cards_disabled))
                }
                NumberSetting(tr(R.string.admineconomy_scratch_cost_coins), economy.scratchCost) {
                    viewModel.updateGlobalState(mapOf("scratchCost" to it), success = tr(R.string.admineconomy_scratch_cost_set_to_1, it))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryAction(
                    text = tr(R.string.admineconomy_add_prize),
                    icon = Icons.Default.Add,
                    onClick = { editing = ScratchPrize(label = tr(R.string.admineconomy_new_prize), value = "50", weight = 1.0) },
                    modifier = Modifier.weight(1f)
                )
                if (prizes.isEmpty()) {
                    GhostAction(
                        text = tr(R.string.admineconomy_load_defaults),
                        onClick = viewModel::seedScratchPrizes,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (prizes.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.ConfirmationNumber,
                    title = tr(R.string.admineconomy_no_prizes_yet),
                    message = tr(R.string.admineconomy_add_prizes_one_by_one_or)
                )
            }
        }
        items(prizes, key = { it.id }) { prize ->
            SurfaceCard(onClick = { editing = prize }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(parseThemeColor(prize.color), CircleShape))
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(prize.label, color = if (prize.enabled) TextHi else TextLow, style = DeadFestType.cardTitle)
                        Spacer(Modifier.height(2.dp))
                        if (!prize.enabled) {
                            StatusPill(tr(R.string.admineconomy_disabled), TextLow)
                        } else {
                            Text(
                                "${prize.type.uppercase()} ${prize.value.ifBlank { "" }} · w ${trimDouble(prize.weight)} · " +
                                    "${com.deadfest.domain.CasualtyTax.formatPct(chances[prize.id] ?: 0.0)}%",
                                color = TextMid,
                                style = DeadFestType.body
                            )
                        }
                    }
                    IconButton(onClick = { deleting = prize }) {
                        Icon(Icons.Default.Delete, contentDescription = tr(R.string.admineconomy_delete_2), tint = Ember)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScratchPrizeDialog(prize: ScratchPrize, onSave: (ScratchPrize) -> Unit, onCancel: () -> Unit) {
    var label by remember { mutableStateOf(prize.label) }
    var value by remember { mutableStateOf(prize.value) }
    var weight by remember { mutableStateOf(trimDouble(prize.weight)) }
    var type by remember { mutableStateOf(prize.type) }
    var color by remember { mutableStateOf(prize.color) }
    var enabled by remember { mutableStateOf(prize.enabled) }
    val weightValue = weight.toDoubleOrNull()
    val valid = label.isNotBlank() && weightValue != null && weightValue >= 0 &&
        (type != ScratchPrize.TYPE_COINS || value.trim().toLongOrNull() != null)
    val palette = listOf("#546E7A", "#7CB342", "#039BE5", "#00897B", "#4DD0E1", "#FDD835", "#FB8C00", "#E53935", "#8E24AA")
    val types = listOf(
        ScratchPrize.TYPE_COINS to tr(R.string.admineconomy_coins),
        ScratchPrize.TYPE_SHIELD to tr(R.string.admineconomy_shield),
        ScratchPrize.TYPE_LUCKY to tr(R.string.admineconomy_lucky),
        ScratchPrize.TYPE_NONE to tr(R.string.admineconomy_no_win)
    )

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = { Text(if (prize.id.isBlank()) tr(R.string.admineconomy_new_prize_2) else tr(R.string.admineconomy_edit_prize), color = TextHi, style = DeadFestType.cardTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(tr(R.string.admineconomy_label)) },
                    singleLine = true,
                    colors = deadFestFieldColors(),
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(types) { (key, name) ->
                        PillChip(
                            text = name,
                            selected = type == key,
                            accent = when (key) {
                                ScratchPrize.TYPE_COINS -> Amber
                                ScratchPrize.TYPE_SHIELD -> Plasma
                                ScratchPrize.TYPE_LUCKY -> Toxic
                                else -> TextLow
                            },
                            onClick = { type = key }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 9) value = v },
                        label = { Text(tr(R.string.admineconomy_coins_2)) },
                        singleLine = true,
                        enabled = type == ScratchPrize.TYPE_COINS,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = deadFestFieldColors(),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) weight = v },
                        label = { Text(tr(R.string.admineconomy_weight)) },
                        singleLine = true,
                        isError = weightValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = deadFestFieldColors(),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(tr(R.string.admineconomy_same_odds_engine_as_the_wheel), color = TextLow, style = DeadFestType.body)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.admineconomy_enabled), color = TextMid, style = DeadFestType.body, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = Ember))
                }
                SectionLabel(tr(R.string.admineconomy_colour))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(palette) { hex ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(parseThemeColor(hex), CircleShape)
                                .border(if (color.equals(hex, true)) Stroke.bold else 0.dp, Color.White, CircleShape)
                                .pressClick(CircleShape) { color = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        prize.copy(
                            label = label.trim(),
                            value = if (type == ScratchPrize.TYPE_COINS) value.trim() else "",
                            weight = weightValue ?: 1.0,
                            type = type,
                            color = color,
                            enabled = enabled
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                shape = RoundedCornerShape(Radius.sm)
            ) { Text(tr(R.string.admineconomy_save_2), fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr(R.string.common_cancel), color = TextMid) } }
    )
}

// ================================================================== flash sale (v2.1.0)

/** Start / end a timed Black Market discount. Survivors see a countdown banner in the shop. */
@Composable
private fun FlashSaleAdminCard(
    sale: com.deadfest.domain.FlashSale,
    onStart: (Int, Long) -> Unit,
    onEnd: () -> Unit
) {
    var pct by remember { mutableStateOf("30") }
    var minutes by remember { mutableStateOf("60") }
    val pctValue = pct.toIntOrNull()?.takeIf { it in 1..com.deadfest.domain.FlashSale.MAX_PCT }
    val minutesValue = minutes.toLongOrNull()?.takeIf { it in 0..10_080 }
    val live = sale.isLive()

    AdminActionCard(
        title = androidx.compose.ui.res.stringResource(com.deadfest.R.string.admin_flash_title),
        subtitle = androidx.compose.ui.res.stringResource(com.deadfest.R.string.admin_flash_desc)
    ) {
        if (live) {
            StatusPill(androidx.compose.ui.res.stringResource(com.deadfest.R.string.admin_flash_live, sale.pct), Toxic)
            if (sale.endsAt > 0L) {
                Spacer(Modifier.height(Spacing.xs))
                Text(tr(R.string.admineconomy_ends_in_1, remainingLabel(sale.endsAt)), color = TextMid, style = DeadFestType.body)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedTextField(
                value = pct,
                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) pct = v },
                label = { Text(androidx.compose.ui.res.stringResource(com.deadfest.R.string.admin_flash_pct)) },
                singleLine = true,
                isError = pctValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = deadFestFieldColors(),
                modifier = Modifier.weight(0.35f)
            )
            OutlinedTextField(
                value = minutes,
                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 5) minutes = v },
                label = { Text(androidx.compose.ui.res.stringResource(com.deadfest.R.string.admin_flash_minutes)) },
                singleLine = true,
                isError = minutesValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = deadFestFieldColors(),
                modifier = Modifier.weight(0.65f)
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        PrimaryAction(
            text = androidx.compose.ui.res.stringResource(com.deadfest.R.string.admin_flash_start),
            icon = Icons.Default.Bolt,
            enabled = pctValue != null && minutesValue != null,
            accent = Toxic,
            onClick = { onStart(pctValue!!, minutesValue!!) }
        )
        if (live) {
            Spacer(Modifier.height(Spacing.xs))
            GhostAction(
                text = androidx.compose.ui.res.stringResource(com.deadfest.R.string.admin_flash_end),
                onClick = onEnd,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
