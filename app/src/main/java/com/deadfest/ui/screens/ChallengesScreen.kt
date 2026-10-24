package com.deadfest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.R
import com.deadfest.domain.ChallengeDef
import com.deadfest.domain.ChallengeMetric
import com.deadfest.domain.Challenges
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import com.deadfest.util.tr
import kotlinx.coroutines.delay

/** This week's four challenges: progress, rewards and claiming. Resets every Monday. */
@Composable
fun ChallengesScreen(navController: NavController, viewModel: MainViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val p = user?.playerData
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // A minute is plenty of precision for "resets in 3d 4h".
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

    val week = Challenges.weekKey(now)
    val challenges = remember(week) { Challenges.forWeek(week) }
    // Last week's numbers don't count once Monday comes round.
    val stats = if (p?.weekKey == week) p.weeklyStats else emptyMap()
    val claimed = if (p?.weekKey == week) p.weeklyClaimed else emptyList()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.challenges_title),
            subtitle = stringResource(R.string.challenges_subtitle),
            onBack = { navController.popBackStack() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            StatusPill(stringResource(R.string.challenges_resets, durationLabel(Challenges.weekEndsAt(now) - now)), Amber)
            challenges.forEachIndexed { i, c ->
                ChallengeCard(
                    c = c,
                    progress = Challenges.progress(c, stats),
                    claimed = c.id in claimed,
                    onClaim = { viewModel.claimChallenge(c) },
                    modifier = Modifier.enterOnMount(i)
                )
            }
            Spacer(Modifier.height(Spacing.scrollBottom))
        }
    }
}

private fun metricIcon(m: ChallengeMetric): ImageVector = when (m) {
    ChallengeMetric.SPINS -> Icons.Default.Casino
    ChallengeMetric.SCRATCHES -> Icons.Default.ConfirmationNumber
    ChallengeMetric.PURCHASES -> Icons.Default.ShoppingCart
    ChallengeMetric.CURSES -> Icons.Default.AutoFixHigh
    ChallengeMetric.SURVIVED_DAYS -> Icons.Default.Shield
    ChallengeMetric.RATIONS -> Icons.Default.Inventory2
    ChallengeMetric.CHATS -> Icons.Default.Forum
    ChallengeMetric.IMAGES -> Icons.Default.PhotoCamera
    ChallengeMetric.COMMENTS -> Icons.Default.ChatBubble
    ChallengeMetric.REACTIONS -> Icons.Default.Favorite
}

@Composable
private fun ChallengeCard(c: ChallengeDef, progress: Long, claimed: Boolean, onClaim: () -> Unit, modifier: Modifier = Modifier) {
    val done = progress >= c.target
    val accent = when {
        claimed -> TextLow
        done -> Toxic
        else -> Amber
    }
    SurfaceCard(accent = if (done && !claimed) Toxic else null, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(metricIcon(c.metric), tint = accent, size = 44)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(c.localTitle(), color = if (claimed) TextMid else TextHi, style = DeadFestType.cardTitle)
                Text(
                    stringResource(R.string.challenges_reward, formatNumber(c.coins), formatNumber(c.xp)),
                    color = Amber,
                    style = DeadFestType.microLabel
                )
            }
            when {
                claimed -> StatusPill(stringResource(R.string.challenges_claimed), TextLow)
                done -> Button(
                    onClick = onClaim,
                    colors = ButtonDefaults.buttonColors(containerColor = Toxic, contentColor = readableOn(Toxic)),
                    contentPadding = PaddingValues(horizontal = Spacing.sm)
                ) { Text(stringResource(R.string.challenges_claim).uppercase(), style = DeadFestType.chip) }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        ProportionBar(progress / c.target.toFloat(), accent = accent)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.challenges_progress, formatNumber(progress), formatNumber(c.target)) +
                if (done && !claimed) " · " + stringResource(R.string.challenges_done) else "",
            color = TextMid,
            style = DeadFestType.microLabel
        )
    }
}

/** "3d 4h", "5h 12m", "9m". */
private fun durationLabel(ms: Long): String {
    val mins = (ms / 60_000).coerceAtLeast(0)
    val d = mins / (60 * 24)
    val h = (mins / 60) % 24
    val m = mins % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

/** The challenge's title in the phone's language; [ChallengeDef.title] is the English fallback. */
internal fun ChallengeDef.localTitle(): String = when (id) {
    "spin_10" -> tr(R.string.challenge_spin_10)
    "spin_25" -> tr(R.string.challenge_spin_25)
    "scratch_5" -> tr(R.string.challenge_scratch_5)
    "buy_3" -> tr(R.string.challenge_buy_3)
    "curse_2" -> tr(R.string.challenge_curse_2)
    "survive_3" -> tr(R.string.challenge_survive_3)
    "rations_5" -> tr(R.string.challenge_rations_5)
    "chat_15" -> tr(R.string.challenge_chat_15)
    "image_2" -> tr(R.string.challenge_image_2)
    "comment_5" -> tr(R.string.challenge_comment_5)
    "react_10" -> tr(R.string.challenge_react_10)
    else -> title
}
