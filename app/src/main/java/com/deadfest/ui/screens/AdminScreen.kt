@file:OptIn(ExperimentalLayoutApi::class)

package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.Archive
import com.deadfest.data.model.GameRequest
import com.deadfest.data.model.GlobalState
import com.deadfest.data.model.User
import com.deadfest.data.model.WheelItem
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminModule(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LIVE("Live", Icons.Default.MonitorHeart),
    PLAYERS("Players", Icons.Default.People),
    SHOP("Shop", Icons.Default.Storefront),
    SCRATCH("Scratch", Icons.Default.ConfirmationNumber),
    SECTORS("Sectors", Icons.Default.Map),
    EVENT("Event", Icons.Default.Celebration),
    OVERLOAD("Overload", Icons.Default.Bolt),
    WHEEL("Wheel", Icons.Default.Casino),
    GAME_REQUESTS("Requests", Icons.Default.SportsEsports),
    DEATHS("Deaths", Icons.Default.PhotoCamera),
    GLOBAL("Global", Icons.Default.Tune),
    BACKUP("Backup", Icons.Default.Archive),
    BROADCAST("Broadcast", Icons.Default.Campaign)
}

/**
 * One accent per module so the switcher reads as a map of the panel instead of 11 identical
 * chips. Presentation only: module names, order and behaviour are untouched.
 */
private val AdminModule.accent: Color
    get() = when (this) {
        AdminModule.LIVE -> Toxic
        AdminModule.PLAYERS -> Plasma
        AdminModule.SHOP -> Amber
        AdminModule.SCRATCH -> EmberGlow
        AdminModule.SECTORS -> Bronze
        AdminModule.EVENT -> Prestige
        AdminModule.OVERLOAD -> AmberDeep
        AdminModule.WHEEL -> Silver
        AdminModule.GAME_REQUESTS -> Prestige
        AdminModule.DEATHS -> Ember
        AdminModule.GLOBAL -> Ember
        AdminModule.BACKUP -> Ember
        AdminModule.BROADCAST -> Plasma
    }

@Composable
fun AdminScreen(navController: NavController, viewModel: MainViewModel) {
    var module by rememberSaveable { mutableStateOf(AdminModule.LIVE) }
    var selectedPlayerUid by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedPlayerUid != null) {
            PlayerDetailView(selectedPlayerUid!!, viewModel) { selectedPlayerUid = null }
            return@Column
        }

        ScreenHeader(title = tr(R.string.admin_admin_override), subtitle = tr(R.string.admin_root_access_handle_with_care))

        // Module switcher: a wrapping chip grid keeps every module visible and one tap away,
        // and the active chip carries that module's accent so its state is unambiguous.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
                .enterOnMount(0),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            AdminModule.entries.forEach { m ->
                PillChip(
                    text = m.label,
                    selected = module == m,
                    accent = m.accent,
                    onClick = { module = m }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            when (module) {
                AdminModule.LIVE -> LiveEventDashboard(viewModel) { selectedPlayerUid = it }
                AdminModule.PLAYERS -> PlayerAdmin(viewModel) { selectedPlayerUid = it }
                AdminModule.SHOP -> ShopAdmin(viewModel)
                AdminModule.SCRATCH -> ScratchAdmin(viewModel)
                AdminModule.SECTORS -> SectorAdmin(viewModel)
                AdminModule.EVENT -> EventAdmin(viewModel)
                AdminModule.OVERLOAD -> OverloadAdmin(viewModel)
                AdminModule.WHEEL -> WheelAdmin(viewModel)
                AdminModule.GAME_REQUESTS -> GameRequestsAdmin(viewModel)
                AdminModule.DEATHS -> AdminDeathEvidence(viewModel)
                AdminModule.GLOBAL -> GlobalAdmin(viewModel)
                AdminModule.BACKUP -> BackupAdmin(viewModel)
                AdminModule.BROADCAST -> NotificationsAdmin(viewModel)
            }
        }
    }
}

// ---------------------------------------------------------------- players

@Composable
private fun PlayerAdmin(viewModel: MainViewModel, onPlayerClick: (String) -> Unit) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(users, query) {
        if (query.isBlank()) users else users.filter { it.displayName?.contains(query.trim(), true) == true }
    }
    Column(
        modifier = Modifier.fillMaxSize().enterOnMount(1),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(tr(R.string.admin_find_survivor), color = TextLow) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMid) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = deadFestFieldColors()
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            contentPadding = PaddingValues(bottom = Spacing.scrollBottom)
        ) {
            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.PersonOff,
                        title = tr(R.string.admin_no_survivors_found),
                        message = if (query.isBlank()) tr(R.string.admin_no_player_records_have_loaded_yet)
                        else tr(R.string.admin_nobody_matches_1, query.trim())
                    )
                }
            } else {
                items(filtered, key = { it.uid }) { user ->
                    PlayerRow(user) { onPlayerClick(user.uid) }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(user: User, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FramedAvatar(
                avatar = user.playerData.avatar,
                frame = user.playerData.avatarFrame,
                color = user.playerData.color,
                size = 40
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    user.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.admin_unknown),
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    tr(R.string.admin_1_coins_2_deaths, formatNumber(user.playerData.coins), formatNumber(user.playerData.games.values.sumOf { it.deaths })),
                    color = TextMid,
                    style = DeadFestType.body
                )
            }
            if (user.admin) StatusPill(tr(R.string.admin_admin), Ember)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextLow)
        }
    }
}

@Composable
private fun PlayerDetailView(uid: String, viewModel: MainViewModel, onBack: () -> Unit) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val me by viewModel.user.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    // Always edit the *live* record. The old screen kept a stale copy, so coin edits
    // were calculated from outdated numbers.
    val user = users.firstOrNull { it.uid == uid }
    var coinInput by remember(uid) { mutableStateOf("") }
    var sectorDialog by remember { mutableStateOf<Long?>(null) } // +1 inject / -1 remove
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        ConfirmDialog(
            title = tr(R.string.admin_clear_penalties),
            message = tr(R.string.admin_wipes_this_survivor_s_pending_spins),
            confirmText = tr(R.string.admin_clear),
            onConfirm = { viewModel.clearPenalties(uid) },
            onDismiss = { confirmClear = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = user?.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.admin_survivor),
            subtitle = tr(R.string.admin_player_network_control),
            onBack = onBack
        )
        if (user == null) {
            EmptyState(Icons.Default.PersonOff, tr(R.string.admin_loading_survivor_record))
            return@Column
        }

        LaunchedEffect(user.playerData.coins) {
            if (coinInput.isEmpty()) coinInput = user.playerData.coins.toString()
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            AdminActionCard(
                title = tr(R.string.admin_coin_balance),
                subtitle = tr(R.string.admin_adjust_by_a_fixed_amount_or)
            ) {
                KeyValueRow(tr(R.string.admin_current_balance), formatNumber(user.playerData.coins))
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(-100L, -10L, 10L, 100L).forEach { delta ->
                        GhostAction(
                            text = if (delta > 0) "+$delta" else "$delta",
                            accent = if (delta > 0) Toxic else Ember,
                            onClick = { viewModel.updatePlayerCoins(uid, delta) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedTextField(
                        value = coinInput,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 9) coinInput = v },
                        label = { Text(tr(R.string.admin_set_exact_coins)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                    PrimaryAction(
                        text = tr(R.string.admin_set),
                        onClick = { coinInput.toLongOrNull()?.let { viewModel.setPlayerCoins(uid, it) } },
                        enabled = coinInput.toLongOrNull() != null,
                        modifier = Modifier.weight(0.45f)
                    )
                }
            }

            AdminActionCard(
                title = tr(R.string.admin_death_record),
                subtitle = tr(R.string.admin_deaths_per_sector_injecting_also_applies)
            ) {
                val games = user.playerData.games.toSortedMap()
                if (games.isEmpty()) {
                    EmptyState(Icons.Default.SportsEsports, tr(R.string.admin_no_deaths_recorded_for_this_survivor))
                } else {
                    games.forEach { (game, stats) ->
                        KeyValueRow(game, formatNumber(stats.deaths))
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PrimaryAction(
                        text = tr(R.string.admin_inject_death),
                        onClick = { sectorDialog = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    GhostAction(
                        text = tr(R.string.admin_remove_death),
                        onClick = { sectorDialog = -1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AdminActionCard(title = tr(R.string.admin_admin_status)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(R.string.admin_admin_rights), color = TextHi, style = DeadFestType.cardTitle)
                        Text(
                            if (uid == me?.uid) tr(R.string.admin_you_can_t_revoke_your_own) else tr(R.string.admin_grants_full_access_to_this_panel),
                            color = TextMid,
                            style = DeadFestType.body
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Switch(
                        checked = user.admin,
                        enabled = uid != me?.uid,
                        onCheckedChange = { viewModel.togglePlayerAdmin(uid, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Ember)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    val shield = user.playerData.shield
                    val lucky = user.playerData.luckyCoin
                    val insured = user.playerData.insurance
                    StatusPill(if (shield) tr(R.string.admin_shield_armed) else tr(R.string.admin_no_shield), if (shield) Plasma else TextLow)
                    StatusPill(if (lucky) tr(R.string.admin_lucky_coin_armed) else tr(R.string.admin_no_lucky_coin), if (lucky) Amber else TextLow)
                    StatusPill(if (insured) tr(R.string.admin_insurance_armed) else tr(R.string.admin_no_insurance), if (insured) Toxic else TextLow)
                }
            }

            AdminActionCard(
                title = tr(R.string.admin_progression_penalties),
                subtitle = tr(R.string.admin_level_state_bankruptcy_and_pending_spins)
            ) {
                val d = user.playerData
                KeyValueRow(tr(R.string.admin_level), d.level.toString())
                KeyValueRow(tr(R.string.common_xp), formatNumber(d.xp))
                if (d.prestigeCount > 0) {
                    KeyValueRow(tr(R.string.admin_prestige), d.prestigeCount.toString())
                }
                KeyValueRow(
                    tr(R.string.admin_insulation),
                    if (d.insulationTier > 0) tr(R.string.admineconomy_tier_1_2, com.deadfest.data.model.ShopItem.roman(d.insulationTier)) else tr(R.string.admin_none)
                )
                KeyValueRow(tr(R.string.admin_ration_upgrade), if (d.dailyUpgradeOwned) tr(R.string.admin_owned) else tr(R.string.admin_none))
                Spacer(Modifier.height(Spacing.sm))
                StatusPill(
                    if (d.bankruptcyLevel > 0) tr(R.string.admin_bankruptcy_level_1, d.bankruptcyLevel) else tr(R.string.admin_solvent),
                    if (d.bankruptcyLevel > 0) Ember else Toxic
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(tr(R.string.admin_1_pending_spin_s, d.pendingSpins), color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.sm))
                // Destructive: kept last in its section and gated by the confirm dialog above.
                GhostAction(
                    text = tr(R.string.admin_clear_penalties_2),
                    onClick = { confirmClear = true },
                    enabled = d.bankruptcyLevel > 0 || d.pendingSpins > 0,
                    accent = Ember,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(Spacing.scrollBottom))
        }
    }

    sectorDialog?.let { delta ->
        val sectors = if (delta > 0) globalState.games
        else user?.playerData?.games?.filterValues { it.deaths > 0 }?.keys?.sorted().orEmpty()
        AlertDialog(
            onDismissRequest = { sectorDialog = null },
            containerColor = Ink2,
            shape = RoundedCornerShape(Radius.xxl),
            title = {
                Text(
                    if (delta > 0) tr(R.string.admin_inject_death_tax_spin_apply) else tr(R.string.admin_remove_death_from),
                    color = TextHi,
                    style = DeadFestType.cardTitle
                )
            },
            text = {
                if (sectors.isEmpty()) {
                    Text(if (delta > 0) tr(R.string.admin_no_sectors_configured) else tr(R.string.admin_this_survivor_has_no_deaths), color = TextMid, style = DeadFestType.body)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.heightIn(max = 320.dp)) {
                        items(sectors) { game ->
                            SurfaceCard(
                                onClick = {
                                    viewModel.updatePlayerDeaths(uid, delta, game)
                                    sectorDialog = null
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(game.uppercase(), color = TextHi, style = DeadFestType.cardTitle, modifier = Modifier.weight(1f))
                                    if (game in globalState.lockedGames) StatusPill(tr(R.string.admin_locked), TextLow)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sectorDialog = null }) { Text(tr(R.string.common_cancel), color = TextMid) }
            }
        )
    }
}

/**
 * Shared admin section shell: a titled [SurfaceCard] with an optional one-line explanation.
 * Every admin panel uses it so sections read identically across modules.
 */
@Composable
internal fun AdminActionCard(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SurfaceCard(modifier = modifier, accent = accent) {
        SectionLabel(title, accent = accent ?: Ember)
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(subtitle, color = TextMid, style = DeadFestType.body)
        }
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

// ---------------------------------------------------------------- wheel

@Composable
private fun WheelAdmin(viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val wheelItems = globalState.wheelItems
    var editingItem by remember { mutableStateOf<WheelItem?>(null) }
    var deleting by remember { mutableStateOf<WheelItem?>(null) }
    val totalWeight = remember(wheelItems) { wheelItems.filter { it.enabled }.sumOf { it.weight.coerceAtLeast(0.0) } }

    editingItem?.let { item ->
        WheelItemDialog(
            item = item,
            onSave = { updated ->
                val exists = wheelItems.any { it.id == updated.id }
                val newItems = if (exists) wheelItems.map { if (it.id == updated.id) updated else it } else wheelItems + updated
                viewModel.updateGlobalState(mapOf("wheelItems" to newItems), success = tr(R.string.admin_wheel_updated))
                editingItem = null
            },
            onCancel = { editingItem = null }
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = tr(R.string.admin_delete_slice),
            message = tr(R.string.admin_remove_1_from_the_wheel_of, item.label),
            confirmText = tr(R.string.admineconomy_delete),
            destructive = true,
            onConfirm = { viewModel.updateGlobalState(mapOf("wheelItems" to wheelItems.filter { it.id != item.id }), success = tr(R.string.admin_slice_removed)) },
            onDismiss = { deleting = null }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().enterOnMount(2),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        PrimaryAction(
            text = tr(R.string.admin_add_slice),
            icon = Icons.Default.Add,
            onClick = { editingItem = WheelItem(id = System.currentTimeMillis().toString(), label = tr(R.string.admin_new_item), weight = 1.0) }
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            contentPadding = PaddingValues(bottom = Spacing.scrollBottom)
        ) {
            if (wheelItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Casino,
                        title = tr(R.string.admin_no_slices_yet),
                        message = tr(R.string.admin_the_wheel_of_fate_can_t)
                    )
                }
            } else {
                items(wheelItems, key = { it.id.ifEmpty { it.hashCode().toString() } }) { item ->
                    val chance = if (item.enabled && totalWeight > 0) item.weight.coerceAtLeast(0.0) / totalWeight * 100 else 0.0
                    SurfaceCard(onClick = { editingItem = item }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp).background(parseThemeColor(item.color), CircleShape))
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.label,
                                    color = if (item.enabled) TextHi else TextLow,
                                    style = DeadFestType.cardTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                if (!item.enabled) {
                                    StatusPill(tr(R.string.admineconomy_disabled), TextLow)
                                } else {
                                    Text(
                                        "${item.actionType.uppercase()} ${item.value.ifBlank { "-" }} · w ${trimDouble(item.weight)} · ${"%.1f".format(chance)}%",
                                        color = if (item.isTake) Ember else TextMid,
                                        style = DeadFestType.body
                                    )
                                }
                            }
                            IconButton(onClick = { deleting = item }) {
                                Icon(Icons.Default.Delete, contentDescription = tr(R.string.admineconomy_delete_2), tint = Ember)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelItemDialog(item: WheelItem, onSave: (WheelItem) -> Unit, onCancel: () -> Unit) {
    var label by remember { mutableStateOf(item.label) }
    var weight by remember { mutableStateOf(trimDouble(item.weight)) }
    var value by remember { mutableStateOf(item.value) }
    var actionType by remember { mutableStateOf(item.actionType.lowercase()) }
    var color by remember { mutableStateOf(item.color) }
    var enabled by remember { mutableStateOf(item.enabled) }
    val weightValue = weight.toDoubleOrNull()
    val valid = label.isNotBlank() && weightValue != null && weightValue >= 0

    val palette = listOf("#E53935", "#8E24AA", "#3949AB", "#039BE5", "#00897B", "#7CB342", "#FDD835", "#FB8C00", "#6D4C41", "#546E7A")

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = { Text(tr(R.string.admin_edit_slice), color = TextHi, style = DeadFestType.cardTitle) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(tr(R.string.admin_coins_value)) },
                        singleLine = true,
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
                Text(tr(R.string.admin_weights_can_be_decimals_e_g), color = TextLow, style = DeadFestType.body)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillChip(text = tr(R.string.admin_give), selected = actionType == "give", accent = Toxic, onClick = { actionType = "give" })
                    Spacer(Modifier.width(Spacing.xs))
                    PillChip(text = tr(R.string.admin_take), selected = actionType == "take", accent = Ember, onClick = { actionType = "take" })
                    Spacer(Modifier.weight(1f))
                    Text(tr(R.string.admin_on), color = TextMid, style = DeadFestType.body)
                    Spacer(Modifier.width(Spacing.xs))
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = Ember))
                }
                SectionLabel(tr(R.string.admineconomy_colour))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(palette) { hex ->
                        val selected = color.equals(hex, ignoreCase = true)
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(parseThemeColor(hex), CircleShape)
                                .border(if (selected) Stroke.bold else 0.dp, Color.White, CircleShape)
                                .pressClick(CircleShape) { color = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(item.copy(label = label.trim(), weight = weightValue ?: 1.0, value = value.trim(), actionType = actionType, color = color, enabled = enabled))
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                shape = RoundedCornerShape(Radius.sm)
            ) { Text(tr(R.string.admineconomy_save_2), fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr(R.string.common_cancel), color = TextMid) } }
    )
}

// ---------------------------------------------------------------- sectors

@Composable
private fun SectorAdmin(viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    var newGameName by remember { mutableStateOf("") }
    var selectedSector by remember { mutableStateOf<String?>(null) }

    fun isDuplicate(name: String) = globalState.games.any { it.equals(name.trim(), ignoreCase = true) }

    selectedSector?.let { sector ->
        SectorActionDialog(
            name = sector,
            isLocked = sector in globalState.lockedGames,
            onDismiss = { selectedSector = null },
            onRename = { raw ->
                val newName = raw.trim()
                when {
                    newName.isEmpty() || newName == sector -> Unit
                    isDuplicate(newName) -> viewModel.showMessage(tr(R.string.admin_a_sector_called_1_already_exists, newName))
                    else -> {
                        val updatedGames = globalState.games.map { if (it == sector) newName else it }
                        val updatedLocked = globalState.lockedGames.map { if (it == sector) newName else it }
                        viewModel.updateGlobalState(mapOf("games" to updatedGames, "lockedGames" to updatedLocked), success = tr(R.string.admin_sector_renamed))
                    }
                }
                selectedSector = null
            },
            onToggleLock = {
                val locked = sector in globalState.lockedGames
                val updatedLocked = if (locked) globalState.lockedGames - sector else globalState.lockedGames + sector
                viewModel.updateGlobalState(mapOf("lockedGames" to updatedLocked), success = if (locked) "$sector unlocked" else "$sector locked")
                selectedSector = null
            },
            onRemove = {
                viewModel.updateGlobalState(
                    mapOf("games" to globalState.games - sector, "lockedGames" to globalState.lockedGames - sector),
                    success = "$sector removed"
                )
                selectedSector = null
            }
        )
    }

    fun addSector() {
        val name = newGameName.trim()
        when {
            name.isEmpty() -> Unit
            isDuplicate(name) -> viewModel.showMessage(tr(R.string.admin_that_sector_already_exists))
            else -> {
                viewModel.updateGlobalState(mapOf("games" to (globalState.games + name)), success = "$name added")
                newGameName = ""
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().enterOnMount(3),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = newGameName,
            onValueChange = { newGameName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr(R.string.admin_new_sector_designation)) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = deadFestFieldColors(),
            trailingIcon = {
                IconButton(onClick = { addSector() }, enabled = newGameName.isNotBlank()) {
                    Icon(Icons.Default.AddCircle, contentDescription = tr(R.string.admin_add_sector), tint = if (newGameName.isNotBlank()) Ember else TextLow)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            contentPadding = PaddingValues(bottom = Spacing.scrollBottom)
        ) {
            if (globalState.games.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Map,
                        title = tr(R.string.admin_no_sectors_yet),
                        message = tr(R.string.admin_add_the_first_sector_above_survivors)
                    )
                }
            }
            itemsIndexed(globalState.games) { index, game ->
                val isLocked = game in globalState.lockedGames
                SurfaceCard(onClick = { selectedSector = game }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(game, color = if (isLocked) TextLow else TextHi, style = DeadFestType.cardTitle)
                            Spacer(Modifier.height(Spacing.xxs))
                            StatusPill(if (isLocked) tr(R.string.admin_locked) else tr(R.string.admin_active), if (isLocked) TextLow else Toxic)
                        }
                        IconButton(onClick = {
                            val list = globalState.games.toMutableList()
                            list.add(index - 1, list.removeAt(index))
                            viewModel.updateGlobalState(mapOf("games" to list))
                        }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, tr(R.string.admin_move_up), tint = if (index > 0) Ember else TextLow)
                        }
                        IconButton(onClick = {
                            val list = globalState.games.toMutableList()
                            list.add(index + 1, list.removeAt(index))
                            viewModel.updateGlobalState(mapOf("games" to list))
                        }, enabled = index < globalState.games.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, tr(R.string.admin_move_down), tint = if (index < globalState.games.lastIndex) Ember else TextLow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectorActionDialog(
    name: String,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onToggleLock: () -> Unit,
    onRemove: () -> Unit
) {
    var newName by remember { mutableStateOf(name) }
    var confirmRemove by remember { mutableStateOf(false) }

    if (confirmRemove) {
        ConfirmDialog(
            title = tr(R.string.admin_remove_sector),
            message = tr(R.string.admin_1_will_disappear_from_the_sector, name),
            confirmText = tr(R.string.admin_remove),
            destructive = true,
            onConfirm = onRemove,
            onDismiss = { confirmRemove = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = { Text("SECTOR: $name", color = TextHi, style = DeadFestType.cardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(tr(R.string.admin_rename_sector)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors()
                )
                GhostAction(
                    text = if (isLocked) tr(R.string.admin_unlock_sector) else tr(R.string.admin_lock_sector),
                    icon = if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    onClick = onToggleLock,
                    modifier = Modifier.fillMaxWidth()
                )
                // Destructive, last, and still confirmed before it is applied.
                GhostAction(
                    text = tr(R.string.admin_remove_sector_2),
                    icon = Icons.Default.Delete,
                    accent = Ember,
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(newName) },
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                shape = RoundedCornerShape(Radius.sm)
            ) { Text(tr(R.string.admineconomy_save_2), fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.common_cancel), color = TextMid) } }
    )
}

// ---------------------------------------------------------------- overload

@Composable
private fun OverloadAdmin(viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    var multiplier by remember { mutableStateOf(trimDouble(globalState.multiplier.takeIf { it > 1.0 } ?: 2.0)) }
    var selectedGame by remember { mutableStateOf(globalState.overloadedSector.takeIf { it.isNotEmpty() }) }
    val games = remember(globalState.games, globalState.lockedGames) { globalState.games.filter { it !in globalState.lockedGames } }
    val multiplierValue = multiplier.toDoubleOrNull()
    var durationMin by remember { mutableStateOf("0") }
    val durationValue = durationMin.toLongOrNull()
    // Lifecycle-aware clock for the countdown pill; idles unless the overload switch is on.
    val now = rememberLiveNow(intervalMs = 10_000L, running = globalState.sectorOverload)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).enterOnMount(4),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AdminActionCard(
            title = tr(R.string.admin_overload_status),
            subtitle = tr(R.string.admin_an_overload_multiplies_coin_rewards_in),
            accent = if (globalState.overloadActive(now.value)) Amber else null
        ) {
            if (globalState.overloadActive(now.value)) {
                val left = if (globalState.overloadEndsAt > 0) tr(R.string.admin_1_left, remainingLabel(globalState.overloadEndsAt, now.value)) else ""
                StatusPill(tr(R.string.admin_overload_active_1_x_2_3, globalState.overloadedSector.uppercase(), trimDouble(globalState.multiplier), left), Amber)
            } else {
                Text(tr(R.string.admineconomy_no_sector_is_overloaded), color = TextMid, style = DeadFestType.body)
            }
        }

        AdminActionCard(
            title = tr(R.string.admin_target_sector),
            subtitle = tr(R.string.admin_only_unlocked_sectors_can_be_overloaded)
        ) {
            if (games.isEmpty()) {
                EmptyState(Icons.Default.Map, tr(R.string.admin_no_unlocked_sectors_available_unlock_one))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    games.forEach { game ->
                        val isSelected = selectedGame == game
                        SurfaceCard(accent = if (isSelected) Amber else null, onClick = { selectedGame = game }) {
                            Text(
                                game,
                                color = if (isSelected) TextHi else TextMid,
                                style = if (isSelected) DeadFestType.cardTitle else DeadFestType.body
                            )
                        }
                    }
                }
            }
        }

        AdminActionCard(
            title = tr(R.string.admin_multiplier_duration),
            subtitle = tr(R.string.admin_set_the_coin_multiplier_and_how)
        ) {
            OutlinedTextField(
                value = multiplier,
                onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) multiplier = v },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr(R.string.admin_coin_multiplier)) },
                prefix = { Text("x ", color = Ember) },
                singleLine = true,
                isError = multiplierValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = durationMin,
                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 5) durationMin = v },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr(R.string.admin_duration_in_minutes_0_until_terminated)) },
                singleLine = true,
                isError = durationValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(
                text = tr(R.string.admin_initiate_overload),
                onClick = {
                    val mins = durationValue ?: 0L
                    viewModel.updateGlobalState(
                        mapOf(
                            "sectorOverload" to true,
                            "overloadedSector" to selectedGame!!,
                            "multiplier" to (multiplierValue ?: 1.0),
                            "overloadEndsAt" to if (mins > 0) System.currentTimeMillis() + mins * 60_000L else 0L
                        ),
                        success = tr(R.string.admin_overload_initiated_on_1, selectedGame)
                    )
                },
                enabled = selectedGame != null && multiplierValue != null && durationValue != null
            )

            if (globalState.sectorOverload) {
                Spacer(Modifier.height(Spacing.xs))
                GhostAction(
                    text = tr(R.string.admin_terminate_overload_protocol),
                    onClick = { viewModel.updateGlobalState(mapOf("sectorOverload" to false, "overloadedSector" to "", "overloadEndsAt" to 0L), success = tr(R.string.admin_overload_terminated)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

// ---------------------------------------------------------------- game requests

/**
 * Admin view of the community game queue.
 *
 * Pending requests can be approved — which makes the game a candidate sector — or removed;
 * approved ones can be removed. The queue is the same stream the request screen reads, so the two
 * can never disagree about what is waiting, and every row records who asked and who decided.
 */
@Composable
private fun GameRequestsAdmin(viewModel: MainViewModel) {
    val requests by viewModel.gameRequests.collectAsStateWithLifecycle()
    val pending = remember(requests) { requests.filter { !it.isApproved } }
    val approved = remember(requests) { requests.filter { it.isApproved } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).enterOnMount(3),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AdminActionCard(
            title = tr(R.string.admin_pending_requests),
            subtitle = tr(R.string.admin_1_waiting_approving_makes_a_game, pending.size),
            accent = if (pending.isNotEmpty()) Amber else null
        ) {
            if (pending.isEmpty()) {
                EmptyState(Icons.Default.CheckCircle, tr(R.string.admin_the_queue_is_clear))
            } else {
                pending.forEach { request ->
                    GameRequestAdminRow(
                        request = request,
                        onApprove = { viewModel.approveGameRequest(request.id) },
                        onRemove = { viewModel.removeGameRequest(request.id) }
                    )
                }
            }
        }

        AdminActionCard(
            title = tr(R.string.admin_approved_games),
            subtitle = tr(R.string.admin_1_approved_remove_one_to_drop, approved.size)
        ) {
            if (approved.isEmpty()) {
                EmptyState(Icons.Default.SportsEsports, tr(R.string.admin_nothing_approved_yet))
            } else {
                approved.forEach { request ->
                    GameRequestAdminRow(
                        request = request,
                        onRemove = { viewModel.removeGameRequest(request.id) }
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun GameRequestAdminRow(
    request: GameRequest,
    onApprove: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    SurfaceCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    request.name,
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(
                    if (request.isApproved) tr(R.string.admin_approved) else tr(R.string.admin_pending),
                    if (request.isApproved) Toxic else Amber
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                buildString {
                    append(tr(R.string.admin_asked_by_1, request.requestedByName))
                    if (request.decidedByName.isNotBlank()) append(tr(R.string.admin_decided_by_1, request.decidedByName))
                },
                color = TextMid,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (onApprove != null) {
                    PrimaryAction(text = tr(R.string.admin_approve), onClick = onApprove, modifier = Modifier.weight(1f))
                }
                GhostAction(text = tr(R.string.admin_remove_2), accent = Ember, onClick = onRemove, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- global

@Composable
private fun GlobalAdmin(viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    var showFirmwareDialog by remember { mutableStateOf(false) }
    var confirmLockdown by remember { mutableStateOf(false) }

    if (showFirmwareDialog) {
        PublishFirmwareDialog(
            currentVersionCode = globalState.latestVersionCode,
            currentVersionName = globalState.latestVersionName,
            currentUrl = globalState.updateUrl,
            currentUrlR8 = globalState.updateUrlR8,
            currentNotes = globalState.updateNotes,
            currentForce = globalState.forceUpdate,
            onDismiss = { showFirmwareDialog = false },
            onPublish = { publish ->
                viewModel.updateGlobalState(
                    mapOf(
                        "latestVersionCode" to publish.versionCode,
                        "latestVersionName" to publish.versionName,
                        "updateUrl" to publish.url,
                        "updateUrlR8" to publish.urlR8,
                        "updateNotes" to publish.notes,
                        "forceUpdate" to publish.force
                    ),
                    success = tr(R.string.admin_firmware_v_1_published, publish.versionName)
                )
                viewModel.logEvent("FIRMWARE_UPDATE", tr(R.string.admin_firmware_v_1_is_now_available, publish.versionName))
                showFirmwareDialog = false
            }
        )
    }
    if (confirmLockdown) {
        ConfirmDialog(
            title = tr(R.string.admin_engage_lockdown),
            message = tr(R.string.admin_the_shop_the_wheel_and_death),
            confirmText = tr(R.string.admin_lock_down),
            destructive = true,
            onConfirm = { viewModel.updateGlobalState(mapOf("lockdownActive" to true), success = tr(R.string.admin_lockdown_engaged)) },
            onDismiss = { confirmLockdown = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SurfaceCard(accent = Plasma, onClick = { showFirmwareDialog = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.SystemUpdate, tint = Plasma, size = 48)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.admin_publish_firmware), color = TextHi, style = DeadFestType.cardTitle)
                    Spacer(Modifier.height(2.dp))
                    Text(tr(R.string.admin_live_v_1_build_2, globalState.latestVersionName, globalState.latestVersionCode), color = TextMid, style = DeadFestType.body)
                    Spacer(Modifier.height(2.dp))
                    Text(publishedBuildsLabel(globalState), color = TextLow, style = DeadFestType.body)
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextLow)
            }
        }

        AdminActionCard(
            title = tr(R.string.admin_system_switches),
            subtitle = tr(R.string.admin_global_toggles_lockdown_freezes_the_shop)
        ) {
            AdminSwitch(tr(R.string.admin_black_market_access), tr(R.string.admin_enable_or_disable_shop_purchases), globalState.marketEnabled) {
                viewModel.updateGlobalState(mapOf("marketEnabled" to it))
            }
            HorizontalDivider(color = InkLine)
            AdminSwitch(tr(R.string.admin_wheel_of_fate), tr(R.string.admin_enable_or_disable_wheel_spinning), globalState.wheelEnabled) {
                viewModel.updateGlobalState(mapOf("wheelEnabled" to it))
            }
            HorizontalDivider(color = InkLine)
            AdminSwitch(tr(R.string.admin_revives), tr(R.string.admin_allow_survivors_to_buy_revives), globalState.reviveEnabled) {
                viewModel.updateGlobalState(mapOf("reviveEnabled" to it))
            }
            HorizontalDivider(color = InkLine)
            AdminSwitch(tr(R.string.admin_lockdown_protocol), tr(R.string.admin_freeze_core_features_death_recording), globalState.lockdownActive, danger = true) { on ->
                if (on) confirmLockdown = true
                else viewModel.updateGlobalState(mapOf("lockdownActive" to false), success = tr(R.string.admin_lockdown_lifted))
            }
            HorizontalDivider(color = InkLine)
            AdminSwitch(
                title = tr(R.string.admin_forced_spin_protocol),
                subtitle = tr(R.string.admin_every_death_adds_a_penalty_spin),
                checked = globalState.forceSpinProtocol
            ) { on ->
                viewModel.updateGlobalState(
                    mapOf("forceSpinProtocol" to on),
                    success = tr(R.string.admin_forced_spin_protocol_1, if (on) "enabled" else "disabled")
                )
                viewModel.logEvent("ADMIN_TOGGLE", tr(R.string.admin_forced_spin_protocol_1_for_everyone, if (on) "enabled" else "disabled"))
            }
        }

        XpConfigCard(globalState.economy.xp) { key, value ->
            viewModel.updateGlobalState(mapOf("xpConfig" to mapOf(key to value)), success = tr(R.string.admin_xp_settings_saved))
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
internal fun AdminSwitch(title: String, subtitle: String, checked: Boolean, danger: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger && checked) Ember else TextHi, style = DeadFestType.cardTitle)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMid, style = DeadFestType.body)
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Ember, uncheckedTrackColor = Ink3)
        )
    }
}

/**
 * The firmware fields the publish dialog collects.
 *
 * A struct rather than six positional arguments, because two of them are download URLs of the same
 * shape: swapping those by accident would hand the R8 build to everyone who asked for the full one.
 * Both builds share a version code and name (they are built together from the same source), so
 * there is one code/name pair and one URL each.
 */
private data class FirmwarePublish(
    val versionCode: Int,
    val versionName: String,
    /** Download URL of the full build, published on the releases page as Dead-Fest.apk. */
    val url: String,
    /** Download URL of the R8 build, published as Dead-Fest R8.apk. */
    val urlR8: String,
    val notes: String,
    val force: Boolean
)

/** True for something the installer could actually fetch. Blank is judged by the caller. */
private fun isDownloadUrl(url: String): Boolean {
    val trimmed = url.trim()
    return trimmed.startsWith("https://") || trimmed.startsWith("http://")
}

/** Which downloads the live document currently points at, spelled out so it is never a guess. */
private fun publishedBuildsLabel(state: GlobalState): String {
    val full = state.updateUrl.isNotBlank()
    val r8 = state.updateUrlR8.isNotBlank()
    return when {
        full && r8 -> tr(R.string.admin_both_builds_linked)
        r8 -> tr(R.string.admin_only_the_r8_build_is_linked)
        full -> tr(R.string.admin_only_the_full_build_is_linked)
        else -> tr(R.string.admin_no_download_linked_yet)
    }
}

@Composable
private fun PublishFirmwareDialog(
    currentVersionCode: Int,
    currentVersionName: String,
    currentUrl: String,
    currentUrlR8: String,
    currentNotes: String,
    currentForce: Boolean,
    onDismiss: () -> Unit,
    onPublish: (FirmwarePublish) -> Unit
) {
    var versionCode by remember { mutableStateOf(currentVersionCode.toString()) }
    var versionName by remember { mutableStateOf(currentVersionName) }
    var apkUrl by remember { mutableStateOf(currentUrl) }
    var apkUrlR8 by remember { mutableStateOf(currentUrlR8) }
    var notes by remember { mutableStateOf(currentNotes) }
    var forceUpdate by remember { mutableStateOf(currentForce) }
    val codeValue = versionCode.toIntOrNull()
    // Either URL on its own is enough to publish: a survivor on the build with no URL is quietly
    // served the other one, which is what makes a one-APK release still work for everybody.
    val urlValid = apkUrl.isBlank() || isDownloadUrl(apkUrl)
    val urlR8Valid = apkUrlR8.isBlank() || isDownloadUrl(apkUrlR8)
    val valid = codeValue != null && versionName.isNotBlank() &&
        (apkUrl.isNotBlank() || apkUrlR8.isNotBlank()) && urlValid && urlR8Valid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = { Text(tr(R.string.admin_publish_firmware), color = TextHi, style = DeadFestType.cardTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { if (it.all { c -> c.isDigit() }) versionCode = it },
                        label = { Text(tr(R.string.admin_code)) },
                        singleLine = true,
                        isError = codeValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.4f),
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text(tr(R.string.admin_version_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f),
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                }
                OutlinedTextField(
                    value = apkUrl,
                    onValueChange = { apkUrl = it },
                    label = { Text(tr(R.string.admin_apk_url_dead_fest)) },
                    singleLine = true,
                    isError = !urlValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors()
                )
                OutlinedTextField(
                    value = apkUrlR8,
                    onValueChange = { apkUrlR8 = it },
                    label = { Text(tr(R.string.admin_apk_url_dead_fest_r8)) },
                    singleLine = true,
                    isError = !urlR8Valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors()
                )
                Text(
                    tr(R.string.admin_both_builds_are_the_same_version) +
                        tr(R.string.admin_settings_update_and_anyone_whose_build) +
                        tr(R.string.admin_the_other_one),
                    color = TextLow,
                    style = DeadFestType.body
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(tr(R.string.admin_protocol_logs_notes)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(Radius.sm)) { forceUpdate = !forceUpdate }
                ) {
                    Checkbox(
                        checked = forceUpdate,
                        onCheckedChange = { forceUpdate = it },
                        colors = CheckboxDefaults.colors(checkedColor = Ember, checkmarkColor = Color.White)
                    )
                    Column {
                        Text(tr(R.string.admin_force_protocol_upgrade), color = TextHi, style = DeadFestType.body, fontWeight = FontWeight.Bold)
                        Text(tr(R.string.admin_survivors_can_t_dismiss_the_update), color = TextLow, style = DeadFestType.body)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPublish(
                        FirmwarePublish(
                            versionCode = codeValue ?: currentVersionCode,
                            versionName = versionName.trim(),
                            url = apkUrl.trim(),
                            urlR8 = apkUrlR8.trim(),
                            notes = notes.trim(),
                            force = forceUpdate
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                shape = RoundedCornerShape(Radius.sm)
            ) { Text(tr(R.string.admin_publish), fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.admin_abort), color = TextMid, fontWeight = FontWeight.Bold) } }
    )
}

// ---------------------------------------------------------------- backup

@Composable
private fun BackupAdmin(viewModel: MainViewModel) {
    val archives by viewModel.archives.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    var showWarningAlert by remember { mutableStateOf(false) }
    var archiveNameToCreate by remember { mutableStateOf(tr(R.string.admin_season_final_archive)) }
    var archiveToRestore by remember { mutableStateOf<Archive?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    if (showWarningAlert) {
        AlertDialog(
            onDismissRequest = { showWarningAlert = false },
            containerColor = Ink2,
            shape = RoundedCornerShape(Radius.xxl),
            title = { Text(tr(R.string.admin_reset_season), color = Ember, style = DeadFestType.cardTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        tr(R.string.admin_this_snapshots_every_survivor_s_stats),
                        color = TextHi,
                        style = DeadFestType.body
                    )
                    OutlinedTextField(
                        value = archiveNameToCreate,
                        onValueChange = { archiveNameToCreate = it },
                        label = { Text(tr(R.string.admin_archive_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createArchive(
                            archiveName = archiveNameToCreate.trim().ifEmpty { tr(R.string.admin_season_archive) },
                            notes = globalState.currentSeasonName,
                            roster = globalState.games
                        )
                        showWarningAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ember),
                    shape = RoundedCornerShape(Radius.sm)
                ) { Text(tr(R.string.admin_confirm_reset), fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { showWarningAlert = false }) { Text(tr(R.string.common_cancel), color = TextMid) } }
        )
    }

    archiveToRestore?.let { archive ->
        ConfirmDialog(
            title = tr(R.string.admin_restore_archive),
            message = tr(R.string.admin_this_overwrites_current_coins_names_and, archive.name),
            confirmText = tr(R.string.admin_restore),
            destructive = true,
            onConfirm = { viewModel.restoreArchive(archive.id) },
            onDismiss = { archiveToRestore = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(bottom = Spacing.scrollBottom)
    ) {
        item {
            // Destructive module action, kept first here as its own ember-tinted section with
            // the confirmation dialog as the gate.
            AdminActionCard(
                title = tr(R.string.admin_season_reset),
                accent = Ember,
                subtitle = tr(R.string.admin_snapshots_every_survivor_then_zeroes_all)
            ) {
                PrimaryAction(
                    text = tr(R.string.admin_create_archive_reset_season),
                    onClick = { showWarningAlert = true }
                )
            }
        }
        item {
            Column {
                SectionLabel(tr(R.string.admin_existing_archives))
                Spacer(Modifier.height(Spacing.xxs))
                Text(tr(R.string.admin_restoring_overwrites_current_coins_names_and), color = TextMid, style = DeadFestType.body)
            }
        }
        if (archives.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Archive,
                    title = tr(R.string.admin_no_archives_yet),
                    message = tr(R.string.admin_create_a_season_snapshot_above_to)
                )
            }
        }
        items(archives, key = { it.id }) { archive ->
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(archive.name, color = TextHi, style = DeadFestType.cardTitle)
                        Spacer(Modifier.height(2.dp))
                        Text(tr(R.string.admin_saved_1, dateFormat.format(Date(archive.timestamp))), color = TextMid, style = DeadFestType.body)
                        Text("${archive.playerSnapshots.size} survivors", color = Amber, style = DeadFestType.body, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    GhostAction(
                        text = tr(R.string.admin_restore_2),
                        accent = Ember,
                        onClick = { archiveToRestore = archive }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- event (v2.0.7)

@Composable
private fun EventAdmin(viewModel: MainViewModel) {
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var seasonName by remember(globalState.currentSeasonName) { mutableStateOf(globalState.currentSeasonName) }
    var enabled by remember(globalState.nextEventEnabled) { mutableStateOf(globalState.nextEventEnabled) }
    var eventName by remember(globalState.nextEventName) { mutableStateOf(globalState.nextEventName) }
    var startsAt by remember(globalState.nextEventStartsAt) { mutableLongStateOf(globalState.nextEventStartsAt) }
    var notes by remember(globalState.nextEventNotes) { mutableStateOf(globalState.nextEventNotes) }
    var games by remember(globalState.nextEventGames) { mutableStateOf(globalState.nextEventGames) }
    var newGame by remember { mutableStateOf("") }

    fun addGame(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (games.any { it.equals(clean, ignoreCase = true) }) return
        games = games + clean
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).enterOnMount(5),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AdminActionCard(
            title = tr(R.string.admin_current_season),
            subtitle = tr(R.string.admin_shown_on_team_progress_as_the)
        ) {
            OutlinedTextField(
                value = seasonName,
                onValueChange = { seasonName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr(R.string.admin_season_name)) },
                placeholder = { Text(tr(R.string.admin_dead_fest_season_1), color = TextLow) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(
                text = tr(R.string.admin_save_season_name),
                onClick = {
                    viewModel.updateGlobalState(
                        mapOf("currentSeasonName" to seasonName.trim().ifEmpty { tr(R.string.admin_dead_fest_season_1) }),
                        success = tr(R.string.admin_current_season_renamed)
                    )
                }
            )
        }

        AdminActionCard(
            title = tr(R.string.common_next_dead_fest),
            subtitle = tr(R.string.admin_the_countdown_and_line_up_every)
        ) {
            AdminSwitch(
                title = tr(R.string.admin_show_on_the_more_tab),
                subtitle = tr(R.string.admin_displays_the_countdown_and_game_line),
                checked = enabled,
                onChange = { enabled = it }
            )
            HorizontalDivider(color = InkLine)
            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr(R.string.admin_next_season_name)) },
                placeholder = { Text(tr(R.string.admin_dead_fest_season_2), color = TextLow) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )

            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = {
                    showDateTimePicker(context, startsAt) { picked -> startsAt = picked }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Radius.md),
                border = BorderStroke(Stroke.hair, if (startsAt > 0) Ember else InkLine)
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = if (startsAt > 0) Ember else TextMid)
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.admin_start_date_time), color = TextMid, style = DeadFestType.microLabel)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatEventTime(startsAt),
                        color = if (startsAt > 0) TextHi else TextLow,
                        style = DeadFestType.chip
                    )
                }
                Icon(Icons.Default.Schedule, contentDescription = null, tint = TextLow, modifier = Modifier.size(18.dp))
            }
            if (startsAt > 0) {
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(formatCountdown(startsAt - System.currentTimeMillis()), Amber)
                    Spacer(Modifier.width(Spacing.xs))
                    TextButton(onClick = { startsAt = 0L }) { Text(tr(R.string.admin_clear_date), color = TextMid) }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                label = { Text(tr(R.string.admin_blurb_optional)) },
                placeholder = { Text(tr(R.string.admin_rules_theme_prizes), color = TextLow) },
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
        }

        AdminActionCard(
            title = tr(R.string.admin_game_line_up),
            subtitle = tr(R.string.admin_the_games_queued_for_the_next)
        ) {
            if (games.isEmpty()) {
                EmptyState(Icons.Default.SportsEsports, tr(R.string.admin_nothing_queued_yet_add_games_below))
            } else {
                games.forEachIndexed { index, game ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
                        color = Ink3,
                        shape = RoundedCornerShape(Radius.sm),
                        border = BorderStroke(Stroke.hair, InkLine)
                    ) {
                        Row(Modifier.padding(start = Spacing.sm, top = Spacing.xxs, bottom = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}.", color = Ember, style = DeadFestType.cardTitle)
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                game,
                                color = TextHi,
                                style = DeadFestType.cardTitle,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = {
                                val list = games.toMutableList()
                                list.add(index - 1, list.removeAt(index))
                                games = list
                            }, enabled = index > 0) {
                                Icon(Icons.Default.ArrowUpward, tr(R.string.admin_move_up), tint = if (index > 0) Ember else TextLow)
                            }
                            IconButton(onClick = {
                                val list = games.toMutableList()
                                list.add(index + 1, list.removeAt(index))
                                games = list
                            }, enabled = index < games.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, tr(R.string.admin_move_down), tint = if (index < games.lastIndex) Ember else TextLow)
                            }
                            IconButton(onClick = { games = games.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Default.Delete, tr(R.string.admin_remove_2), tint = Ember)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = newGame,
                onValueChange = { newGame = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr(R.string.admin_add_a_game)) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors(),
                trailingIcon = {
                    IconButton(
                        onClick = { addGame(newGame); newGame = "" },
                        enabled = newGame.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = tr(R.string.admin_add_game),
                            tint = if (newGame.isNotBlank()) Ember else TextLow
                        )
                    }
                }
            )

            val suggestions = globalState.games.filter { candidate -> games.none { it.equals(candidate, ignoreCase = true) } }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                SectionLabel(tr(R.string.admin_add_from_sectors))
                Spacer(Modifier.height(Spacing.xs))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(suggestions) { sector ->
                        PillChip(text = sector, selected = false, onClick = { addGame(sector) })
                    }
                }
            }
        }

        PrimaryAction(
            text = tr(R.string.admin_save_next_dead_fest),
            onClick = {
                viewModel.updateGlobalState(
                    mapOf(
                        "nextEventEnabled" to enabled,
                        "nextEventName" to eventName.trim(),
                        "nextEventStartsAt" to startsAt,
                        "nextEventGames" to games,
                        "nextEventNotes" to notes.trim()
                    ),
                    success = tr(R.string.admin_next_dead_fest_saved)
                )
            }
        )

        Spacer(Modifier.height(Spacing.md))
    }
}

// ---------------------------------------------------------------- broadcast

@Composable
private fun NotificationsAdmin(viewModel: MainViewModel) {
    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AdminActionCard(
            title = tr(R.string.admin_broadcast_to_survivors),
            subtitle = tr(R.string.admin_appears_in_the_live_ticker_on)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { if (it.length <= 280) messageText = it },
                label = { Text(tr(R.string.admin_message)) },
                supportingText = { Text("${messageText.length}/280", color = TextLow) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(
                text = tr(R.string.admin_broadcast_now),
                icon = Icons.Default.Campaign,
                enabled = messageText.isNotBlank(),
                onClick = {
                    viewModel.logEvent("ADMIN_BROADCAST", messageText.trim())
                    viewModel.showMessage(tr(R.string.admin_broadcast_transmitted_to_the_wasteland))
                    messageText = ""
                }
            )
        }
    }
}
