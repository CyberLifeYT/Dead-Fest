package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 * "threshold = rate" pairs are the only entries split onto [IntelRule.stats] rows. A getter rather
 * than a stored list, so the copy follows the current language.
 */
private val intelSections: List<IntelSection>
    get() = listOf(
    IntelSection(
        title = tr(R.string.intel_origin_rebuild),
        logId = tr(R.string.intel_disaster_recovery_log_001),
        icon = Icons.Default.Storage,
        accent = Ember,
        rules = listOf(
            IntelRule(
                title = tr(R.string.intel_catastrophic_failure),
                body = tr(R.string.intel_the_original_source_code_of)
            ),
            IntelRule(
                title = tr(R.string.intel_total_loss_protocol),
                body = tr(R.string.intel_all_legacy_project_archives_version)
            ),
            IntelRule(
                title = tr(R.string.intel_ground_up_phoenix_protocol),
                body = tr(R.string.intel_rather_than_abandoning_the_survivors)
            )
        )
    ),
    IntelSection(
        title = tr(R.string.intel_next_gen_architecture),
        logId = tr(R.string.intel_system_specifications_002),
        icon = Icons.Default.Build,
        accent = Plasma,
        rules = listOf(
            IntelRule(
                title = tr(R.string.intel_material_3_cyberpunk_design),
                body = tr(R.string.intel_crafted_with_modern_ui_components)
            ),
            IntelRule(
                title = tr(R.string.intel_enhanced_stability),
                body = tr(R.string.intel_rebuilt_with_strict_reactive_state)
            ),
            IntelRule(
                title = tr(R.string.intel_streamlined_layouts),
                body = tr(R.string.intel_redesigned_navigation_flows_and_touch)
            )
        )
    ),
    IntelSection(
        title = tr(R.string.intel_wasteland_optimizations),
        logId = tr(R.string.intel_hardware_efficiency_003),
        icon = Icons.Default.PhoneAndroid,
        accent = Toxic,
        rules = listOf(
            IntelRule(
                title = tr(R.string.intel_performance_mode),
                body = tr(R.string.intel_cuts_transitions_and_speeds_up)
            ),
            IntelRule(
                title = tr(R.string.intel_battery_saver_integration),
                body = tr(R.string.intel_live_data_feeds_disconnect_a)
            ),
            IntelRule(
                title = tr(R.string.intel_fast_startup_protocol),
                body = tr(R.string.intel_bypasses_unnecessary_initialization_checks_to)
            )
        )
    ),
    IntelSection(
        title = tr(R.string.intel_economy_protocols),
        logId = tr(R.string.intel_survival_finance_005),
        icon = Icons.Default.Security,
        accent = Amber,
        rules = listOf(
            IntelRule(
                title = tr(R.string.intel_casualty_tax),
                body = tr(R.string.intel_every_recorded_death_costs_coins),
                stats = listOf(
                    tr(R.string.intel_over_5_000) to tr(R.string.intel_30),
                    tr(R.string.intel_over_1_000) to tr(R.string.intel_20),
                    tr(R.string.intel_over_500) to tr(R.string.intel_15),
                    tr(R.string.intel_otherwise) to tr(R.string.intel_a_flat_25)
                ),
                trailer = tr(R.string.intel_your_balance_never_goes_below)
            ),
            IntelRule(
                title = tr(R.string.intel_wealth_insulation_black_market),
                body = tr(R.string.intel_cuts_the_tax_rate_permanently),
                stats = listOf(
                    tr(R.string.intel_tier_i) to tr(R.string.intel_10),
                    tr(R.string.intel_tier_ii) to tr(R.string.intel_25),
                    tr(R.string.intel_tier_iii) to tr(R.string.intel_50),
                    tr(R.string.intel_tier_iv) to tr(R.string.intel_100)
                ),
                trailer = tr(R.string.intel_insurance_then_absorbs_part_of)
            ),
            IntelRule(
                title = tr(R.string.intel_forced_spin_protocol),
                body = tr(R.string.intel_each_death_adds_a_pending),
                warnIcon = Icons.Default.Casino
            ),
            IntelRule(
                title = tr(R.string.intel_bankruptcy),
                body = tr(R.string.intel_hit_0_coins_and_you),
                warnIcon = Icons.Default.MoneyOff
            ),
            IntelRule(
                title = tr(R.string.intel_mutual_survival_reward),
                body = tr(R.string.intel_when_anyone_dies_everyone_else)
            ),
            IntelRule(
                title = tr(R.string.intel_daily_rations),
                body = tr(R.string.intel_claim_free_coins_once_per)
            ),
            IntelRule(
                title = tr(R.string.intel_xp_levels),
                body = tr(R.string.intel_deaths_purchases_spins_scratch_cards)
            )
        )
    ),
    IntelSection(
        title = tr(R.string.intel_community_future),
        logId = tr(R.string.intel_operational_horizon_004),
        icon = Icons.Default.Groups,
        accent = Prestige,
        rules = listOf(
            IntelRule(
                title = tr(R.string.intel_community_driven_features),
                body = tr(R.string.intel_built_directly_around_survivor_feedback)
            ),
            IntelRule(
                title = tr(R.string.intel_real_time_firestore_sync),
                body = tr(R.string.intel_instantaneous_cloud_updates_ensuring_all)
            ),
            IntelRule(
                title = tr(R.string.intel_custom_admin_overrides),
                body = tr(R.string.intel_direct_command_channel_integration_allowing)
            )
        )
    )
)

@Composable
fun SurvivalIntelScreen(navController: NavController) {
    // No background of its own: DeadFestBackground is applied once behind the whole NavHost.
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = tr(R.string.more_survival_intel),
            subtitle = tr(R.string.survivalintel_classified_terminal_data_v_1, com.deadfest.BuildConfig.VERSION_NAME),
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
                    eyebrow = tr(R.string.survivalintel_field_manual),
                    title = tr(R.string.survivalintel_operator_briefing),
                    icon = Icons.Default.Security,
                    modifier = Modifier.enterOnMount(0),
                    accent = Ember,
                    badge = { StatusPill(tr(R.string.survivalintel_priority), Ember) }
                ) {
                    Text(
                        tr(R.string.survivalintel_what_was_lost_what_was_rebuilt),
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

/**
 * A single rule: accent marker, heading, body and any label/value stats.
 *
 * The whole rule is one merged semantics node, so the heading, body and any stats are announced as
 * a single item instead of separate fragments. heading() merges up into that node, so heading
 * navigation still stops at every rule.
 */
@Composable
private fun IntelRuleBlock(rule: IntelRule, accent: Color) {
    val warnIcon = rule.warnIcon
    if (warnIcon != null) {
        // AlertBanner has no modifier slot, so the wrapper carries the merged heading semantics.
        Box(Modifier.semantics(mergeDescendants = true) { heading() }) {
            AlertBanner(
                icon = warnIcon,
                title = rule.title.uppercase(),
                message = rule.body,
                accent = accent
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                rule.title,
                color = TextHi,
                style = DeadFestType.cardTitle,
                modifier = Modifier.semantics { heading() }
            )
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
