package com.deadfest.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
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

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(
            title = "IMAGES",
            subtitle = "SHARED WITH EVERY SURVIVOR",
            onBack = { navController.popBackStack() },
            trailing = { StatusPill("${images.size}", RedAccent) }
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !upload.busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent, disabledContainerColor = CardBgRaised),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (upload.processing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("PREPARING IMAGE...", fontWeight = FontWeight.Black)
                    } else {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("SHARE AN IMAGE", fontWeight = FontWeight.Black)
                    }
                }
            }
            if (images.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.PhotoLibrary, "No images yet. Be the first to share one with the wasteland.")
                }
            }
            items(images, key = { it.id }) { image ->
                ImageTile(image) { viewing = image }
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
                onClose = { viewing = null }
            )
        }
    }
}

/** Thumbnail decoded off the main thread and cached, so scrolling stays smooth. */
@Composable
private fun rememberThumbnail(image: SharedImage): Bitmap? {
    val state = produceState(initialValue = ThumbnailCache.get(image.id), image.id) {
        if (value == null) value = withContext(Dispatchers.Default) { ThumbnailCache.decode(image.id, image.thumb) }
    }
    return state.value
}

@Composable
private fun ImageTile(image: SharedImage, onClick: () -> Unit) {
    val bitmap = rememberThumbnail(image)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .pressClick(RoundedCornerShape(14.dp), onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = image.caption.ifBlank { "Image from ${image.uploaderName}" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            image.uploaderName,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
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
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text("SHARE IMAGE", color = TextPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preview != null) {
                    Image(
                        bitmap = remember(preview) { preview.asImageBitmap() },
                        contentDescription = "Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(14.dp))
                    )
                }
                Text("Compressed to $sizeKb KB · visible to every survivor", color = TextMuted, fontSize = 11.sp)
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= MAX_CAPTION) caption = it },
                    label = { Text("Caption (optional)") },
                    supportingText = { Text("${caption.length}/$MAX_CAPTION", color = TextMuted) },
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
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uploading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("POST", fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !uploading) { Text("CANCEL", color = TextSecondary) }
        }
    )
}

/** Full-screen viewer: loads the full image on demand, pinch to zoom, double info bar. */
@Composable
private fun ImageViewer(
    image: SharedImage,
    canDelete: Boolean,
    load: suspend (String) -> ByteArray?,
    onDelete: () -> Unit,
    onClose: () -> Unit
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
            title = "DELETE IMAGE?",
            message = "This removes it for every survivor.",
            confirmText = "DELETE",
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val shown = full ?: thumb
            if (shown != null) {
                Image(
                    bitmap = remember(shown) { shown.asImageBitmap() },
                    contentDescription = image.caption.ifBlank { "Image from ${image.uploaderName}" },
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
                CircularProgressIndicator(color = RedAccent, modifier = Modifier.align(Alignment.Center))
            }
            if (failed) {
                Text(
                    "Couldn't load the full image. Check your connection.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            }

            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(image.uploaderName, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Presence.lastSeenLabel(image.createdAt, System.currentTimeMillis())} · ${image.width}×${image.height}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                if (canDelete) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete image", tint = RedAccent)
                    }
                }
            }

            if (image.caption.isNotBlank()) {
                Text(
                    image.caption,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp)
                )
            }
        }
    }
}
