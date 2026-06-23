package com.ccubas.camera.components.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ccubas.camera.components.editor.EditorState
import com.ccubas.camera.components.editor.ImageEditorScreen
import com.ccubas.camera.components.editor.flattenAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * WhatsApp-style multi-photo/video review screen shown after selecting media
 * via the system PickVisualMedia picker.
 *
 * - Photos: fully editable with the built-in annotation editor.
 * - Videos: shown as a preview thumbnail (no editing).
 * - Thumbnail strip at the bottom lets the user navigate between items.
 * - Green send button confirms and returns all URIs (edited photos are
 *   flattened to temp files before returning).
 */
@Composable
fun PickerReviewScreen(
    uris: List<Uri>,
    onConfirm: (List<Uri>) -> Unit,
    onDismiss: () -> Unit
) {
    if (uris.isEmpty()) {
        onDismiss()
        return
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentIndex by remember { mutableStateOf(0) }

    // Lazily-loaded bitmaps (null while loading or for videos)
    val bitmaps: SnapshotStateMap<Int, Bitmap?> = remember { mutableStateMapOf() }
    val isVideoMap: SnapshotStateMap<Int, Boolean> = remember { mutableStateMapOf() }

    // Per-item editor state — persisted across thumbnail switches
    val editorStates: SnapshotStateMap<Int, EditorState> = remember {
        mutableStateMapOf<Int, EditorState>().also { map ->
            uris.indices.forEach { i -> map[i] = EditorState() }
        }
    }

    // Detect MIME and decode bitmaps in background
    LaunchedEffect(uris) {
        uris.forEachIndexed { i, uri ->
            launch(Dispatchers.IO) {
                val mime = ctx.contentResolver.getType(uri)
                val video = mime?.startsWith("video/") == true
                isVideoMap[i] = video
                if (!video) {
                    runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { stream ->
                            bitmaps[i] = BitmapFactory.decodeStream(stream)
                        }
                    }
                }
            }
        }
    }

    var isSending by remember { mutableStateOf(false) }

    fun send() {
        if (isSending) return
        isSending = true
        scope.launch {
            val result = uris.mapIndexed { i, uri ->
                val bmp = bitmaps[i]
                val state = editorStates[i]
                if (bmp != null && state != null && state.annotations.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val flat = flattenAnnotations(bmp, state.annotations.toList())
                            val file = File(ctx.cacheDir, "review_${i}_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { out ->
                                flat.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            }
                            file.toUri()
                        }.getOrDefault(uri)
                    }
                } else uri
            }
            withContext(Dispatchers.Main) { onConfirm(result) }
        }
    }

    BackHandler { onDismiss() }

    val imageLoader = remember {
        ImageLoader.Builder(ctx)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    // ── Thumbnail strip injected into the editor's bottom slot ──
    val thumbnailStrip: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uris) { i, uri ->
                    val isCurrent = i == currentIndex
                    val hasEdits = editorStates[i]?.annotations?.isNotEmpty() == true
                    val isVid = isVideoMap[i] == true
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (isCurrent)
                                    Modifier.border(2.5.dp, Color.White, RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .clickable { currentIndex = i }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(uri).build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (isVid) {
                            Icon(
                                Icons.Outlined.Videocam,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .size(14.dp),
                                tint = Color.White
                            )
                        }
                        if (hasEdits) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Send / confirm button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                    .clickable(enabled = !isSending) { send() },
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Enviar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${uris.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }

    val currentIsVideo = isVideoMap[currentIndex] == true
    val currentBitmap = bitmaps[currentIndex]
    val currentState = editorStates[currentIndex] ?: EditorState()

    when {
        currentIsVideo -> VideoPickerPreview(
            uri = uris[currentIndex],
            imageLoader = imageLoader,
            onDismiss = onDismiss,
            bottomContent = thumbnailStrip
        )
        currentBitmap != null -> ImageEditorScreen(
            src = currentBitmap,
            onClose = onDismiss,
            onUse = {},
            state = currentState,
            additionalBottomContent = thumbnailStrip
        )
        else -> Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

// ── Video preview (no editing tools, just thumbnail + strip) ──
@Composable
private fun VideoPickerPreview(
    uri: Uri,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    bottomContent: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(uri).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Icon(
            Icons.Outlined.Videocam,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp),
            tint = Color.White.copy(alpha = 0.55f)
        )
        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White)
        }
        // Thumbnail strip
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
        ) {
            bottomContent()
        }
    }
}
