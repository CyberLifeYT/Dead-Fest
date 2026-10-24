package com.deadfest.ui.viewmodel

import com.deadfest.R
import com.deadfest.util.tr
import com.deadfest.ui.screens.localTitle
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deadfest.data.AppPrefs
import com.deadfest.data.model.*
import com.deadfest.data.repository.FirebaseRepository
import com.deadfest.data.repository.GameInfo
import com.deadfest.data.repository.TheGamesDbClient
import com.deadfest.domain.*
import com.deadfest.util.ImageCompressor
import com.deadfest.util.NotificationHelper
import com.deadfest.util.AppVisibility
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FirebaseRepository = FirebaseRepository()
    private val auth = FirebaseAuth.getInstance()
    val prefs = AppPrefs(application)

    /** One-off messages for the snackbar (purchase results, errors, confirmations). */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // Firestore listeners attach only while a screen is visible, and detach 5s after
    // the app goes to the background. This saves battery and data on every phone.
    private val whileVisible = SharingStarted.WhileSubscribed(5_000)

    private val authUid: StateFlow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser?.uid)

    // A single user-doc listener that switches with the signed-in account. The old code
    // stacked a new listener on every auth change and never removed any of them.
    val user: StateFlow<User?> = authUid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            // A missing or not-yet-cached profile keeps a placeholder instead of
            // bouncing a signed-in survivor back to the login screen.
            else repository.getUser(uid)
                // A profile that was never written (a sign-up that lost its connection
                // half way) would be locked out of every write by the security rules, so
                // it is repaired as soon as it is noticed.
                .onEach { if (it == null) runCatching { repository.ensureProfile(uid) } }
                .map { it ?: User(uid = uid) }
                .catch { logError("user", it); emit(User(uid = uid)) }
        }
        .stateIn(viewModelScope, whileVisible, auth.currentUser?.let { User(uid = it.uid) })

    val globalState: StateFlow<GlobalState> = repository.getGlobalState()
        .catch { logError("globalState", it) }
        .stateIn(viewModelScope, whileVisible, GlobalState())

    val eventLogs: StateFlow<List<EventLog>> = repository.getEventLogs()
        .catch { logError("eventLogs", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    val users: StateFlow<List<User>> = repository.getUsers()
        .catch { logError("users", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    val archives: StateFlow<List<Archive>> = repository.getArchives()
        .catch { logError("archives", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    /** Level -> reward track. Falls back to the built-in catalog when the collection is empty. */
    val levelRewards: StateFlow<List<LevelReward>> = repository.getLevelRewards()
        .map { it.ifEmpty { LevelRewardCatalog.DEFAULTS } }
        .catch { logError("levelRewards", it); emit(LevelRewardCatalog.DEFAULTS) }
        .stateIn(viewModelScope, whileVisible, LevelRewardCatalog.DEFAULTS)

    val scratchPrizes: StateFlow<List<ScratchPrize>> = repository.getScratchPrizes()
        .catch { logError("scratchPrizes", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    /**
     * Live Event Dashboard only: events from the last 15 minutes, for the death rate. The
     * window start is recomputed whenever the dashboard re-subscribes.
     */
    val recentEvents: StateFlow<List<EventLog>> = flow {
        emitAll(repository.getEventsSince(System.currentTimeMillis() - 15 * 60_000L))
    }.catch { logError("recentEvents", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    /** Shared Images gallery (metadata + thumbnails). Only listens while the Images screen is open. */
    val images: StateFlow<List<SharedImage>> = repository.getImages()
        .catch { logError("images", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    private val _imageUpload = MutableStateFlow(ImageUploadState())
    val imageUpload: StateFlow<ImageUploadState> = _imageUpload.asStateFlow()

    /** Your personal alerts inbox. Follows the signed-in account; only listens while visible. */
    val alerts: StateFlow<List<AlertItem>> = authUid
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else repository.getAlerts(uid) }
        .catch { logError("alerts", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    /** Group chat: newest 100 messages. Only listens while the chat screen is open. */
    val chat: StateFlow<List<ChatMessage>> = repository.getChat()
        .catch { logError("chat", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    /** Newest chat message only: drives chat notifications and the unread badge while the app is open. */
    val latestChat: StateFlow<ChatMessage?> = repository.getLatestChat()
        .catch { logError("latestChat", it) }
        .stateIn(viewModelScope, whileVisible, null)

    private val _chatImage = MutableStateFlow(ImageUploadState())
    val chatImage: StateFlow<ImageUploadState> = _chatImage.asStateFlow()

    private val _chatSending = MutableStateFlow(false)
    val chatSending: StateFlow<Boolean> = _chatSending.asStateFlow()

    // Not shown anywhere yet, so these cost nothing until a screen collects them.
    val messages: StateFlow<List<Message>> = repository.getMessages()
        .catch { logError("messages", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    val votes: StateFlow<List<Vote>> = repository.getVotes()
        .catch { logError("votes", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    /** The community game queue, newest first. */
    val gameRequests: StateFlow<List<GameRequest>> = repository.getGameRequests()
        .catch { logError("gameRequests", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    // ================================================================ game database (v2.0.8)

    /**
     * Live state of the game-name lookup.
     *
     * The lookup goes to an external game database. When it is unreachable — no key configured,
     * the service is down, the device is offline — [offline] is true and the request screen falls
     * back to letting the survivor type the game by hand, so a dead lookup never blocks a feature
     * that otherwise works entirely on Firestore.
     */
    private val _gameSearch = MutableStateFlow(GameSearchState())
    val gameSearch: StateFlow<GameSearchState> = _gameSearch.asStateFlow()

    /** True when no API key is configured at all, so the search box shouldn't even be offered. */
    val gameDatabaseAvailable: Boolean = TheGamesDbClient.configuredApiKey.isNotBlank()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _spin = MutableStateFlow(SpinState())
    val spin: StateFlow<SpinState> = _spin.asStateFlow()

    private val _scratch = MutableStateFlow(ScratchState())
    val scratch: StateFlow<ScratchState> = _scratch.asStateFlow()

    // App-level result dialogs.
    private val _deathReport = MutableStateFlow<DeathReport?>(null)
    val deathReport: StateFlow<DeathReport?> = _deathReport.asStateFlow()

    private val _dailyResult = MutableStateFlow<DailyResult?>(null)
    val dailyResult: StateFlow<DailyResult?> = _dailyResult.asStateFlow()

    private val _levelUp = MutableStateFlow<LevelUpEvent?>(null)
    val levelUp: StateFlow<LevelUpEvent?> = _levelUp.asStateFlow()

    private val cfg: EconomyConfig get() = globalState.value.economy
    private val myName: String
        get() = user.value?.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.vm_a_survivor_2)

    init {
        // Mark the survivor online whenever an account signs in.
        authUid.filterNotNull()
            .onEach { repository.setUserOnline(it, true) }
            .launchIn(viewModelScope)
    }

    // ---------------------------------------------------------------- auth

    fun login(email: String, pass: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || pass.isEmpty()) {
            _authState.value = AuthState.Error(tr(R.string.vm_enter_your_email_and_password))
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            _authState.value = try {
                auth.signInWithEmailAndPassword(cleanEmail, pass).await()
                AuthState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuthState.Error(friendlyAuthError(e))
            }
        }
    }

    fun signup(email: String, pass: String, name: String) {
        val cleanEmail = email.trim()
        val cleanName = name.trim()
        when {
            cleanName.isEmpty() -> { _authState.value = AuthState.Error(tr(R.string.vm_pick_a_display_name)); return }
            cleanEmail.isEmpty() -> { _authState.value = AuthState.Error(tr(R.string.vm_enter_your_email)); return }
            pass.length < 6 -> { _authState.value = AuthState.Error(tr(R.string.vm_password_needs_at_least_6_characters)); return }
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            _authState.value = try {
                val result = auth.createUserWithEmailAndPassword(cleanEmail, pass).await()
                val uid = result.user?.uid ?: throw IllegalStateException(tr(R.string.vm_signup_failed))
                // New survivors start with the same stake as a season reset, so they don't
                // begin life in Bankruptcy Level 1.
                repository.saveUser(
                    User(
                        uid = uid,
                        displayName = cleanName,
                        email = cleanEmail,
                        playerData = PlayerData(coins = FirebaseRepository.STARTING_COINS)
                    )
                )
                AuthState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuthState.Error(friendlyAuthError(e))
            }
        }
    }

    fun logout() {
        val uid = user.value?.uid
        viewModelScope.launch {
            // Give the "offline" flag a moment to reach the server, but never let a
            // missing connection block logout (it used to hang forever offline).
            if (uid != null) {
                try {
                    withTimeoutOrNull(1_500) { repository.setUserOnline(uid, false).await() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError("logout", e)
                }
            }
            com.deadfest.work.CheckInWorker.cancel(getApplication())
            auth.signOut()
            _authState.value = AuthState.Idle
        }
    }

    fun resetAuthState() {
        if (_authState.value is AuthState.Error) _authState.value = AuthState.Idle
    }

    /** Presence heartbeat, sent every minute while the app is on screen. */
    fun heartbeat() {
        authUid.value?.let { repository.heartbeat(it) }
    }

    /** Presence: the app left the screen. */
    fun goOffline() {
        authUid.value?.let { repository.goOffline(it) }
    }

    // ---------------------------------------------------------------- updates

    fun checkUpdate(
        currentVersionCode: Int,
        onUpdateAvailable: (url: String, versionName: String) -> Unit,
        onUpToDate: () -> Unit
    ) {
        val state = globalState.value
        // Whichever build this survivor chose. The URL falls back to the other build when the
        // chosen one has not been published yet, so this can never resolve to nothing.
        val url = state.updateUrlFor(prefs.updateVariant.value)
        if (state.latestVersionCode > currentVersionCode && url.isNotBlank()) {
            onUpdateAvailable(url, state.latestVersionName)
        } else {
            onUpToDate()
        }
    }

    // ---------------------------------------------------------------- deaths

    /** Survivor records their own death: tax, spins, bankruptcy, XP, then the mutual reward. */
    fun addDeath(gameName: String) {
        val me = user.value ?: return
        processDeath(me.uid, myName, gameName, showReport = true)
    }

    private fun processDeath(uid: String, name: String, game: String, showReport: Boolean) {
        val config = cfg
        launchSafe(tr(R.string.vm_couldn_t_record_death)) {
            // The Forced Spin Protocol switch is read live, so an admin turning it off takes
            // effect on the very next death rather than after an app restart.
            val gs = globalState.value
            // Sector Overload: deaths in the overloaded sector earn boosted XP and pay everyone more.
            val overload = OverloadRules.multiplierFor(game, gs.overloadedSector, gs.overloadActive(), gs.multiplier)
            val result = repository.recordDeath(
                uid, game, config, System.currentTimeMillis(), gs.forceSpinProtocol, overload
            ) ?: throw IllegalStateException(tr(R.string.vm_survivor_record_not_found))
            val o = result.outcome
            val tax = o.tax

            if (showReport) {
                _deathReport.value = DeathReport(result, OverloadRules.boost(config.survivalReward, overload))
            } else {
                // Only mention spins when some were actually added: with the protocol off the
                // count is zero and "+0 pending spin(s)" reads like a bug.
                val spinNote = if (o.spinsAdded > 0) tr(R.string.vm_1_pending_spin_s, o.spinsAdded) else ""
                _events.tryEmit(tr(R.string.vm_death_injected_into_1_2_tax, game, fmt(tax.finalTax), spinNote))
            }

            val bankruptNote = when {
                o.levelAfter == Bankruptcy.LEVEL_2 && o.levelBefore < Bankruptcy.LEVEL_2 -> tr(R.string.vm_1_is_in_bankruptcy_level_2, name)
                o.levelAfter == Bankruptcy.LEVEL_1 && o.levelBefore == Bankruptcy.NONE -> tr(R.string.vm_1_went_bankrupt, name)
                else -> ""
            }
            repository.logEvent(
                "DEATH",
                tr(R.string.vm_1_died_in_2_casualty_tax, name, game, fmt(tax.finalTax), bankruptNote),
                uid
            )
            if (tax.insuranceUsed) {
                repository.logEvent("BLACK_MARKET", tr(R.string.vm_1_s_insurance_absorbed_the_casualty, name), uid)
            }
            if (overload > 1.0) {
                repository.logEvent("DEATH", tr(R.string.vm_overload_x_1_2_s_fall, trimMultiplier(overload), name, game), uid)
            }
            if (uid == authUid.value) {
                handleXp(result.xp)
                if (tax.insuranceUsed) grantAchievements(setOf(AchievementEvent.INSURANCE_CLAIMED))
            } else {
                notifyPlayer(uid, AlertItem.TYPE_ADMIN, tr(R.string.vm_a_death_was_recorded_for_you),
                    tr(R.string.vm_an_admin_injected_a_death_in, game, fmt(tax.finalTax)))
            }

            // Mutual Survival Reward: best effort, never undoes the death record.
            val paid = try {
                repository.payMutualReward(uid, config, overload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("mutualReward", e)
                -1
            }
            if (showReport) _deathReport.value = _deathReport.value?.copy(survivorsPaid = paid)
        }
    }

    fun dismissDeathReport() { _deathReport.value = null }

    // ---------------------------------------------------------------- daily rations

    fun claimDaily() {
        val me = user.value ?: return
        launchSafe(tr(R.string.vm_couldn_t_claim_rations)) {
            val result = repository.claimDaily(me.uid, cfg, System.currentTimeMillis())
            if (!result.claimed) {
                _events.tryEmit(tr(R.string.vm_today_s_rations_are_already_claimed))
                return@launchSafe
            }
            _dailyResult.value = result
            handleXp(result.xp)
            bumpChallenge(ChallengeMetric.RATIONS)
            if (result.survivalBonusXp > 0) bumpChallenge(ChallengeMetric.SURVIVED_DAYS)
        }
    }

    fun dismissDaily() { _dailyResult.value = null }

    // ---------------------------------------------------------------- black market

    fun sendMessage(text: String) {
        val currentUser = user.value ?: return
        launchSafe(tr(R.string.vm_message_failed)) {
            repository.sendMessage(
                Message(
                    senderUid = currentUser.uid,
                    senderName = currentUser.displayName,
                    senderAvatar = currentUser.playerData.avatar,
                    text = text
                )
            )
        }
    }

    fun buyShopItem(item: ShopItem, target: User? = null, sector: String? = null) {
        val currentUser = user.value ?: return
        val config = cfg
        if (currentUser.playerData.coins < item.priceIn(config)) {
            _events.tryEmit(tr(R.string.vm_not_enough_coins_for_1, item.displayName))
            return
        }
        if (item == ShopItem.REVIVE && !globalState.value.reviveEnabled) {
            _events.tryEmit(tr(R.string.vm_revives_are_disabled_by_an_admin))
            return
        }
        val buyerName = myName
        val targetName = target?.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.vm_a_survivor)

        launchSafe(tr(R.string.vm_purchase_failed)) {
            val result = repository.applyShopItem(currentUser.uid, item, config, target?.uid, sector)
            val where = result.sector?.let { " in $it" } ?: ""
            when (result.outcome) {
                ShopOutcome.APPLIED -> {
                    val roman = ShopItem.roman(item.tier)
                    val (toast, log) = when {
                        item.isCurse -> tr(R.string.vm_toast_curse_landed, targetName, where) to
                            tr(R.string.vm_1_cursed_2_with_3_4, buyerName, targetName, item.logName, where)
                        item == ShopItem.REVIVE -> tr(R.string.vm_toast_revived, where) to
                            tr(R.string.vm_1_used_a_revive_and_cheated, buyerName, where)
                        item == ShopItem.SHIELD -> tr(R.string.vm_toast_shield) to
                            tr(R.string.vm_1_raised_a_shield, buyerName)
                        item == ShopItem.LUCKY_COIN -> tr(R.string.vm_toast_lucky) to
                            tr(R.string.vm_1_bought_a_lucky_coin, buyerName)
                        item == ShopItem.INSURANCE -> tr(R.string.vm_toast_insurance, config.insurancePct) to
                            tr(R.string.vm_1_took_out_an_insurance_policy, buyerName)
                        item == ShopItem.RATION_UPGRADE -> tr(R.string.vm_toast_ration, fmt(config.dailyUpgraded)) to
                            tr(R.string.vm_1_upgraded_their_daily_rations, buyerName)
                        item.isInsulation -> tr(R.string.vm_toast_insulation, roman) to
                            tr(R.string.vm_1_upgraded_their_wealth_insulation_to, buyerName, roman)
                        else -> tr(R.string.vm_toast_purchased, item.displayName) to tr(R.string.vm_1_bought_2, buyerName, item.logName)
                    }
                    _events.tryEmit(toast)
                    repository.logEvent("BLACK_MARKET", log, currentUser.uid, target?.uid)
                    handleXp(result.xp)
                    bumpChallenge(ChallengeMetric.PURCHASES)
                    if (item.isCurse) bumpChallenge(ChallengeMetric.CURSES)
                    if (item.isCurse && target != null) {
                        notifyPlayer(target.uid, AlertItem.TYPE_CURSED, tr(R.string.vm_you_were_cursed_by_1, buyerName),
                            tr(R.string.vm_1_2_death_s_3, item.displayName.lowercase().replaceFirstChar { it.uppercase() }, item.curseDeaths, where))
                        grantAchievements(setOf(AchievementEvent.CURSE_LANDED))
                    }
                }
                ShopOutcome.BLOCKED_BY_SHIELD -> {
                    _events.tryEmit(tr(R.string.vm_1_s_shield_blocked_your_curse, targetName))
                    repository.logEvent("BLACK_MARKET", tr(R.string.vm_1_s_shield_blocked_a_curse, targetName, buyerName), currentUser.uid, target?.uid)
                    handleXp(result.xp)
                    bumpChallenge(ChallengeMetric.PURCHASES)
                    target?.let {
                        notifyPlayer(it.uid, AlertItem.TYPE_SHIELD_BLOCKED, tr(R.string.vm_your_shield_blocked_a_curse),
                            tr(R.string.vm_1_tried_to_curse_you_your, buyerName))
                    }
                }
                ShopOutcome.INSUFFICIENT_FUNDS -> _events.tryEmit(tr(R.string.vm_not_enough_coins_for_1, item.displayName))
                ShopOutcome.ALREADY_ACTIVE -> _events.tryEmit(
                    if (item.oneTime) tr(R.string.vm_you_already_own_1, item.displayName) else tr(R.string.vm_you_already_have_an_active_1, item.displayName.lowercase())
                )
                ShopOutcome.NO_DEATHS -> _events.tryEmit(tr(R.string.vm_no_deaths_to_revive_you_re))
                ShopOutcome.TARGET_MISSING -> _events.tryEmit(tr(R.string.vm_that_survivor_couldn_t_be_found))
                ShopOutcome.DISABLED -> _events.tryEmit(tr(R.string.vm_1_is_disabled_by_an_admin, item.displayName))
                ShopOutcome.TIER_LOCKED -> _events.tryEmit(tr(R.string.vm_buy_the_previous_insulation_tier_first))
            }
        }
    }

    fun buyTitle(title: String, cost: Long) {
        val currentUser = user.value ?: return
        launchSafe(tr(R.string.vm_purchase_failed)) {
            val result = repository.buyTitle(currentUser.uid, title, cost, cfg)
            when (result.outcome) {
                ShopOutcome.APPLIED -> {
                    _events.tryEmit(tr(R.string.vm_title_unlocked_and_equipped_1, title))
                    handleXp(result.xp)
                    bumpChallenge(ChallengeMetric.PURCHASES)
                }
                ShopOutcome.INSUFFICIENT_FUNDS -> _events.tryEmit(tr(R.string.vm_not_enough_coins_for_1, title))
                ShopOutcome.ALREADY_ACTIVE -> _events.tryEmit(tr(R.string.vm_you_already_own_1, title))
                else -> _events.tryEmit(tr(R.string.vm_purchase_failed_2))
            }
        }
    }

    fun equipTitle(title: String) {
        val currentUser = user.value ?: return
        launchSafe(tr(R.string.vm_couldn_t_equip_title), success = tr(R.string.vm_equipped_1, title)) {
            repository.equipTitle(currentUser.uid, title)
        }
    }

    fun equipColor(hex: String) {
        val uid = user.value?.uid ?: return
        launchSafe(tr(R.string.vm_couldn_t_equip_badge_colour), success = tr(R.string.vm_badge_colour_equipped)) { repository.equipCosmetic(uid, "color", hex) }
    }

    fun equipFrame(frame: String) {
        val uid = user.value?.uid ?: return
        launchSafe(tr(R.string.vm_couldn_t_equip_frame), success = tr(R.string.vm_avatar_frame_equipped)) { repository.equipCosmetic(uid, "avatarFrame", frame) }
    }

    fun updateAvatar(avatar: String) {
        val currentUser = user.value ?: return
        launchSafe(tr(R.string.vm_couldn_t_update_avatar)) { repository.updateAvatar(currentUser.uid, avatar) }
    }

    fun castVote(voteId: String, optionIndex: Int) {
        val currentUser = user.value ?: return
        launchSafe(tr(R.string.vm_vote_failed)) { repository.castVote(voteId, optionIndex, currentUser.uid) }
    }

    fun logEvent(category: String, message: String, userUid: String? = null, targetUid: String? = null) {
        launchSafe(null) { repository.logEvent(category, message, userUid, targetUid) }
    }

    // ---------------------------------------------------------------- wheel

    /**
     * Picks a weighted winner and settles the payout in the ViewModel, so leaving the
     * screen mid-spin can't dodge a loss or lose a win. The UI animates towards
     * [SpinState.targetIndex] while the reveal waits for [durationMs].
     */
    fun spinWheel(items: List<WheelItem>, durationMs: Long) {
        val currentUser = user.value ?: return
        if (_spin.value.spinning || items.isEmpty()) return
        val index = Weighted.pick(items.map { it.weight })
        val item = items[index]
        val config = cfg
        val penalty = currentUser.playerData.pendingSpins > 0
        _spin.value = SpinState(spinning = true, targetIndex = index, spinId = _spin.value.spinId + 1, penalty = penalty)

        viewModelScope.launch {
            delay(durationMs)
            val name = myName
            try {
                val payout = repository.applyWheelResult(currentUser.uid, item, config)
                val (resultText, log) = when {
                    payout.delta > 0 -> {
                        val lucky = if (payout.luckyCoinUsed) tr(R.string.vm_lucky_x2) else ""
                        "YOU WON ${fmt(payout.delta)} COINS$lucky" to
                            tr(R.string.vm_1_spun_the_wheel_of_fate, name, fmt(payout.delta), lucky)
                    }
                    payout.delta < 0 -> "YOU LOST ${fmt(-payout.delta)} COINS" to
                        tr(R.string.vm_1_spun_the_wheel_of_fate_2, name, fmt(-payout.delta))
                    item.coinAmount != null && item.isTake -> "${item.label.uppercase()}: NOTHING LEFT TO TAKE" to
                        tr(R.string.vm_1_spun_the_wheel_of_fate_3, name, item.label)
                    else -> item.label.uppercase() to tr(R.string.vm_1_spun_the_wheel_of_fate_3, name, item.label)
                }
                val penaltyNote = when {
                    !payout.penaltyCleared -> null
                    payout.pendingLeft > 0 -> tr(R.string.vm_penalty_cleared_1_left, payout.pendingLeft)
                    else -> tr(R.string.vm_all_penalty_spins_cleared)
                }
                _spin.value = _spin.value.copy(
                    spinning = false,
                    resultText = resultText,
                    resultPositive = when {
                        payout.delta > 0 -> true
                        payout.delta < 0 -> false
                        else -> null
                    },
                    penaltyNote = penaltyNote
                )
                repository.logEvent(
                    "WHEEL_SPIN",
                    if (payout.penaltyCleared) tr(R.string.vm_1_penalty_spin, log) else log,
                    currentUser.uid
                )
                handleXp(payout.xp)
                bumpChallenge(ChallengeMetric.SPINS)
                grantAchievements(buildSet {
                    if (payout.delta >= Achievements.JACKPOT_COINS) add(AchievementEvent.JACKPOT)
                    if (payout.luckyCoinUsed && payout.delta > 0) add(AchievementEvent.LUCKY_STRIKE)
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("spin", e)
                _spin.value = _spin.value.copy(spinning = false, resultText = tr(R.string.vm_signal_lost_try_again), resultPositive = null)
                _events.tryEmit(tr(R.string.vm_spin_couldn_t_be_settled_1, friendlyError(e)))
            }
        }
    }

    // ---------------------------------------------------------------- scratch cards

    /** Charges the card and settles the prize up front; the UI then plays the reveal. */
    fun scratchCard(prizes: List<ScratchPrize>) {
        val me = user.value ?: return
        val config = cfg
        if (_scratch.value.busy || prizes.isEmpty()) return
        if (me.playerData.coins < config.scratchCost) {
            _events.tryEmit(tr(R.string.vm_not_enough_coins_for_a_scratch, fmt(config.scratchCost)))
            return
        }
        val prize = prizes[Weighted.pick(prizes.map { it.weight })]
        _scratch.value = _scratch.value.copy(busy = true)

        launchSafe(tr(R.string.vm_scratch_failed)) {
            try {
                val result = repository.scratch(me.uid, prize, config)
                _scratch.value = ScratchState(busy = false, scratchId = _scratch.value.scratchId + 1, result = result)
                val name = myName
                val log = when {
                    result.convertedItem -> tr(R.string.vm_1_scratched_a_2_they_already, name, prize.label, fmt(result.coinsWon))
                    result.outcome == ScratchOutcome.ITEM -> tr(R.string.vm_1_scratched_a_card_and_found, name, prize.label)
                    result.coinsWon > 0 -> tr(R.string.vm_1_scratched_a_card_and_won, name, fmt(result.coinsWon))
                    else -> tr(R.string.vm_1_scratched_a_dud, name)
                }
                repository.logEvent("BLACK_MARKET", log, me.uid)
                handleXp(result.xp)
                if (result.coinsWon >= Achievements.JACKPOT_COINS) grantAchievements(setOf(AchievementEvent.JACKPOT))
                bumpChallenge(ChallengeMetric.SCRATCHES)
            } finally {
                if (_scratch.value.busy) _scratch.value = _scratch.value.copy(busy = false)
            }
        }
    }

    // ---------------------------------------------------------------- shared images

    /** Step 1: compress the picked photo off the main thread and show a preview. */
    fun prepareImage(uri: android.net.Uri) {
        if (_imageUpload.value.busy) return
        _imageUpload.value = ImageUploadState(processing = true)
        viewModelScope.launch {
            try {
                val processed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ImageCompressor.process(getApplication(), uri)
                }
                val preview = android.graphics.BitmapFactory.decodeByteArray(processed.thumb, 0, processed.thumb.size)
                _imageUpload.value = ImageUploadState(pending = processed, preview = preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: also catches OutOfMemoryError on very old phones.
                logError("prepareImage", e)
                _imageUpload.value = ImageUploadState()
                _events.tryEmit(tr(R.string.vm_couldn_t_load_that_image_1, e.message ?: "unsupported file"))
            }
        }
    }

    /** Step 2: share it with everyone. */
    fun postImage(caption: String) {
        val me = user.value ?: return
        val pending = _imageUpload.value.pending ?: return
        _imageUpload.value = _imageUpload.value.copy(uploading = true)
        val name = myName
        val cleanCaption = caption.trim().take(MAX_CAPTION)
        viewModelScope.launch {
            try {
                val task = repository.uploadImage(
                    me.uid, name, cleanCaption, pending.full, pending.thumb, pending.width, pending.height
                )
                // Offline, Firestore keeps the write queued and sends it later: stop the spinner.
                val sent = withTimeoutOrNull(20_000) { task.await(); true } ?: false
                _imageUpload.value = ImageUploadState()
                _events.tryEmit(if (sent) tr(R.string.vm_image_shared_with_every_survivor) else tr(R.string.vm_no_signal_your_image_will_upload))
                repository.logEvent(
                    "IMAGES",
                    if (cleanCaption.isBlank()) tr(R.string.vm_1_shared_an_image, name) else tr(R.string.vm_1_shared_an_image_2, name, cleanCaption),
                    me.uid
                )
                grantAchievements(setOf(AchievementEvent.IMAGE_SHARED))
                bumpChallenge(ChallengeMetric.IMAGES)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("postImage", e)
                _imageUpload.value = _imageUpload.value.copy(uploading = false)
                _events.tryEmit(tr(R.string.vm_upload_failed_1, friendlyError(e)))
            }
        }
    }

    fun cancelImage() {
        if (!_imageUpload.value.uploading) _imageUpload.value = ImageUploadState()
    }

    /** Full-size bytes for the viewer; null if it couldn't be loaded. */
    suspend fun loadImage(id: String): ByteArray? = try {
        repository.getImageData(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logError("loadImage", e)
        null
    }

    /** The uploader or an admin can remove an image. */
    fun deleteImage(image: SharedImage) {
        val me = user.value ?: return
        if (image.uploaderUid != me.uid && !me.admin) {
            _events.tryEmit(tr(R.string.vm_only_the_uploader_or_an_admin))
            return
        }
        launchSafe(tr(R.string.vm_couldn_t_delete_image), success = tr(R.string.vm_image_deleted)) {
            repository.deleteImage(image.id)
            if (image.uploaderUid != me.uid) {
                val what = if (image.caption.isBlank()) tr(R.string.vm_one_of_your_images) else tr(R.string.vm_your_image_1, image.caption)
                notifyPlayer(image.uploaderUid, AlertItem.TYPE_IMAGE_REMOVED, tr(R.string.vm_your_image_was_removed),
                    tr(R.string.vm_an_admin_removed_1_from_the, what))
            }
        }
    }

    // ---------------------------------------------------------------- progression

    fun prestige() {
        val me = user.value ?: return
        launchSafe(tr(R.string.vm_prestige_failed)) {
            val count = repository.prestige(me.uid, cfg)
            if (count == null) {
                _events.tryEmit(tr(R.string.vm_reach_level_1_to_prestige, cfg.xp.maxLevel))
                return@launchSafe
            }
            _events.tryEmit(tr(R.string.vm_prestige_1_unlocked_back_to_level, count))
            repository.logEvent("LEVEL_UP", tr(R.string.vm_1_entered_prestige_2, myName, count), me.uid)
            NotificationHelper.notifyProgress(getApplication(), tr(R.string.vm_prestige_1, count), tr(R.string.vm_your_legend_resets_the_badge_is))
        }
    }

    fun dismissLevelUp() { _levelUp.value = null }

    /** Level-ups from any XP source: in-app celebration, native notification and feed entry. */
    private suspend fun handleXp(xp: XpResult?) {
        if (xp == null || xp.levelsGained.isEmpty()) return
        val unlocked = levelRewards.value.filter { it.level in xp.levelsGained }
        _levelUp.value = LevelUpEvent(xp.level, unlocked)
        val rewardText = if (unlocked.isEmpty()) tr(R.string.vm_keep_surviving) else "Unlocked: ${unlocked.joinToString { it.label }}"
        NotificationHelper.notifyProgress(getApplication(), tr(R.string.vm_level_up_lv_1, xp.level), rewardText)
        repository.logEvent("LEVEL_UP", tr(R.string.vm_1_reached_level_2, myName, xp.level), authUid.value)
    }

    // ---------------------------------------------------------------- admin

    fun setPlayerCoins(uid: String, coins: Long) =
        launchSafe(tr(R.string.vm_err_coin_update), success = tr(R.string.vm_ok_coins_set, fmt(coins))) {
            repository.setCoins(uid, coins)
            notifyPlayer(uid, AlertItem.TYPE_ADMIN, tr(R.string.vm_an_admin_set_your_coins), tr(R.string.vm_your_balance_is_now_1_coins, fmt(coins)))
        }

    fun updatePlayerCoins(uid: String, delta: Long) =
        launchSafe(tr(R.string.vm_err_coin_update)) {
            repository.adjustCoins(uid, delta)
            val change = if (delta >= 0) "+${fmt(delta)}" else "-${fmt(-delta)}"
            notifyPlayer(uid, AlertItem.TYPE_ADMIN, tr(R.string.vm_an_admin_adjusted_your_coins), tr(R.string.vm_change_coins, change))
        }

    /** +1 injects a death through the full death pipeline; -1 just removes one (no refund). */
    fun updatePlayerDeaths(uid: String, delta: Long, gameName: String) {
        if (delta > 0) {
            val name = users.value.firstOrNull { it.uid == uid }?.displayName?.takeIf { it.isNotBlank() } ?: tr(R.string.vm_a_survivor_2)
            processDeath(uid, name, gameName, showReport = uid == authUid.value)
        } else {
            launchSafe(tr(R.string.vm_death_update_failed), success = tr(R.string.vm_death_removed_from_1, gameName)) {
                repository.updateDeaths(uid, delta, gameName)
                notifyPlayer(uid, AlertItem.TYPE_ADMIN, tr(R.string.vm_a_death_was_removed), tr(R.string.vm_an_admin_removed_one_of_your, gameName))
            }
        }
    }

    fun clearPenalties(uid: String) =
        launchSafe(tr(R.string.vm_err_clear_penalties), success = tr(R.string.vm_ok_penalties_cleared)) {
            repository.clearPenalties(uid)
            notifyPlayer(uid, AlertItem.TYPE_ADMIN, tr(R.string.vm_penalties_cleared), tr(R.string.vm_an_admin_cleared_your_pending_spins))
        }

    fun togglePlayerAdmin(uid: String, isAdmin: Boolean) {
        if (uid == user.value?.uid && !isAdmin) {
            _events.tryEmit(tr(R.string.vm_you_can_t_revoke_your_own))
            return
        }
        launchSafe(tr(R.string.vm_couldn_t_change_admin_rights)) { repository.toggleAdminStatus(uid, isAdmin) }
    }

    fun updateGlobalState(data: Map<String, Any>, success: String? = null) {
        launchSafe(tr(R.string.vm_update_failed), success = success) { repository.updateGlobalState(data) }
    }

    fun saveScratchPrize(prize: ScratchPrize) =
        launchSafe(tr(R.string.vm_err_save_prize), success = tr(R.string.vm_ok_prize_saved)) { repository.upsertScratchPrize(prize) }

    fun deleteScratchPrize(id: String) =
        launchSafe(tr(R.string.vm_err_delete_prize), success = tr(R.string.vm_ok_prize_removed)) { repository.deleteScratchPrize(id) }

    fun seedScratchPrizes() =
        launchSafe(tr(R.string.vm_err_load_prize_pool), success = tr(R.string.vm_ok_prize_pool_loaded)) {
            repository.seedScratchPrizes(ScratchPrize.DEFAULT_POOL)
        }

    fun createArchive(
        archiveName: String,
        notes: String = "",
        startedAt: Long = 0L,
        roster: List<String> = emptyList()
    ) = launchSafe(tr(R.string.vm_err_archive), success = tr(R.string.vm_ok_season_archived)) {
        repository.createArchive(archiveName, notes, startedAt, roster)
    }

    fun restoreArchive(archiveId: String) =
        launchSafe(tr(R.string.vm_err_restore), success = tr(R.string.vm_ok_archive_restored)) { repository.restoreArchive(archiveId) }

    // ---------------------------------------------------------------- identity & game queue (v2.0.8)

    /**
     * Changes the signed-in survivor's public handle.
     *
     * Validated here so a bad name never leaves the phone, and again in the repository because the
     * registry write is what actually has to be safe. "Same" is not an error — the field already
     * holds that handle — so it gets its own message rather than a false "updated".
     */
    fun changeUsername(desired: String) {
        val uid = user.value?.uid ?: return
        val trimmed = desired.trim()
        if (UsernameRules.keyOf(user.value?.username ?: "") == UsernameRules.keyOf(trimmed)) {
            _events.tryEmit(tr(R.string.vm_that_s_already_your_handle))
            return
        }
        launchSafe(tr(R.string.vm_couldn_t_update_handle)) {
            when (val result = repository.changeUsername(uid, trimmed)) {
                UsernameChangeResult.Ok -> _events.tryEmit(tr(R.string.vm_handle_updated_to_1, trimmed))
                UsernameChangeResult.Same -> _events.tryEmit(tr(R.string.vm_that_s_already_your_handle))
                UsernameChangeResult.Taken -> _events.tryEmit(tr(R.string.vm_1_is_already_taken_by_another, trimmed))
                is UsernameChangeResult.Invalid -> _events.tryEmit(result.reason)
            }
        }
    }

    /**
     * Looks a game up in the external game database.
     *
     * Results land in [gameSearch]. Any failure leaves the previous results alone and sets
     * [GameSearchState.offline], so the request screen can offer manual entry rather than showing an
     * empty list that reads as "no such game".
     */
    fun searchGames(query: String) {
        val q = query.trim()
        if (q.length < 2) {
            _gameSearch.value = GameSearchState()
            return
        }
        if (!gameDatabaseAvailable) {
            _gameSearch.value = GameSearchState(query = q, offline = true)
            return
        }
        _gameSearch.value = GameSearchState(query = q, loading = true)
        viewModelScope.launch {
            try {
                _gameSearch.value = GameSearchState(query = q, results = TheGamesDbClient().searchGames(q))
            } catch (e: Exception) {
                logError("gameSearch", e)
                _gameSearch.value = GameSearchState(query = q, offline = true)
            }
        }
    }

    /** Drops any in-flight search when the dialog closes. */
    fun clearGameSearch() {
        _gameSearch.value = GameSearchState()
    }

    /** Submits a game to the community request queue. */
    fun submitGameRequest(name: String, coverUrl: String) {
        val me = user.value ?: return
        launchSafe(tr(R.string.vm_couldn_t_send_request)) {
            when (val result = repository.submitGameRequest(name, coverUrl, me.uid, me.handle)) {
                GameRequestResult.Ok -> _events.tryEmit(tr(R.string.vm_request_sent_1, name.trim()))
                is GameRequestResult.Invalid -> _events.tryEmit(result.reason)
            }
        }
    }

    /** Admin: approve a request, making it a candidate sector. */
    fun approveGameRequest(id: String) {
        launchSafe(tr(R.string.vm_couldn_t_approve_request)) {
            repository.setGameRequestStatus(id, true, user.value?.handle ?: tr(R.string.vm_admin))
            gameRequests.value.firstOrNull { it.id == id }?.let { req ->
                notifyPlayer(req.requestedBy, AlertItem.TYPE_GAME_REQUEST, tr(R.string.vm_game_request_approved),
                    tr(R.string.vm_1_was_approved_as_a_candidate, req.name))
            }
        }
    }

    /** Admin: remove a request from the queue. */
    fun removeGameRequest(id: String) =
        launchSafe(tr(R.string.vm_err_remove_request)) { repository.removeGameRequest(id) }

    // ---------------------------------------------------------------- profile (v2.1.0)

    fun updateProfile(avatar: String, bio: String, color: String) {
        val uid = user.value?.uid ?: return
        val cleanBio = bio.trim().take(MAX_BIO)
        val cleanAvatar = avatar.trim().ifBlank { "☠" }
        launchSafe(tr(R.string.vm_couldn_t_save_profile), success = tr(R.string.vm_profile_saved)) {
            repository.updateProfile(uid, cleanAvatar, cleanBio, color)
        }
    }

    // ---------------------------------------------------------------- achievements & stats (v2.1.0)

    /** Achievements unlocked this session, newest last, for the in-app toast queue. */
    private val _achievementUnlocked = MutableStateFlow<Achievement?>(null)
    val achievementUnlocked: StateFlow<Achievement?> = _achievementUnlocked.asStateFlow()
    fun dismissAchievement() { _achievementUnlocked.value = null }

    /** Guards against double-granting while a write is still in flight. */
    private val grantedThisSession = mutableSetOf<String>()

    /**
     * Runs whenever your own profile changes while the app is open: records the daily coin sample
     * for Personal Stats, remembers bankruptcy, and unlocks any profile-driven achievements.
     */
    fun onProfileChanged() {
        val me = user.value ?: return
        val d = me.playerData
        if (d.bankruptcyLevel >= 1 && !d.everBankrupt) repository.markEverBankrupt(me.uid)
        val today = StatsHistory.dayKey(System.currentTimeMillis())
        if (d.coinHistory[today] != d.coins) repository.recordCoinSample(me.uid, today, d.coins, d.coinHistory.keys)
        viewModelScope.launch { grantAchievements(emptySet()) }
    }

    /** Unlocks whatever [events] (plus the profile itself) now qualify for. */
    private suspend fun grantAchievements(events: Set<AchievementEvent>) {
        val me = user.value ?: return
        val fresh = Achievements.newlyEarned(me.playerData, events).filter { grantedThisSession.add(it.id) }
        if (fresh.isEmpty()) return
        try {
            repository.unlockAchievements(me.uid, fresh.map { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("achievements", e)
            fresh.forEach { grantedThisSession.remove(it.id) }
            return
        }
        fresh.forEach { a ->
            _achievementUnlocked.value = a
            NotificationHelper.notifyProgress(getApplication(), "ACHIEVEMENT: ${a.title.uppercase()}", a.description)
            repository.logEvent("LEVEL_UP", tr(R.string.vm_1_earned_the_2_achievement, myName, a.title), me.uid)
        }
    }

    // ---------------------------------------------------------------- personal alerts (v2.1.0)

    /** Writes an alert into another survivor's inbox. Best effort: never fails the action itself. */
    private suspend fun notifyPlayer(toUid: String, type: String, title: String, body: String) {
        val me = user.value ?: return
        try {
            repository.sendAlert(toUid, type, title, body, me.uid, me.handle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("sendAlert", e)
        }
    }

    /**
     * Turns alerts that arrived since the last check into phone notifications (once each), and
     * reacts to the ones that unlock achievements.
     */
    fun onAlertsChanged(list: List<AlertItem>) {
        if (!prefs.alertNotificationsEnabled) return
        val since = prefs.lastAlertNotifiedAt
        val fresh = list.filter { !it.read && it.createdAt > since }.sortedBy { it.createdAt }
        if (fresh.isEmpty()) return
        prefs.lastAlertNotifiedAt = fresh.maxOf { it.createdAt }
        // On a first run, don't flood the tray with the whole backlog.
        val toShow = if (since == 0L) fresh.takeLast(3) else fresh
        toShow.forEach { NotificationHelper.notifyAlert(getApplication(), it) }
        if (fresh.any { it.type == AlertItem.TYPE_SHIELD_BLOCKED }) {
            viewModelScope.launch { grantAchievements(setOf(AchievementEvent.SHIELD_BLOCKED)) }
        }
    }

    fun markAlertsRead() {
        val uid = user.value?.uid ?: return
        val unread = alerts.value.filter { !it.read }.map { it.id }
        if (unread.isEmpty()) return
        launchSafe(null) { repository.markAlertsRead(uid, unread) }
    }

    fun clearAlerts() {
        val uid = user.value?.uid ?: return
        val ids = alerts.value.map { it.id }
        launchSafe(tr(R.string.vm_couldn_t_clear_alerts), success = tr(R.string.vm_alerts_cleared)) { repository.clearAlerts(uid, ids) }
    }

    // ---------------------------------------------------------------- group chat (v2.1.0)

    fun prepareChatImage(uri: android.net.Uri) {
        if (_chatImage.value.processing) return
        _chatImage.value = ImageUploadState(processing = true)
        viewModelScope.launch {
            try {
                val processed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ImageCompressor.process(getApplication(), uri)
                }
                val preview = android.graphics.BitmapFactory.decodeByteArray(processed.thumb, 0, processed.thumb.size)
                _chatImage.value = ImageUploadState(pending = processed, preview = preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logError("prepareChatImage", e)
                _chatImage.value = ImageUploadState()
                _events.tryEmit(tr(R.string.vm_couldn_t_load_that_image_1, e.message ?: "unsupported file"))
            }
        }
    }

    fun cancelChatImage() {
        if (!_chatSending.value) _chatImage.value = ImageUploadState()
    }

    /** Sends a chat message (text, photo or both). Returns false if there was nothing to send. */
    fun sendChat(text: String): Boolean {
        val me = user.value ?: return false
        val clean = text.trim().take(ChatMessage.MAX_TEXT)
        val image = _chatImage.value.pending
        if ((clean.isEmpty() && image == null) || _chatSending.value) return false
        _chatSending.value = true
        viewModelScope.launch {
            try {
                val task = repository.sendChat(me, clean, image)
                _chatImage.value = ImageUploadState()
                val sent = withTimeoutOrNull(20_000) { task.await(); true } ?: false
                if (!sent) _events.tryEmit(tr(R.string.vm_no_signal_your_message_will_send))
                grantAchievements(setOf(AchievementEvent.CHAT_MESSAGE))
                bumpChallenge(ChallengeMetric.CHATS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("sendChat", e)
                _events.tryEmit(tr(R.string.vm_message_failed_1, friendlyError(e)))
            } finally {
                _chatSending.value = false
            }
        }
        return true
    }

    fun deleteChatMessage(message: ChatMessage) {
        val me = user.value ?: return
        if (message.senderUid != me.uid && !me.admin) return
        launchSafe(tr(R.string.vm_couldn_t_delete_message), success = tr(R.string.vm_message_deleted)) { repository.deleteChatMessage(message) }
    }

    // ---------------------------------------------------------------- flash sales (v2.1.0)

    /** Admin: starts a [pct]% Black Market sale for [minutes] (0 = until ended) and announces it. */
    fun startFlashSale(pct: Int, minutes: Long) {
        val clean = pct.coerceIn(1, FlashSale.MAX_PCT)
        val endsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        launchSafe(tr(R.string.vm_couldn_t_start_flash_sale), success = tr(R.string.vm_flash_sale_live_1_off, clean)) {
            repository.updateGlobalState(mapOf(
                "flashSaleActive" to true,
                "flashSalePct" to clean,
                "flashSaleEndsAt" to endsAt
            ))
            val duration = if (minutes > 0) tr(R.string.vm_for_1_minutes, minutes) else ""
            repository.logEvent("BLACK_MARKET", tr(R.string.vm_flash_sale_everything_in_the_black, clean, duration), authUid.value)
        }
    }

    fun endFlashSale() =
        launchSafe(tr(R.string.vm_err_end_flash_sale), success = tr(R.string.vm_ok_flash_sale_ended)) {
            repository.updateGlobalState(mapOf("flashSaleActive" to false, "flashSaleEndsAt" to 0L))
        }

    private fun trimMultiplier(m: Double): String =
        if (m % 1.0 == 0.0) m.toLong().toString() else String.format(java.util.Locale.US, "%.1f", m)

    // ---------------------------------------------------------------- chat notifications (v2.1.2)

    /**
     * Called whenever the newest chat message changes while the app is open. Notifies about
     * messages from others unless you're looking at the chat right now.
     */
    fun onLatestChat(msg: ChatMessage?) {
        if (msg == null || msg.createdAt <= 0) return
        val me = authUid.value
        val seen = maxOf(prefs.lastChatSeenAt, prefs.lastChatNotifiedAt)
        when {
            // First run: start the clock without flooding.
            seen == 0L -> prefs.lastChatSeenAt = msg.createdAt
            AppVisibility.chatOpen -> prefs.lastChatSeenAt = msg.createdAt
            msg.senderUid == me || msg.createdAt <= seen -> Unit
            prefs.chatNotifications.value -> {
                prefs.lastChatNotifiedAt = msg.createdAt
                NotificationHelper.notifyChat(getApplication(), listOf(msg), user.value?.handle ?: "")
            }
        }
    }

    /** The chat screen is showing: everything up to now is read and its notification can go. */
    fun onChatVisible(visible: Boolean) {
        AppVisibility.chatOpen = visible
        if (visible) {
            latestChat.value?.createdAt?.takeIf { it > 0 }?.let { prefs.lastChatSeenAt = maxOf(prefs.lastChatSeenAt, it) }
            NotificationHelper.cancelChat(getApplication())
        }
    }

    /** True when someone else has posted since you last looked at the chat. */
    fun hasUnreadChat(msg: ChatMessage?): Boolean =
        msg != null && msg.senderUid != authUid.value && msg.createdAt > prefs.lastChatSeenAt && prefs.lastChatSeenAt > 0

    // ---------------------------------------------------------------- weekly challenges (v2.1.2)

    fun currentWeekKey(): String = Challenges.weekKey(System.currentTimeMillis())

    /** Counts one action towards this week's challenges and announces any it just completed. */
    private suspend fun bumpChallenge(metric: ChallengeMetric, amount: Long = 1) {
        val uid = authUid.value ?: return
        val week = currentWeekKey()
        val total = try {
            repository.bumpWeekly(uid, metric.key, amount, week)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("bumpWeekly", e)
            return
        }
        Challenges.forWeek(week)
            .filter { it.metric == metric && total >= it.target && total - amount < it.target }
            .forEach { c ->
                _events.tryEmit(tr(R.string.vm_challenge_complete_1_claim_your_reward, c.localTitle()))
                NotificationHelper.notifyProgress(
                    getApplication(),
                    getApplication<Application>().getString(com.deadfest.R.string.notif_challenge_title, c.localTitle()),
                    getApplication<Application>().getString(com.deadfest.R.string.notif_challenge_body, fmt(c.coins), fmt(c.xp)),
                    route = "challenges"
                )
            }
    }

    fun claimChallenge(challenge: ChallengeDef) {
        val uid = user.value?.uid ?: return
        launchSafe(tr(R.string.vm_couldn_t_claim_challenge)) {
            val xp = repository.claimChallenge(uid, challenge, currentWeekKey(), cfg)
            if (xp == null) {
                _events.tryEmit(tr(R.string.vm_that_challenge_isn_t_ready_to))
                return@launchSafe
            }
            _events.tryEmit(getApplication<Application>().getString(com.deadfest.R.string.challenges_claimed_msg, fmt(challenge.coins), fmt(challenge.xp)))
            repository.logEvent("LEVEL_UP", tr(R.string.vm_1_completed_the_weekly_challenge_2, myName, challenge.localTitle()), uid)
            handleXp(xp)
        }
    }

    // ---------------------------------------------------------------- image reactions & comments (v2.1.2)

    fun reactionsFor(imageId: String): Flow<Map<String, String>> =
        repository.getReactions(imageId).catch { logError("reactions", it); emit(emptyMap()) }

    fun commentsFor(imageId: String): Flow<List<ImageComment>> =
        repository.getComments(imageId).catch { logError("comments", it); emit(emptyList()) }

    /** Tapping your current reaction removes it; tapping another replaces it. */
    fun react(image: SharedImage, emoji: String, current: String?) {
        val uid = user.value?.uid ?: return
        val next = if (current == emoji) null else emoji
        launchSafe(tr(R.string.vm_couldn_t_react)) {
            repository.setReaction(image.id, uid, next)
            if (current == null && next != null) bumpChallenge(ChallengeMetric.REACTIONS)
        }
    }

    fun addComment(image: SharedImage, text: String) {
        val me = user.value ?: return
        val clean = text.trim().take(ImageComment.MAX_TEXT)
        if (clean.isEmpty()) return
        launchSafe(tr(R.string.vm_couldn_t_post_comment)) {
            repository.addComment(image.id, me, clean)
            notifyPlayer(image.uploaderUid, AlertItem.TYPE_COMMENT, tr(R.string.vm_1_commented_on_your_image, me.handle), clean)
            bumpChallenge(ChallengeMetric.COMMENTS)
        }
    }

    fun deleteComment(image: SharedImage, comment: ImageComment) {
        val me = user.value ?: return
        if (comment.uid != me.uid && image.uploaderUid != me.uid && !me.admin) return
        launchSafe(tr(R.string.vm_couldn_t_delete_comment)) { repository.deleteComment(image.id, comment.id) }
    }


    // ---------------------------------------------------------------- helpers

    fun showMessage(text: String) {
        _events.tryEmit(text)
    }

    /**
     * Runs a Firestore call without ever crashing the app. Failures (offline,
     * permission denied, and so on) turn into a readable snackbar message.
     */
    private fun launchSafe(errorPrefix: String?, success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { _events.tryEmit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(errorPrefix ?: "background", e)
                errorPrefix?.let { _events.tryEmit("$it: ${friendlyError(e)}") }
            }
        }
    }

    private fun logError(where: String, t: Throwable) = Log.e("MainViewModel", "Error in $where", t)

    private fun friendlyError(e: Throwable): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            tr(R.string.vm_access_denied_by_server_rules)
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            tr(R.string.vm_you_re_offline)
        e.message?.contains("offline", ignoreCase = true) == true -> tr(R.string.vm_you_re_offline)
        else -> e.message ?: tr(R.string.vm_unknown_error)
    }

    private fun friendlyAuthError(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidUserException -> tr(R.string.vm_no_survivor_found_with_that_email)
        is FirebaseAuthInvalidCredentialsException -> tr(R.string.vm_wrong_email_or_password)
        is FirebaseAuthUserCollisionException -> tr(R.string.vm_that_email_is_already_registered_try)
        is FirebaseAuthWeakPasswordException -> tr(R.string.vm_password_is_too_weak_use_at)
        is FirebaseNetworkException -> tr(R.string.vm_no_connection_check_your_internet_and)
        else -> e.message ?: tr(R.string.vm_something_went_wrong)
    }

    private fun fmt(n: Long): String = NumberFormat.getIntegerInstance().format(n)
}

data class SpinState(
    val spinning: Boolean = false,
    val targetIndex: Int = -1,
    val spinId: Int = 0,
    val resultText: String? = null,
    /** true = win, false = loss, null = neutral event. */
    val resultPositive: Boolean? = null,
    /** This spin is a Forced Spin Protocol penalty spin. */
    val penalty: Boolean = false,
    val penaltyNote: String? = null
)

data class ScratchState(
    val busy: Boolean = false,
    val scratchId: Int = 0,
    val result: ScratchResult? = null
)

data class DeathReport(
    val result: DeathResult,
    val reward: Long,
    /** null = still paying out, -1 = payout failed. */
    val survivorsPaid: Int? = null
)

data class LevelUpEvent(val level: Int, val rewards: List<LevelReward>)

data class ImageUploadState(
    val processing: Boolean = false,
    val uploading: Boolean = false,
    val pending: ImageCompressor.Processed? = null,
    val preview: android.graphics.Bitmap? = null
) {
    val busy: Boolean get() = processing || uploading || pending != null
}

/**
 * Live state of the game-name lookup. [offline] means the game database could not be reached, so
 * the request screen should offer manual entry rather than an empty result list.
 */
data class GameSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<GameInfo> = emptyList(),
    val offline: Boolean = false
)

const val MAX_CAPTION = 140

/** Edit Profile: maximum bio length. */
const val MAX_BIO = 160

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
