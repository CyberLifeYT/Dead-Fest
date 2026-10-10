package com.deadfest.ui.screens

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.BuildConfig
import com.deadfest.util.BatteryOptimization
import com.deadfest.data.AppPrefs
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("General", "System", "Diagnostics")

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "TERMINAL CONFIG", subtitle = "SYSTEM OPERATIONS & PERSONALIZATION")

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> GeneralTab(viewModel)
                1 -> SystemTab(viewModel)
                2 -> DiagnosticsTab(viewModel)
            }
        }
    }
}

@Composable
private fun GeneralTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    var downloading by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    downloading?.let { (url, name) ->
        UpdateDownloader(url = url, versionName = name, onClose = { downloading = null })
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "TERMINATE SESSION?",
            message = "You'll be logged out of Dead-Fest on this device.",
            confirmText = "LOG OUT",
            destructive = true,
            onConfirm = { viewModel.logout() },
            onDismiss = { confirmLogout = false }
        )
    }

    val updateAvailable = globalState.latestVersionCode > BuildConfig.VERSION_CODE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("ABOUT DEAD-FEST", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "The ultimate companion for your survival journey: your central hub for data tracking, surveillance, and economic warfare.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }

    // Firmware card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (updateAvailable) RedAccent else Outline)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.SystemUpdate, size = 44)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("SYSTEM UPDATES", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("Installed v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = TextSecondary, fontSize = 12.sp)
                }
                if (updateAvailable) StatusPill("NEW", RedAccent) else StatusPill("LATEST", OnlineGreen)
            }
            Button(
                onClick = {
                    viewModel.checkUpdate(
                        BuildConfig.VERSION_CODE,
                        onUpdateAvailable = { url, name -> downloading = url to name },
                        onUpToDate = { viewModel.showMessage("Terminal is up to date (v${BuildConfig.VERSION_NAME}).") }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (updateAvailable) RedAccent else CardBgRaised),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (updateAvailable) "INSTALL v${globalState.latestVersionName}" else "CHECK FOR UPDATES",
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    Button(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/rYq5WywRBG")))
            } catch (_: ActivityNotFoundException) {
                viewModel.showMessage("No browser found to open Discord.")
            }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.Forum, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text("JOIN OUR DISCORD", fontWeight = FontWeight.Black)
    }

    Spacer(Modifier.height(4.dp))
    SectionLabel("SESSION")
    OutlinedButton(
        onClick = { confirmLogout = true },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.6f))
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RedAccent)
        Spacer(Modifier.width(10.dp))
        Text("TERMINATE SESSION (LOGOUT)", color = RedAccent, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SystemTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    val performance by prefs.performanceMode.collectAsStateWithLifecycle()
    val wheel by prefs.wheelAlerts.collectAsStateWithLifecycle()
    val market by prefs.marketAlerts.collectAsStateWithLifecycle()
    val wasteland by prefs.wastelandAlerts.collectAsStateWithLifecycle()
    val lowEnd = remember { AppPrefs.isLowEndDevice(context) }

    BackgroundActivityGroup()

    SettingsGroup("PERFORMANCE") {
        SettingSwitch(
            icon = Icons.Default.Speed,
            title = "Performance mode",
            subtitle = if (lowEnd) "Recommended for this device. Fewer animations and a faster wheel." else "Fewer animations and a faster wheel for older phones.",
            checked = performance,
            onChange = prefs::setPerformanceMode
        )
    }

    SettingsGroup("FEED & ALERTS") {
        SettingSwitch(Icons.Default.Casino, "Wheel spins", "Show other survivors' spins in the live ticker", wheel, prefs::setWheelAlerts)
        HorizontalDivider(color = Outline)
        SettingSwitch(Icons.Default.ShoppingCart, "Black Market", "Purchases and curses", market, prefs::setMarketAlerts)
        HorizontalDivider(color = Outline)
        SettingSwitch(Icons.Default.Campaign, "Wasteland alerts", "Admin broadcasts and firmware notices, including push notifications", wasteland, prefs::setWastelandAlerts)
    }
}

/**
 * Battery optimisation + background restriction status. Re-checked every time the screen
 * resumes, so it updates as soon as the survivor comes back from system settings.
 */
@Composable
private fun BackgroundActivityGroup() {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val unrestricted = remember(lifecycleState) { BatteryOptimization.isIgnoring(context) }
    val restricted = remember(lifecycleState) { BatteryOptimization.isBackgroundRestricted(context) }

    SettingsGroup("BACKGROUND ACTIVITY") {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (unrestricted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = if (unrestricted) OnlineGreen else Gold,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Battery optimisation", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (unrestricted) "Disabled for Dead-Fest. Alerts and updates won't be held back."
                    else "On. Your phone may delay alerts and updates while Dead-Fest is closed.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            if (unrestricted) {
                StatusPill("OFF", OnlineGreen)
            } else {
                Button(
                    onClick = {
                        if (!BatteryOptimization.requestExemption(context)) {
                            BatteryOptimization.openAppSettings(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("DISABLE", fontWeight = FontWeight.Black, fontSize = 12.sp) }
            }
        }
        if (restricted) {
            HorizontalDivider(color = Outline)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, contentDescription = null, tint = RedAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Background usage restricted", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Lift it under App info → Battery → Unrestricted.", color = TextSecondary, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { BatteryOptimization.openAppSettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Outline),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("OPEN", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp) }
            }
        }
        HorizontalDivider(color = Outline)
        Text(
            "Some brands (Xiaomi, Huawei, Oppo, Samsung) have their own extra \"auto-start\" or \"sleeping apps\" lists. Allow Dead-Fest there too.",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun DiagnosticsTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsStateWithLifecycle()
    val performance by viewModel.prefs.performanceMode.collectAsStateWithLifecycle()
    val info = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        listOf(
            "App version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Device RAM" to "${mem.totalMem / (1024 * 1024)} MB",
            "Low-RAM device" to if (am?.isLowRamDevice == true) "YES" else "NO"
        )
    }
    val online = remember { isOnline(context) }

    SettingsGroup("UPLINK") {
        DiagRow("Network", if (online) "CONNECTED" else "OFFLINE", if (online) OnlineGreen else RedAccent)
        DiagRow("Firebase node", if (online) "SECURE" else "WAITING FOR SIGNAL", if (online) OnlineGreen else Gold)
        DiagRow("Survivor ID", user?.uid?.take(10)?.plus("…") ?: "-", TextSecondary)
    }
    SettingsGroup("HARDWARE") {
        info.forEach { (k, v) -> DiagRow(k, v, TextPrimary) }
        DiagRow("Performance mode", if (performance) "ON" else "OFF", if (performance) Gold else TextSecondary)
    }
}

private fun isOnline(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (_: Exception) {
    false
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(label)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressClick(RoundedCornerShape(12.dp)) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (checked) RedAccent else TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RedAccent,
                uncheckedTrackColor = CardBgRaised,
                uncheckedBorderColor = Outline
            )
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
