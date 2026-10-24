package com.deadfest.ui.components

import com.deadfest.R
import com.deadfest.util.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deadfest.ui.theme.*
import com.deadfest.updater.UpdateManager

/** Starts the OTA download when shown and displays live progress until install/failure. */
@Composable
fun UpdateDownloader(url: String, versionName: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var status by remember(url) { mutableStateOf(UpdateManager.DownloadStatus()) }
    LaunchedEffect(url) {
        UpdateManager(context.applicationContext).downloadAndInstall(url, versionName) { status = it }
    }
    UpdateProgressDialog(versionName = versionName, status = status, onDismiss = onClose)
}

@Composable
fun UpdateProgressDialog(
    versionName: String,
    status: UpdateManager.DownloadStatus,
    onDismiss: () -> Unit
) {
    val canClose = status.isCompleted || status.isFailed
    Dialog(
        onDismissRequest = { if (canClose) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = canClose, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconTile(Icons.Default.SystemUpdate)
                Text(
                    text = when {
                        status.isFailed -> tr(R.string.updateprogressdialog_update_failed)
                        status.isCompleted -> tr(R.string.updateprogressdialog_firmware_received)
                        else -> tr(R.string.updateprogressdialog_downloading_firmware)
                    },
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )
                Text("v$versionName", color = RedAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                if (status.isFailed) {
                    Text(
                        text = status.error ?: tr(R.string.updateprogressdialog_download_failed),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(tr(R.string.updateprogressdialog_close), fontWeight = FontWeight.Black)
                    }
                } else {
                    // Cyber-terminal style progress bar.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(Outline, RoundedCornerShape(50))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(status.progress.coerceIn(0, 100) / 100f)
                                .fillMaxHeight()
                                .background(RedAccent, RoundedCornerShape(50))
                        )
                    }
                    Text(
                        text = "${status.progress}%",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (status.isCompleted) tr(R.string.updateprogressdialog_launching_installer) else tr(R.string.updateprogressdialog_keep_the_app_open_while_the),
                        color = if (status.isCompleted) OnlineGreen else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (status.isCompleted) {
                        TextButton(onClick = onDismiss) { Text(tr(R.string.updateprogressdialog_close), color = TextSecondary) }
                    }
                }
            }
        }
    }
}

/** Shown on launch when a newer build is published. A forced update can't be dismissed. */
@Composable
fun UpdateAvailableDialog(
    versionName: String,
    notes: String,
    forced: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!forced) onLater() },
        properties = DialogProperties(dismissOnBackPress = !forced, dismissOnClickOutside = !forced)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Default.SystemUpdate, size = 44)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            if (forced) tr(R.string.updateprogressdialog_update_required) else tr(R.string.updateprogressdialog_update_available),
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text("v$versionName", color = RedAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                if (notes.isNotBlank()) {
                    Text(
                        notes,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                    )
                }
                if (forced) {
                    Text(
                        tr(R.string.updateprogressdialog_this_version_is_required_to_keep),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!forced) {
                        TextButton(onClick = onLater) { Text(tr(R.string.updateprogressdialog_later), color = TextSecondary, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(tr(R.string.updateprogressdialog_update_now), fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}
