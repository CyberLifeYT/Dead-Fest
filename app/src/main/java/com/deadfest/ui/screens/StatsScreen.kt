package com.deadfest.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.R
import com.deadfest.domain.Achievements
import com.deadfest.domain.StatsHistory
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

/**
 * Personal stats. Same chart grammar as the admin Live Dashboard: one hue per chart, thin marks
 * with rounded data ends, values in text colours, and a spoken summary for screen readers.
 */
@Composable
fun StatsScreen(navController: NavController, viewModel: MainViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val p = user?.playerData
    val sectors = remember(p?.games) {
        p?.games?.mapValues { it.value.deaths }?.filterValues { it > 0 }?.entries
            ?.sortedByDescending { it.value }?.map { it.key to it.value } ?: emptyList()
    }
    val totalDeaths = sectors.sumOf { it.second }
    val coinSeries = remember(p?.coinHistory) { StatsHistory.series(p?.coinHistory ?: emptyMap()) }
    val daySeries = remember(p?.deathsByDay) { lastDays(p?.deathsByDay ?: emptyMap(), 14) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.stats_title),
            subtitle = stringResource(R.string.stats_subtitle),
            onBack = { navController.popBackStack() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // ---- headline tiles
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.enterOnMount(0)) {
                MiniTile(stringResource(R.string.stats_deaths), formatNumber(totalDeaths), Ember, Modifier.weight(1f))
                MiniTile(stringResource(R.string.stats_coins), formatNumber(p?.coins ?: 0), Amber, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.enterOnMount(1)) {
                MiniTile(stringResource(R.string.stats_level), "${p?.level ?: 1}", Prestige, Modifier.weight(1f))
                MiniTile(stringResource(R.string.stats_streak), stringResource(R.string.stats_streak_days, p?.survivalStreak ?: 0), Toxic, Modifier.weight(1f))
                MiniTile(
                    stringResource(R.string.stats_achievements),
                    "${p?.achievements?.count { Achievements.byId(it) != null } ?: 0}/${Achievements.ALL.size}",
                    Plasma,
                    Modifier.weight(1f)
                )
            }

            // ---- deaths by sector
            DashboardPanel(stringResource(R.string.stats_deaths_by_sector)) {
                if (sectors.isEmpty()) {
                    Text(stringResource(R.string.stats_no_deaths), color = TextMid, style = DeadFestType.body)
                } else {
                    val max = sectors.maxOf { it.second }.coerceAtLeast(1)
                    sectors.forEach { (name, deaths) ->
                        val cd = stringResource(R.string.cd_chart_sector, name, deaths.toInt())
                        Row(
                            Modifier.padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = cd },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, color = TextHi, style = DeadFestType.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                            Box(Modifier.weight(1f).height(12.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(deaths / max.toFloat())
                                        .fillMaxHeight()
                                        .background(Ember, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                )
                            }
                            Text(formatNumber(deaths), color = TextHi, style = DeadFestType.cardTitle, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                        }
                    }
                }
            }

            // ---- coins over time
            DashboardPanel(stringResource(R.string.stats_coins_over_time)) {
                if (coinSeries.size < 2) {
                    Text(stringResource(R.string.stats_no_history), color = TextMid, style = DeadFestType.body)
                } else {
                    val low = coinSeries.minOf { it.second }
                    val high = coinSeries.maxOf { it.second }
                    val cd = stringResource(
                        R.string.cd_chart_coins, coinSeries.size,
                        formatNumber(coinSeries.first().second), formatNumber(coinSeries.last().second)
                    )
                    LineChart(
                        values = coinSeries.map { it.second },
                        modifier = Modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics { contentDescription = cd }
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Row(Modifier.fillMaxWidth()) {
                        Text(shortDay(coinSeries.first().first), color = TextLow, style = DeadFestType.microLabel)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.stats_low_high, formatNumber(low), formatNumber(high)), color = TextMid, style = DeadFestType.microLabel)
                        Spacer(Modifier.weight(1f))
                        Text(shortDay(coinSeries.last().first), color = TextLow, style = DeadFestType.microLabel)
                    }
                }
            }

            // ---- deaths per day
            DashboardPanel(stringResource(R.string.stats_deaths_per_day)) {
                val max = daySeries.maxOf { it.second }.coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    daySeries.forEach { (day, n) ->
                        val cd = stringResource(R.string.cd_chart_day, shortDay(day), n.toInt())
                        Column(
                            Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = cd },
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (n > 0) Text("$n", color = TextMid, style = DeadFestType.microLabel)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((n / max.toFloat()).coerceAtLeast(if (n > 0) 0.04f else 0f) * 0.82f)
                                    .background(Ember, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(shortDay(daySeries.first().first), color = TextLow, style = DeadFestType.microLabel)
                    Spacer(Modifier.weight(1f))
                    Text(shortDay(daySeries.last().first), color = TextLow, style = DeadFestType.microLabel)
                }
            }

            if (totalDeaths == 0L && coinSeries.size < 2) {
                EmptyState(Icons.Default.BarChart, stringResource(R.string.stats_no_history))
            }
            Spacer(Modifier.height(Spacing.scrollBottom))
        }
    }
}

/** The last [days] calendar days (oldest first), filling missing days with 0. */
private fun lastDays(map: Map<String, Long>, days: Int): List<Pair<String, Long>> {
    val now = System.currentTimeMillis()
    return (days - 1 downTo 0).map { back ->
        val key = StatsHistory.dayKey(now - back * 86_400_000L)
        key to (map[key] ?: 0L)
    }
}

/** "2026-10-24" -> "24/10". */
private fun shortDay(key: String): String =
    key.split("-").takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}" } ?: key

@Composable
private fun MiniTile(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier.semantics(mergeDescendants = true) {}, padding = Spacing.sm) {
        Text(label.uppercase(), color = TextLow, style = DeadFestType.microLabel, maxLines = 1)
        Text(value, color = accent, style = DeadFestType.stat, maxLines = 1)
    }
}

/** Single-series line: 2dp stroke, rounded joins, faint baseline. Amber = currency. */
@Composable
private fun LineChart(values: List<Long>, modifier: Modifier = Modifier) {
    val color = Amber
    val grid = InkLine
    Canvas(modifier) {
        val min = values.min().toFloat()
        val max = values.max().toFloat()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val pad = 6.dp.toPx()
        fun y(v: Long) = pad + (size.height - pad * 2) * (1f - (v - min) / range)
        drawLine(grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1f)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Mark the latest value.
        drawCircle(color, radius = 4.dp.toPx(), center = Offset((values.size - 1) * stepX, y(values.last())))
    }
}
