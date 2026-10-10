package com.deadfest.ui.screens

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.delay

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

    // Re-evaluate the time windows (online = 2 min, death rate = 10 min) every 10 seconds.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    val online = remember(users, now) { users.count { it.playerData.onlineNow(now) } }
    val deaths10 = remember(recent, now) {
        recent.count { it.category == "DEATH" && (it.timestamp?.toDate()?.time ?: 0L) >= now - 10 * 60_000L }
    }
    val pendingPlayers = remember(users) { users.filter { it.playerData.pendingSpins > 0 }.sortedByDescending { it.playerData.pendingSpins } }
    val pendingTotal = pendingPlayers.sumOf { it.playerData.pendingSpins }
    val tiers = remember(users) { TaxTier.entries.associateWith { t -> users.count { TaxTier.forBalance(it.playerData.coins) == t } } }
    val bankrupt1 = users.count { it.playerData.bankruptcyLevel == 1 }
    val bankrupt2 = users.count { it.playerData.bankruptcyLevel >= 2 }
    val insulated = remember(users) { users.filter { it.playerData.insulationTier > 0 }.sortedByDescending { it.playerData.insulationTier } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            StatPanel("ONLINE NOW", "$online", "in the app right now · ${users.size} total", OnlineGreen)
        }
        item {
            StatPanel(
                "DEATH RATE",
                com.deadfest.domain.CasualtyTax.formatPct(deaths10 / 10.0),
                "deaths / min · $deaths10 in last 10 min",
                RedAccent
            )
        }
        item {
            StatPanel("PENDING SPINS", formatNumber(pendingTotal), "${pendingPlayers.size} survivor(s) owe spins", Gold)
        }
        item {
            DashboardPanel("BANKRUPTCY") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$bankrupt1", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text(" L1", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("$bankrupt2", color = RedAccent, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text(" L2", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("survivors in bankruptcy", color = TextMuted, fontSize = 11.sp)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            DashboardPanel("ACTIVE OVERLOADS") {
                if (globalState.overloadActive(now)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(globalState.overloadedSector, color = TextPrimary, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text(
                            "x${trimDouble(globalState.multiplier)} · " +
                                if (globalState.overloadEndsAt > 0) "${remainingLabel(globalState.overloadEndsAt, now)} left" else "no time limit",
                            color = Gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text("No sector is overloaded.", color = TextMuted, fontSize = 13.sp)
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            DashboardPanel("WEALTH DISTRIBUTION (TAX TIERS)") { WealthChart(tiers) }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            DashboardPanel("PENDING SPINS BY SURVIVOR") {
                if (pendingPlayers.isEmpty()) {
                    Text("Nobody owes the Wheel right now.", color = TextMuted, fontSize = 13.sp)
                } else {
                    pendingPlayers.take(12).forEach { u -> PlayerLine(u, "${u.playerData.pendingSpins} spins", RedAccent) { onPlayerClick(u.uid) } }
                    if (pendingPlayers.size > 12) Text("+${pendingPlayers.size - 12} more", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            DashboardPanel("WEALTH INSULATION OWNERSHIP") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { t ->
                        val count = users.count { it.playerData.insulationTier == t }
                        Column(
                            Modifier.weight(1f).background(CardBgRaised, RoundedCornerShape(10.dp)).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TIER ${ShopItem.roman(t)}", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text("$count", color = if (count > 0) Gold else TextSecondary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
                if (insulated.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    insulated.take(12).forEach { u ->
                        PlayerLine(u, "Tier ${ShopItem.roman(u.playerData.insulationTier)}", Gold) { onPlayerClick(u.uid) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPanel(title: String, value: String, caption: String, accent: Color) {
    DashboardPanel(title) {
        Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 28.sp, maxLines = 1)
        Text(caption, color = TextMuted, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun PlayerLine(user: User, value: String, valueColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressClick(RoundedCornerShape(8.dp), onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FramedAvatar(user.playerData.avatar, user.playerData.avatarFrame, user.playerData.color, 26)
        Spacer(Modifier.width(10.dp))
        Text(
            user.displayName?.takeIf { it.isNotBlank() } ?: "Survivor",
            color = TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
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
        TaxTier.BASE to "≤ 500 · flat 25"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TaxTier.entries.forEach { tier ->
            val count = counts[tier] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    Text(tier.label, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text(ranges[tier] ?: "", color = TextMuted, fontSize = 10.sp)
                }
                Box(Modifier.weight(1f).height(12.dp)) {
                    if (count > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(count / max.toFloat())
                                .fillMaxHeight()
                                .background(RedAccent, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        )
                    }
                }
                Text(
                    "$count",
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
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

    fun save(field: String, value: Any, msg: String = "Saved") =
        viewModel.updateGlobalState(mapOf(field to value), success = msg)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            AdminActionCard("ECONOMY") {
                NumberSetting("Daily rations", economy.dailyBase) { save("dailyBase", it, "Daily rations set to $it") }
                NumberSetting("Daily rations with Ration Upgrade", economy.dailyUpgraded) { save("dailyUpgraded", it, "Upgraded rations set to $it") }
                NumberSetting("Mutual Survival Reward per death (0 = off)", economy.survivalReward) { save("survivalReward", it, "Survival reward set to $it") }
                NumberSetting("Insurance protection %", economy.insurancePct.toLong(), max = 100) { save("insurancePct", it, "Insurance now covers $it%") }
                HorizontalDivider(color = Outline)
                AdminSwitch("Wealth Insulation system", "Show and sell the four insulation tiers", economy.insulationEnabled) {
                    save("insulationEnabled", it, if (it) "Wealth Insulation enabled" else "Wealth Insulation disabled")
                }
            }
        }
        item { SectionLabel("BLACK MARKET ITEMS", Modifier.padding(top = 4.dp)) }
        items(ShopItem.entries, key = { it.id }) { item ->
            ShopItemAdminRow(
                item = item,
                enabled = economy.shop[item.id]?.enabled ?: true,
                price = item.priceIn(economy),
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

    Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, color = if (enabled) TextPrimary else TextMuted, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(
                        if (item.isInsulation) "Insulation Tier ${ShopItem.roman(item.tier)}" else item.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onSave(it, priceValue ?: price, reductionValue) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RedAccent, uncheckedTrackColor = CardBgRaised)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 9) priceText = v },
                    label = { Text("Price") },
                    singleLine = true,
                    isError = priceValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = deadFestFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                if (item.isInsulation) {
                    OutlinedTextField(
                        value = reductionText,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) reductionText = v },
                        label = { Text("Tax cut %") },
                        singleLine = true,
                        isError = reductionValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = deadFestFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = { onSave(enabled, priceValue!!, reductionValue) },
                    enabled = dirty && priceValue != null && (!item.isInsulation || reductionValue != null),
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp)
                ) { Text("SAVE", fontWeight = FontWeight.Black) }
            }
        }
    }
}

/** Whole-number setting with its own SAVE button. */
@Composable
internal fun NumberSetting(label: String, value: Long, max: Long = 1_000_000_000, onSave: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toLongOrNull()?.takeIf { it in 0..max }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 10) text = v },
            label = { Text(label) },
            singleLine = true,
            isError = parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = deadFestFieldColors(),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed != value) {
            Icon(Icons.Default.Save, contentDescription = "Save", tint = if (parsed != null && parsed != value) RedAccent else TextMuted)
        }
    }
}

@Composable
private fun DecimalSetting(label: String, value: Double, onSave: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(trimDouble(value)) }
    val parsed = text.toDoubleOrNull()?.takeIf { it > 0 }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) text = v },
            label = { Text(label) },
            singleLine = true,
            isError = parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = deadFestFieldColors(),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed != value) {
            Icon(Icons.Default.Save, contentDescription = "Save", tint = if (parsed != null && parsed != value) RedAccent else TextMuted)
        }
    }
}

// ================================================================== XP config (Global tab)

@Composable
internal fun XpConfigCard(xp: XpConfig, onSave: (key: String, value: Any) -> Unit) {
    AdminActionCard("XP & LEVELS") {
        NumberSetting("XP: recording a death", xp.death) { onSave("death", it) }
        NumberSetting("XP: surviving a full day", xp.survivalDay) { onSave("survivalDay", it) }
        NumberSetting("XP: shop purchase", xp.purchase) { onSave("purchase", it) }
        NumberSetting("XP: wheel spin", xp.spin) { onSave("spin", it) }
        NumberSetting("XP: scratch card", xp.scratch) { onSave("scratch", it) }
        NumberSetting("XP: clearing a penalty spin", xp.penaltySpin) { onSave("penaltySpin", it) }
        HorizontalDivider(color = Outline, modifier = Modifier.padding(vertical = 4.dp))
        Text("Level curve: xpForLevel(n) = base × n ^ exponent", color = TextMuted, fontSize = 11.sp)
        DecimalSetting("Curve base", xp.curveBase) { onSave("curveBase", it) }
        DecimalSetting("Curve exponent", xp.curveExponent) { onSave("curveExponent", it) }
        NumberSetting("Max level (prestige unlocks here)", xp.maxLevel.toLong(), max = 999) { onSave("maxLevel", it) }
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
            title = "DELETE PRIZE?",
            message = "Remove \"${prize.label}\" from the Scratch Card prize pool?",
            confirmText = "DELETE",
            destructive = true,
            onConfirm = { viewModel.deleteScratchPrize(prize.id) },
            onDismiss = { deleting = null }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            AdminActionCard("SCRATCH CARDS") {
                AdminSwitch("Scratch Card system", "Global on/off switch", economy.scratchEnabled) {
                    viewModel.updateGlobalState(mapOf("scratchEnabled" to it), success = if (it) "Scratch Cards enabled" else "Scratch Cards disabled")
                }
                NumberSetting("Scratch cost (coins)", economy.scratchCost) {
                    viewModel.updateGlobalState(mapOf("scratchCost" to it), success = "Scratch cost set to $it")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { editing = ScratchPrize(label = "New Prize", value = "50", weight = 1.0) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("ADD PRIZE", fontWeight = FontWeight.Black)
                }
                if (prizes.isEmpty()) {
                    OutlinedButton(
                        onClick = viewModel::seedScratchPrizes,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Outline)
                    ) { Text("LOAD DEFAULTS", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp) }
                }
            }
        }
        if (prizes.isEmpty()) {
            item { EmptyState(Icons.Default.ConfirmationNumber, "The prize pool is empty. Add prizes or load the default pool.") }
        }
        items(prizes, key = { it.id }) { prize ->
            Surface(
                modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(14.dp)) { editing = prize },
                color = CardBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(parseThemeColor(prize.color), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(prize.label, color = if (prize.enabled) TextPrimary else TextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            if (!prize.enabled) "DISABLED"
                            else "${prize.type.uppercase()} ${prize.value.ifBlank { "" }} · w ${trimDouble(prize.weight)} · " +
                                "${com.deadfest.domain.CasualtyTax.formatPct(chances[prize.id] ?: 0.0)}%",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { deleting = prize }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
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
        ScratchPrize.TYPE_COINS to "COINS",
        ScratchPrize.TYPE_SHIELD to "SHIELD",
        ScratchPrize.TYPE_LUCKY to "LUCKY",
        ScratchPrize.TYPE_NONE to "NO WIN"
    )

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (prize.id.isBlank()) "NEW PRIZE" else "EDIT PRIZE", color = TextPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, colors = deadFestFieldColors(), modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(types) { (key, name) ->
                        FilterChip(selected = type == key, onClick = { type = key }, label = { Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value, { v -> if (v.all { it.isDigit() } && v.length <= 9) value = v },
                        label = { Text("Coins") }, singleLine = true, enabled = type == ScratchPrize.TYPE_COINS,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = deadFestFieldColors(), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        weight, { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) weight = v },
                        label = { Text("Weight") }, singleLine = true, isError = weightValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = deadFestFieldColors(), modifier = Modifier.weight(1f)
                    )
                }
                Text("Same odds engine as the Wheel: decimal weights like 0.25 or 1.35 are fine.", color = TextMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = RedAccent))
                }
                SectionLabel("COLOUR")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(palette) { hex ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(parseThemeColor(hex), CircleShape)
                                .border(if (color.equals(hex, true)) 3.dp else 0.dp, Color.White, CircleShape)
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
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SAVE", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL", color = TextSecondary) } }
    )
}
