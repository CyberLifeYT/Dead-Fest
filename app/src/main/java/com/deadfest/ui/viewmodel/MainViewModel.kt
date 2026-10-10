package com.deadfest.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deadfest.data.AppPrefs
import com.deadfest.data.model.*
import com.deadfest.data.repository.FirebaseRepository
import com.deadfest.domain.*
import com.deadfest.util.ImageCompressor
import com.deadfest.util.NotificationHelper
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

    // Not shown anywhere yet, so these cost nothing until a screen collects them.
    val messages: StateFlow<List<Message>> = repository.getMessages()
        .catch { logError("messages", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

    val votes: StateFlow<List<Vote>> = repository.getVotes()
        .catch { logError("votes", it) }
        .stateIn(viewModelScope, whileVisible, emptyList())

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
        get() = user.value?.displayName?.takeIf { it.isNotBlank() } ?: "A survivor"

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
            _authState.value = AuthState.Error("Enter your email and password.")
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
            cleanName.isEmpty() -> { _authState.value = AuthState.Error("Pick a display name."); return }
            cleanEmail.isEmpty() -> { _authState.value = AuthState.Error("Enter your email."); return }
            pass.length < 6 -> { _authState.value = AuthState.Error("Password needs at least 6 characters."); return }
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            _authState.value = try {
                val result = auth.createUserWithEmailAndPassword(cleanEmail, pass).await()
                val uid = result.user?.uid ?: throw IllegalStateException("Signup failed")
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
        if (state.latestVersionCode > currentVersionCode && state.updateUrl.isNotBlank()) {
            onUpdateAvailable(state.updateUrl, state.latestVersionName)
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
        launchSafe("Couldn't record death") {
            val result = repository.recordDeath(uid, game, config, System.currentTimeMillis())
                ?: throw IllegalStateException("Survivor record not found")
            val o = result.outcome
            val tax = o.tax

            if (showReport) {
                _deathReport.value = DeathReport(result, config.survivalReward)
            } else {
                _events.tryEmit("Death injected into $game: ${fmt(tax.finalTax)} tax, +${o.spinsAdded} pending spin(s).")
            }

            val bankruptNote = when {
                o.levelAfter == Bankruptcy.LEVEL_2 && o.levelBefore < Bankruptcy.LEVEL_2 -> " $name is in BANKRUPTCY LEVEL 2!"
                o.levelAfter == Bankruptcy.LEVEL_1 && o.levelBefore == Bankruptcy.NONE -> " $name went BANKRUPT."
                else -> ""
            }
            repository.logEvent(
                "DEATH",
                "$name died in $game. Casualty tax: ${fmt(tax.finalTax)} coins.$bankruptNote",
                uid
            )
            if (tax.insuranceUsed) {
                repository.logEvent("BLACK_MARKET", "$name's Insurance absorbed the casualty tax", uid)
            }
            if (uid == authUid.value) handleXp(result.xp)

            // Mutual Survival Reward: best effort, never undoes the death record.
            val paid = try {
                repository.payMutualReward(uid, config)
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
        launchSafe("Couldn't claim rations") {
            val result = repository.claimDaily(me.uid, cfg, System.currentTimeMillis())
            if (!result.claimed) {
                _events.tryEmit("Today's rations are already claimed.")
                return@launchSafe
            }
            _dailyResult.value = result
            handleXp(result.xp)
        }
    }

    fun dismissDaily() { _dailyResult.value = null }

    // ---------------------------------------------------------------- black market

    fun sendMessage(text: String) {
        val currentUser = user.value ?: return
        launchSafe("Message failed") {
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
            _events.tryEmit("Not enough coins for ${item.displayName}.")
            return
        }
        if (item == ShopItem.REVIVE && !globalState.value.reviveEnabled) {
            _events.tryEmit("Revives are disabled by an admin.")
            return
        }
        val buyerName = myName
        val targetName = target?.displayName?.takeIf { it.isNotBlank() } ?: "a survivor"

        launchSafe("Purchase failed") {
            val result = repository.applyShopItem(currentUser.uid, item, config, target?.uid, sector)
            val where = result.sector?.let { " in $it" } ?: ""
            when (result.outcome) {
                ShopOutcome.APPLIED -> {
                    val roman = ShopItem.roman(item.tier)
                    val (toast, log) = when {
                        item.isCurse -> "Curse landed on $targetName$where." to
                            "$buyerName cursed $targetName with ${item.logName}$where!"
                        item == ShopItem.REVIVE -> "Revived: 1 death removed$where." to
                            "$buyerName used a Revive and cheated death$where."
                        item == ShopItem.SHIELD -> "Shield is up. The next curse on you will be blocked." to
                            "$buyerName raised a Shield."
                        item == ShopItem.LUCKY_COIN -> "Lucky Coin armed. Your next Wheel win is doubled." to
                            "$buyerName bought a Lucky Coin."
                        item == ShopItem.INSURANCE -> "Insurance armed: ${config.insurancePct}% of your next casualty tax is covered." to
                            "$buyerName took out an Insurance Policy."
                        item == ShopItem.RATION_UPGRADE -> "Ration Upgrade active: ${fmt(config.dailyUpgraded)} coins every day." to
                            "$buyerName upgraded their daily rations."
                        item.isInsulation -> "WEALTH INSULATION UPGRADED: Tier $roman active" to
                            "$buyerName upgraded their Wealth Insulation to Tier $roman"
                        else -> "Purchased ${item.displayName}." to "$buyerName bought ${item.logName}."
                    }
                    _events.tryEmit(toast)
                    repository.logEvent("BLACK_MARKET", log, currentUser.uid, target?.uid)
                    handleXp(result.xp)
                }
                ShopOutcome.BLOCKED_BY_SHIELD -> {
                    _events.tryEmit("$targetName's Shield blocked your curse. The Shield is gone now.")
                    repository.logEvent("BLACK_MARKET", "$targetName's Shield blocked a curse from $buyerName!", currentUser.uid, target?.uid)
                    handleXp(result.xp)
                }
                ShopOutcome.INSUFFICIENT_FUNDS -> _events.tryEmit("Not enough coins for ${item.displayName}.")
                ShopOutcome.ALREADY_ACTIVE -> _events.tryEmit(
                    if (item.oneTime) "You already own ${item.displayName}." else "You already have an active ${item.displayName.lowercase()}."
                )
                ShopOutcome.NO_DEATHS -> _events.tryEmit("No deaths to revive. You're clean!")
                ShopOutcome.TARGET_MISSING -> _events.tryEmit("That survivor couldn't be found.")
                ShopOutcome.DISABLED -> _events.tryEmit("${item.displayName} is disabled by an admin.")
                ShopOutcome.TIER_LOCKED -> _events.tryEmit("Buy the previous insulation tier first.")
            }
        }
    }

    fun buyTitle(title: String, cost: Long) {
        val currentUser = user.value ?: return
        launchSafe("Purchase failed") {
            val result = repository.buyTitle(currentUser.uid, title, cost, cfg)
            when (result.outcome) {
                ShopOutcome.APPLIED -> {
                    _events.tryEmit("Title unlocked and equipped: $title")
                    handleXp(result.xp)
                }
                ShopOutcome.INSUFFICIENT_FUNDS -> _events.tryEmit("Not enough coins for $title.")
                ShopOutcome.ALREADY_ACTIVE -> _events.tryEmit("You already own $title.")
                else -> _events.tryEmit("Purchase failed.")
            }
        }
    }

    fun equipTitle(title: String) {
        val currentUser = user.value ?: return
        launchSafe("Couldn't equip title", success = "Equipped $title") {
            repository.equipTitle(currentUser.uid, title)
        }
    }

    fun equipColor(hex: String) {
        val uid = user.value?.uid ?: return
        launchSafe("Couldn't equip badge colour", success = "Badge colour equipped") { repository.equipCosmetic(uid, "color", hex) }
    }

    fun equipFrame(frame: String) {
        val uid = user.value?.uid ?: return
        launchSafe("Couldn't equip frame", success = "Avatar frame equipped") { repository.equipCosmetic(uid, "avatarFrame", frame) }
    }

    fun updateAvatar(avatar: String) {
        val currentUser = user.value ?: return
        launchSafe("Couldn't update avatar") { repository.updateAvatar(currentUser.uid, avatar) }
    }

    fun castVote(voteId: String, optionIndex: Int) {
        val currentUser = user.value ?: return
        launchSafe("Vote failed") { repository.castVote(voteId, optionIndex, currentUser.uid) }
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
                        val lucky = if (payout.luckyCoinUsed) " (LUCKY x2)" else ""
                        "YOU WON ${fmt(payout.delta)} COINS$lucky" to
                            "$name spun the Wheel of Fate and won ${fmt(payout.delta)} coins$lucky!"
                    }
                    payout.delta < 0 -> "YOU LOST ${fmt(-payout.delta)} COINS" to
                        "$name spun the Wheel of Fate and lost ${fmt(-payout.delta)} coins!"
                    item.coinAmount != null && item.isTake -> "${item.label.uppercase()}: NOTHING LEFT TO TAKE" to
                        "$name spun the Wheel of Fate and got ${item.label}!"
                    else -> item.label.uppercase() to "$name spun the Wheel of Fate and got ${item.label}!"
                }
                val penaltyNote = when {
                    !payout.penaltyCleared -> null
                    payout.pendingLeft > 0 -> "PENALTY CLEARED · ${payout.pendingLeft} LEFT"
                    else -> "ALL PENALTY SPINS CLEARED"
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
                    if (payout.penaltyCleared) "$log (penalty spin)" else log,
                    currentUser.uid
                )
                handleXp(payout.xp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("spin", e)
                _spin.value = _spin.value.copy(spinning = false, resultText = "SIGNAL LOST: TRY AGAIN", resultPositive = null)
                _events.tryEmit("Spin couldn't be settled: ${friendlyError(e)}")
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
            _events.tryEmit("Not enough coins for a scratch card (${fmt(config.scratchCost)} needed).")
            return
        }
        val prize = prizes[Weighted.pick(prizes.map { it.weight })]
        _scratch.value = _scratch.value.copy(busy = true)

        launchSafe("Scratch failed") {
            try {
                val result = repository.scratch(me.uid, prize, config)
                _scratch.value = ScratchState(busy = false, scratchId = _scratch.value.scratchId + 1, result = result)
                val name = myName
                val log = when {
                    result.convertedItem -> "$name scratched a ${prize.label} they already had and cashed it in for ${fmt(result.coinsWon)} coins."
                    result.outcome == ScratchOutcome.ITEM -> "$name scratched a card and found a ${prize.label}!"
                    result.coinsWon > 0 -> "$name scratched a card and won ${fmt(result.coinsWon)} coins!"
                    else -> "$name scratched a dud."
                }
                repository.logEvent("BLACK_MARKET", log, me.uid)
                handleXp(result.xp)
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
                _events.tryEmit("Couldn't load that image: ${e.message ?: "unsupported file"}")
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
                _events.tryEmit(if (sent) "Image shared with every survivor." else "No signal: your image will upload when you're back online.")
                repository.logEvent(
                    "IMAGES",
                    if (cleanCaption.isBlank()) "$name shared an image." else "$name shared an image: \"$cleanCaption\"",
                    me.uid
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("postImage", e)
                _imageUpload.value = _imageUpload.value.copy(uploading = false)
                _events.tryEmit("Upload failed: ${friendlyError(e)}")
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
            _events.tryEmit("Only the uploader or an admin can delete this image.")
            return
        }
        launchSafe("Couldn't delete image", success = "Image deleted") { repository.deleteImage(image.id) }
    }

    // ---------------------------------------------------------------- progression

    fun prestige() {
        val me = user.value ?: return
        launchSafe("Prestige failed") {
            val count = repository.prestige(me.uid, cfg)
            if (count == null) {
                _events.tryEmit("Reach level ${cfg.xp.maxLevel} to prestige.")
                return@launchSafe
            }
            _events.tryEmit("PRESTIGE $count UNLOCKED. Back to level 1, legend.")
            repository.logEvent("LEVEL_UP", "$myName entered PRESTIGE $count!", me.uid)
            NotificationHelper.showNotification(getApplication(), "PRESTIGE $count", "Your legend resets. The badge is forever.")
        }
    }

    fun dismissLevelUp() { _levelUp.value = null }

    /** Level-ups from any XP source: in-app celebration, native notification and feed entry. */
    private suspend fun handleXp(xp: XpResult?) {
        if (xp == null || xp.levelsGained.isEmpty()) return
        val unlocked = levelRewards.value.filter { it.level in xp.levelsGained }
        _levelUp.value = LevelUpEvent(xp.level, unlocked)
        val rewardText = if (unlocked.isEmpty()) "Keep surviving." else "Unlocked: ${unlocked.joinToString { it.label }}"
        NotificationHelper.showNotification(getApplication(), "LEVEL UP: LV ${xp.level}", rewardText)
        repository.logEvent("LEVEL_UP", "$myName reached Level ${xp.level}!", authUid.value)
    }

    // ---------------------------------------------------------------- admin

    fun setPlayerCoins(uid: String, coins: Long) =
        launchSafe("Coin update failed", success = "Coins set to ${fmt(coins)}") { repository.setCoins(uid, coins) }

    fun updatePlayerCoins(uid: String, delta: Long) =
        launchSafe("Coin update failed") { repository.adjustCoins(uid, delta) }

    /** +1 injects a death through the full death pipeline; -1 just removes one (no refund). */
    fun updatePlayerDeaths(uid: String, delta: Long, gameName: String) {
        if (delta > 0) {
            val name = users.value.firstOrNull { it.uid == uid }?.displayName?.takeIf { it.isNotBlank() } ?: "A survivor"
            processDeath(uid, name, gameName, showReport = uid == authUid.value)
        } else {
            launchSafe("Death update failed", success = "Death removed from $gameName") {
                repository.updateDeaths(uid, delta, gameName)
            }
        }
    }

    fun clearPenalties(uid: String) =
        launchSafe("Couldn't clear penalties", success = "Pending spins and bankruptcy cleared") { repository.clearPenalties(uid) }

    fun togglePlayerAdmin(uid: String, isAdmin: Boolean) {
        if (uid == user.value?.uid && !isAdmin) {
            _events.tryEmit("You can't revoke your own admin rights.")
            return
        }
        launchSafe("Couldn't change admin rights") { repository.toggleAdminStatus(uid, isAdmin) }
    }

    fun updateGlobalState(data: Map<String, Any>, success: String? = null) {
        launchSafe("Update failed", success = success) { repository.updateGlobalState(data) }
    }

    fun saveScratchPrize(prize: ScratchPrize) =
        launchSafe("Couldn't save prize", success = "Prize saved") { repository.upsertScratchPrize(prize) }

    fun deleteScratchPrize(id: String) =
        launchSafe("Couldn't delete prize", success = "Prize removed") { repository.deleteScratchPrize(id) }

    fun seedScratchPrizes() =
        launchSafe("Couldn't load prize pool", success = "Default prize pool loaded") {
            repository.seedScratchPrizes(ScratchPrize.DEFAULT_POOL)
        }

    fun createArchive(archiveName: String) =
        launchSafe("Archive failed", success = "Season archived and reset") { repository.createArchive(archiveName) }

    fun restoreArchive(archiveId: String) =
        launchSafe("Restore failed", success = "Archive restored") { repository.restoreArchive(archiveId) }

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
            "access denied by server rules"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "you're offline"
        e.message?.contains("offline", ignoreCase = true) == true -> "you're offline"
        else -> e.message ?: "unknown error"
    }

    private fun friendlyAuthError(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidUserException -> "No survivor found with that email."
        is FirebaseAuthInvalidCredentialsException -> "Wrong email or password."
        is FirebaseAuthUserCollisionException -> "That email is already registered. Try logging in."
        is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters."
        is FirebaseNetworkException -> "No connection. Check your internet and try again."
        else -> e.message ?: "Something went wrong."
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

const val MAX_CAPTION = 140

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
