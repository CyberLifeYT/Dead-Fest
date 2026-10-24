package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
 * Split into four tabs because the concerns genuinely differ: what the app is (General), how it
 * behaves (System: performance, alerts, background survival), what it's doing right now
 * (Diagnostics), and how it looks (Themes). Alerts and performance are the two settings that
 * actually change the experience, so they are the ones given the most visual weight.
 */
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("General", tr(R.string.settings_system), tr(R.string.settings_diagnostics), tr(R.string.settings_themes))

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = tr(R.string.settings_terminal_config), subtitle = tr(R.string.settings_system_operations_personalization))

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
                3 -> ThemesTab(viewModel)
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun GeneralTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val prefs = viewModel.prefs
    val updateVariant by prefs.updateVariant.collectAsStateWithLifecycle()
    var downloading by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    downloading?.let { (url, name) ->
        UpdateDownloader(url = url, versionName = name, onClose = { downloading = null })
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = tr(R.string.settings_terminate_session),
            message = tr(R.string.settings_you_ll_be_logged_out_of),
            confirmText = tr(R.string.settings_log_out),
            destructive = true,
            onConfirm = { viewModel.logout() },
            onDismiss = { confirmLogout = false }
        )
    }

    val updateAvailable = globalState.latestVersionCode > BuildConfig.VERSION_CODE

    // Firmware is the top block: an available update is the only thing on this screen that changes
    // what the app can do, so it leads.
    HeroPanel(
        eyebrow = if (updateAvailable) tr(R.string.settings_update_available) else tr(R.string.settings_firmware_current),
        title = "v${BuildConfig.VERSION_NAME}",
        icon = Icons.Default.SystemUpdate,
        accent = if (updateAvailable) Ember else OnlineGreen,
        modifier = Modifier.enterOnMount(0)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(R.string.settings_build_1, BuildConfig.VERSION_CODE),
                color = TextMid,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (updateAvailable) StatusPill(globalState.latestVersionName, Ember)
            else StatusPill(tr(R.string.settings_latest), OnlineGreen)
        }
        Spacer(Modifier.height(Spacing.sm))
        // Which of the two published builds to install. They are the same version and the same key,
        // so either installs straight over the other: this is purely size against safety, and it is
        // the survivor's call. A phone that is nearly full wants the R8 build, and anyone chasing
        // something odd on their own device wants the full one.
        Text(tr(R.string.settings_update_build), color = TextLow, style = DeadFestType.microLabel)
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
                    onUpToDate = { viewModel.showMessage(tr(R.string.settings_terminal_is_up_to_date_v, BuildConfig.VERSION_NAME)) }
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
                if (updateAvailable) tr(R.string.settings_install_v_1_2, globalState.latestVersionName, updateVariant.label)
                else tr(R.string.settings_check_for_updates),
                style = DeadFestType.action
            )
        }
    }

    // Identity: the handle the rest of the community sees. Written through the usernames registry,
    // so a rename is atomic and unique, and every screen picks it up from the same listener.
    SettingsGroup(tr(R.string.settings_identity), modifier = Modifier.enterOnMount(1)) {
        Text(
            tr(R.string.settings_what_the_community_sees_on_the),
            color = TextMid,
            style = DeadFestType.body
        )
        Spacer(Modifier.height(Spacing.sm))
        // Remembering against the current handle means an external change (a rename from another
        // device, or a user document that was still loading) resets the field instead of leaving a
        // stale value sitting in the box. `user` is null until the document lands, so the field
        // starts blank and fills itself in.
        val currentHandle = user?.handle ?: ""
        var handleInput by remember(currentHandle) { mutableStateOf(currentHandle) }
        OutlinedTextField(
            value = handleInput,
            onValueChange = { handleInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr(R.string.settings_handle)) },
            supportingText = { Text(tr(R.string.settings_3_20_characters_letters_numbers_and)) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = deadFestFieldColors()
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = { viewModel.changeUsername(handleInput) },
            enabled = handleInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
            shape = RoundedCornerShape(Radius.md),
            elevation = null
        ) {
            Text(tr(R.string.settings_save_handle), style = DeadFestType.action)
        }
    }

    SurfaceCard(modifier = Modifier.enterOnMount(2)) {
        Text(tr(R.string.settings_about_dead_fest), color = TextHi, style = DeadFestType.cardTitle)
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            tr(R.string.settings_the_ultimate_companion_for_your_survival) +
                tr(R.string.settings_surveillance_and_economic_warfare),
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
                Text(tr(R.string.settings_survivor_relay), color = TextHi, style = DeadFestType.cardTitle)
                Text(
                    tr(R.string.settings_chat_arrange_matches_and_get_help),
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
                    viewModel.showMessage(tr(R.string.settings_no_browser_found_to_open_discord))
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2), contentColor = Color.White),
            shape = RoundedCornerShape(Radius.md),
            elevation = null
        ) {
            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(tr(R.string.settings_join_our_discord), style = DeadFestType.action)
        }
    }

    // Destructive action, visually separated, last.
    Spacer(Modifier.height(Spacing.xxs))
    SectionLabel(tr(R.string.settings_session))
    GhostAction(
        text = tr(R.string.settings_terminate_session_log_out),
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
    val personal by prefs.personalAlerts.collectAsStateWithLifecycle()
    val chatNotifs by prefs.chatNotifications.collectAsStateWithLifecycle()
    val backgroundSync by prefs.backgroundSync.collectAsStateWithLifecycle()
    val lowEnd = remember { AppPrefs.isLowEndDevice(context) }

    BackgroundActivityGroup()

    SettingsGroup(tr(R.string.settings_performance), modifier = Modifier.enterOnMount(1)) {
        SettingSwitch(
            icon = Icons.Default.Speed,
            title = tr(R.string.settings_performance_mode),
            subtitle = if (lowEnd) {
                tr(R.string.settings_recommended_for_this_device_turns_off)
            } else {
                tr(R.string.settings_turns_off_animations_trims_the_background)
            },
            checked = performance,
            onChange = prefs::setPerformanceMode
        )
    }

    SettingsGroup(tr(R.string.settings_feed_alerts), modifier = Modifier.enterOnMount(2)) {
        SettingSwitch(
            icon = Icons.Default.Casino,
            title = tr(R.string.settings_wheel_spins),
            subtitle = tr(R.string.settings_show_other_survivors_spins_in_the),
            checked = wheel,
            onChange = prefs::setWheelAlerts
        )
        GroupDivider()
        SettingSwitch(
            icon = Icons.Default.ShoppingCart,
            title = tr(R.string.settings_black_market),
            subtitle = tr(R.string.settings_purchases_and_curses),
            checked = market,
            onChange = prefs::setMarketAlerts
        )
        GroupDivider()
        SettingSwitch(
            icon = Icons.Default.Campaign,
            title = tr(R.string.settings_wasteland_alerts),
            subtitle = tr(R.string.settings_admin_broadcasts_and_firmware_notices_including),
            checked = wasteland,
            onChange = prefs::setWastelandAlerts
        )
        GroupDivider()
        SettingSwitch(
            icon = Icons.Default.NotificationsActive,
            title = stringResource(com.deadfest.R.string.settings_personal_alerts),
            subtitle = stringResource(com.deadfest.R.string.settings_personal_alerts_desc),
            checked = personal,
            onChange = prefs::setPersonalAlerts
        )
        GroupDivider()
        SettingSwitch(
            icon = Icons.Default.Forum,
            title = stringResource(com.deadfest.R.string.settings_chat_notifications),
            subtitle = stringResource(com.deadfest.R.string.settings_chat_notifications_desc),
            checked = chatNotifs,
            onChange = prefs::setChatNotifications
        )
        GroupDivider()
        SettingSwitch(
            icon = Icons.Default.Sync,
            title = stringResource(com.deadfest.R.string.settings_background_sync),
            subtitle = stringResource(com.deadfest.R.string.settings_background_sync_desc),
            checked = backgroundSync,
            onChange = prefs::setBackgroundSync
        )
        GroupDivider()
        val notifContext = LocalContext.current
        SettingSwitchLink(
            icon = Icons.Default.Tune,
            title = stringResource(com.deadfest.R.string.settings_notification_categories),
            subtitle = stringResource(com.deadfest.R.string.settings_notification_categories_desc)
        ) {
            // Android's per-app notification page, where each channel can be muted.
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, notifContext.packageName)
            try { notifContext.startActivity(intent) } catch (_: Exception) { }
        }
    }
}

/** A settings row that opens something instead of toggling. */
@Composable
private fun SettingSwitchLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressClick(androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm), onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextMid, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, style = DeadFestType.cardTitle)
            Text(subtitle, color = TextMid, style = DeadFestType.body)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextLow)
    }
}

/**
 * App themes (v2.0.8).
 *
 * Each option previews with its own real colours — surface, brand and currency — so the choice is
 * concrete rather than a name on a list. Selecting one writes the preference and the whole app
 * restyles immediately: the colour constants all read from the active palette, so there is no
 * per-screen work.
 */
@Composable
private fun ThemesTab(viewModel: MainViewModel) {
    val theme by viewModel.prefs.theme.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().enterOnMount(0),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            tr(R.string.settings_restyle_the_terminal_semantic_colours_stay) +
                tr(R.string.settings_currency_green_always_success_so_a),
            color = TextMid,
            style = DeadFestType.body,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(Spacing.xxs))
        AppPalettes.forEachIndexed { index, palette ->
            ThemeOption(
                palette = palette,
                selected = palette.name.equals(theme, ignoreCase = true),
                onSelect = { viewModel.prefs.setTheme(palette.name) },
                modifier = Modifier.enterOnMount(index + 1)
            )
        }
    }
}

@Composable
private fun ThemeOption(
    palette: DeadFestPalette,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        modifier = modifier,
        accent = if (selected) palette.ember else null,
        onClick = onSelect
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The palette's own colours, drawn at the size they will actually be used at.
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(palette.ink2)
                    .border(BorderStroke(Stroke.hair, palette.inkLine), RoundedCornerShape(Radius.md))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(palette.ink4, palette.ink2)))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.ember)
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.amber)
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    palette.name,
                    color = if (selected) TextHi else TextMid,
                    style = DeadFestType.cardTitle
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (selected) tr(R.string.admin_active) else tr(R.string.settings_tap_to_apply),
                    color = if (selected) palette.ember else TextLow,
                    style = DeadFestType.body
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = palette.ember,
                    unselectedColor = TextLow
                )
            )
        }
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

    SettingsGroup(tr(R.string.settings_background_activity), modifier = Modifier.enterOnMount(0)) {
        StatusRow(
            icon = if (unrestricted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
            tint = if (unrestricted) OnlineGreen else Amber,
            title = tr(R.string.settings_battery_optimisation),
            subtitle = if (unrestricted) {
                tr(R.string.settings_disabled_for_dead_fest_alerts_and)
            } else {
                tr(R.string.settings_on_your_phone_may_delay_alerts)
            },
            trailing = {
                if (unrestricted) {
                    StatusPill(tr(R.string.settings_off), OnlineGreen)
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
                    ) { Text(tr(R.string.settings_disable), style = DeadFestType.chip) }
                }
            }
        )
        if (restricted) {
            GroupDivider()
            StatusRow(
                icon = Icons.Default.Block,
                tint = Ember,
                title = tr(R.string.settings_background_usage_restricted),
                subtitle = tr(R.string.settings_lift_it_under_app_info_battery),
                trailing = {
                    GhostAction(
                        text = tr(R.string.settings_open),
                        onClick = { BatteryOptimization.openAppSettings(context) },
                        accent = TextHi
                    )
                }
            )
        }
        GroupDivider()
        Text(
            tr(R.string.settings_some_brands_xiaomi_huawei_oppo_samsung) +
                tr(R.string.settings_sleeping_apps_lists_allow_dead_fest),
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
            tr(R.string.settings_app_version) to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Android" to tr(R.string.settings_1_api_2, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            tr(R.string.settings_device) to "${Build.MANUFACTURER} ${Build.MODEL}",
            tr(R.string.settings_device_ram) to tr(R.string.settings_1_mb, mem.totalMem / (1024 * 1024)),
            tr(R.string.settings_low_ram_device) to if (am?.isLowRamDevice == true) tr(R.string.settings_yes) else tr(R.string.settings_no)
        )
    }
    val online = remember { isOnline(context) }

    SettingsGroup(tr(R.string.settings_uplink), modifier = Modifier.enterOnMount(0)) {
        DiagRow(tr(R.string.settings_network), if (online) tr(R.string.settings_connected) else tr(R.string.settings_offline), if (online) OnlineGreen else Ember)
        DiagRow(tr(R.string.settings_firebase_node), if (online) tr(R.string.settings_secure) else tr(R.string.settings_waiting_for_signal), if (online) OnlineGreen else Amber)
        DiagRow(tr(R.string.settings_survivor_id), user?.uid?.take(10)?.plus("…") ?: "-", TextMid)
    }
    SettingsGroup(tr(R.string.settings_hardware), modifier = Modifier.enterOnMount(1)) {
        info.forEach { (k, v) -> DiagRow(k, v, TextHi) }
        DiagRow(tr(R.string.settings_performance_mode), if (performance) tr(R.string.settings_on) else tr(R.string.settings_off), if (performance) Amber else TextMid)
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
