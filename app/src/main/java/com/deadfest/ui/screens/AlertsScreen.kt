package com.deadfest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.deadfest.data.model.AlertItem
import com.deadfest.domain.Presence
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

/** Personal alerts inbox. Opening it marks everything read (after it's been seen). */
@Composable
fun AlertsScreen(navController: NavController, viewModel: MainViewModel) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    // Mark read on the way out, so unread ones stay highlighted while you're looking at them.
    DisposableEffect(Unit) { onDispose { viewModel.markAlertsRead() } }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.action_clear_all),
            message = stringResource(R.string.alerts_empty_message),
            confirmText = stringResource(R.string.action_clear_all),
            destructive = true,
            onConfirm = viewModel::clearAlerts,
            onDismiss = { confirmClear = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.alerts_title),
            subtitle = stringResource(R.string.alerts_subtitle),
            onBack = { navController.popBackStack() },
            trailing = if (alerts.isNotEmpty()) {
                {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.action_clear_all), tint = TextMid)
                    }
                }
            } else null
        )
        if (alerts.isEmpty()) {
            Box(Modifier.padding(Spacing.gutter)) {
                EmptyStateCard(
                    icon = Icons.Default.NotificationsNone,
                    title = stringResource(R.string.alerts_empty_title),
                    message = stringResource(R.string.alerts_empty_message)
                )
            }
        } else {
            val now = remember(alerts) { System.currentTimeMillis() }
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.scrollBottom),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                itemsIndexed(alerts, key = { _, a -> a.id }) { index, alert ->
                    AlertRow(alert, now, Modifier.enterOnMount(index.coerceAtMost(8)))
                }
            }
        }
    }
}

private fun alertStyle(type: String): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (type) {
    AlertItem.TYPE_CURSED -> Icons.Default.AutoFixHigh to Ember
    AlertItem.TYPE_SHIELD_BLOCKED -> Icons.Default.Shield to Plasma
    AlertItem.TYPE_IMAGE_REMOVED -> Icons.Default.HideImage to Amber
    AlertItem.TYPE_GAME_REQUEST -> Icons.Default.SportsEsports to Toxic
    else -> Icons.Default.AdminPanelSettings to Prestige
}

@Composable
private fun AlertRow(alert: AlertItem, now: Long, modifier: Modifier = Modifier) {
    val (icon, accent) = alertStyle(alert.type)
    SurfaceCard(accent = if (!alert.read) accent else null, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(icon, tint = accent, size = 40)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.title, color = TextHi, style = DeadFestType.cardTitle, modifier = Modifier.weight(1f))
                    if (!alert.read) StatusPill(stringResource(R.string.alerts_unread_label), accent)
                }
                Spacer(Modifier.height(2.dp))
                Text(alert.body, color = TextMid, style = DeadFestType.body)
                Spacer(Modifier.height(4.dp))
                val from = if (alert.fromName.isNotBlank()) " · " + stringResource(R.string.alerts_from, alert.fromName) else ""
                Text(
                    Presence.lastSeenLabel(alert.createdAt, now) + from,
                    color = TextLow,
                    style = DeadFestType.microLabel
                )
            }
        }
    }
}
