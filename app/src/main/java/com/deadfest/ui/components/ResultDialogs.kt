package com.deadfest.ui.components

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
            Text("CASUALTY TAX", color = RedAccent, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
            Text(
                "${formatNumber(tax.finalTax)} coins lost",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp
            )
            Text(
                "Died in ${report.result.game} · ${tax.tier.label} tier · balance ${formatNumber(o.coinsBefore)} → ${formatNumber(o.coinsAfter)}",
                color = TextSecondary,
                fontSize = 12.sp
            )
            HorizontalDivider(color = Outline)

            if (tax.insulationSaved > 0) {
                ReportLine(
                    Icons.Default.Savings,
                    "TAX REDUCED: Insulation Tier ${ShopItem.roman(tax.insulationTier)} saved you ${formatNumber(tax.insulationSaved)} coins",
                    Gold
                )
            }
            if (tax.insuranceUsed) {
                ReportLine(
                    Icons.Default.HealthAndSafety,
                    "INSURANCE CLAIMED: ${tax.insurancePct}% of tax absorbed (${formatNumber(tax.insuranceSaved)} coins)",
                    ShieldCyan
                )
            }
            ReportLine(Icons.Default.Casino, "+${o.spinsAdded} pending spin(s) · ${o.pendingAfter} total", Gold)
            when {
                o.levelAfter >= Bankruptcy.LEVEL_2 && o.levelBefore < Bankruptcy.LEVEL_2 ->
                    ReportLine(Icons.Default.MoneyOff, "BANKRUPTCY LEVEL 2: clear your penalty spins to escape", RedAccent)
                o.levelAfter == Bankruptcy.LEVEL_1 && o.levelBefore == Bankruptcy.NONE ->
                    ReportLine(Icons.Default.MoneyOff, "BANKRUPTCY LEVEL 1: survival payouts halved", RedAccent)
            }
            report.result.xp?.let { ReportLine(Icons.Default.Star, "+${it.gained} XP", TextSecondary) }
            if (report.reward > 0) {
                val payoutText = when (val paid = report.survivorsPaid) {
                    null -> "Paying the Mutual Survival Reward..."
                    -1 -> "Mutual Survival Reward couldn't be paid out"
                    else -> "$paid survivor(s) collected ${formatNumber(report.reward)} coins from your fall"
                }
                ReportLine(Icons.Default.Groups, payoutText, TextSecondary)
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("ACKNOWLEDGED", fontWeight = FontWeight.Black) }
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
                    Text("RATIONS RECEIVED", color = OnlineGreen, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
                    Text("+${formatNumber(result.amount)} coins", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
            }
            Text("New balance: ${formatNumber(result.newBalance)} coins", color = TextSecondary, fontSize = 13.sp)
            if (result.upgraded) ReportLine(Icons.Default.Upgrade, "Ration Upgrade active", Gold)
            if (result.survivalBonusXp > 0) {
                ReportLine(Icons.Default.Shield, "Survived a full day without dying: +${result.survivalBonusXp} XP", ShieldCyan)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("COLLECT", fontWeight = FontWeight.Black) }
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
                Text("KEEP DEAD-FEST AWAKE?", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Text(
                "Some phones put apps to sleep to save battery. That can delay or block broadcasts, level-up alerts and firmware updates.",
                color = TextSecondary,
                fontSize = 13.sp
            )
            ReportLine(Icons.Default.NotificationsActive, "Alerts arrive on time", OnlineGreen)
            ReportLine(Icons.Default.SystemUpdate, "Updates finish downloading", OnlineGreen)
            ReportLine(Icons.Default.BatteryStd, "No extra drain: Dead-Fest runs no background service", TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text("NOT NOW", color = TextSecondary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onAllow,
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("ALLOW", fontWeight = FontWeight.Black) }
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
                "LEVEL UP",
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
                    "Keep surviving. More rewards ahead.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                SectionLabel("UNLOCKED")
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
                Text("Equip it from Utility Hub → Progression.", color = TextMuted, fontSize = 11.sp)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("CONTINUE", fontWeight = FontWeight.Black) }
        }
    }
}
