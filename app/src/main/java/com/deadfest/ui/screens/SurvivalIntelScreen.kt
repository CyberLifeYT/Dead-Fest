package com.deadfest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deadfest.ui.theme.CardBg
import com.deadfest.ui.theme.RedAccent

@Composable
fun SurvivalIntelScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Bar & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RedAccent)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "SURVIVAL INTEL",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "CLASSIFIED TERMINAL DATA // V${com.deadfest.BuildConfig.VERSION_NAME}",
                    color = RedAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Section 1: Origin & Rebuild
        IntelSectionCard(
            title = "ORIGIN & REBUILD",
            subtitle = "DISASTER RECOVERY LOG // 001",
            icon = Icons.Default.Storage
        ) {
            IntelBullet("Catastrophic Failure: The original source code of Dead-Fest was stored exclusively on a single Hard Disk Drive (HDD) that suffered catastrophic hardware failure.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Total Loss Protocol: All legacy project archives, version histories, and uncommitted modules were permanently lost to the wasteland static.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Ground-Up Phoenix Protocol: Rather than abandoning the survivors, Dead-Fest was rebuilt entirely from scratch, engineering a more resilient, scalable, and powerful core architecture.")
        }

        // Section 2: Next-Gen Architecture
        IntelSectionCard(
            title = "NEXT-GEN ARCHITECTURE",
            subtitle = "SYSTEM SPECIFICATIONS // 002",
            icon = Icons.Default.Build
        ) {
            IntelBullet("Material 3 Cyberpunk Design: Crafted with modern UI components, sleek surfaces, high-contrast typography, and signature crimson atmospheric accents.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Enhanced Stability: Rebuilt with strict reactive state management to eliminate memory leaks and runtime crashes under heavy network load.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Streamlined Layouts: Redesigned navigation flows and touch targets optimized for high-stress tactical environments and quick operator decisions.")
        }

        // Section 3: Wasteland Optimizations
        IntelSectionCard(
            title = "WASTELAND OPTIMIZATIONS",
            subtitle = "HARDWARE EFFICIENCY // 003",
            icon = Icons.Default.PhoneAndroid
        ) {
            IntelBullet("Performance Mode: Cuts transitions and speeds up the wheel on older, battered wasteland hardware. Switches on automatically for low-RAM devices (Settings → System).")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Battery Saver Integration: Live data feeds disconnect a few seconds after the terminal is backgrounded and reconnect instantly on return.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Fast Startup Protocol: Bypasses unnecessary initialization checks to drop operators directly into active feeds with minimal latency.")
        }

        // Section: Economy rules (v2.0.5)
        IntelSectionCard(
            title = "ECONOMY PROTOCOLS",
            subtitle = "SURVIVAL FINANCE // 005",
            icon = Icons.Default.Security
        ) {
            IntelBullet("Casualty Tax: every recorded death costs coins. Over 5,000 = 30%, over 1,000 = 20%, over 500 = 15%, otherwise a flat 25. Your balance never goes below 0.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Wealth Insulation (Black Market) cuts the tax rate permanently: Tier I -10%, II -25%, III -50%, IV -100%. Insurance then absorbs part of whatever tax is left, once.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Forced Spin Protocol: each death adds a pending penalty spin. Clear them on the Wheel of Fate.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Bankruptcy: hit 0 coins and you're Level 1 (survival payouts halved). Die again while bankrupt and it's Level 2 (+2 extra spins). Clear every spin with coins above 0 to escape.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Mutual Survival Reward: when anyone dies, everyone else collects a few coins.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Daily Rations: claim free coins once per day on the Dashboard. A Ration Upgrade doubles them for good.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("XP & Levels: deaths, purchases, spins, scratch cards and surviving a full day all earn XP. Levels unlock badge colours, frames and titles. Hit the cap to Prestige.")
        }

        // Section 4: Community & Future
        IntelSectionCard(
            title = "COMMUNITY & FUTURE",
            subtitle = "OPERATIONAL HORIZON // 004",
            icon = Icons.Default.Security
        ) {
            IntelBullet("Community-Driven Features: Built directly around survivor feedback, feature requests, and combat telemetry reports.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Real-Time Firestore Sync: Instantaneous cloud updates ensuring all operatives receive intel, alerts, and schedule changes simultaneously.")
            Spacer(modifier = Modifier.height(8.dp))
            IntelBullet("Custom Admin Overrides: Direct command channel integration allowing emergency broadcasts and rapid tactical updates from headquarters.")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun IntelSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = RedAccent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        subtitle,
                        color = RedAccent.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
fun IntelBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "▶",
            color = RedAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.LightGray,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
