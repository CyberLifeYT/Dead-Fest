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
import com.deadfest.ui.components.AchievementUnlockedDialog
import androidx.compose.ui.res.stringResource
import com.deadfest.ui.components.DeadFestBackground
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.atomic.AtomicBoolean

/** Longest the branded splash may ever stay up, in milliseconds. See [MainActivity.splashHeld]. */
private const val SPLASH_MAX_MS = 2_000L

class MainActivity : ComponentActivity() {
    // Same instance that viewModel() returns inside setContent (same owner).
    private val mainViewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * True while the splash is deliberately held over the first frame.
     *
     * A returning survivor is signed in from a session on disk, but Firebase hands that session
     * back asynchronously. Without this the app would draw its first frame before the user object
     * exists — an empty dashboard, and for a split second possibly even the login screen, which
     * looks like the app has forgotten you. Holding the splash until the user lands removes that
     * entirely.
     *
     * It is bounded twice over: a device with no saved session releases immediately (there is
     * nothing to wait for), and [SPLASH_MAX_MS] caps the wait so a broken session can never trap
     * anyone behind a logo.
     */
    private val splashHeld = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() so the splash exists before the window is created.
        val splash = installSplashScreen()
        splashHeld.set(FirebaseAuth.getInstance().currentUser != null)
        splash.setKeepOnScreenCondition { splashHeld.get() }

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

                // Hand the screen over the instant the survivor is known — and unconditionally
                // after SPLASH_MAX_MS, so an expired session can never strand anyone on the logo.
                LaunchedEffect(user) { if (user != null) splashHeld.set(false) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MAX_MS)
                    splashHeld.set(false)
                }

                CompositionLocalProvider(LocalPerformanceMode provides performanceMode) {
                    // The stored theme is pushed into the palette holder once, here, and again
                    // whenever it changes — so picking a theme in Settings restyles the whole app
                    // with no per-screen work.
                    LaunchedEffect(Unit) { viewModel.prefs.theme.collect { com.deadfest.ui.theme.DeadFestTheme.select(it) } }

                    // One ambient background paints the whole app: the ink base, the ember and
                    // plasma washes, and (outside Performance Mode) the terminal grid. Screens
                    // stay transparent so nothing is drawn twice and no screen pays for it.
                    DeadFestBackground {
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
    // Which of the two published builds this device wants, resolved to a concrete URL. Resolved
    // here as well as inside checkUpdate so the prompt and the button can never disagree about
    // what is about to be installed.
    val updateVariant by viewModel.prefs.updateVariant.collectAsStateWithLifecycle()
    val updateUrl = globalState.updateUrlFor(updateVariant)
    val updateAvailable = globalState.latestVersionCode > BuildConfig.VERSION_CODE && updateUrl.isNotBlank()
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
            onUpdate = { downloading = updateUrl to globalState.latestVersionName },
            onLater = { dismissedUpdateCode = globalState.latestVersionCode }
        )
    }

    // ---- Result dialogs (one at a time: death report, then rations, then level-up) ----
    val deathReport by viewModel.deathReport.collectAsStateWithLifecycle()
    val dailyResult by viewModel.dailyResult.collectAsStateWithLifecycle()
    val levelUp by viewModel.levelUp.collectAsStateWithLifecycle()
    val achievement by viewModel.achievementUnlocked.collectAsStateWithLifecycle()

    // ---- v2.1.0: your own profile and inbox, watched while the app is open ----
    // Profile changes drive the daily stats sample and profile-based achievements; new alerts
    // become phone notifications. Both piggyback on listeners the app already holds.
    LaunchedEffect(user?.playerData) { if (user != null) viewModel.onProfileChanged() }
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    LaunchedEffect(alerts) { viewModel.onAlertsChanged(alerts) }
    // First launch: offer to lift battery optimisation (asked once; also in Settings → System).
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBatteryPrompt by remember {
        mutableStateOf(!viewModel.prefs.batteryPromptShown && !BatteryOptimization.isIgnoring(context))
    }
    when {
        deathReport != null -> DeathReportDialog(deathReport!!, viewModel::dismissDeathReport)
        dailyResult != null -> DailyClaimDialog(dailyResult!!, viewModel::dismissDaily)
        levelUp != null -> LevelUpDialog(levelUp!!, viewModel::dismissLevelUp)
        achievement != null -> AchievementUnlockedDialog(achievement!!, viewModel::dismissAchievement)
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

    // Labels come from strings.xml so the navigation follows the phone's language.
    val navLabels = listOf(
        stringResource(R.string.nav_dash), stringResource(R.string.nav_players), stringResource(R.string.nav_shop),
        stringResource(R.string.nav_wheel), stringResource(R.string.nav_more), stringResource(R.string.nav_admin),
        stringResource(R.string.nav_settings)
    )
    val items = remember(isAdmin, navLabels) {
        listOfNotNull(
            NavigationItem(navLabels[0], "home", Icons.Default.Dashboard),
            NavigationItem(navLabels[1], "players", Icons.Default.People),
            NavigationItem(navLabels[2], "shop", Icons.Default.ShoppingCart),
            NavigationItem(navLabels[3], "wheel", Icons.Default.Cyclone),
            NavigationItem(navLabels[4], "more", Icons.Default.GridView),
            if (isAdmin) NavigationItem(navLabels[5], "admin", Icons.Default.AdminPanelSettings) else null,
            NavigationItem(navLabels[6], "settings", Icons.Default.Settings)
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                .padding(padding),
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
            composable("team_progress") { TeamProgressScreen(navController, viewModel) }
            composable("archives") { ArchivesScreen(navController, viewModel) }
            composable("survival_intel") { SurvivalIntelScreen(navController) }
            composable("request_game") { RequestGameScreen(navController, viewModel) }
            composable("scratch") { ScratchScreen(navController, viewModel) }
            composable("progression") { ProgressionScreen(navController, viewModel) }
            composable("images") { ImagesScreen(navController, viewModel) }
            composable("edit_profile") { EditProfileScreen(navController, viewModel) }
            composable("alerts") { AlertsScreen(navController, viewModel) }
            composable("chat") { ChatScreen(navController, viewModel) }
            composable("stats") { StatsScreen(navController, viewModel) }
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
    // With more than five tabs (admins get seven) there isn't room for every label on a
    // small phone, so unselected tabs collapse to their icon only.
    val compact = items.size > 5
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Scrim, not a hard edge: content dissolves into the bar as it scrolls under it.
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Ink0.copy(alpha = 0.86f), Ink0)
                )
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(if (compact) 62.dp else 68.dp),
            shape = RoundedCornerShape(Radius.xl),
            color = Ink2.copy(alpha = 0.97f),
            border = androidx.compose.foundation.BorderStroke(Stroke.hair, InkLine)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    BottomBarItem(
                        item = item,
                        selected = isSelected(item.route),
                        compact = compact,
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val animMs = if (LocalPerformanceMode.current) 0 else 160
    val tint by animateColorAsState(if (selected) Ember else TextLow, tween(animMs), label = "navTint")
    val pill by animateColorAsState(
        if (selected) Ember.copy(alpha = Tint.fill) else Color.Transparent,
        tween(animMs),
        label = "navPill"
    )
    val indicator by animateColorAsState(
        if (selected) Ember else Color.Transparent,
        tween(animMs),
        label = "navIndicator"
    )
    val showLabel = !compact || selected
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(Radius.md))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Ember),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Selected marker: a 3dp ember bar directly above the active icon.
        Box(
            Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(indicator)
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(pill)
                .padding(horizontal = if (showLabel) 10.dp else 9.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(if (showLabel) 20.dp else 22.dp)
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.label.uppercase(),
                color = tint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
