@file:OptIn(ExperimentalLayoutApi::class)

package com.deadfest.ui.screens

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.Archive
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

        ScreenHeader(title = "ADMIN OVERRIDE", subtitle = "ROOT ACCESS // HANDLE WITH CARE")

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
            placeholder = { Text("Find survivor...", color = TextLow) },
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
                        title = "No survivors found",
                        message = if (query.isBlank()) "No player records have loaded yet."
                        else "Nobody matches \"${query.trim()}\"."
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
                    user.displayName?.takeIf { it.isNotBlank() } ?: "Unknown",
                    color = TextHi,
                    style = DeadFestType.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatNumber(user.playerData.coins)} coins · ${formatNumber(user.playerData.games.values.sumOf { it.deaths })} deaths",
                    color = TextMid,
                    style = DeadFestType.body
                )
            }
            if (user.admin) StatusPill("ADMIN", Ember)
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
            title = "CLEAR PENALTIES?",
            message = "Wipes this survivor's pending spins and lifts bankruptcy (they stay at Level 1 if their balance is 0).",
            confirmText = "CLEAR",
            onConfirm = { viewModel.clearPenalties(uid) },
            onDismiss = { confirmClear = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = user?.displayName?.takeIf { it.isNotBlank() } ?: "SURVIVOR",
            subtitle = "PLAYER NETWORK CONTROL",
            onBack = onBack
        )
        if (user == null) {
            EmptyState(Icons.Default.PersonOff, "Loading survivor record...")
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
                title = "Coin balance",
                subtitle = "Adjust by a fixed amount or set an exact balance. Every change is logged."
            ) {
                KeyValueRow("Current balance", formatNumber(user.playerData.coins))
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
                        label = { Text("Set exact coins") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                    PrimaryAction(
                        text = "Set",
                        onClick = { coinInput.toLongOrNull()?.let { viewModel.setPlayerCoins(uid, it) } },
                        enabled = coinInput.toLongOrNull() != null,
                        modifier = Modifier.weight(0.45f)
                    )
                }
            }

            AdminActionCard(
                title = "Death record",
                subtitle = "Deaths per sector. Injecting also applies tax and a penalty spin; removal is for correcting mistakes."
            ) {
                val games = user.playerData.games.toSortedMap()
                if (games.isEmpty()) {
                    EmptyState(Icons.Default.SportsEsports, "No deaths recorded for this survivor.")
                } else {
                    games.forEach { (game, stats) ->
                        KeyValueRow(game, formatNumber(stats.deaths))
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PrimaryAction(
                        text = "Inject death",
                        onClick = { sectorDialog = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    GhostAction(
                        text = "Remove death",
                        onClick = { sectorDialog = -1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AdminActionCard(title = "Admin status") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin rights", color = TextHi, style = DeadFestType.cardTitle)
                        Text(
                            if (uid == me?.uid) "You can't revoke your own access" else "Grants full access to this panel",
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
                    StatusPill(if (shield) "Shield armed" else "No shield", if (shield) Plasma else TextLow)
                    StatusPill(if (lucky) "Lucky coin armed" else "No lucky coin", if (lucky) Amber else TextLow)
                    StatusPill(if (insured) "Insurance armed" else "No insurance", if (insured) Toxic else TextLow)
                }
            }

            AdminActionCard(
                title = "Progression & penalties",
                subtitle = "Level state, bankruptcy and pending spins. Clearing penalties wipes pending spins and lifts bankruptcy."
            ) {
                val d = user.playerData
                KeyValueRow("Level", d.level.toString())
                KeyValueRow("XP", formatNumber(d.xp))
                if (d.prestigeCount > 0) {
                    KeyValueRow("Prestige", d.prestigeCount.toString())
                }
                KeyValueRow(
                    "Insulation",
                    if (d.insulationTier > 0) "Tier ${com.deadfest.data.model.ShopItem.roman(d.insulationTier)}" else "None"
                )
                KeyValueRow("Ration upgrade", if (d.dailyUpgradeOwned) "Owned" else "None")
                Spacer(Modifier.height(Spacing.sm))
                StatusPill(
                    if (d.bankruptcyLevel > 0) "Bankruptcy level ${d.bankruptcyLevel}" else "Solvent",
                    if (d.bankruptcyLevel > 0) Ember else Toxic
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text("${d.pendingSpins} pending spin(s)", color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(Spacing.sm))
                // Destructive: kept last in its section and gated by the confirm dialog above.
                GhostAction(
                    text = "Clear penalties",
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
                    if (delta > 0) "INJECT DEATH (TAX + SPIN APPLY)" else "REMOVE DEATH FROM",
                    color = TextHi,
                    style = DeadFestType.cardTitle
                )
            },
            text = {
                if (sectors.isEmpty()) {
                    Text(if (delta > 0) "No sectors configured." else "This survivor has no deaths.", color = TextMid, style = DeadFestType.body)
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
                                    if (game in globalState.lockedGames) StatusPill("Locked", TextLow)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sectorDialog = null }) { Text("CANCEL", color = TextMid) }
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
                viewModel.updateGlobalState(mapOf("wheelItems" to newItems), success = "Wheel updated")
                editingItem = null
            },
            onCancel = { editingItem = null }
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "DELETE SLICE?",
            message = "Remove \"${item.label}\" from the Wheel of Fate?",
            confirmText = "DELETE",
            destructive = true,
            onConfirm = { viewModel.updateGlobalState(mapOf("wheelItems" to wheelItems.filter { it.id != item.id }), success = "Slice removed") },
            onDismiss = { deleting = null }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().enterOnMount(2),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        PrimaryAction(
            text = "Add slice",
            icon = Icons.Default.Add,
            onClick = { editingItem = WheelItem(id = System.currentTimeMillis().toString(), label = "New Item", weight = 1.0) }
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
                        title = "No slices yet",
                        message = "The Wheel of Fate can't spin without slices. Add one above."
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
                                    StatusPill("Disabled", TextLow)
                                } else {
                                    Text(
                                        "${item.actionType.uppercase()} ${item.value.ifBlank { "-" }} · w ${trimDouble(item.weight)} · ${"%.1f".format(chance)}%",
                                        color = if (item.isTake) Ember else TextMid,
                                        style = DeadFestType.body
                                    )
                                }
                            }
                            IconButton(onClick = { deleting = item }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Ember)
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
        title = { Text("EDIT SLICE", color = TextHi, style = DeadFestType.cardTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    colors = deadFestFieldColors(),
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Coins / value") },
                        singleLine = true,
                        colors = deadFestFieldColors(),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) weight = v },
                        label = { Text("Weight") },
                        singleLine = true,
                        isError = weightValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = deadFestFieldColors(),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Weights can be decimals, e.g. 0.25 for ultra-rare loot.", color = TextLow, style = DeadFestType.body)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillChip(text = "Give", selected = actionType == "give", accent = Toxic, onClick = { actionType = "give" })
                    Spacer(Modifier.width(Spacing.xs))
                    PillChip(text = "Take", selected = actionType == "take", accent = Ember, onClick = { actionType = "take" })
                    Spacer(Modifier.weight(1f))
                    Text("On", color = TextMid, style = DeadFestType.body)
                    Spacer(Modifier.width(Spacing.xs))
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = Ember))
                }
                SectionLabel("Colour")
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
            ) { Text("SAVE", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL", color = TextMid) } }
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
                    isDuplicate(newName) -> viewModel.showMessage("A sector called \"$newName\" already exists.")
                    else -> {
                        val updatedGames = globalState.games.map { if (it == sector) newName else it }
                        val updatedLocked = globalState.lockedGames.map { if (it == sector) newName else it }
                        viewModel.updateGlobalState(mapOf("games" to updatedGames, "lockedGames" to updatedLocked), success = "Sector renamed")
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
            isDuplicate(name) -> viewModel.showMessage("That sector already exists.")
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
            label = { Text("New sector designation") },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = deadFestFieldColors(),
            trailingIcon = {
                IconButton(onClick = { addSector() }, enabled = newGameName.isNotBlank()) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add sector", tint = if (newGameName.isNotBlank()) Ember else TextLow)
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
                        title = "No sectors yet",
                        message = "Add the first sector above. Survivors record deaths against these."
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
                            StatusPill(if (isLocked) "Locked" else "Active", if (isLocked) TextLow else Toxic)
                        }
                        IconButton(onClick = {
                            val list = globalState.games.toMutableList()
                            list.add(index - 1, list.removeAt(index))
                            viewModel.updateGlobalState(mapOf("games" to list))
                        }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, "Move up", tint = if (index > 0) Ember else TextLow)
                        }
                        IconButton(onClick = {
                            val list = globalState.games.toMutableList()
                            list.add(index + 1, list.removeAt(index))
                            viewModel.updateGlobalState(mapOf("games" to list))
                        }, enabled = index < globalState.games.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, "Move down", tint = if (index < globalState.games.lastIndex) Ember else TextLow)
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
            title = "REMOVE SECTOR?",
            message = "\"$name\" will disappear from the sector list. Player death records are kept.",
            confirmText = "REMOVE",
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
                    label = { Text("Rename sector") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors()
                )
                GhostAction(
                    text = if (isLocked) "Unlock sector" else "Lock sector",
                    icon = if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    onClick = onToggleLock,
                    modifier = Modifier.fillMaxWidth()
                )
                // Destructive, last, and still confirmed before it is applied.
                GhostAction(
                    text = "Remove sector",
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
            ) { Text("SAVE", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMid) } }
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
            title = "Overload status",
            subtitle = "An overload multiplies coin rewards in one sector until it expires or is terminated.",
            accent = if (globalState.overloadActive(now.value)) Amber else null
        ) {
            if (globalState.overloadActive(now.value)) {
                val left = if (globalState.overloadEndsAt > 0) " · ${remainingLabel(globalState.overloadEndsAt, now.value)} LEFT" else ""
                StatusPill("OVERLOAD ACTIVE: ${globalState.overloadedSector.uppercase()} x${trimDouble(globalState.multiplier)}$left", Amber)
            } else {
                Text("No sector is overloaded.", color = TextMid, style = DeadFestType.body)
            }
        }

        AdminActionCard(
            title = "Target sector",
            subtitle = "Only unlocked sectors can be overloaded."
        ) {
            if (games.isEmpty()) {
                EmptyState(Icons.Default.Map, "No unlocked sectors available. Unlock one in Sectors first.")
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
            title = "Multiplier & duration",
            subtitle = "Set the coin multiplier and how long the overload runs. 0 minutes means until terminated."
        ) {
            OutlinedTextField(
                value = multiplier,
                onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) multiplier = v },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Coin multiplier") },
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
                label = { Text("Duration in minutes (0 = until terminated)") },
                singleLine = true,
                isError = durationValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(
                text = "Initiate overload",
                onClick = {
                    val mins = durationValue ?: 0L
                    viewModel.updateGlobalState(
                        mapOf(
                            "sectorOverload" to true,
                            "overloadedSector" to selectedGame!!,
                            "multiplier" to (multiplierValue ?: 1.0),
                            "overloadEndsAt" to if (mins > 0) System.currentTimeMillis() + mins * 60_000L else 0L
                        ),
                        success = "Overload initiated on $selectedGame"
                    )
                },
                enabled = selectedGame != null && multiplierValue != null && durationValue != null
            )

            if (globalState.sectorOverload) {
                Spacer(Modifier.height(Spacing.xs))
                GhostAction(
                    text = "Terminate overload protocol",
                    onClick = { viewModel.updateGlobalState(mapOf("sectorOverload" to false, "overloadedSector" to "", "overloadEndsAt" to 0L), success = "Overload terminated") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
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
            currentNotes = globalState.updateNotes,
            currentForce = globalState.forceUpdate,
            onDismiss = { showFirmwareDialog = false },
            onPublish = { code, name, url, notes, force ->
                viewModel.updateGlobalState(
                    mapOf(
                        "latestVersionCode" to code,
                        "latestVersionName" to name,
                        "updateUrl" to url,
                        "updateNotes" to notes,
                        "forceUpdate" to force
                    ),
                    success = "Firmware v$name published"
                )
                viewModel.logEvent("FIRMWARE_UPDATE", "Firmware v$name is now available. Update from Settings.")
                showFirmwareDialog = false
            }
        )
    }
    if (confirmLockdown) {
        ConfirmDialog(
            title = "ENGAGE LOCKDOWN?",
            message = "The shop, the wheel and death recording will be frozen for every survivor until you lift it.",
            confirmText = "LOCK DOWN",
            destructive = true,
            onConfirm = { viewModel.updateGlobalState(mapOf("lockdownActive" to true), success = "Lockdown engaged") },
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
                    Text("PUBLISH FIRMWARE", color = TextHi, style = DeadFestType.cardTitle)
                    Spacer(Modifier.height(2.dp))
                    Text("Live: v${globalState.latestVersionName} (build ${globalState.latestVersionCode})", color = TextMid, style = DeadFestType.body)
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextLow)
            }
        }

        AdminActionCard(
            title = "System switches",
            subtitle = "Global toggles. Lockdown freezes the shop, the wheel and death recording for everyone."
        ) {
            AdminSwitch("Black Market access", "Enable or disable shop purchases", globalState.marketEnabled) {
                viewModel.updateGlobalState(mapOf("marketEnabled" to it))
            }
            HorizontalDivider(color = InkLine)
            AdminSwitch("Wheel of Fate", "Enable or disable wheel spinning", globalState.wheelEnabled) {
                viewModel.updateGlobalState(mapOf("wheelEnabled" to it))
            }
            HorizontalDivider(color = InkLine)
            AdminSwitch("Revives", "Allow survivors to buy Revives", globalState.reviveEnabled) {
                viewModel.updateGlobalState(mapOf("reviveEnabled" to it))
            }
            HorizontalDivider(color = InkLine)
            AdminSwitch("Lockdown protocol", "Freeze core features & death recording", globalState.lockdownActive, danger = true) { on ->
                if (on) confirmLockdown = true
                else viewModel.updateGlobalState(mapOf("lockdownActive" to false), success = "Lockdown lifted")
            }
        }

        XpConfigCard(globalState.economy.xp) { key, value ->
            viewModel.updateGlobalState(mapOf("xpConfig" to mapOf(key to value)), success = "XP settings saved")
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

@Composable
private fun PublishFirmwareDialog(
    currentVersionCode: Int,
    currentVersionName: String,
    currentUrl: String,
    currentNotes: String,
    currentForce: Boolean,
    onDismiss: () -> Unit,
    onPublish: (Int, String, String, String, Boolean) -> Unit
) {
    var versionCode by remember { mutableStateOf(currentVersionCode.toString()) }
    var versionName by remember { mutableStateOf(currentVersionName) }
    var apkUrl by remember { mutableStateOf(currentUrl) }
    var notes by remember { mutableStateOf(currentNotes) }
    var forceUpdate by remember { mutableStateOf(currentForce) }
    val codeValue = versionCode.toIntOrNull()
    val urlValid = apkUrl.trim().startsWith("https://") || apkUrl.trim().startsWith("http://")
    val valid = codeValue != null && versionName.isNotBlank() && urlValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = { Text("PUBLISH FIRMWARE", color = TextHi, style = DeadFestType.cardTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { if (it.all { c -> c.isDigit() }) versionCode = it },
                        label = { Text("Code") },
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
                        label = { Text("Version name") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f),
                        shape = RoundedCornerShape(Radius.md),
                        colors = deadFestFieldColors()
                    )
                }
                OutlinedTextField(
                    value = apkUrl,
                    onValueChange = { apkUrl = it },
                    label = { Text("APK download URL") },
                    singleLine = true,
                    isError = apkUrl.isNotEmpty() && !urlValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = deadFestFieldColors()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Protocol logs (notes)") },
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
                        Text("FORCE PROTOCOL UPGRADE", color = TextHi, style = DeadFestType.body, fontWeight = FontWeight.Bold)
                        Text("Survivors can't dismiss the update prompt", color = TextLow, style = DeadFestType.body)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(codeValue ?: currentVersionCode, versionName.trim(), apkUrl.trim(), notes.trim(), forceUpdate) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                shape = RoundedCornerShape(Radius.sm)
            ) { Text("PUBLISH", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABORT", color = TextMid, fontWeight = FontWeight.Bold) } }
    )
}

// ---------------------------------------------------------------- backup

@Composable
private fun BackupAdmin(viewModel: MainViewModel) {
    val archives by viewModel.archives.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    var showWarningAlert by remember { mutableStateOf(false) }
    var archiveNameToCreate by remember { mutableStateOf("Season Final Archive") }
    var archiveToRestore by remember { mutableStateOf<Archive?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    if (showWarningAlert) {
        AlertDialog(
            onDismissRequest = { showWarningAlert = false },
            containerColor = Ink2,
            shape = RoundedCornerShape(Radius.xxl),
            title = { Text("⚠️ RESET SEASON", color = Ember, style = DeadFestType.cardTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "This snapshots every survivor's stats, then resets all deaths to 0 and all coins to 250.",
                        color = TextHi,
                        style = DeadFestType.body
                    )
                    OutlinedTextField(
                        value = archiveNameToCreate,
                        onValueChange = { archiveNameToCreate = it },
                        label = { Text("Archive name") },
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
                            archiveName = archiveNameToCreate.trim().ifEmpty { "Season Archive" },
                            notes = globalState.currentSeasonName,
                            roster = globalState.games
                        )
                        showWarningAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ember),
                    shape = RoundedCornerShape(Radius.sm)
                ) { Text("CONFIRM & RESET", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { showWarningAlert = false }) { Text("CANCEL", color = TextMid) } }
        )
    }

    archiveToRestore?.let { archive ->
        ConfirmDialog(
            title = "⚠️ RESTORE ARCHIVE?",
            message = "This overwrites current coins, names and deaths with the snapshot from \"${archive.name}\".",
            confirmText = "RESTORE",
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
                title = "Season reset",
                accent = Ember,
                subtitle = "Snapshots every survivor, then zeroes all deaths and resets all coins to 250."
            ) {
                PrimaryAction(
                    text = "Create archive & reset season",
                    onClick = { showWarningAlert = true }
                )
            }
        }
        item {
            Column {
                SectionLabel("Existing archives")
                Spacer(Modifier.height(Spacing.xxs))
                Text("Restoring overwrites current coins, names and deaths with the snapshot.", color = TextMid, style = DeadFestType.body)
            }
        }
        if (archives.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Archive,
                    title = "No archives yet",
                    message = "Create a season snapshot above to freeze the current standings."
                )
            }
        }
        items(archives, key = { it.id }) { archive ->
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(archive.name, color = TextHi, style = DeadFestType.cardTitle)
                        Spacer(Modifier.height(2.dp))
                        Text("Saved ${dateFormat.format(Date(archive.timestamp))}", color = TextMid, style = DeadFestType.body)
                        Text("${archive.playerSnapshots.size} survivors", color = Amber, style = DeadFestType.body, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    GhostAction(
                        text = "Restore",
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
            title = "Current season",
            subtitle = "Shown on Team Progress as the season everyone is playing right now."
        ) {
            OutlinedTextField(
                value = seasonName,
                onValueChange = { seasonName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Season name") },
                placeholder = { Text("Dead-Fest Season 1", color = TextLow) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(
                text = "Save season name",
                onClick = {
                    viewModel.updateGlobalState(
                        mapOf("currentSeasonName" to seasonName.trim().ifEmpty { "Dead-Fest Season 1" }),
                        success = "Current season renamed"
                    )
                }
            )
        }

        AdminActionCard(
            title = "Next Dead-Fest",
            subtitle = "The countdown and line-up every survivor sees on the More tab."
        ) {
            AdminSwitch(
                title = "Show on the More tab",
                subtitle = "Displays the countdown and game line-up to every survivor",
                checked = enabled,
                onChange = { enabled = it }
            )
            HorizontalDivider(color = InkLine)
            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Next season name") },
                placeholder = { Text("Dead-Fest Season 2", color = TextLow) },
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
                    Text("START DATE & TIME", color = TextMid, style = DeadFestType.microLabel)
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
                    TextButton(onClick = { startsAt = 0L }) { Text("CLEAR DATE", color = TextMid) }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                label = { Text("Blurb (optional)") },
                placeholder = { Text("Rules, theme, prizes...", color = TextLow) },
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
        }

        AdminActionCard(
            title = "Game line-up",
            subtitle = "The games queued for the next Dead-Fest, in play order."
        ) {
            if (games.isEmpty()) {
                EmptyState(Icons.Default.SportsEsports, "Nothing queued yet. Add games below or pull one in from your sectors.")
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
                                Icon(Icons.Default.ArrowUpward, "Move up", tint = if (index > 0) Ember else TextLow)
                            }
                            IconButton(onClick = {
                                val list = games.toMutableList()
                                list.add(index + 1, list.removeAt(index))
                                games = list
                            }, enabled = index < games.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, "Move down", tint = if (index < games.lastIndex) Ember else TextLow)
                            }
                            IconButton(onClick = { games = games.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Default.Delete, "Remove", tint = Ember)
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
                label = { Text("Add a game") },
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
                            contentDescription = "Add game",
                            tint = if (newGame.isNotBlank()) Ember else TextLow
                        )
                    }
                }
            )

            val suggestions = globalState.games.filter { candidate -> games.none { it.equals(candidate, ignoreCase = true) } }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                SectionLabel("Add from sectors")
                Spacer(Modifier.height(Spacing.xs))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(suggestions) { sector ->
                        PillChip(text = sector, selected = false, onClick = { addGame(sector) })
                    }
                }
            }
        }

        PrimaryAction(
            text = "Save next Dead-Fest",
            onClick = {
                viewModel.updateGlobalState(
                    mapOf(
                        "nextEventEnabled" to enabled,
                        "nextEventName" to eventName.trim(),
                        "nextEventStartsAt" to startsAt,
                        "nextEventGames" to games,
                        "nextEventNotes" to notes.trim()
                    ),
                    success = "Next Dead-Fest saved"
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
            title = "Broadcast to survivors",
            subtitle = "Appears in the live ticker on every survivor's dashboard."
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { if (it.length <= 280) messageText = it },
                label = { Text("Message") },
                supportingText = { Text("${messageText.length}/280", color = TextLow) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(Radius.md),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(
                text = "Broadcast now",
                icon = Icons.Default.Campaign,
                enabled = messageText.isNotBlank(),
                onClick = {
                    viewModel.logEvent("ADMIN_BROADCAST", messageText.trim())
                    viewModel.showMessage("Broadcast transmitted to the wasteland!")
                    messageText = ""
                }
            )
        }
    }
}
