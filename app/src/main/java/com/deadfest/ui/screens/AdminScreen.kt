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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.Archive
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

@Composable
fun AdminScreen(navController: NavController, viewModel: MainViewModel) {
    var module by rememberSaveable { mutableStateOf(AdminModule.LIVE) }
    var selectedPlayerUid by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        if (selectedPlayerUid != null) {
            PlayerDetailView(selectedPlayerUid!!, viewModel) { selectedPlayerUid = null }
            return@Column
        }

        ScreenHeader(title = "ADMIN OVERRIDE", subtitle = "ROOT ACCESS // HANDLE WITH CARE")

        // Horizontal module picker. The old 140dp sidebar ate half of a phone screen.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AdminModule.entries.size) { i ->
                val m = AdminModule.entries[i]
                FilterChip(
                    selected = module == m,
                    onClick = { module = m },
                    label = { Text(m.label.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp) },
                    leadingIcon = { Icon(m.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardBg,
                        labelColor = TextSecondary,
                        iconColor = TextSecondary,
                        selectedContainerColor = RedAccent,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = module == m,
                        borderColor = Outline,
                        selectedBorderColor = RedAccent
                    )
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Find survivor...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = deadFestFieldColors()
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(filtered, key = { it.uid }) { user ->
                Surface(
                    modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(14.dp)) { onPlayerClick(user.uid) },
                    color = CardBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(user.playerData.avatar?.takeIf { it.isNotBlank() } ?: "💀", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                user.displayName?.takeIf { it.isNotBlank() } ?: "Unknown",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${formatNumber(user.playerData.coins)} coins · ${formatNumber(user.playerData.games.values.sumOf { it.deaths })} deaths",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (user.admin) StatusPill("ADMIN", RedAccent)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }
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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdminActionCard("COINS: ${formatNumber(user.playerData.coins)}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-100L, -10L, 10L, 100L).forEach { delta ->
                        OutlinedButton(
                            onClick = { viewModel.updatePlayerCoins(uid, delta) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Outline)
                        ) {
                            Text(if (delta > 0) "+$delta" else "$delta", color = if (delta > 0) OnlineGreen else RedAccent, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = coinInput,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 9) coinInput = v },
                        label = { Text("Set exact coins") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = deadFestFieldColors()
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { coinInput.toLongOrNull()?.let { viewModel.setPlayerCoins(uid, it) } },
                        enabled = coinInput.toLongOrNull() != null,
                        colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) { Text("SET", fontWeight = FontWeight.Black) }
                }
            }

            AdminActionCard("DEATHS") {
                user.playerData.games.toSortedMap().forEach { (game, stats) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(game, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(formatNumber(stats.deaths), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { sectorDialog = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("INJECT DEATH", fontWeight = FontWeight.Black, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { sectorDialog = -1 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Outline)
                    ) { Text("REMOVE DEATH", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp) }
                }
            }

            AdminActionCard("STATUS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin rights", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (uid == me?.uid) "You can't revoke your own access" else "Grants full access to this panel",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = user.admin,
                        enabled = uid != me?.uid,
                        onCheckedChange = { viewModel.togglePlayerAdmin(uid, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RedAccent)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Shield: ${if (user.playerData.shield) "ACTIVE" else "none"} · Lucky coin: ${if (user.playerData.luckyCoin) "ARMED" else "none"} · " +
                        "Insurance: ${if (user.playerData.insurance) "ARMED" else "none"}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            AdminActionCard("PROGRESSION & PENALTIES") {
                val d = user.playerData
                Text(
                    "Level ${d.level} · ${formatNumber(d.xp)} XP" + (if (d.prestigeCount > 0) " · Prestige ${d.prestigeCount}" else ""),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "Insulation: ${if (d.insulationTier > 0) "Tier ${com.deadfest.data.model.ShopItem.roman(d.insulationTier)}" else "none"} · " +
                        "Ration upgrade: ${if (d.dailyUpgradeOwned) "owned" else "no"}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (d.bankruptcyLevel > 0) "BANKRUPTCY LEVEL ${d.bankruptcyLevel}" else "Solvent",
                            color = if (d.bankruptcyLevel > 0) RedAccent else OnlineGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Text("${d.pendingSpins} pending spin(s)", color = TextSecondary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = d.bankruptcyLevel > 0 || d.pendingSpins > 0,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Outline)
                    ) { Text("CLEAR", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    sectorDialog?.let { delta ->
        val sectors = if (delta > 0) globalState.games
        else user?.playerData?.games?.filterValues { it.deaths > 0 }?.keys?.sorted().orEmpty()
        AlertDialog(
            onDismissRequest = { sectorDialog = null },
            containerColor = CardBg,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    if (delta > 0) "INJECT DEATH (TAX + SPIN APPLY)" else "REMOVE DEATH FROM",
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            },
            text = {
                if (sectors.isEmpty()) {
                    Text(if (delta > 0) "No sectors configured." else "This survivor has no deaths.", color = TextSecondary)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 320.dp)) {
                        items(sectors) { game ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(10.dp)) {
                                    viewModel.updatePlayerDeaths(uid, delta, game)
                                    sectorDialog = null
                                },
                                color = CardBgRaised,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(game.uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (game in globalState.lockedGames) StatusPill("LOCKED", TextMuted)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sectorDialog = null }) { Text("CANCEL", color = TextSecondary) }
            }
        )
    }
}

@Composable
internal fun AdminActionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = RedAccent, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { editingItem = WheelItem(id = System.currentTimeMillis().toString(), label = "New Item", weight = 1.0) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("ADD SLICE", fontWeight = FontWeight.Black)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(wheelItems, key = { it.id.ifEmpty { it.hashCode().toString() } }) { item ->
                val chance = if (item.enabled && totalWeight > 0) item.weight.coerceAtLeast(0.0) / totalWeight * 100 else 0.0
                Surface(
                    modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(14.dp)) { editingItem = item },
                    color = CardBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Row(modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).background(parseThemeColor(item.color), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label,
                                color = if (item.enabled) TextPrimary else TextMuted,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (!item.enabled) "DISABLED"
                                else "${item.actionType.uppercase()} ${item.value.ifBlank { "-" }} · w ${trimDouble(item.weight)} · ${"%.1f".format(chance)}%",
                                color = if (item.isTake) RedAccent else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { deleting = item }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
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
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text("EDIT SLICE", color = TextPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, colors = deadFestFieldColors(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value, { value = it }, label = { Text("Coins / value") }, singleLine = true,
                        colors = deadFestFieldColors(), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        weight, { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) weight = v },
                        label = { Text("Weight") }, singleLine = true, isError = weightValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = deadFestFieldColors(), modifier = Modifier.weight(1f)
                    )
                }
                Text("Weights can be decimals, e.g. 0.25 for ultra-rare loot.", color = TextMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = actionType == "give", onClick = { actionType = "give" }, label = { Text("GIVE") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = actionType == "take", onClick = { actionType = "take" }, label = { Text("TAKE") })
                    Spacer(Modifier.weight(1f))
                    Text("On", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = RedAccent))
                }
                SectionLabel("COLOUR")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(palette) { hex ->
                        val selected = color.equals(hex, ignoreCase = true)
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(parseThemeColor(hex), CircleShape)
                                .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
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
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SAVE", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL", color = TextSecondary) } }
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = newGameName,
            onValueChange = { newGameName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("New sector designation") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = deadFestFieldColors(),
            trailingIcon = {
                IconButton(onClick = { addSector() }, enabled = newGameName.isNotBlank()) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add sector", tint = if (newGameName.isNotBlank()) RedAccent else TextMuted)
                }
            }
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(globalState.games) { index, game ->
                val isLocked = game in globalState.lockedGames
                Surface(
                    modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(14.dp)) { selectedSector = game },
                    color = CardBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (isLocked) Outline else RedAccent.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(game, color = if (isLocked) TextMuted else TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                if (isLocked) "LOCKED" else "ACTIVE",
                                color = if (isLocked) TextMuted else OnlineGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        IconButton(onClick = {
                            val list = globalState.games.toMutableList()
                            list.add(index - 1, list.removeAt(index))
                            viewModel.updateGlobalState(mapOf("games" to list))
                        }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, "Move up", tint = if (index > 0) RedAccent else Outline)
                        }
                        IconButton(onClick = {
                            val list = globalState.games.toMutableList()
                            list.add(index + 1, list.removeAt(index))
                            viewModel.updateGlobalState(mapOf("games" to list))
                        }, enabled = index < globalState.games.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, "Move down", tint = if (index < globalState.games.lastIndex) RedAccent else Outline)
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
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text("SECTOR: $name", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Rename sector") },
                    singleLine = true,
                    colors = deadFestFieldColors()
                )
                OutlinedButton(
                    onClick = onToggleLock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Icon(if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock, null, tint = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isLocked) "UNLOCK SECTOR" else "LOCK SECTOR", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Delete, null, tint = RedAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("REMOVE SECTOR", color = RedAccent, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(newName) },
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SAVE", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextSecondary) } }
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

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (globalState.overloadActive()) {
            val left = if (globalState.overloadEndsAt > 0) " · ${remainingLabel(globalState.overloadEndsAt)} LEFT" else ""
            StatusPill("OVERLOAD ACTIVE: ${globalState.overloadedSector.uppercase()} x${trimDouble(globalState.multiplier)}$left", Gold)
        }
        SectionLabel("SELECT TARGET SECTOR")
        games.forEach { game ->
            val isSelected = selectedGame == game
            Surface(
                modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(12.dp)) { selectedGame = game },
                color = if (isSelected) RedAccent.copy(alpha = 0.18f) else CardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isSelected) RedAccent else Outline)
            ) {
                Text(
                    game,
                    modifier = Modifier.padding(14.dp),
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                )
            }
        }

        OutlinedTextField(
            value = multiplier,
            onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) multiplier = v },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Coin multiplier") },
            prefix = { Text("x ", color = RedAccent) },
            singleLine = true,
            isError = multiplierValue == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            colors = deadFestFieldColors()
        )

        OutlinedTextField(
            value = durationMin,
            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 5) durationMin = v },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Duration in minutes (0 = until terminated)") },
            singleLine = true,
            isError = durationValue == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = deadFestFieldColors()
        )

        Button(
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
            enabled = selectedGame != null && multiplierValue != null && durationValue != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
            shape = RoundedCornerShape(14.dp)
        ) { Text("INITIATE OVERLOAD", fontWeight = FontWeight.Black) }

        if (globalState.sectorOverload) {
            OutlinedButton(
                onClick = { viewModel.updateGlobalState(mapOf("sectorOverload" to false, "overloadedSector" to "", "overloadEndsAt" to 0L), success = "Overload terminated") },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Outline)
            ) { Text("TERMINATE OVERLOAD PROTOCOL", color = TextPrimary, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(16.dp))
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(18.dp)) { showFirmwareDialog = true },
            color = CardBg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.SystemUpdate, size = 48)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("PUBLISH FIRMWARE", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("Live: v${globalState.latestVersionName} (build ${globalState.latestVersionCode})", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
            }
        }

        AdminActionCard("SYSTEM SWITCHES") {
            AdminSwitch("Black Market access", "Enable or disable shop purchases", globalState.marketEnabled) {
                viewModel.updateGlobalState(mapOf("marketEnabled" to it))
            }
            HorizontalDivider(color = Outline)
            AdminSwitch("Wheel of Fate", "Enable or disable wheel spinning", globalState.wheelEnabled) {
                viewModel.updateGlobalState(mapOf("wheelEnabled" to it))
            }
            HorizontalDivider(color = Outline)
            AdminSwitch("Revives", "Allow survivors to buy Revives", globalState.reviveEnabled) {
                viewModel.updateGlobalState(mapOf("reviveEnabled" to it))
            }
            HorizontalDivider(color = Outline)
            AdminSwitch("Lockdown protocol", "Freeze core features & death recording", globalState.lockdownActive, danger = true) { on ->
                if (on) confirmLockdown = true
                else viewModel.updateGlobalState(mapOf("lockdownActive" to false), success = "Lockdown lifted")
            }
        }

        XpConfigCard(globalState.economy.xp) { key, value ->
            viewModel.updateGlobalState(mapOf("xpConfig" to mapOf(key to value)), success = "XP settings saved")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun AdminSwitch(title: String, subtitle: String, checked: Boolean, danger: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger && checked) RedAccent else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RedAccent, uncheckedTrackColor = CardBgRaised)
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
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text("PUBLISH FIRMWARE", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { if (it.all { c -> c.isDigit() }) versionCode = it },
                        label = { Text("Code") },
                        singleLine = true,
                        isError = codeValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.4f),
                        colors = deadFestFieldColors()
                    )
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text("Version name") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f),
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
                    colors = deadFestFieldColors()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Protocol logs (notes)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = deadFestFieldColors()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().pressClick(RoundedCornerShape(8.dp)) { forceUpdate = !forceUpdate }
                ) {
                    Checkbox(
                        checked = forceUpdate,
                        onCheckedChange = { forceUpdate = it },
                        colors = CheckboxDefaults.colors(checkedColor = RedAccent, checkmarkColor = Color.White)
                    )
                    Column {
                        Text("FORCE PROTOCOL UPGRADE", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Survivors can't dismiss the update prompt", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(codeValue ?: currentVersionCode, versionName.trim(), apkUrl.trim(), notes.trim(), forceUpdate) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("PUBLISH", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABORT", color = TextSecondary, fontWeight = FontWeight.Bold) } }
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
            containerColor = CardBg,
            shape = RoundedCornerShape(24.dp),
            title = { Text("⚠️ RESET SEASON", color = RedAccent, fontWeight = FontWeight.Black, fontSize = 20.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This snapshots every survivor's stats, then resets all deaths to 0 and all coins to 250.",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = archiveNameToCreate,
                        onValueChange = { archiveNameToCreate = it },
                        label = { Text("Archive name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("CONFIRM & RESET", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { showWarningAlert = false }) { Text("CANCEL", color = TextSecondary) } }
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Button(
                onClick = { showWarningAlert = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(14.dp)
            ) { Text("CREATE ARCHIVE & RESET SEASON", fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }
        item { SectionLabel("EXISTING ARCHIVES", Modifier.padding(top = 8.dp)) }
        if (archives.isEmpty()) {
            item { EmptyState(Icons.Default.Archive, "No archives yet.") }
        }
        items(archives, key = { it.id }) { archive ->
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Outline)) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(archive.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Saved ${dateFormat.format(Date(archive.timestamp))}", color = TextSecondary, fontSize = 12.sp)
                        Text("${archive.playerSnapshots.size} survivors", color = RedAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { archiveToRestore = archive },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Outline)
                    ) { Text("RESTORE", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AdminActionCard("CURRENT SEASON") {
            Text(
                "Shown on Team Progress as the season everyone is playing right now.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = seasonName,
                onValueChange = { seasonName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Season name") },
                placeholder = { Text("Dead-Fest Season 1", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    viewModel.updateGlobalState(
                        mapOf("currentSeasonName" to seasonName.trim().ifEmpty { "Dead-Fest Season 1" }),
                        success = "Current season renamed"
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SAVE SEASON NAME", fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }

        AdminActionCard("NEXT DEAD-FEST") {
            AdminSwitch(
                title = "Show on the More tab",
                subtitle = "Displays the countdown and game line-up to every survivor",
                checked = enabled,
                onChange = { enabled = it }
            )
            HorizontalDivider(color = Outline)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Next season name") },
                placeholder = { Text("Dead-Fest Season 2", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = deadFestFieldColors()
            )

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    showDateTimePicker(context, startsAt) { picked -> startsAt = picked }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (startsAt > 0) RedAccent else Outline)
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = if (startsAt > 0) RedAccent else TextSecondary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("START DATE & TIME", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(
                        formatEventTime(startsAt),
                        color = if (startsAt > 0) TextPrimary else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
            if (startsAt > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(formatCountdown(startsAt - System.currentTimeMillis()), Gold)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { startsAt = 0L }) { Text("CLEAR DATE", color = TextSecondary, fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                label = { Text("Blurb (optional)") },
                placeholder = { Text("Rules, theme, prizes...", color = TextMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = deadFestFieldColors()
            )
        }

        AdminActionCard("GAME LINE-UP") {
            Text(
                "The games queued for the next Dead-Fest, in play order.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))

            if (games.isEmpty()) {
                Text("Nothing queued yet.", color = TextMuted, fontSize = 12.sp)
            } else {
                games.forEachIndexed { index, game ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        color = CardBgRaised,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}.", color = RedAccent, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                game,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = {
                                val list = games.toMutableList()
                                list.add(index - 1, list.removeAt(index))
                                games = list
                            }, enabled = index > 0) {
                                Icon(Icons.Default.ArrowUpward, "Move up", tint = if (index > 0) RedAccent else Outline)
                            }
                            IconButton(onClick = {
                                val list = games.toMutableList()
                                list.add(index + 1, list.removeAt(index))
                                games = list
                            }, enabled = index < games.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, "Move down", tint = if (index < games.lastIndex) RedAccent else Outline)
                            }
                            IconButton(onClick = { games = games.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Default.Delete, "Remove", tint = TextMuted)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = newGame,
                onValueChange = { newGame = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Add a game") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = deadFestFieldColors(),
                trailingIcon = {
                    IconButton(
                        onClick = { addGame(newGame); newGame = "" },
                        enabled = newGame.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = "Add game",
                            tint = if (newGame.isNotBlank()) RedAccent else TextMuted
                        )
                    }
                }
            )

            val suggestions = globalState.games.filter { candidate -> games.none { it.equals(candidate, ignoreCase = true) } }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionLabel("ADD FROM SECTORS")
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { sector ->
                        FilterChip(
                            selected = false,
                            onClick = { addGame(sector) },
                            label = { Text(sector, fontSize = 12.sp) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
        }

        Button(
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
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
            shape = RoundedCornerShape(14.dp)
        ) { Text("SAVE NEXT DEAD-FEST", fontWeight = FontWeight.Black) }

        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- broadcast

@Composable
private fun NotificationsAdmin(viewModel: MainViewModel) {
    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AdminActionCard("BROADCAST TO SURVIVORS") {
            Text(
                "Appears in the live ticker on every survivor's dashboard.",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = messageText,
                onValueChange = { if (it.length <= 280) messageText = it },
                label = { Text("Message") },
                supportingText = { Text("${messageText.length}/280", color = TextMuted) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = deadFestFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.logEvent("ADMIN_BROADCAST", messageText.trim())
                    viewModel.showMessage("Broadcast transmitted to the wasteland!")
                    messageText = ""
                },
                enabled = messageText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("BROADCAST NOW", fontWeight = FontWeight.Black)
            }
        }
    }
}
