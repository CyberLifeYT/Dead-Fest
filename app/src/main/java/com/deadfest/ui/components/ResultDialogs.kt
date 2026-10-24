package com.deadfest.ui.components

import com.deadfest.R
import com.deadfest.util.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.deadfest.data.model.DailyResult
import com.deadfest.data.model.ShopItem
import com.deadfest.domain.Bankruptcy
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.DeathReport
import com.deadfest.ui.viewmodel.LevelUpEvent

@Composable
private fun ResultDialogFrame(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ReportLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Shown after recording your own death: tax breakdown, spins, bankruptcy, payout. */
@Composable
fun DeathReportDialog(report: DeathReport, onDismiss: () -> Unit) {
    val o = report.result.outcome
    val tax = o.tax
    Dialog(onDismissRequest = onDismiss) {
        ResultDialogFrame(RedAccent) {
            Text(tr(R.string.resultdialogs_casualty_tax), color = RedAccent, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
            Text(
                tr(R.string.resultdialogs_1_coins_lost, formatNumber(tax.finalTax)),
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp
            )
            Text(
                tr(R.string.resultdialogs_died_in_1_2_tier_balance, report.result.game, tax.tier.label, formatNumber(o.coinsBefore), formatNumber(o.coinsAfter)),
                color = TextSecondary,
                fontSize = 12.sp
            )
            HorizontalDivider(color = Outline)

            if (report.result.overloadMultiplier > 1.0) {
                val m = report.result.overloadMultiplier
                ReportLine(
                    Icons.Default.Bolt,
                    androidx.compose.ui.res.stringResource(
                        com.deadfest.R.string.report_overload,
                        if (m % 1.0 == 0.0) m.toLong().toString() else String.format(java.util.Locale.US, "%.1f", m)
                    ),
                    Gold
                )
            }
            if (tax.insulationSaved > 0) {
                ReportLine(
                    Icons.Default.Savings,
                    tr(R.string.resultdialogs_tax_reduced_insulation_tier_1_saved, ShopItem.roman(tax.insulationTier), formatNumber(tax.insulationSaved)),
                    Gold
                )
            }
            if (tax.insuranceUsed) {
                ReportLine(
                    Icons.Default.HealthAndSafety,
                    tr(R.string.resultdialogs_insurance_claimed_1_of_tax_absorbed, tax.insurancePct, formatNumber(tax.insuranceSaved)),
                    ShieldCyan
                )
            }
            ReportLine(Icons.Default.Casino, tr(R.string.resultdialogs_1_pending_spin_s_2_total, o.spinsAdded, o.pendingAfter), Gold)
            when {
                o.levelAfter >= Bankruptcy.LEVEL_2 && o.levelBefore < Bankruptcy.LEVEL_2 ->
                    ReportLine(Icons.Default.MoneyOff, tr(R.string.resultdialogs_bankruptcy_level_2_clear_your_penalty), RedAccent)
                o.levelAfter == Bankruptcy.LEVEL_1 && o.levelBefore == Bankruptcy.NONE ->
                    ReportLine(Icons.Default.MoneyOff, tr(R.string.resultdialogs_bankruptcy_level_1_survival_payouts_halved), RedAccent)
            }
            report.result.xp?.let { ReportLine(Icons.Default.Star, tr(R.string.resultdialogs_1_xp, it.gained), TextSecondary) }
            if (report.reward > 0) {
                val payoutText = when (val paid = report.survivorsPaid) {
                    null -> tr(R.string.resultdialogs_paying_the_mutual_survival_reward)
                    -1 -> tr(R.string.resultdialogs_mutual_survival_reward_couldn_t_be)
                    else -> tr(R.string.resultdialogs_1_survivor_s_collected_2_coins, paid, formatNumber(report.reward))
                }
                ReportLine(Icons.Default.Groups, payoutText, TextSecondary)
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text(tr(R.string.resultdialogs_acknowledged), fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun DailyClaimDialog(result: DailyResult, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ResultDialogFrame(OnlineGreen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.Inventory2, tint = OnlineGreen, size = 48)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(tr(R.string.resultdialogs_rations_received), color = OnlineGreen, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
                    Text(tr(R.string.resultdialogs_1_coins, formatNumber(result.amount)), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
            }
            Text(tr(R.string.resultdialogs_new_balance_1_coins, formatNumber(result.newBalance)), color = TextSecondary, fontSize = 13.sp)
            if (result.upgraded) ReportLine(Icons.Default.Upgrade, tr(R.string.resultdialogs_ration_upgrade_active), Gold)
            if (result.survivalBonusXp > 0) {
                ReportLine(Icons.Default.Shield, tr(R.string.resultdialogs_survived_a_full_day_without_dying, result.survivalBonusXp), ShieldCyan)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text(tr(R.string.resultdialogs_collect), fontWeight = FontWeight.Black) }
        }
    }
}

/** First-launch offer to lift battery optimisation (Android's own prompt follows on ALLOW). */
@Composable
fun BatteryOptimizationDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        ResultDialogFrame(Gold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.BatteryChargingFull, tint = Gold, size = 48)
                Spacer(Modifier.width(14.dp))
                Text(tr(R.string.resultdialogs_keep_dead_fest_awake), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Text(
                tr(R.string.resultdialogs_some_phones_put_apps_to_sleep),
                color = TextSecondary,
                fontSize = 13.sp
            )
            ReportLine(Icons.Default.NotificationsActive, tr(R.string.resultdialogs_alerts_arrive_on_time), OnlineGreen)
            ReportLine(Icons.Default.SystemUpdate, tr(R.string.resultdialogs_updates_finish_downloading), OnlineGreen)
            ReportLine(Icons.Default.BatteryStd, tr(R.string.resultdialogs_no_extra_drain_dead_fest_runs), TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text(tr(R.string.resultdialogs_not_now), color = TextSecondary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onAllow,
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(tr(R.string.resultdialogs_allow), fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
fun LevelUpDialog(event: LevelUpEvent, onDismiss: () -> Unit) {
    val reduced = LocalPerformanceMode.current
    val scale = remember { Animatable(if (reduced) 1f else 0.4f) }
    LaunchedEffect(event.level) {
        if (!reduced) scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f))
    }
    Dialog(onDismissRequest = onDismiss) {
        ResultDialogFrame(Gold) {
            Text(
                tr(R.string.resultdialogs_level_up),
                color = Gold,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .size(96.dp)
                    .background(Gold.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${event.level}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 44.sp)
            }
            if (event.rewards.isEmpty()) {
                Text(
                    tr(R.string.resultdialogs_keep_surviving_more_rewards_ahead),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                SectionLabel(tr(R.string.resultdialogs_unlocked))
                event.rewards.forEach { r ->
                    ReportLine(
                        when (r.type) {
                            "frame" -> Icons.Default.AccountCircle
                            "title" -> Icons.Default.MilitaryTech
                            else -> Icons.Default.Palette
                        },
                        r.label,
                        Gold
                    )
                }
                Text(tr(R.string.resultdialogs_equip_it_from_utility_hub_progression), color = TextMuted, fontSize = 11.sp)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text(tr(R.string.resultdialogs_continue), fontWeight = FontWeight.Black) }
        }
    }
}
