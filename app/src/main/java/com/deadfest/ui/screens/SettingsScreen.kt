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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.deadfest.domain.UpdateVariant
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

/**
 * Terminal configuration.
 *
 * Split into three tabs because the concerns genuinely differ: what the app is (General), how it
 * behaves (System: performance, alerts, background survival), and what it's doing right now
 * (Diagnostics). Alerts and performance are the two settings that actually change the experience,
 * so they are the ones given the most visual weight.
 */
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("General", "System", "Diagnostics")

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "TERMINAL CONFIG", subtitle = "System Operations & Personalization")

        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs.size) { index ->
                PillChip(
                    text = tabs[index],
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            when (selectedTab) {
                0 -> GeneralTab(viewModel)
                1 -> SystemTab(viewModel)
                2 -> DiagnosticsTab(viewModel)
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun GeneralTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val prefs = viewModel.prefs
    val updateVariant by prefs.updateVariant.collectAsStateWithLifecycle()
    var downloading by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    downloading?.let { (url, name) ->
        UpdateDownloader(url = url, versionName = name, onClose = { downloading = null })
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "TERMINATE SESSION?",
            message = "You'll be logged out of Dead-Fest on this device. Your coins, level and history stay safe on the server.",
            confirmText = "LOG OUT",
            destructive = true,
            onConfirm = { viewModel.logout() },
            onDismiss = { confirmLogout = false }
        )
    }

    val updateAvailable = globalState.latestVersionCode > BuildConfig.VERSION_CODE

    // Firmware is the top block: an available update is the only thing on this screen that changes
    // what the app can do, so it leads.
    HeroPanel(
        eyebrow = if (updateAvailable) "Update available" else "Firmware current",
        title = "v${BuildConfig.VERSION_NAME}",
        icon = Icons.Default.SystemUpdate,
        accent = if (updateAvailable) Ember else OnlineGreen,
        modifier = Modifier.enterOnMount(0)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Build ${BuildConfig.VERSION_CODE}",
                color = TextMid,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (updateAvailable) StatusPill(globalState.latestVersionName, Ember)
            else StatusPill("LATEST", OnlineGreen)
        }
        Spacer(Modifier.height(Spacing.sm))
        // Which of the two published builds to install. They are the same version and the same key,
        // so either installs straight over the other: this is purely size against safety, and it is
        // the survivor's call. A phone that is nearly full wants the R8 build, and anyone chasing
        // something odd on their own device wants the full one.
        Text("UPDATE BUILD", color = TextLow, style = DeadFestType.microLabel)
        Spacer(Modifier.height(Spacing.xxs))
        UpdateVariant.entries.forEach { option ->
            UpdateBuildOption(
                variant = option,
                selected = option == updateVariant,
                onSelect = { prefs.setUpdateVariant(option) }
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = {
                viewModel.checkUpdate(
                    BuildConfig.VERSION_CODE,
                    onUpdateAvailable = { url, name -> downloading = url to name },
                    onUpToDate = { viewModel.showMessage("Terminal is up to date (v${BuildConfig.VERSION_NAME}).") }
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (updateAvailable) Ember else Ink4,
                contentColor = if (updateAvailable) Color.White else TextHi
            ),
            shape = RoundedCornerShape(Radius.md),
            elevation = null
        ) {
            Text(
                // Naming the build matters: two downloads, same version, and the button is the last
                // thing seen before a 26 MB or a 3.8 MB file starts.
                if (updateAvailable) "INSTALL v${globalState.latestVersionName} · ${updateVariant.label}"
                else "CHECK FOR UPDATES",
                style = DeadFestType.action
            )
        }
    }

    SurfaceCard(modifier = Modifier.enterOnMount(1)) {
        Text("ABOUT DEAD-FEST", color = TextHi, style = DeadFestType.cardTitle)
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            "The ultimate companion for your survival journey: your central hub for data tracking, " +
                "surveillance, and economic warfare.",
            color = TextMid,
            style = DeadFestType.body,
            lineHeight = 20.sp
        )
    }

    SurfaceCard(modifier = Modifier.enterOnMount(2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Forum, tint = Color(0xFF5865F2), size = 44)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text("SURVIVOR RELAY", color = TextHi, style = DeadFestType.cardTitle)
                Text(
                    "Chat, arrange matches and get help from the rest of the group.",
                    color = TextMid,
                    style = DeadFestType.body
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/rYq5WywRBG")))
                } catch (_: ActivityNotFoundException) {
                    viewModel.showMessage("No browser found to open Discord.")
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2), contentColor = Color.White),
            shape = RoundedCornerShape(Radius.md),
            elevation = null
        ) {
            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("JOIN OUR DISCORD", style = DeadFestType.action)
        }
    }

    // Destructive action, visually separated, last.
    Spacer(Modifier.height(Spacing.xxs))
    SectionLabel("Session")
    GhostAction(
        text = "Terminate session (log out)",
        onClick = { confirmLogout = true },
        accent = Ember,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * One selectable build in the update chooser.
 *
 * The whole row is the touch target, not just the radio, so it is easy to hit one-handed. The radio
 * itself is left non-interactive on purpose: two controls doing the same thing would make TalkBack
 * read each build twice.
 */
@Composable
private fun UpdateBuildOption(
    variant: UpdateVariant,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressClick(RoundedCornerShape(Radius.md), onClick = onSelect)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Ember, unselectedColor = TextLow)
        )
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                variant.label,
                color = if (selected) TextHi else TextMid,
                style = DeadFestType.body,
                fontWeight = FontWeight.Bold
            )
            Text(variant.blurb, color = TextLow, style = DeadFestType.body)
        }
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

    SettingsGroup("Performance", modifier = Modifier.enterOnMount(1)) {
        SettingSwitch(
            icon = Icons.Default.Speed,
            title = "Performance mode",
            subtitle = if (lowEnd) {
                "Recommended for this device. Turns off animations and drops the countdown to one update every 30 seconds."
            } else {
                "Turns off animations, trims the background grid and drops the countdown to one update every 30 seconds."
            },
            checked = performance,
            onChange = prefs::setPerformanceMode
        )
    }

    SettingsGroup("Feed & alerts", modifier = Modifier.enterOnMount(2)) {
        SettingSwitch(
            icon = Icons.Default.Casino,
            title = "Wheel spins",
            subtitle = "Show other survivors' spins in the live ticker",
            checked = wheel,
            onChange = prefs::setWheelAlerts
        )
        GroupDivider()
        SettingSwitch(
            icon = Icons.Default.ShoppingCart,
            title = "Black Market",
            subtitle = "Purchases and curses",
            checked = market,
            onChange = prefs::setMarketAlerts
        )
        GroupDivider()
        SettingSwitch(
            icon = Icons.Default.Campaign,
            title = "Wasteland alerts",
            subtitle = "Admin broadcasts and firmware notices, including push notifications",
            checked = wasteland,
            onChange = prefs::setWastelandAlerts
        )
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

    SettingsGroup("Background activity", modifier = Modifier.enterOnMount(0)) {
        StatusRow(
            icon = if (unrestricted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
            tint = if (unrestricted) OnlineGreen else Amber,
            title = "Battery optimisation",
            subtitle = if (unrestricted) {
                "Disabled for Dead-Fest. Alerts and updates won't be held back."
            } else {
                "On. Your phone may delay alerts and updates while Dead-Fest is closed."
            },
            trailing = {
                if (unrestricted) {
                    StatusPill("OFF", OnlineGreen)
                } else {
                    Button(
                        onClick = {
                            if (!BatteryOptimization.requestExemption(context)) {
                                BatteryOptimization.openAppSettings(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
                        shape = RoundedCornerShape(Radius.sm),
                        elevation = null,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("DISABLE", style = DeadFestType.chip) }
                }
            }
        )
        if (restricted) {
            GroupDivider()
            StatusRow(
                icon = Icons.Default.Block,
                tint = Ember,
                title = "Background usage restricted",
                subtitle = "Lift it under App info → Battery → Unrestricted.",
                trailing = {
                    GhostAction(
                        text = "Open",
                        onClick = { BatteryOptimization.openAppSettings(context) },
                        accent = TextHi
                    )
                }
            )
        }
        GroupDivider()
        Text(
            "Some brands (Xiaomi, Huawei, Oppo, Samsung) have their own extra \"auto-start\" or " +
                "\"sleeping apps\" lists. Allow Dead-Fest there too.",
            color = TextLow,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(vertical = Spacing.xs)
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

    SettingsGroup("Uplink", modifier = Modifier.enterOnMount(0)) {
        DiagRow("Network", if (online) "CONNECTED" else "OFFLINE", if (online) OnlineGreen else Ember)
        DiagRow("Firebase node", if (online) "SECURE" else "WAITING FOR SIGNAL", if (online) OnlineGreen else Amber)
        DiagRow("Survivor ID", user?.uid?.take(10)?.plus("…") ?: "-", TextMid)
    }
    SettingsGroup("Hardware", modifier = Modifier.enterOnMount(1)) {
        info.forEach { (k, v) -> DiagRow(k, v, TextHi) }
        DiagRow("Performance mode", if (performance) "ON" else "OFF", if (performance) Amber else TextMid)
    }
}

private fun isOnline(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (_: Exception) {
    false
}

// ================================================================== shared bits

/** Hairline between rows inside one settings card. */
@Composable
private fun GroupDivider() = HorizontalDivider(color = InkLine, thickness = Stroke.hair)

@Composable
private fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(Spacing.xs))
        SurfaceCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

/** Icon + title + subtitle + trailing control. Shared by settings rows so they align. */
@Composable
private fun StatusRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = TextMid, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(Spacing.xs))
        trailing()
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
    StatusRow(
        icon = icon,
        tint = if (checked) Ember else TextLow,
        title = title,
        subtitle = subtitle,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Ember,
                    uncheckedTrackColor = Ink4,
                    uncheckedBorderColor = InkLine
                )
            )
        }
    )
}

@Composable
private fun DiagRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMid, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
