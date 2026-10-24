package com.deadfest.ui.screens

import com.deadfest.R
import com.deadfest.util.tr
import com.deadfest.data.model.ImageComment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.data.model.SharedImage
import com.deadfest.domain.Presence
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MAX_CAPTION
import com.deadfest.ui.viewmodel.MainViewModel
import com.deadfest.util.ThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImagesScreen(navController: NavController, viewModel: MainViewModel) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val upload by viewModel.imageUpload.collectAsStateWithLifecycle()
    val me by viewModel.user.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<SharedImage?>(null) }

    // System photo picker: no storage permission needed on any Android version.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.prepareImage(uri)
    }

    // No background of its own: DeadFestBackground is applied once behind the whole NavHost.
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "IMAGES",
            subtitle = tr(R.string.images_shared_with_every_survivor),
            onBack = { navController.popBackStack() },
            trailing = { StatusPill("${images.size}", Ember) }
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.md,
                bottom = Spacing.scrollBottom
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShareHero(
                    processing = upload.processing,
                    busy = upload.busy,
                    onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.enterOnMount(0)
                )
            }
            if (images.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyStateCard(
                        icon = Icons.Default.PhotoLibrary,
                        title = tr(R.string.images_no_images_yet),
                        message = tr(R.string.images_be_the_first_to_share_one),
                        modifier = Modifier.enterOnMount(1)
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(tr(R.string.images_latest_shares))
                }
                items(images, key = { it.id }) { image ->
                    ImageTile(image) { viewing = image }
                }
            }
        }
    }

    // Caption + preview before posting.
    val pending = upload.pending
    if (pending != null) {
        PostImageDialog(
            preview = upload.preview,
            sizeKb = pending.full.size / 1024,
            uploading = upload.uploading,
            onPost = viewModel::postImage,
            onCancel = viewModel::cancelImage
        )
    }

    viewing?.let { image ->
        // Keep showing the live copy; close if it was deleted.
        val live = images.firstOrNull { it.id == image.id }
        if (live == null) {
            LaunchedEffect(image.id) { viewing = null }
        } else {
            ImageViewer(
                image = live,
                canDelete = live.uploaderUid == me?.uid || me?.admin == true,
                load = viewModel::loadImage,
                onDelete = { viewModel.deleteImage(live); viewing = null },
                onClose = { viewing = null },
                social = { ImageSocialPanel(live, viewModel) }
            )
        }
    }
}

/**
 * TalkBack label for a shared image: it always names the uploader, and carries the caption when the
 * uploader wrote one, so the grid tile and the full-screen viewer describe the same picture the same
 * way. Never a bare "image" — the label has to identify the share.
 */
private fun imageDescription(image: SharedImage): String {
    val caption = image.caption
    return if (caption.isBlank()) {
        tr(R.string.images_image_from_1, image.uploaderName)
    } else {
        tr(R.string.images_image_from_1_2, image.uploaderName, caption)
    }
}

/** Thumbnail decoded off the main thread and cached, so scrolling stays smooth. */
@Composable
internal fun rememberThumbnail(image: SharedImage): Bitmap? {
    val state = produceState(initialValue = ThumbnailCache.get(image.id), image.id) {
        if (value == null) value = withContext(Dispatchers.Default) { ThumbnailCache.decode(image.id, image.thumb) }
    }
    return state.value
}

/**
 * The screen's primary action as the top block: a hero that carries the same busy state the old
 * inline spinner communicated (state-driven title + badge, action disabled while working).
 */
@Composable
private fun ShareHero(
    processing: Boolean,
    busy: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeroPanel(
        eyebrow = tr(R.string.images_field_archive),
        title = if (processing) tr(R.string.images_preparing_image) else tr(R.string.images_ready_to_share),
        icon = Icons.Default.AddPhotoAlternate,
        modifier = modifier,
        accent = Ember,
        badge = {
            StatusPill(
                text = if (processing) tr(R.string.images_processing) else tr(R.string.images_ready),
                color = if (processing) Amber else Toxic
            )
        }
    ) {
        Text(
            tr(R.string.images_pick_a_photo_it_is_compressed),
            color = TextMid,
            style = DeadFestType.body
        )
        Spacer(Modifier.height(Spacing.sm))
        PrimaryAction(
            text = tr(R.string.images_share_an_image),
            onClick = onPick,
            enabled = !busy
        )
    }
}

/** Grid tile: square thumbnail in a card, with uploader and caption as the caption rows. */
@Composable
private fun ImageTile(image: SharedImage, onClick: () -> Unit) {
    val bitmap = rememberThumbnail(image)
    SurfaceCard(
        padding = Spacing.xs,
        onClick = onClick
    ) {
        // The frame is always square so a slow decode never reflows the grid.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radius.md))
                .background(Ink3),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = imageDescription(image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = TextLow,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            image.uploaderName,
            color = TextMid,
            style = DeadFestType.microLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (image.caption.isNotBlank()) {
            Text(
                image.caption,
                color = TextLow,
                style = DeadFestType.microLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PostImageDialog(
    preview: Bitmap?,
    sizeKb: Int,
    uploading: Boolean,
    onPost: (String) -> Unit,
    onCancel: () -> Unit
) {
    var caption by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!uploading) onCancel() },
        containerColor = Ink2,
        shape = RoundedCornerShape(Radius.xxl),
        title = { Text(tr(R.string.images_share_image), color = TextHi, style = DeadFestType.cardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (preview != null) {
                    Image(
                        bitmap = remember(preview) { preview.asImageBitmap() },
                        contentDescription = tr(R.string.images_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(Radius.md))
                    )
                }
                Text(
                    tr(R.string.images_compressed_to_1_kb_visible_to, sizeKb),
                    color = TextMid,
                    style = DeadFestType.body
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= MAX_CAPTION) caption = it },
                    label = { Text(tr(R.string.images_caption_optional)) },
                    supportingText = { Text("${caption.length}/$MAX_CAPTION", color = TextLow) },
                    enabled = !uploading,
                    colors = deadFestFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(caption) },
                enabled = !uploading,
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
                shape = RoundedCornerShape(Radius.md)
            ) {
                if (uploading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(tr(R.string.images_post), style = DeadFestType.action)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !uploading) { Text(tr(R.string.common_cancel), color = TextMid) }
        }
    )
}

/** Full-screen viewer: loads the full image on demand, pinch to zoom, double info bar. */
@Composable
internal fun ImageViewer(
    image: SharedImage,
    canDelete: Boolean,
    load: suspend (String) -> ByteArray?,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    /** Reactions & comments (gallery images only; chat photos pass null). */
    social: (@Composable ColumnScope.() -> Unit)? = null
) {
    val screenPx = with(LocalDensity.current) {
        val cfg = LocalConfiguration.current
        maxOf(cfg.screenWidthDp, cfg.screenHeightDp).dp.roundToPx()
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var failed by remember(image.id) { mutableStateOf(false) }
    val full by produceState<Bitmap?>(initialValue = null, image.id) {
        val bytes = load(image.id)
        value = bytes?.let { withContext(Dispatchers.Default) { ThumbnailCache.decodeFull(it, screenPx) } }
        if (value == null) failed = true
    }
    val thumb = rememberThumbnail(image)
    var scale by remember(image.id) { mutableFloatStateOf(1f) }
    var offset by remember(image.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    if (confirmDelete) {
        ConfirmDialog(
            title = tr(R.string.images_delete_image),
            message = tr(R.string.images_this_removes_it_for_every_survivor),
            confirmText = tr(R.string.admineconomy_delete),
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Ink0 rather than pure black so the overlay stays on the app's surface scale.
        Box(Modifier.fillMaxSize().background(Ink0)) {
            val shown = full ?: thumb
            if (shown != null) {
                Image(
                    bitmap = remember(shown) { shown.asImageBitmap() },
                    contentDescription = imageDescription(image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(image.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                            }
                        }
                        // Zoom/pan live in the draw layer: no recomposition while pinching.
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
            if (full == null && !failed) {
                LoadingState(tr(R.string.images_decrypting_image), Modifier.align(Alignment.Center))
            }
            if (failed) {
                ErrorState(
                    message = tr(R.string.images_couldn_t_load_the_full_image),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Ink0.copy(alpha = 0.6f))
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = tr(R.string.images_close), tint = TextHi) }
                Column(Modifier.weight(1f)) {
                    Text(
                        image.uploaderName,
                        color = TextHi,
                        style = DeadFestType.cardTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${Presence.lastSeenLabel(image.createdAt, System.currentTimeMillis())} · ${image.width}×${image.height}",
                        color = TextMid,
                        style = DeadFestType.body
                    )
                }
                if (canDelete) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = tr(R.string.images_delete_image_by_1, image.uploaderName), tint = Ember)
                    }
                }
            }

            if (image.caption.isNotBlank() || social != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Ink0.copy(alpha = 0.78f))
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(Spacing.md)
                ) {
                    if (image.caption.isNotBlank()) {
                        Text(image.caption, color = TextHi, style = DeadFestType.body)
                        if (social != null) Spacer(Modifier.height(Spacing.xs))
                    }
                    social?.invoke(this)
                }
            }
        }
    }
}

/**
 * Reactions and comments under a gallery image (v2.1.2). One reaction per survivor (tap yours to
 * remove it); comments are live, and the uploader is alerted about each one.
 */
@Composable
private fun ImageSocialPanel(image: SharedImage, viewModel: MainViewModel) {
    val me by viewModel.user.collectAsStateWithLifecycle()
    val reactions by remember(image.id) { viewModel.reactionsFor(image.id) }.collectAsStateWithLifecycle(emptyMap())
    val comments by remember(image.id) { viewModel.commentsFor(image.id) }.collectAsStateWithLifecycle(emptyList())
    var showComments by remember(image.id) { mutableStateOf(false) }
    var draft by remember(image.id) { mutableStateOf("") }
    val mine = me?.uid?.let { reactions[it] }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ImageComment.REACTIONS.forEach { emoji ->
            val count = reactions.values.count { it == emoji }
            val selected = mine == emoji
            val cd = if (selected) stringResource(com.deadfest.R.string.cd_reaction_mine, emoji, count)
            else stringResource(com.deadfest.R.string.cd_reaction, emoji, count)
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (selected) Ember.copy(alpha = 0.28f) else Ink3)
                    .pressClick(RoundedCornerShape(Radius.pill)) { viewModel.react(image, emoji, mine) }
                    .clearAndSetSemantics { contentDescription = cd }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 16.sp)
                if (count > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("$count", color = TextHi, style = DeadFestType.microLabel)
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        stringResource(com.deadfest.R.string.comments_count, comments.size),
        color = Plasma,
        style = DeadFestType.chip,
        modifier = Modifier
            .pressClick(RoundedCornerShape(Radius.sm)) { showComments = !showComments }
            .padding(vertical = 6.dp)
    )
    if (showComments) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (comments.isEmpty()) {
                item { Text(stringResource(com.deadfest.R.string.comments_empty), color = TextMid, style = DeadFestType.body) }
            }
            items(comments, key = { it.id }) { c ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(c.avatar.ifBlank { "\u2620" }, fontSize = 18.sp)
                    Spacer(Modifier.width(Spacing.xs))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = TextMid, style = DeadFestType.microLabel)
                        Text(c.text, color = TextHi, style = DeadFestType.body)
                    }
                    val canDelete = c.uid == me?.uid || image.uploaderUid == me?.uid || me?.admin == true
                    if (canDelete) {
                        IconButton(onClick = { viewModel.deleteComment(image, c) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(com.deadfest.R.string.cd_delete_comment), tint = TextLow, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= ImageComment.MAX_TEXT) draft = it },
                placeholder = { Text(stringResource(com.deadfest.R.string.comments_hint), color = TextLow) },
                singleLine = true,
                colors = deadFestFieldColors(),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { viewModel.addComment(image, draft); draft = "" },
                enabled = draft.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(com.deadfest.R.string.cd_chat_send), tint = if (draft.isNotBlank()) Ember else TextLow)
            }
        }
    }
}
