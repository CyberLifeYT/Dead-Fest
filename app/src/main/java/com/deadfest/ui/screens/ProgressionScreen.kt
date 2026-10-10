package com.deadfest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.LevelReward
import com.deadfest.data.model.LevelRewardCatalog
import com.deadfest.domain.Leveling
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

@Composable
fun ProgressionScreen(navController: NavController, viewModel: MainViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val globalState by viewModel.globalState.collectAsStateWithLifecycle()
    val rewards by viewModel.levelRewards.collectAsStateWithLifecycle()
    val p = user?.playerData
    val xpCfg = globalState.economy.xp
    val level = p?.level ?: 1
    val prestige = p?.prestigeCount ?: 0
    var confirmPrestige by remember { mutableStateOf(false) }

    if (confirmPrestige) {
        ConfirmDialog(
            title = "ENTER PRESTIGE ${prestige + 1}?",
            message = "Your level and XP reset to 1 / 0. You keep every reward you've unlocked, gain a permanent prestige badge and the Prestige frame.",
            confirmText = "PRESTIGE",
            onConfirm = viewModel::prestige,
            onDismiss = { confirmPrestige = false }
        )
    }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(
            title = "PROGRESSION",
            subtitle = "LEVELS, REWARDS & PRESTIGE",
            onBack = { navController.popBackStack() }
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FramedAvatar(p?.avatar, p?.avatarFrame, p?.color, 64)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("LEVEL", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                                Text("$level", color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Black)
                                if (prestige > 0) PrestigeBadge(prestige)
                            }
                            Text("/ ${xpCfg.maxLevel}", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp))
                        XpBar(p?.xp ?: 0, level, xpCfg)
                    }
                }
            }

            item {
                DashboardPanel("HOW TO EARN XP") {
                    XpRow(Icons.Default.Warning, "Recording a death", xpCfg.death)
                    XpRow(Icons.Default.Shield, "Surviving a full day (claim rations)", xpCfg.survivalDay)
                    XpRow(Icons.Default.ShoppingCart, "Black Market purchase", xpCfg.purchase)
                    XpRow(Icons.Default.Casino, "Wheel spin", xpCfg.spin)
                    XpRow(Icons.Default.ConfirmationNumber, "Scratch card", xpCfg.scratch)
                    XpRow(Icons.Default.Cyclone, "Clearing a penalty spin (bonus)", xpCfg.penaltySpin)
                }
            }

            item {
                val ready = Leveling.canPrestige(level, xpCfg)
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (ready) PrestigePurple.copy(alpha = 0.12f) else CardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, if (ready) PrestigePurple else Outline)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Default.AutoAwesome, tint = PrestigePurple, size = 46)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("PRESTIGE", color = TextPrimary, fontWeight = FontWeight.Black)
                            Text(
                                if (ready) "You've hit the cap. Reset to level 1 for a permanent badge."
                                else "Reach level ${xpCfg.maxLevel} to prestige. Prestiges so far: $prestige",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (ready) {
                            Button(
                                onClick = { confirmPrestige = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrestigePurple),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("GO", fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }

            item { SectionLabel("REWARD TRACK", Modifier.padding(top = 6.dp)) }
            items(rewards, key = { "${it.level}-${it.rewardId}" }) { reward ->
                val unlocked = LevelRewardCatalog.isUnlocked(reward, level, prestige)
                val equipped = when (reward.type) {
                    "color" -> p?.color.equals(reward.value, ignoreCase = true)
                    "frame" -> p?.avatarFrame == reward.value
                    "title" -> p?.title.equals(reward.value, ignoreCase = true)
                    else -> false
                }
                RewardRow(reward, unlocked, equipped) {
                    when (reward.type) {
                        "color" -> viewModel.equipColor(reward.value)
                        "frame" -> viewModel.equipFrame(reward.value)
                        "title" -> viewModel.equipTitle(reward.value)
                    }
                }
            }
            if (prestige > 0) {
                item {
                    RewardRow(
                        LevelReward(xpCfg.maxLevel, "frame_prestige", "frame", "Prestige frame", "prestige"),
                        unlocked = true,
                        equipped = p?.avatarFrame == "prestige"
                    ) { viewModel.equipFrame("prestige") }
                }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.equipFrame("none") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Outline)
                ) { Text("REMOVE AVATAR FRAME", color = TextSecondary, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun XpRow(icon: ImageVector, label: String, xp: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("+$xp XP", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

@Composable
private fun RewardRow(reward: LevelReward, unlocked: Boolean, equipped: Boolean, onEquip: () -> Unit) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (equipped) RedAccent else Outline)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(if (unlocked) Gold.copy(alpha = 0.15f) else CardBgRaised, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${reward.level}", color = if (unlocked) Gold else TextMuted, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            // Preview of the cosmetic itself
            when (reward.type) {
                "color" -> Box(Modifier.size(18.dp).background(parseThemeColor(reward.value), CircleShape))
                "frame" -> FramedAvatar("☠", reward.value, "#536DFE", 28)
                else -> Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(reward.label, color = if (unlocked) TextPrimary else TextMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    when (reward.type) { "frame" -> "Avatar frame"; "title" -> "Title"; else -> "Badge colour" },
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            when {
                equipped -> StatusPill("EQUIPPED", RedAccent)
                unlocked -> OutlinedButton(
                    onClick = onEquip,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Outline),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("EQUIP", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                else -> Icon(Icons.Default.Lock, contentDescription = "Locked", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
