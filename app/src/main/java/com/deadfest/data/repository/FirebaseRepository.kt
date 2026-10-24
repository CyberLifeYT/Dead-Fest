package com.deadfest.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.deadfest.data.model.*
import com.deadfest.domain.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usersRef get() = firestore.collection("users")
    private val globalRef get() = firestore.collection("gameState").document("global")
    private val scratchRef get() = firestore.collection("scratchPrizes")
    private val rewardsRef get() = firestore.collection("levelRewards")
    private val eventLogRef get() = firestore.collection("eventLog")

    /**
     * Handle registry: `usernames/{lowercase handle}` -> { uid, display }.
     *
     * This collection is what makes handles unique. A handle can only be taken by writing its
     * registry document, so two survivors racing for the same name produce exactly one winner —
     * the second transaction sees the document exist and is rejected. It is also the only thing
     * that lets a rename release the old handle instead of stranding it forever.
     */
    private val usernamesRef get() = firestore.collection("usernames")

    /** Community game requests: the queue survivors submit and admins approve. */
    private val gameRequestsRef get() = firestore.collection("gameRequests")

    fun getCurrentUser() = auth.currentUser

    // ================================================================ streams

    fun getUser(uid: String): Flow<User?> =
        usersRef.document(uid).snapshots().map { it.toUserModel() }

    /**
     * Makes sure the signed-in survivor has a profile document.
     *
     * The security rules decide whether a write is allowed by reading the admin flag off this
     * document, so a survivor without one cannot write anything - not even their own record.
     * The app creates the document at sign-up, but a sign-up that lost its connection half way
     * through leaves an account with no profile, and that account would be locked out of the
     * whole game. Repairing it here means the one document everything depends on always exists.
     */
    suspend fun ensureProfile(uid: String) {
        val ref = usersRef.document(uid)
        if (ref.get().await().exists()) return
        ref.set(
            mapOf(
                "uid" to uid,
                "displayName" to "",
                "email" to "",
                "admin" to false,
                "username" to "",
                "playerData" to mapOf(
                    "coins" to STARTING_COINS,
                    "level" to 1,
                    "xp" to 0,
                    "games" to emptyMap<String, Any>()
                )
            )
        ).await()
    }

    fun getUsers(): Flow<List<User>> =
        usersRef.orderBy("playerData.coins", Query.Direction.DESCENDING).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toUserModel() }
        }

    fun getGlobalState(): Flow<GlobalState> =
        globalRef.snapshots().map { snapshot ->
            if (!snapshot.exists()) return@map GlobalState()

            val state = GlobalState(
                marketEnabled = snapshot.getBoolean("marketEnabled") ?: true,
                wheelEnabled = snapshot.getBoolean("wheelEnabled") ?: true,
                reviveEnabled = snapshot.getBoolean("reviveEnabled") ?: true,
                multiplier = (snapshot.get("multiplier") as? Number)?.toDouble() ?: 1.0,
                sectorOverload = snapshot.getBoolean("sectorOverload") ?: false,
                flashSaleActive = snapshot.getBoolean("flashSaleActive") ?: false,
                featuredVideoUrl = snapshot.getString("featuredVideoUrl") ?: "",
                showFeaturedVideo = snapshot.getBoolean("showFeaturedVideo") ?: false,
                lockdownActive = snapshot.getBoolean("lockdownActive") ?: false,
                grandTotal = (snapshot.get("grandTotal") as? Number)?.toLong() ?: 0L,
                onlineCount = (snapshot.get("onlineCount") as? Number)?.toInt() ?: 0,
                latestVersionName = snapshot.getString("latestVersionName") ?: "1.5.9",
                latestVersionCode = (snapshot.get("latestVersionCode") as? Number)?.toInt() ?: 1,
                updateUrl = snapshot.getString("updateUrl") ?: "",
                updateUrlR8 = snapshot.getString("updateUrlR8") ?: "",
                updateNotes = snapshot.getString("updateNotes") ?: "",
                forceUpdate = snapshot.getBoolean("forceUpdate") ?: false,
                /**
                 * Forced Spin Protocol master switch. Defaults to ON when the document has no
                 * such field — which is every live document today — so the behaviour survivors
                 * already have is the default, and an admin turning it off is an explicit act.
                 */
                forceSpinProtocol = snapshot.getBoolean("forceSpinProtocol") ?: true,
                games = (snapshot.get("games") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                lockedGames = (snapshot.get("lockedGames") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                overloadedSector = snapshot.getString("overloadedSector") ?: "",
                overloadEndsAt = (snapshot.get("overloadEndsAt") as? Number)?.toLong() ?: 0L,
                currentSeasonName = snapshot.getString("currentSeasonName")?.takeIf { it.isNotBlank() }
                    ?: "Dead-Fest Season 1",
                nextEventEnabled = snapshot.getBoolean("nextEventEnabled") ?: false,
                nextEventName = snapshot.getString("nextEventName") ?: "",
                nextEventStartsAt = (snapshot.get("nextEventStartsAt") as? Number)?.toLong() ?: 0L,
                nextEventGames = (snapshot.get("nextEventGames") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                nextEventNotes = snapshot.getString("nextEventNotes") ?: "",
                economy = parseEconomy(snapshot)
            )

            val items = (snapshot.get("wheelItems") as? List<*>)?.filterIsInstance<Map<*, *>>()
            state.wheelItems = items?.map { item ->
                WheelItem(
                    id = item["id"] as? String ?: "",
                    label = item["label"] as? String ?: "",
                    // Values may have been saved as numbers by other clients.
                    value = item["value"]?.toString() ?: "",
                    actionType = item["actionType"] as? String ?: "give",
                    color = item["color"] as? String ?: "#FF0000",
                    enabled = item["enabled"] as? Boolean ?: true,
                    // Accept Long or Double so decimal weights (e.g. 0.25) aren't lost.
                    weight = (item["weight"] as? Number)?.toDouble() ?: 1.0
                )
            } ?: emptyList()
            state
        }

    fun getEventLogs(): Flow<List<EventLog>> =
        eventLogRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(50).snapshots().map { snapshot ->
            snapshot.toObjects(EventLog::class.java)
        }

    /** Live Event Dashboard only: every event logged since [sinceMillis] (death-rate window). */
    fun getEventsSince(sinceMillis: Long): Flow<List<EventLog>> =
        eventLogRef.whereGreaterThanOrEqualTo("timestamp", Timestamp(Date(sinceMillis)))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots().map { it.toObjects(EventLog::class.java) }

    fun getMessages(): Flow<List<Message>> =
        firestore.collection("messages").orderBy("timestamp", Query.Direction.DESCENDING).limit(100).snapshots().map { snapshot ->
            snapshot.toObjects(Message::class.java).asReversed()
        }

    fun getVotes(): Flow<List<Vote>> =
        firestore.collection("votes").snapshots().map { snapshot ->
            snapshot.toObjects(Vote::class.java)
        }

    fun getArchives(): Flow<List<Archive>> =
        firestore.collection("archives").orderBy("timestamp", Query.Direction.DESCENDING).snapshots().map { snapshot ->
            snapshot.toObjects(Archive::class.java)
        }

    fun getScratchPrizes(): Flow<List<ScratchPrize>> =
        scratchRef.snapshots().map { snap ->
            snap.documents.map { d ->
                ScratchPrize(
                    id = d.id,
                    label = d.getString("label") ?: "",
                    weight = (d.get("weight") as? Number)?.toDouble() ?: 1.0,
                    value = d.get("value")?.toString() ?: "",
                    type = d.getString("type") ?: ScratchPrize.TYPE_COINS,
                    color = d.getString("color") ?: "#4CE07F",
                    enabled = d.getBoolean("enabled") ?: true
                )
            }.sortedBy { it.label.lowercase() }
        }

    fun getLevelRewards(): Flow<List<LevelReward>> =
        rewardsRef.snapshots().map { snap ->
            snap.documents.mapNotNull { d ->
                LevelReward(
                    level = (d.get("level") as? Number)?.toInt() ?: return@mapNotNull null,
                    rewardId = d.getString("rewardId") ?: d.id,
                    type = d.getString("type") ?: "color",
                    label = d.getString("label") ?: "",
                    value = d.getString("value") ?: ""
                )
            }.sortedBy { it.level }
        }

    // ================================================================ simple writes

    suspend fun updateGlobalState(data: Map<String, Any>) {
        globalRef.set(data, SetOptions.merge()).await()
    }

    suspend fun logEvent(category: String, message: String, userUid: String? = null, targetUid: String? = null) {
        val event = EventLog(
            id = System.currentTimeMillis().toString(),
            category = category,
            message = message,
            userUid = userUid,
            targetUid = targetUid,
            timestamp = Timestamp.now()
        )
        eventLogRef.add(event).await()
    }

    /**
     * Fire-and-forget: queued while offline and never blocks the caller. Awaiting this
     * used to make logout hang forever with no connection.
     */
    fun setUserOnline(uid: String, isOnline: Boolean) =
        usersRef.document(uid).update("playerData.isOnline", isOnline)

    /** Presence heartbeat: ONLINE, refreshed every minute while the app is on screen (see domain.Presence). */
    fun heartbeat(uid: String) =
        usersRef.document(uid).update(
            mapOf("playerData.lastSeen" to FieldValue.serverTimestamp(), "playerData.isOnline" to true)
        )

    /** Presence: OFFLINE as soon as the app leaves the screen. Fire-and-forget. */
    fun goOffline(uid: String) =
        usersRef.document(uid).update(
            mapOf("playerData.lastSeen" to FieldValue.serverTimestamp(), "playerData.isOnline" to false)
        )

    suspend fun saveUser(user: User) {
        usersRef.document(user.uid).set(user).await()
    }

    suspend fun toggleAdminStatus(uid: String, isAdmin: Boolean) {
        usersRef.document(uid).update("admin", isAdmin).await()
    }

    suspend fun updateAvatar(uid: String, avatar: String) {
        usersRef.document(uid).update("playerData.avatar", avatar).await()
    }

    suspend fun equipTitle(uid: String, title: String) {
        usersRef.document(uid).update("playerData.title", title).await()
    }

    /** Level-reward cosmetics: badge colour or avatar frame. */
    suspend fun equipCosmetic(uid: String, field: String, value: String) {
        require(field == "color" || field == "avatarFrame")
        usersRef.document(uid).update("playerData.$field", value).await()
    }

    // ================================================================ handle (v2.0.8)

    /**
     * Changes a survivor's public handle, releasing the old one and claiming the new one.
     *
     * The whole change is a single transaction so the three writes can never disagree: the old
     * registry document is deleted, the new one is created, and the user document is updated. If
     * the new handle is already claimed, nothing at all is written and the caller is told
     * [UsernameChangeResult.Taken] — the loser of a race keeps their current handle rather than
     * being left with neither.
     *
     * The user document is updated through the normal listener, so every screen showing the
     * survivor picks the new handle up in real time without any extra plumbing.
     */
    suspend fun changeUsername(uid: String, desired: String): UsernameChangeResult {
        val name = desired.trim()
        UsernameRules.errorFor(name)?.let { return UsernameChangeResult.Invalid(it) }
        val key = UsernameRules.keyOf(name)
        return try {
            firestore.runTransaction { tx ->
                val current = tx.get(usersRef.document(uid)).toUserModel()
                val currentKey = current?.username?.trim()?.let(UsernameRules::keyOf)
                if (currentKey == key) return@runTransaction UsernameChangeResult.Same
                if (tx.get(usernamesRef.document(key)).exists()) {
                    return@runTransaction UsernameChangeResult.Taken
                }
                if (currentKey != null) tx.delete(usernamesRef.document(currentKey))
                tx.set(usernamesRef.document(key), mapOf("uid" to uid, "display" to name))
                tx.update(usersRef.document(uid), "username", name)
                UsernameChangeResult.Ok
            }.await()
        } catch (e: Exception) {
            // A failed transaction leaves every write untouched, which is the point of doing it
            // atomically — so this is only ever "try again", never a half-applied rename.
            android.util.Log.w("FirebaseRepository", "changeUsername failed", e)
            UsernameChangeResult.Invalid("Couldn't save that handle. Check your connection and try again.")
        }
    }

    suspend fun sendMessage(message: Message) {
        firestore.collection("messages").add(message).await()
    }

    suspend fun castVote(voteId: String, optionIndex: Int, uid: String) {
        val voteDoc = firestore.collection("votes").document(voteId)
        firestore.runTransaction { transaction ->
            val vote = transaction.get(voteDoc).toObject<Vote>() ?: return@runTransaction
            if (vote.userVotes.containsKey(uid)) return@runTransaction // one vote per survivor
            val results = vote.results.toMutableMap()
            val userVotes = vote.userVotes.toMutableMap()
            val optionKey = vote.options.getOrNull(optionIndex) ?: return@runTransaction
            results[optionKey] = (results[optionKey] ?: 0) + 1
            userVotes[uid] = optionIndex
            transaction.update(voteDoc, mapOf("results" to results, "userVotes" to userVotes))
        }.await()
    }

    // ================================================================ game requests (v2.0.8)

    /**
     * The community game queue, newest first.
     *
     * One stream rather than separate pending/approved ones: the queue is small, every screen
     * filters it in memory, and a single listener is one less thing that can drift out of sync.
     */
    fun getGameRequests(): Flow<List<GameRequest>> =
        gameRequestsRef.orderBy("createdAt", Query.Direction.DESCENDING).snapshots().map { snap ->
            snap.documents.mapNotNull { d -> d.toObject<GameRequest>()?.also { it.id = d.id } }
        }

    /**
     * Submits a game request for the queue.
     *
     * A duplicate check runs first: a game already queued (or already approved) can't be asked
     * for twice. The check is a read rather than part of a transaction, so two survivors could in
     * theory submit the same new name at the same instant — the queue would then hold two rows an
     * admin can spot and merge, which is a smaller problem than a lost request.
     */
    suspend fun submitGameRequest(
        name: String,
        coverUrl: String,
        uid: String,
        byName: String
    ): GameRequestResult {
        val cleaned = name.trim()
        if (cleaned.length < 2) return GameRequestResult.Invalid("Give the game a name first.")
        if (cleaned.length > 80) return GameRequestResult.Invalid("That name is too long.")
        return try {
            val queued = gameRequestsRef.get().await()
                .mapNotNull { it.toObject<GameRequest>() }
            val clash = queued.firstOrNull { it.name.trim().equals(cleaned, ignoreCase = true) }
            if (clash != null) {
                return GameRequestResult.Invalid(
                    if (clash.isApproved) "\"${clash.name}\" is already an approved game."
                    else "\"${clash.name}\" is already waiting in the queue."
                )
            }
            gameRequestsRef.add(
                GameRequest(
                    name = cleaned,
                    coverUrl = coverUrl.trim(),
                    requestedBy = uid,
                    requestedByName = byName,
                    status = GameRequest.STATUS_PENDING,
                    createdAt = System.currentTimeMillis()
                )
            ).await()
            GameRequestResult.Ok
        } catch (e: Exception) {
            android.util.Log.w("FirebaseRepository", "submitGameRequest failed", e)
            GameRequestResult.Invalid("Couldn't send the request. Check your connection and try again.")
        }
    }

    /** Admin: approve a request (or send it back to pending), recording who decided. */
    suspend fun setGameRequestStatus(id: String, approved: Boolean, decidedByName: String) {
        gameRequestsRef.document(id).update(
            mapOf(
                "status" to if (approved) GameRequest.STATUS_APPROVED else GameRequest.STATUS_PENDING,
                "decidedByName" to decidedByName
            )
        ).await()
    }

    /** Admin: remove a request from the queue entirely. */
    suspend fun removeGameRequest(id: String) {
        gameRequestsRef.document(id).delete().await()
    }

    // ================================================================ coins (admin)

    /** Admin coin nudge. Transactional so the bankruptcy state follows the live balance. */
    suspend fun adjustCoins(uid: String, delta: Long) = setCoinsTx(uid) { it + delta }

    suspend fun setCoins(uid: String, coins: Long) = setCoinsTx(uid) { coins }

    private suspend fun setCoinsTx(uid: String, transform: (Long) -> Long) {
        val ref = usersRef.document(uid)
        firestore.runTransaction { tx ->
            val d = tx.get(ref).toUserModel()?.playerData ?: return@runTransaction
            val coins = transform(d.coins).coerceAtLeast(0)
            tx.update(ref, balanceUpdates(d, coins))
        }.await()
    }

    /** Admin: wipe pending penalty spins and bankruptcy for one survivor. */
    suspend fun clearPenalties(uid: String) {
        val ref = usersRef.document(uid)
        firestore.runTransaction { tx ->
            val d = tx.get(ref).toUserModel()?.playerData ?: return@runTransaction
            tx.update(ref, mapOf(
                "playerData.pendingSpins" to 0,
                "playerData.bankruptcyLevel" to if (d.coins > 0) Bankruptcy.NONE else Bankruptcy.LEVEL_1
            ))
        }.await()
    }

    // ================================================================ deaths

    /**
     * Records a death with the full v2.0.5 pipeline, in one transaction on live data:
     * sector death +1, casualty tax (tier -> Wealth Insulation -> Insurance), Forced Spin
     * Protocol, bankruptcy escalation and death XP.
     *
     * [forceSpinProtocol] is the admin master switch: with it off, a death adds no penalty spin,
     * which is the whole point of the toggle. Bankruptcy escalation still applies — that is the
     * bankruptcy system, not the spin protocol.
     */
    suspend fun recordDeath(
        uid: String,
        game: String,
        cfg: EconomyConfig,
        now: Long,
        forceSpinProtocol: Boolean = true,
        /** Sector Overload multiplier for this death; boosts the death XP (see OverloadRules). */
        overloadMultiplier: Double = 1.0
    ): DeathResult? {
        val ref = usersRef.document(uid)
        val result = firestore.runTransaction { tx ->
            val d = tx.get(ref).toUserModel()?.playerData ?: return@runTransaction null
            val outcome = DeathRules.resolve(
                coins = d.coins,
                bankruptcyLevel = d.bankruptcyLevel,
                pendingSpins = d.pendingSpins,
                insulationTier = d.insulationTier,
                insulationReductionPct = ShopItem.insulationReduction(d.insulationTier, cfg),
                insuranceActive = d.insurance,
                insurancePct = cfg.insurancePct,
                forceSpinProtocol = forceSpinProtocol
            )
            val xp = Leveling.addXp(d.xp, d.level, OverloadRules.boost(cfg.xp.death, overloadMultiplier), cfg.xp)
            val deaths = d.games[game]?.deaths ?: 0L
            tx.update(ref, FieldPath.of("playerData", "games", game, "deaths"), deaths + 1)
            // Personal stats: deaths per day, keeping the last StatsHistory.KEEP_DAYS days.
            val day = StatsHistory.dayKey(now)
            tx.update(ref, FieldPath.of("playerData", "deathsByDay", day), (d.deathsByDay[day] ?: 0L) + 1)
            StatsHistory.keysToPrune(d.deathsByDay.keys + day).forEach { old ->
                tx.update(ref, FieldPath.of("playerData", "deathsByDay", old), FieldValue.delete())
            }
            val updates = mutableMapOf<String, Any>(
                "playerData.coins" to outcome.coinsAfter,
                "playerData.bankruptcyLevel" to outcome.levelAfter,
                "playerData.pendingSpins" to outcome.pendingAfter,
                "playerData.lastDeathAt" to now,
                "playerData.xp" to xp.xp,
                "playerData.level" to xp.level,
                // A death breaks the survival streak (Survived 7 Days achievement).
                "playerData.survivalStreak" to 0
            )
            if (outcome.tax.insuranceUsed) updates["playerData.insurance"] = false
            if (outcome.levelAfter >= Bankruptcy.LEVEL_1) updates["playerData.everBankrupt"] = true
            tx.update(ref, updates)
            DeathResult(game, outcome, xp, overloadMultiplier)
        }.await()
        if (result != null) bumpGrandTotal(1)
        return result
    }

    /**
     * Mutual Survival Reward: every *other* survivor collects [EconomyConfig.survivalReward]
     * coins (halved while bankrupt). Separate transaction from the death itself, so a rules
     * problem on other players' documents never blocks recording the death.
     * @return how many survivors were paid
     */
    suspend fun payMutualReward(deadUid: String, cfg: EconomyConfig, overloadMultiplier: Double = 1.0): Int {
        if (cfg.survivalReward <= 0) return 0
        // Sector Overload: a death in the overloaded sector pays everyone x multiplier.
        val reward = OverloadRules.boost(cfg.survivalReward, overloadMultiplier)
        val refs = usersRef.get().await().documents.map { it.reference }.filter { it.id != deadUid }
        if (refs.isEmpty()) return 0
        return firestore.runTransaction { tx ->
            val snaps = refs.map { tx.get(it) }
            var paid = 0
            snaps.forEach { snap ->
                val d = snap.toUserModel()?.playerData ?: return@forEach
                val amount = Bankruptcy.payoutFor(d.bankruptcyLevel, reward)
                if (amount <= 0) return@forEach
                tx.update(snap.reference, balanceUpdates(d, d.coins + amount))
                paid++
            }
            paid
        }.await()
    }

    /** Plain death count change (admin removal, Revive). No tax, no spins. */
    suspend fun updateDeaths(uid: String, amount: Long, gameName: String? = null) {
        val userRef = usersRef.document(uid)
        val applied = firestore.runTransaction { tx ->
            val user = tx.get(userRef).toUserModel() ?: return@runTransaction 0L
            val game = gameName ?: user.playerData.games.keys.firstOrNull() ?: DEFAULT_GAME
            addDeathsInTx(tx, userRef, user, game, amount)
        }.await()
        bumpGrandTotal(applied)
    }

    // ================================================================ daily rations

    suspend fun claimDaily(uid: String, cfg: EconomyConfig, now: Long): DailyResult {
        val ref = usersRef.document(uid)
        return firestore.runTransaction { tx ->
            val d = tx.get(ref).toUserModel()?.playerData
                ?: throw IllegalStateException("Survivor record not found")
            if (!DailyClock.canClaim(d.lastDailyClaim, now)) {
                return@runTransaction DailyResult(0, d.coins, d.dailyUpgradeOwned, 0, null, claimed = false)
            }
            val amount = if (d.dailyUpgradeOwned) cfg.dailyUpgraded else cfg.dailyBase
            // "Survived a full day": no recorded death since the previous claim.
            val survived = d.lastDailyClaim > 0 && d.lastDeathAt < d.lastDailyClaim
            val bonusXp = if (survived) cfg.xp.survivalDay else 0L
            val xp = if (bonusXp > 0) Leveling.addXp(d.xp, d.level, bonusXp, cfg.xp) else null
            val coins = d.coins + amount
            val updates = balanceUpdates(d, coins).toMutableMap()
            updates["playerData.lastDailyClaim"] = now
            updates["playerData.survivalStreak"] = if (survived) d.survivalStreak + 1 else 0
            if (xp != null) {
                updates["playerData.xp"] = xp.xp
                updates["playerData.level"] = xp.level
            }
            tx.update(ref, updates)
            DailyResult(amount, coins, d.dailyUpgradeOwned, bonusXp, xp)
        }.await()
    }

    // ================================================================ black market

    suspend fun buyTitle(uid: String, title: String, cost: Long, cfg: EconomyConfig): ShopResult {
        val userRef = usersRef.document(uid)
        return firestore.runTransaction { tx ->
            val d = tx.get(userRef).toUserModel()?.playerData ?: return@runTransaction ShopResult(ShopOutcome.TARGET_MISSING)
            when {
                title in d.ownedTitles -> ShopResult(ShopOutcome.ALREADY_ACTIVE)
                d.coins < cost -> ShopResult(ShopOutcome.INSUFFICIENT_FUNDS)
                else -> {
                    val xp = Leveling.addXp(d.xp, d.level, cfg.xp.purchase, cfg.xp)
                    val updates = balanceUpdates(d, d.coins - cost).toMutableMap()
                    updates["playerData.ownedTitles"] = FieldValue.arrayUnion(title)
                    updates["playerData.title"] = title
                    updates["playerData.xp"] = xp.xp
                    updates["playerData.level"] = xp.level
                    tx.update(userRef, updates)
                    ShopResult(ShopOutcome.APPLIED, xp = xp)
                }
            }
        }.await()
    }

    /**
     * Buys and applies a Black Market item in one transaction, so coins are only
     * taken when the effect actually lands.
     *
     * @param targetUid survivor to curse (curses only)
     * @param sector    sector the curse death is recorded in (curses only)
     */
    suspend fun applyShopItem(
        uid: String,
        item: ShopItem,
        cfg: EconomyConfig,
        targetUid: String? = null,
        sector: String? = null
    ): ShopResult {
        val buyerRef = usersRef.document(uid)
        val targetRef = targetUid?.let { usersRef.document(it) }
        val price = item.priceIn(cfg)

        return firestore.runTransaction { tx ->
            // All reads must happen before any writes in a transaction.
            val buyer = tx.get(buyerRef).toUserModel()
                ?: return@runTransaction ShopResult(ShopOutcome.TARGET_MISSING)
            val target = if (item.isCurse) {
                targetRef?.let { tx.get(it).toUserModel() }
                    ?: return@runTransaction ShopResult(ShopOutcome.TARGET_MISSING)
            } else null

            val data = buyer.playerData
            if (!item.enabledIn(cfg)) return@runTransaction ShopResult(ShopOutcome.DISABLED)

            // Ownership checks come before the price check so "already owned" wins.
            when (item) {
                ShopItem.SHIELD -> if (data.shield) return@runTransaction ShopResult(ShopOutcome.ALREADY_ACTIVE)
                ShopItem.LUCKY_COIN -> if (data.luckyCoin) return@runTransaction ShopResult(ShopOutcome.ALREADY_ACTIVE)
                ShopItem.INSURANCE -> if (data.insurance) return@runTransaction ShopResult(ShopOutcome.ALREADY_ACTIVE)
                ShopItem.RATION_UPGRADE -> if (data.dailyUpgradeOwned) return@runTransaction ShopResult(ShopOutcome.ALREADY_ACTIVE)
                else -> if (item.isInsulation) {
                    if (data.insulationTier >= item.tier) return@runTransaction ShopResult(ShopOutcome.ALREADY_ACTIVE)
                    if (data.insulationTier != item.requiresTier) return@runTransaction ShopResult(ShopOutcome.TIER_LOCKED)
                }
            }
            if (data.coins < price) return@runTransaction ShopResult(ShopOutcome.INSUFFICIENT_FUNDS)

            val xp = Leveling.addXp(data.xp, data.level, cfg.xp.purchase, cfg.xp)
            fun charge(vararg extra: Pair<String, Any>) {
                val updates = balanceUpdates(data, data.coins - price).toMutableMap()
                updates["playerData.xp"] = xp.xp
                updates["playerData.level"] = xp.level
                extra.forEach { (k, v) -> updates[k] = v }
                tx.update(buyerRef, updates)
            }

            when (item) {
                ShopItem.SHIELD -> { charge("playerData.shield" to true); ShopResult(ShopOutcome.APPLIED, xp = xp) }
                ShopItem.LUCKY_COIN -> { charge("playerData.luckyCoin" to true); ShopResult(ShopOutcome.APPLIED, xp = xp) }
                ShopItem.INSURANCE -> { charge("playerData.insurance" to true); ShopResult(ShopOutcome.APPLIED, xp = xp) }
                ShopItem.RATION_UPGRADE -> { charge("playerData.dailyUpgradeOwned" to true); ShopResult(ShopOutcome.APPLIED, xp = xp) }
                ShopItem.INSULATION_1, ShopItem.INSULATION_2, ShopItem.INSULATION_3, ShopItem.INSULATION_4 -> {
                    // Upgrading replaces the lower tier (no refund).
                    charge("playerData.insulationTier" to item.tier)
                    ShopResult(ShopOutcome.APPLIED, xp = xp)
                }

                ShopItem.REVIVE -> {
                    // Remove the death from the sector where the survivor has died the most.
                    val worst = data.games.filterValues { it.deaths > 0 }.maxByOrNull { it.value.deaths }
                        ?: return@runTransaction ShopResult(ShopOutcome.NO_DEATHS)
                    charge()
                    val applied = addDeathsInTx(tx, buyerRef, buyer, worst.key, -1)
                    ShopResult(ShopOutcome.APPLIED, worst.key, applied, xp)
                }

                ShopItem.CURSE, ShopItem.DOUBLE_CURSE -> {
                    val victim = target!!
                    val victimRef = targetRef!!
                    val game = sector ?: victim.playerData.games.keys.firstOrNull() ?: DEFAULT_GAME
                    charge()
                    if (victim.playerData.shield) {
                        // The shield absorbs the whole curse and is used up.
                        tx.update(victimRef, "playerData.shield", false)
                        ShopResult(ShopOutcome.BLOCKED_BY_SHIELD, game, xp = xp)
                    } else {
                        val applied = addDeathsInTx(tx, victimRef, victim, game, item.curseDeaths)
                        ShopResult(ShopOutcome.APPLIED, game, applied, xp)
                    }
                }
            }
        }.await().also { bumpGrandTotal(it.deathDelta) }
    }

    // ================================================================ wheel & scratch

    /**
     * Applies a Wheel of Fate result. A Lucky Coin doubles the next coin win and is then
     * used up; losses never push a survivor below 0 coins. If penalty spins are pending,
     * this spin clears one (Forced Spin Protocol).
     */
    suspend fun applyWheelResult(uid: String, item: WheelItem, cfg: EconomyConfig): WheelPayout {
        val userRef = usersRef.document(uid)
        return firestore.runTransaction { tx ->
            val d = tx.get(userRef).toUserModel()?.playerData
                ?: return@runTransaction WheelPayout(0, false)
            val amount = item.coinAmount ?: 0L
            var lucky = false
            val delta = when {
                amount == 0L -> 0L
                item.isTake -> -amount.coerceAtMost(d.coins)
                else -> {
                    lucky = d.luckyCoin
                    if (lucky) amount * 2 else amount
                }
            }
            val penalty = d.pendingSpins > 0
            val pending = if (penalty) d.pendingSpins - 1 else d.pendingSpins.coerceAtLeast(0)
            val coins = d.coins + delta
            val level = Bankruptcy.afterBalanceChange(d.bankruptcyLevel, coins, pending)
            val xp = Leveling.addXp(d.xp, d.level, cfg.xp.spin + if (penalty) cfg.xp.penaltySpin else 0, cfg.xp)

            val updates = mutableMapOf<String, Any>(
                "playerData.coins" to coins,
                "playerData.bankruptcyLevel" to level,
                "playerData.pendingSpins" to pending,
                "playerData.xp" to xp.xp,
                "playerData.level" to xp.level
            )
            if (lucky) updates["playerData.luckyCoin"] = false
            tx.update(userRef, updates)
            WheelPayout(delta, lucky, penalty, pending, level, xp)
        }.await()
    }

    /** Pays for and resolves one Scratch Card (the prize is picked by the shared weighted RNG). */
    suspend fun scratch(uid: String, prize: ScratchPrize, cfg: EconomyConfig): ScratchResult {
        val ref = usersRef.document(uid)
        return firestore.runTransaction { tx ->
            val d = tx.get(ref).toUserModel()?.playerData
                ?: throw IllegalStateException("Survivor record not found")
            val cost = cfg.scratchCost.coerceAtLeast(0)
            if (d.coins < cost) throw IllegalStateException("Not enough coins (${cost} needed)")

            var coins = d.coins - cost
            var won = 0L
            var converted = false
            val extra = mutableMapOf<String, Any>()
            when (prize.type) {
                ScratchPrize.TYPE_COINS -> won = prize.coinAmount
                ScratchPrize.TYPE_SHIELD ->
                    if (d.shield) { won = ShopItem.SHIELD.priceIn(cfg); converted = true }
                    else extra["playerData.shield"] = true
                ScratchPrize.TYPE_LUCKY ->
                    if (d.luckyCoin) { won = ShopItem.LUCKY_COIN.priceIn(cfg); converted = true }
                    else extra["playerData.luckyCoin"] = true
            }
            coins += won
            val xp = Leveling.addXp(d.xp, d.level, cfg.xp.scratch, cfg.xp)
            val updates = balanceUpdates(d, coins).toMutableMap()
            updates.putAll(extra)
            updates["playerData.xp"] = xp.xp
            updates["playerData.level"] = xp.level
            tx.update(ref, updates)

            val outcome = when {
                prize.isItem -> ScratchOutcome.ITEM
                won >= cost && won > 0 -> ScratchOutcome.WIN
                else -> ScratchOutcome.LOSS
            }
            ScratchResult(prize, outcome, won, converted, xp)
        }.await()
    }

    suspend fun upsertScratchPrize(prize: ScratchPrize) {
        if (prize.id.isBlank()) scratchRef.add(prize.toMap()).await()
        else scratchRef.document(prize.id).set(prize.toMap()).await()
    }

    suspend fun deleteScratchPrize(id: String) {
        scratchRef.document(id).delete().await()
    }

    suspend fun seedScratchPrizes(prizes: List<ScratchPrize>) {
        val batch = firestore.batch()
        prizes.forEach { batch.set(scratchRef.document(), it.toMap()) }
        batch.commit().await()
    }

    // ================================================================ shared images

    private val imagesRef get() = firestore.collection("images")
    private val imageBlobsRef get() = firestore.collection("imageBlobs")

    /** Live gallery: newest first, metadata + thumbnail only (full images load on demand). */
    fun getImages(): Flow<List<SharedImage>> =
        imagesRef.orderBy("createdAt", Query.Direction.DESCENDING).limit(MAX_GALLERY.toLong()).snapshots().map { snap ->
            snap.documents.mapNotNull { d ->
                SharedImage(
                    id = d.id,
                    uploaderUid = d.getString("uploaderUid") ?: "",
                    uploaderName = d.getString("uploaderName") ?: "Survivor",
                    caption = d.getString("caption") ?: "",
                    createdAt = (d.get("createdAt") as? Number)?.toLong() ?: 0L,
                    width = (d.get("width") as? Number)?.toInt() ?: 0,
                    height = (d.get("height") as? Number)?.toInt() ?: 0,
                    sizeBytes = (d.get("sizeBytes") as? Number)?.toLong() ?: 0L,
                    thumb = d.getBlob("thumb")?.toBytes() ?: return@mapNotNull null
                )
            }
        }

    /**
     * Writes the full image and its gallery entry atomically. Returns the commit task so the
     * caller can stop waiting while offline (Firestore queues the write and sends it later).
     */
    fun uploadImage(
        uid: String,
        uploaderName: String,
        caption: String,
        full: ByteArray,
        thumb: ByteArray,
        width: Int,
        height: Int
    ): com.google.android.gms.tasks.Task<Void> {
        val id = imagesRef.document().id
        val batch = firestore.batch()
        batch.set(imageBlobsRef.document(id), mapOf(
            "data" to com.google.firebase.firestore.Blob.fromBytes(full),
            "uploaderUid" to uid
        ))
        batch.set(imagesRef.document(id), mapOf(
            "uploaderUid" to uid,
            "uploaderName" to uploaderName,
            "caption" to caption,
            "createdAt" to System.currentTimeMillis(),
            "width" to width,
            "height" to height,
            "sizeBytes" to full.size.toLong(),
            "thumb" to com.google.firebase.firestore.Blob.fromBytes(thumb)
        ))
        return batch.commit()
    }

    /** Full-size image bytes (served from the offline cache after the first view). */
    suspend fun getImageData(id: String): ByteArray? =
        imageBlobsRef.document(id).get().await().getBlob("data")?.toBytes()

    suspend fun deleteImage(id: String) {
        firestore.batch()
            .delete(imagesRef.document(id))
            .delete(imageBlobsRef.document(id))
            .commit().await()
    }

    // ================================================================ profile & achievements (v2.1.0)

    /** Edit Profile: avatar emoji, bio and badge colour in one write to your own document. */
    suspend fun updateProfile(uid: String, avatar: String, bio: String, color: String) {
        usersRef.document(uid).update(mapOf(
            "playerData.avatar" to avatar,
            "playerData.bio" to bio,
            "playerData.color" to color
        )).await()
    }

    suspend fun unlockAchievements(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        usersRef.document(uid).update("playerData.achievements", FieldValue.arrayUnion(*ids.toTypedArray())).await()
    }

    /** Marks the survivor as having been bankrupt (for "Bankrupt and Back"). Fire-and-forget. */
    fun markEverBankrupt(uid: String) {
        usersRef.document(uid).update("playerData.everBankrupt", true)
    }

    /** Personal stats: today's coin balance, plus pruning anything older than the kept window. */
    fun recordCoinSample(uid: String, day: String, coins: Long, existingDays: Collection<String>) {
        val ref = usersRef.document(uid)
        val batch = firestore.batch()
        batch.update(ref, FieldPath.of("playerData", "coinHistory", day), coins)
        StatsHistory.keysToPrune(existingDays + day).forEach { old ->
            batch.update(ref, FieldPath.of("playerData", "coinHistory", old), FieldValue.delete())
        }
        batch.commit()
    }

    // ================================================================ personal alerts (v2.1.0)

    private fun alertsRef(uid: String) = usersRef.document(uid).collection("alerts")

    /** Your newest 50 alerts. Only you can read them (see firestore.rules). */
    fun getAlerts(uid: String): Flow<List<AlertItem>> =
        alertsRef(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(50).snapshots().map { snap ->
            snap.documents.map { d ->
                AlertItem(
                    id = d.id,
                    type = d.getString("type") ?: "",
                    title = d.getString("title") ?: "",
                    body = d.getString("body") ?: "",
                    fromUid = d.getString("fromUid") ?: "",
                    fromName = d.getString("fromName") ?: "",
                    createdAt = (d.get("createdAt") as? Number)?.toLong() ?: 0L,
                    read = d.getBoolean("read") ?: false
                )
            }
        }

    /** Drops an alert into [toUid]'s inbox. Never alerts yourself. */
    suspend fun sendAlert(toUid: String, type: String, title: String, body: String, fromUid: String, fromName: String) {
        if (toUid.isBlank() || toUid == fromUid) return
        alertsRef(toUid).add(mapOf(
            "type" to type,
            "title" to title.take(120),
            "body" to body.take(400),
            "fromUid" to fromUid,
            "fromName" to fromName.take(40),
            "createdAt" to System.currentTimeMillis(),
            "read" to false
        )).await()
    }

    suspend fun markAlertsRead(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = firestore.batch()
        ids.forEach { batch.update(alertsRef(uid).document(it), "read", true) }
        batch.commit().await()
    }

    suspend fun clearAlerts(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = firestore.batch()
        ids.forEach { batch.delete(alertsRef(uid).document(it)) }
        batch.commit().await()
    }

    // ================================================================ group chat (v2.1.0)

    private val messagesRef get() = firestore.collection("messages")

    /** The newest 100 messages, oldest first. Shares the desktop client's `timestamp` ordering. */
    fun getChat(): Flow<List<ChatMessage>> =
        messagesRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(ChatMessage.MAX_MESSAGES).snapshots().map { snap ->
            snap.documents.map { d ->
                ChatMessage(
                    id = d.id,
                    senderUid = d.getString("senderUid") ?: "",
                    senderName = d.getString("senderName") ?: "Survivor",
                    senderAvatar = d.getString("senderAvatar") ?: "",
                    senderColor = d.getString("senderColor") ?: "",
                    text = d.getString("text") ?: "",
                    createdAt = (d.get("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) as? Timestamp)
                        ?.toDate()?.time ?: 0L,
                    imageId = d.getString("imageId")?.takeIf { it.isNotBlank() },
                    thumb = d.getBlob("thumb")?.toBytes(),
                    imageWidth = (d.get("imageWidth") as? Number)?.toInt() ?: 0,
                    imageHeight = (d.get("imageHeight") as? Number)?.toInt() ?: 0
                )
            }.asReversed()
        }

    /**
     * Posts a chat message, optionally with a photo (full image in `imageBlobs`, thumbnail on the
     * message). Returns the commit task so the caller can stop waiting while offline.
     */
    fun sendChat(
        sender: User,
        text: String,
        image: com.deadfest.util.ImageCompressor.Processed?
    ): com.google.android.gms.tasks.Task<Void> {
        val msgRef = messagesRef.document()
        val batch = firestore.batch()
        val data = mutableMapOf<String, Any>(
            "id" to msgRef.id,
            "senderUid" to sender.uid,
            "senderName" to sender.handle,
            "senderAvatar" to (sender.playerData.avatar ?: ""),
            "senderColor" to (sender.playerData.color ?: ""),
            "text" to text.take(ChatMessage.MAX_TEXT),
            "timestamp" to Timestamp.now()
        )
        if (image != null) {
            val imageId = imageBlobsRef.document().id
            batch.set(imageBlobsRef.document(imageId), mapOf(
                "data" to com.google.firebase.firestore.Blob.fromBytes(image.full),
                "uploaderUid" to sender.uid
            ))
            data["imageId"] = imageId
            data["thumb"] = com.google.firebase.firestore.Blob.fromBytes(image.thumb)
            data["imageWidth"] = image.width
            data["imageHeight"] = image.height
        }
        batch.set(msgRef, data)
        return batch.commit()
    }

    /** Sender or admin: removes a message (and its photo). */
    suspend fun deleteChatMessage(message: ChatMessage) {
        val batch = firestore.batch().delete(messagesRef.document(message.id))
        message.imageId?.let { batch.delete(imageBlobsRef.document(it)) }
        batch.commit().await()
    }


    // ================================================================ progression

    /** Prestige: back to level 1 / 0 XP, +1 permanent prestige. Only allowed at max level. */
    suspend fun prestige(uid: String, cfg: EconomyConfig): Int? {
        val ref = usersRef.document(uid)
        return firestore.runTransaction { tx ->
            val d = tx.get(ref).toUserModel()?.playerData ?: return@runTransaction null
            if (!Leveling.canPrestige(d.level, cfg.xp)) return@runTransaction null
            val count = d.prestigeCount + 1
            tx.update(ref, mapOf(
                "playerData.level" to 1,
                "playerData.xp" to 0L,
                "playerData.prestigeCount" to count
            ))
            count
        }.await()
    }

    // ================================================================ helpers

    /** Coins + the bankruptcy transition that follows from the new balance. */
    private fun balanceUpdates(d: PlayerData, coins: Long): Map<String, Any> {
        val safe = coins.coerceAtLeast(0)
        return mapOf(
            "playerData.coins" to safe,
            "playerData.bankruptcyLevel" to Bankruptcy.afterBalanceChange(d.bankruptcyLevel, safe, d.pendingSpins)
        )
    }

    /**
     * Adds (or removes, if negative) deaths in a sector inside a transaction.
     * Returns the change actually applied (deaths never go below 0).
     */
    private fun addDeathsInTx(tx: Transaction, userRef: DocumentReference, user: User, game: String, amount: Long): Long {
        val current = user.playerData.games[game]?.deaths ?: 0L
        val updated = (current + amount).coerceAtLeast(0L)
        val applied = updated - current
        if (applied == 0L) return 0L
        // FieldPath.of keeps sector names containing dots or symbols from being split into nested paths.
        tx.update(userRef, FieldPath.of("playerData", "games", game, "deaths"), updated)
        return applied
    }

    /**
     * Bumps the global eliminations counter once, *after* a transaction commits. It used to
     * run inside the transaction body, which Firestore may retry, so one death could be
     * counted several times. It's best-effort, so a rules/permission issue on gameState
     * never blocks the survivor's own death record.
     */
    private fun bumpGrandTotal(delta: Long) {
        if (delta == 0L) return
        globalRef.update("grandTotal", FieldValue.increment(delta))
            .addOnFailureListener { android.util.Log.w("FirebaseRepository", "grandTotal update failed", it) }
    }

    private fun DocumentSnapshot.toUserModel(): User? = toObject<User>()?.also { user ->
        user.uid = id
        // ESTIMATE: our own pending heartbeat reads as "now" instead of null until the server confirms.
        user.playerData.lastSeenMs = when (val v = get("playerData.lastSeen", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)) {
            is Timestamp -> v.toDate().time
            is Number -> v.toLong()
            else -> 0L
        }
    }

    private fun parseEconomy(s: DocumentSnapshot): EconomyConfig {
        val d = EconomyConfig()
        fun long(field: String, def: Long) = (s.get(field) as? Number)?.toLong() ?: def
        fun bool(field: String, def: Boolean) = s.getBoolean(field) ?: def

        val shop = (s.get("shopConfig") as? Map<*, *>)?.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val m = v as? Map<*, *> ?: return@mapNotNull null
            key to ShopOverride(
                enabled = m["enabled"] as? Boolean,
                price = (m["price"] as? Number)?.toLong(),
                reductionPct = (m["reductionPct"] as? Number)?.toInt()
            )
        }?.toMap() ?: emptyMap()

        val x = s.get("xpConfig") as? Map<*, *>
        val dx = d.xp
        fun xl(key: String, def: Long) = (x?.get(key) as? Number)?.toLong() ?: def
        val xp = XpConfig(
            death = xl("death", dx.death),
            survivalDay = xl("survivalDay", dx.survivalDay),
            purchase = xl("purchase", dx.purchase),
            spin = xl("spin", dx.spin),
            scratch = xl("scratch", dx.scratch),
            penaltySpin = xl("penaltySpin", dx.penaltySpin),
            curveBase = (x?.get("curveBase") as? Number)?.toDouble()?.takeIf { it > 0 } ?: dx.curveBase,
            curveExponent = (x?.get("curveExponent") as? Number)?.toDouble()?.takeIf { it > 0 } ?: dx.curveExponent,
            maxLevel = (x?.get("maxLevel") as? Number)?.toInt()?.takeIf { it >= 2 } ?: dx.maxLevel
        )

        return EconomyConfig(
            dailyBase = long("dailyBase", d.dailyBase),
            dailyUpgraded = long("dailyUpgraded", d.dailyUpgraded),
            survivalReward = long("survivalReward", d.survivalReward),
            insurancePct = (s.get("insurancePct") as? Number)?.toInt()?.coerceIn(0, 100) ?: d.insurancePct,
            insulationEnabled = bool("insulationEnabled", d.insulationEnabled),
            scratchEnabled = bool("scratchEnabled", d.scratchEnabled),
            scratchCost = long("scratchCost", d.scratchCost),
            shop = shop,
            xp = xp,
            flashSale = FlashSale(
                active = bool("flashSaleActive", false),
                pct = (s.get("flashSalePct") as? Number)?.toInt()?.coerceIn(0, FlashSale.MAX_PCT) ?: 0,
                endsAt = long("flashSaleEndsAt", 0L)
            )
        )
    }

    // ================================================================ archives

    suspend fun createArchive(archiveName: String, notes: String = "", startedAt: Long = 0L, roster: List<String> = emptyList()) {
        val docs = usersRef.get().await().documents
        val snapshotUsers = docs.mapNotNull { it.toUserModel() }
        val snapshots = snapshotUsers.map { user ->
            val d = user.playerData
            PlayerSnapshot(
                uid = user.uid,
                // Snapshot the handle, not the auth display name: the handle is what the survivor
                // is actually called, and it is what a past season should be able to show.
                displayName = user.handle,
                coins = d.coins,
                totalDeaths = d.games.values.sumOf { it.deaths },
                gameDeaths = d.games.mapValues { it.value.deaths },
                // v2.0.7: keep the rest of the survivor card so a past season can be
                // explored in full from Team Progress.
                level = d.level,
                xp = d.xp,
                prestigeCount = d.prestigeCount,
                title = d.title,
                insulationTier = d.insulationTier,
                bankruptcyLevel = d.bankruptcyLevel,
                pendingSpins = d.pendingSpins,
                avatar = d.avatar,
                avatarFrame = d.avatarFrame,
                color = d.color
            )
        }
        // Best-effort: a missing gameState doc must not block archiving the season.
        val grandTotal = try {
            (globalRef.get().await().get("grandTotal") as? Number)?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
        val archive = Archive(
            id = System.currentTimeMillis().toString(),
            name = archiveName,
            timestamp = System.currentTimeMillis(),
            playerSnapshots = snapshots,
            schemaVersion = 2,
            startedAt = startedAt,
            sectorRoster = roster,
            grandTotalAtEnd = grandTotal,
            notes = notes
        )
        firestore.collection("archives").document(archive.id).set(archive).await()

        // Season reset: fresh coins, deaths and penalties. Levels, XP, prestige and permanent
        // upgrades (Ration Upgrade, Wealth Insulation) carry over.
        val batch = firestore.batch()
        for (user in snapshotUsers) {
            val docRef = usersRef.document(user.uid)
            batch.update(docRef, mapOf(
                "playerData.coins" to STARTING_COINS,
                "playerData.pendingSpins" to 0,
                "playerData.bankruptcyLevel" to Bankruptcy.NONE
            ))
            for (gameKey in user.playerData.games.keys) {
                batch.update(docRef, FieldPath.of("playerData", "games", gameKey, "deaths"), 0L)
            }
        }
        batch.commit().await()
    }

    suspend fun restoreArchive(archiveId: String) {
        val archive = firestore.collection("archives").document(archiveId).get().await()
            .toObject<Archive>() ?: return
        val batch = firestore.batch()
        for (snapshot in archive.playerSnapshots) {
            if (snapshot.uid.isBlank()) continue
            val docRef = usersRef.document(snapshot.uid)
            batch.update(docRef, mapOf(
                "playerData.coins" to snapshot.coins,
                "displayName" to snapshot.displayName
            ))
            // Restore per-sector deaths too; previously only coins and names came back.
            for ((game, deaths) in snapshot.gameDeaths) {
                batch.update(docRef, FieldPath.of("playerData", "games", game, "deaths"), deaths)
            }
        }
        batch.commit().await()
    }

    companion object {
        const val DEFAULT_GAME = "Dead Island 2"
        const val STARTING_COINS = 250L
        /** Newest images streamed into the gallery. */
        const val MAX_GALLERY = 120
    }
}
