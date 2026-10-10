package com.deadfest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deadfest.data.AppPrefs
import com.deadfest.domain.Presence
import com.deadfest.ui.components.BatteryOptimizationDialog
import com.deadfest.ui.components.DailyClaimDialog
import com.deadfest.util.BatteryOptimization
import com.deadfest.ui.components.DeathReportDialog
import com.deadfest.ui.components.LevelUpDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deadfest.ui.components.LocalPerformanceMode
import com.deadfest.ui.components.NoticeCard
import com.deadfest.ui.components.UpdateAvailableDialog
import com.deadfest.ui.components.UpdateDownloader
import com.deadfest.ui.screens.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    // Same instance that viewModel() returns inside setContent (same owner).
    private val mainViewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionOnce()

        // Presence: ONLINE + heartbeat while on screen, OFFLINE the moment the app leaves it.
        // (A rotation isn't "leaving", so configuration changes don't flicker the status.)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    while (isActive) {
                        mainViewModel.heartbeat()
                        delay(Presence.HEARTBEAT_MS)
                    }
                } finally {
                    if (!isChangingConfigurations) mainViewModel.goOffline()
                }
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            DeadFestTheme {
                val viewModel: MainViewModel = viewModel()
                val user by viewModel.user.collectAsStateWithLifecycle()
                val performanceMode by viewModel.prefs.performanceMode.collectAsStateWithLifecycle()

                CompositionLocalProvider(LocalPerformanceMode provides performanceMode) {
                    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                        if (user == null) {
                            LoginScreen(viewModel)
                        } else {
                            DeadFestApp(viewModel)
                        }
                    }
                }
            }
        }
    }

    /**
     * Android 13+ blocks every notification (push broadcasts, level-ups) until the user
     * grants POST_NOTIFICATIONS. Ask once; respect the answer afterwards.
     */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}

private data class NavigationItem(val label: String, val route: String, val icon: ImageVector)

@Composable
fun DeadFestApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val user by viewModel.user.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val isAdmin = user?.admin == true
    val performanceMode = LocalPerformanceMode.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // ---- OTA update prompt (force update is enforced here) ----
    var dismissedUpdateCode by rememberSaveable { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf<Pair<String, String>?>(null) }
    val updateAvailable = globalState.latestVersionCode > BuildConfig.VERSION_CODE && globalState.updateUrl.isNotBlank()
    if (downloading != null) {
        UpdateDownloader(
            url = downloading!!.first,
            versionName = downloading!!.second,
            onClose = { downloading = null }
        )
    } else if (updateAvailable && (globalState.forceUpdate || dismissedUpdateCode != globalState.latestVersionCode)) {
        UpdateAvailableDialog(
            versionName = globalState.latestVersionName,
            notes = globalState.updateNotes,
            forced = globalState.forceUpdate,
            onUpdate = { downloading = globalState.updateUrl to globalState.latestVersionName },
            onLater = { dismissedUpdateCode = globalState.latestVersionCode }
        )
    }

    // ---- Result dialogs (one at a time: death report, then rations, then level-up) ----
    val deathReport by viewModel.deathReport.collectAsStateWithLifecycle()
    val dailyResult by viewModel.dailyResult.collectAsStateWithLifecycle()
    val levelUp by viewModel.levelUp.collectAsStateWithLifecycle()
    // First launch: offer to lift battery optimisation (asked once; also in Settings → System).
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBatteryPrompt by remember {
        mutableStateOf(!viewModel.prefs.batteryPromptShown && !BatteryOptimization.isIgnoring(context))
    }
    when {
        deathReport != null -> DeathReportDialog(deathReport!!, viewModel::dismissDeathReport)
        dailyResult != null -> DailyClaimDialog(dailyResult!!, viewModel::dismissDaily)
        levelUp != null -> LevelUpDialog(levelUp!!, viewModel::dismissLevelUp)
        showBatteryPrompt && downloading == null && !(updateAvailable && globalState.forceUpdate) ->
            BatteryOptimizationDialog(
                onAllow = {
                    viewModel.prefs.batteryPromptShown = true
                    showBatteryPrompt = false
                    if (!BatteryOptimization.requestExemption(context)) {
                        viewModel.showMessage("Couldn't open battery settings. Try Settings → System.")
                    }
                },
                onLater = {
                    viewModel.prefs.batteryPromptShown = true
                    showBatteryPrompt = false
                    viewModel.showMessage("You can change this any time in Settings → System.")
                }
            )
    }

    val items = remember(isAdmin) {
        listOfNotNull(
            NavigationItem("Dash", "home", Icons.Default.Dashboard),
            NavigationItem("Players", "players", Icons.Default.People),
            NavigationItem("Shop", "shop", Icons.Default.ShoppingCart),
            NavigationItem("Wheel", "wheel", Icons.Default.Cyclone),
            NavigationItem("More", "more", Icons.Default.GridView),
            if (isAdmin) NavigationItem("Admin", "admin", Icons.Default.AdminPanelSettings) else null,
            NavigationItem("Settings", "settings", Icons.Default.Settings)
        )
    }

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CardBgRaised,
                    contentColor = TextPrimary,
                    actionColor = RedAccent,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        bottomBar = {
            BottomBar(
                items = items,
                isSelected = { route -> currentDestination?.hierarchy?.any { it.route == route } == true },
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { padding ->
        // Short fades feel snappy. The navigation default is a slow 700ms crossfade,
        // and performance mode turns transitions off entirely.
        val enterMs = if (performanceMode) 0 else 180
        val exitMs = if (performanceMode) 0 else 120
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .padding(padding)
                .background(DarkBg),
            enterTransition = { fadeIn(tween(enterMs)) },
            exitTransition = { fadeOut(tween(exitMs)) },
            popEnterTransition = { fadeIn(tween(enterMs)) },
            popExitTransition = { fadeOut(tween(exitMs)) }
        ) {
            composable("home") { HomeScreen(navController, viewModel) }
            composable("players") { PlayersScreen(navController, viewModel) }
            composable("shop") { ShopScreen(navController, viewModel) }
            composable("wheel") { WheelScreen(navController, viewModel) }
            composable("more") { MoreScreen(navController, viewModel) }
            composable("archives") { ArchivesScreen(navController, viewModel) }
            composable("survival_intel") { SurvivalIntelScreen(navController) }
            composable("scratch") { ScratchScreen(navController, viewModel) }
            composable("progression") { ProgressionScreen(navController, viewModel) }
            composable("images") { ImagesScreen(navController, viewModel) }
            composable("admin") {
                if (isAdmin) {
                    AdminScreen(navController, viewModel)
                } else {
                    NoticeCard(
                        icon = Icons.Default.Lock,
                        title = "ACCESS DENIED",
                        message = "SECURITY OVERRIDE REQUIRED. Administrator privileges are needed to access this sector."
                    )
                }
            }
            composable("settings") { SettingsScreen(navController, viewModel) }
        }
    }
}

@Composable
private fun BottomBar(
    items: List<NavigationItem>,
    isSelected: (String) -> Boolean,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF121212),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    BottomBarItem(
                        item = item,
                        selected = isSelected(item.route),
                        onClick = { onSelect(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    item: NavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animMs = if (LocalPerformanceMode.current) 0 else 150
    val tint by animateColorAsState(if (selected) RedAccent else TextMuted, tween(animMs), label = "navTint")
    val pill by animateColorAsState(
        if (selected) RedAccent.copy(alpha = 0.16f) else Color.Transparent,
        tween(animMs),
        label = "navPill"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = RedAccent),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(pill)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}
