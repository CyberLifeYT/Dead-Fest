package com.deadfest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*

/**
 * One entry in the manual. Every entry used to be a single "Label: sentence" bullet; splitting at
 * that colon gives the entry a heading the layout can style, while [body], [stats] and [trailer]
 * still carry the original words unchanged. [warnIcon] marks the entries that are penalties, which
 * are promoted to an [AlertBanner] so they cannot be skimmed past.
 */
private data class IntelRule(
    val title: String,
    val body: String,
    val stats: List<Pair<String, String>> = emptyList(),
    val trailer: String = "",
    val warnIcon: ImageVector? = null
)

/**
 * A dossier section. The copy is data rather than hand-built composables so every section renders
 * through the same frame and a new section is one list entry.
 */
private data class IntelSection(
    val title: String,
    val logId: String,
    val icon: ImageVector,
    val accent: Color,
    val rules: List<IntelRule>
)

/**
 * The manual itself, in the original reading order. Numeric rules whose text was a list of
 * "threshold = rate" pairs are the only entries split onto [IntelRule.stats] rows.
 */
private val intelSections = listOf(
    IntelSection(
        title = "Origin & Rebuild",
        logId = "DISASTER RECOVERY LOG // 001",
        icon = Icons.Default.Storage,
        accent = Ember,
        rules = listOf(
            IntelRule(
                title = "Catastrophic Failure",
                body = "The original source code of Dead-Fest was stored exclusively on a single Hard Disk Drive (HDD) that suffered catastrophic hardware failure."
            ),
            IntelRule(
                title = "Total Loss Protocol",
                body = "All legacy project archives, version histories, and uncommitted modules were permanently lost to the wasteland static."
            ),
            IntelRule(
                title = "Ground-Up Phoenix Protocol",
                body = "Rather than abandoning the survivors, Dead-Fest was rebuilt entirely from scratch, engineering a more resilient, scalable, and powerful core architecture."
            )
        )
    ),
    IntelSection(
        title = "Next-Gen Architecture",
        logId = "SYSTEM SPECIFICATIONS // 002",
        icon = Icons.Default.Build,
        accent = Plasma,
        rules = listOf(
            IntelRule(
                title = "Material 3 Cyberpunk Design",
                body = "Crafted with modern UI components, sleek surfaces, high-contrast typography, and signature crimson atmospheric accents."
            ),
            IntelRule(
                title = "Enhanced Stability",
                body = "Rebuilt with strict reactive state management to eliminate memory leaks and runtime crashes under heavy network load."
            ),
            IntelRule(
                title = "Streamlined Layouts",
                body = "Redesigned navigation flows and touch targets optimized for high-stress tactical environments and quick operator decisions."
            )
        )
    ),
    IntelSection(
        title = "Wasteland Optimizations",
        logId = "HARDWARE EFFICIENCY // 003",
        icon = Icons.Default.PhoneAndroid,
        accent = Toxic,
        rules = listOf(
            IntelRule(
                title = "Performance Mode",
                body = "Cuts transitions and speeds up the wheel on older, battered wasteland hardware. Switches on automatically for low-RAM devices (Settings → System)."
            ),
            IntelRule(
                title = "Battery Saver Integration",
                body = "Live data feeds disconnect a few seconds after the terminal is backgrounded and reconnect instantly on return."
            ),
            IntelRule(
                title = "Fast Startup Protocol",
                body = "Bypasses unnecessary initialization checks to drop operators directly into active feeds with minimal latency."
            )
        )
    ),
    IntelSection(
        title = "Economy Protocols",
        logId = "SURVIVAL FINANCE // 005",
        icon = Icons.Default.Security,
        accent = Amber,
        rules = listOf(
            IntelRule(
                title = "Casualty Tax",
                body = "Every recorded death costs coins.",
                stats = listOf(
                    "Over 5,000" to "30%",
                    "Over 1,000" to "20%",
                    "Over 500" to "15%",
                    "Otherwise" to "a flat 25"
                ),
                trailer = "Your balance never goes below 0."
            ),
            IntelRule(
                title = "Wealth Insulation (Black Market)",
                body = "Cuts the tax rate permanently.",
                stats = listOf(
                    "Tier I" to "-10%",
                    "Tier II" to "-25%",
                    "Tier III" to "-50%",
                    "Tier IV" to "-100%"
                ),
                trailer = "Insurance then absorbs part of whatever tax is left, once."
            ),
            IntelRule(
                title = "Forced Spin Protocol",
                body = "Each death adds a pending penalty spin. Clear them on the Wheel of Fate.",
                warnIcon = Icons.Default.Casino
            ),
            IntelRule(
                title = "Bankruptcy",
                body = "Hit 0 coins and you're Level 1 (survival payouts halved). Die again while bankrupt and it's Level 2 (+2 extra spins). Clear every spin with coins above 0 to escape.",
                warnIcon = Icons.Default.MoneyOff
            ),
            IntelRule(
                title = "Mutual Survival Reward",
                body = "When anyone dies, everyone else collects a few coins."
            ),
            IntelRule(
                title = "Daily Rations",
                body = "Claim free coins once per day on the Dashboard. A Ration Upgrade doubles them for good."
            ),
            IntelRule(
                title = "XP & Levels",
                body = "Deaths, purchases, spins, scratch cards and surviving a full day all earn XP. Levels unlock badge colours, frames and titles. Hit the cap to Prestige."
            )
        )
    ),
    IntelSection(
        title = "Community & Future",
        logId = "OPERATIONAL HORIZON // 004",
        icon = Icons.Default.Groups,
        accent = Prestige,
        rules = listOf(
            IntelRule(
                title = "Community-Driven Features",
                body = "Built directly around survivor feedback, feature requests, and combat telemetry reports."
            ),
            IntelRule(
                title = "Real-Time Firestore Sync",
                body = "Instantaneous cloud updates ensuring all operatives receive intel, alerts, and schedule changes simultaneously."
            ),
            IntelRule(
                title = "Custom Admin Overrides",
                body = "Direct command channel integration allowing emergency broadcasts and rapid tactical updates from headquarters."
            )
        )
    )
)

@Composable
fun SurvivalIntelScreen(navController: NavController) {
    // No background of its own: DeadFestBackground is applied once behind the whole NavHost.
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "SURVIVAL INTEL",
            subtitle = "CLASSIFIED TERMINAL DATA // V${com.deadfest.BuildConfig.VERSION_NAME}",
            onBack = { navController.popBackStack() }
        )

        // The manual is long, so it is one lazy scroll container with no nested scrollers.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.md,
                bottom = Spacing.scrollBottom
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            item {
                HeroPanel(
                    eyebrow = "Field Manual",
                    title = "OPERATOR BRIEFING",
                    icon = Icons.Default.Security,
                    modifier = Modifier.enterOnMount(0),
                    accent = Ember,
                    badge = { StatusPill("PRIORITY", Ember) }
                ) {
                    Text(
                        "What was lost, what was rebuilt, and the protocols that decide who survives.",
                        color = TextMid,
                        style = DeadFestType.body
                    )
                }
            }

            itemsIndexed(intelSections, key = { _, section -> section.logId }) { index, section ->
                IntelSectionCard(
                    title = section.title,
                    subtitle = section.logId,
                    icon = section.icon,
                    modifier = Modifier.enterOnMount(index + 1),
                    accent = section.accent
                ) {
                    section.rules.forEachIndexed { ruleIndex, rule ->
                        if (ruleIndex > 0) Spacer(Modifier.height(Spacing.md))
                        IntelRuleBlock(rule, section.accent)
                    }
                }
            }
        }
    }
}

/**
 * One dossier section: icon, [SectionLabel] heading, archive stamp and a [SurfaceCard] holding the
 * section's rules. Same shape for every section, so the document reads as one continuous file.
 */
@Composable
fun IntelSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = Ember,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint = accent, size = 40)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                SectionLabel(title, accent = accent)
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    subtitle,
                    color = TextLow,
                    style = DeadFestType.microLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        SurfaceCard(accent = accent, content = content)
    }
}

/** A single rule: accent marker, heading, body and any label/value stats. */
@Composable
private fun IntelRuleBlock(rule: IntelRule, accent: Color) {
    val warnIcon = rule.warnIcon
    if (warnIcon != null) {
        AlertBanner(
            icon = warnIcon,
            title = rule.title.uppercase(),
            message = rule.body,
            accent = accent
        )
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(rule.title, color = TextHi, style = DeadFestType.cardTitle)
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(rule.body, color = TextMid, style = DeadFestType.body)
        if (rule.stats.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            rule.stats.forEach { (label, value) -> KeyValueRow(label, value) }
        }
        if (rule.trailer.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(rule.trailer, color = TextMid, style = DeadFestType.body)
        }
    }
}
