package com.deadfest.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.R
import com.deadfest.data.model.ChatMessage
import com.deadfest.data.model.SharedImage
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Group chat: the newest 100 messages, live. Photos reuse the gallery pipeline (compressed
 * thumbnail on the message, full image fetched on tap). Long-press your own message to delete it;
 * admins can delete any.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(navController: NavController, viewModel: MainViewModel) {
    val messages by viewModel.chat.collectAsStateWithLifecycle()
    val me by viewModel.user.collectAsStateWithLifecycle()
    val chatImage by viewModel.chatImage.collectAsStateWithLifecycle()
    val sending by viewModel.chatSending.collectAsStateWithLifecycle()
    var draft by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var viewing by remember { mutableStateOf<ChatMessage?>(null) }
    var deleting by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    // While this screen is open: no chat notifications, and everything shown counts as read.
    DisposableEffect(Unit) {
        viewModel.onChatVisible(true)
        onDispose { viewModel.onChatVisible(false) }
    }
    LaunchedEffect(messages.lastOrNull()?.id) { viewModel.onChatVisible(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.prepareChatImage(uri)
    }

    // Follow new messages to the bottom.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = stringResource(R.string.chat_title),
            subtitle = stringResource(R.string.chat_subtitle),
            onBack = { navController.popBackStack() }
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Box(Modifier.padding(Spacing.gutter)) {
                    EmptyStateCard(
                        icon = Icons.Default.Forum,
                        title = stringResource(R.string.chat_empty_title),
                        message = stringResource(R.string.chat_empty_message)
                    )
                }
            } else {
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val mine = msg.senderUid == me?.uid
                        val canDelete = mine || me?.admin == true
                        ChatBubble(
                            msg = msg,
                            mine = mine,
                            time = if (msg.createdAt > 0) timeFormat.format(Date(msg.createdAt)) else "",
                            onOpenPhoto = { viewing = msg },
                            onLongPress = if (canDelete) ({ deleting = msg }) else null
                        )
                    }
                }
            }
        }

        // ---- attached photo preview
        val preview = chatImage.preview
        if (chatImage.processing || preview != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preview != null) {
                    Image(
                        bitmap = remember(preview) { preview.asImageBitmap() },
                        contentDescription = stringResource(R.string.chat_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm))
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.chat_photo), color = TextMid, style = DeadFestType.body, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::cancelChatImage, enabled = !sending) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_chat_remove_photo), tint = TextMid)
                    }
                } else {
                    CircularProgressIndicator(color = Ember, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.chat_preparing), color = TextMid, style = DeadFestType.body)
                }
            }
        }

        // ---- composer
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ink1)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !chatImage.processing && !sending
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.cd_chat_attach), tint = TextMid)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= ChatMessage.MAX_TEXT) draft = it },
                placeholder = { Text(stringResource(R.string.chat_hint), color = TextLow) },
                maxLines = 4,
                colors = deadFestFieldColors(),
                shape = RoundedCornerShape(Radius.lg),
                modifier = Modifier.weight(1f)
            )
            val canSend = (draft.isNotBlank() || chatImage.pending != null) && !sending
            IconButton(onClick = { if (viewModel.sendChat(draft)) draft = "" }, enabled = canSend) {
                if (sending) {
                    CircularProgressIndicator(color = Ember, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_chat_send),
                        tint = if (canSend) Ember else TextLow
                    )
                }
            }
        }
    }

    deleting?.let { msg ->
        ConfirmDialog(
            title = stringResource(R.string.chat_delete_title),
            message = stringResource(R.string.chat_delete_message),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.deleteChatMessage(msg) },
            onDismiss = { deleting = null }
        )
    }

    viewing?.let { msg ->
        val image = remember(msg.id) { msg.asSharedImage() }
        if (image != null) {
            ImageViewer(
                image = image,
                canDelete = false,
                load = viewModel::loadImage,
                onDelete = {},
                onClose = { viewing = null }
            )
        }
    }
}

/** A chat photo shown through the gallery viewer (same cache, same full-size loading). */
private fun ChatMessage.asSharedImage(): SharedImage? {
    val id = imageId ?: return null
    val bytes = thumb ?: return null
    return SharedImage(id, senderUid, senderName, text, createdAt, imageWidth, imageHeight, 0L, bytes)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    msg: ChatMessage,
    mine: Boolean,
    time: String,
    onOpenPhoto: () -> Unit,
    onLongPress: (() -> Unit)?
) {
    val accent = parseThemeColor(msg.senderColor.ifBlank { null })
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            FramedAvatar(msg.senderAvatar, null, msg.senderColor, 32)
            Spacer(Modifier.width(Spacing.xs))
        }
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(
                    topStart = Radius.lg, topEnd = Radius.lg,
                    bottomStart = if (mine) Radius.lg else Radius.xs,
                    bottomEnd = if (mine) Radius.xs else Radius.lg
                ))
                .background(if (mine) Ember.copy(alpha = 0.22f) else Ink3)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            if (!mine) {
                Text(msg.senderName, color = accent, style = DeadFestType.microLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
            }
            if (msg.hasImage) {
                val image = remember(msg.id) { msg.asSharedImage() }
                val bmp = image?.let { rememberThumbnail(it) }
                val cd = stringResource(R.string.cd_chat_open_photo, msg.senderName)
                Box(
                    Modifier
                        .size(width = 220.dp, height = 160.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(Ink2)
                        .pressClick(RoundedCornerShape(Radius.md), onClick = onOpenPhoto)
                        .semantics { contentDescription = cd }
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = remember(bmp) { bmp.asImageBitmap() },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (msg.text.isNotBlank()) Spacer(Modifier.height(Spacing.xxs))
            }
            if (msg.text.isNotBlank()) Text(msg.text, color = TextHi, style = DeadFestType.body)
            if (time.isNotBlank()) {
                Text(
                    if (mine) "${stringResource(R.string.chat_you)} · $time" else time,
                    color = TextLow,
                    style = DeadFestType.microLabel,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
