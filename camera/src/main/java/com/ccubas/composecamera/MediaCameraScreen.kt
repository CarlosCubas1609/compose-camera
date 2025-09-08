package com.ccubas.composecamera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import androidx.camera.core.*
import androidx.camera.view.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ccubas.composecamera.components.CropperFullScreen
import com.ccubas.composecamera.models.CameraMode
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Main camera screen with WhatsApp-style UI and advanced features
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MediaCameraScreen(
    onDone: (List<Uri>) -> Unit,
    onClose: () -> Unit,
    config: MediaCameraConfig = MediaCameraConfig(),
    viewModel: MediaCameraViewModel = viewModel { MediaCameraViewModel(LocalContext.current) }
) {
    // Configure ViewModel
    LaunchedEffect(config) { 
        viewModel.setConfig(config) 
    }
    
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val exec = remember(ctx) { ContextCompat.getMainExecutor(ctx) }
    val ui by viewModel.ui.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadThumbs() }
    
    // Configure initial mode based on configuration
    LaunchedEffect(config.mediaType) {
        when (config.mediaType) {
            MediaType.PHOTO_ONLY -> viewModel.setMode(CameraMode.Photo)
            MediaType.VIDEO_ONLY -> viewModel.setMode(CameraMode.Video)
            MediaType.BOTH -> { /* keep current mode */ }
        }
    }

    val controller = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    LaunchedEffect(Unit) { controller.bindToLifecycle(owner) }

    var showGallery by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showGallery) { if (showGallery) viewModel.loadGallery() }

    val longPressMs = 250L
    val hasAudio = remember {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    val performCleanup = {
        viewModel.cleanupOnDispose()
        runCatching { controller.enableTorch(false) }
        runCatching { controller.unbind() }
        runCatching { controller.clearImageAnalysisAnalyzer() }
        runCatching { controller.setEnabledUseCases(0) }
    }

    val sendUris = { uris: List<Uri> ->
        if (uris.isNotEmpty()) onDone(uris)
        performCleanup()
        onClose()
    }

    fun formatTimer(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

    // Shutter gesture handler with zoom support
    val shutterModifier = Modifier.pointerInput(ui.mode, longPressMs, hasAudio, ui.config.mediaType) {
        awaitEachGesture {
            awaitFirstDown()

            if (ui.mode == CameraMode.Photo) {
                if (ui.config.mediaType == MediaType.PHOTO_ONLY) {
                    waitForUpOrCancellation()?.let {
                        viewModel.capturePhoto(controller, exec)
                    }
                } else {
                    val up = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() }
                    if (up != null) {
                        viewModel.capturePhoto(controller, exec)
                    } else {
                        viewModel.setLongPressingToRecord(true)
                        viewModel.startRecording(controller, exec, hasAudio)

                        var initialY = 0f
                        var initialZoom = ui.zoomRatio
                        val initialEvent = awaitPointerEvent()
                        if (initialEvent.changes.isNotEmpty()) {
                            initialY = initialEvent.changes[0].position.y
                            initialZoom = ui.zoomRatio
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) break

                            val currentChange = event.changes.firstOrNull()
                            if (currentChange != null && currentChange.pressed) {
                                val currentY = currentChange.position.y
                                val deltaY = initialY - currentY
                                
                                if (kotlin.math.abs(deltaY) > 20f) {
                                    val zoomDelta = deltaY / 100f
                                    val newZoom = (initialZoom + zoomDelta).coerceIn(1f, 10f)
                                    viewModel.updateZoom(newZoom, controller)
                                }
                            }
                        }

                        viewModel.stopRecording()
                        viewModel.setLongPressingToRecord(false)
                    }
                }
            } else {
                waitForUpOrCancellation()?.let {
                    if (viewModel.isRecording()) viewModel.stopRecording()
                    else viewModel.startRecording(controller, exec, hasAudio)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }
        )

        // Top bar: close, timer, flash
        Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            if (ui.recSeconds > 0 && ui.recording) {
                Row(
                    Modifier.align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.width(6.dp))
                    Text(formatTimer(ui.recSeconds), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(
                onClick = { if (ui.recording) viewModel.toggleTorch(controller) else viewModel.toggleFlash(controller) },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                val on = if (ui.recording) ui.torchOn else ui.flashMode == ImageCapture.FLASH_MODE_ON
                Icon(if (on) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, null, tint = Color.White)
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            // Carousel: only in Photo mode and when not long pressing
            if (ui.mode == CameraMode.Photo && !ui.longPressingToRecord) {
                val swipeUpToSheet = remember { 60f }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, drag ->
                                if (drag < -swipeUpToSheet) showGallery = true
                            }
                        }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    itemsIndexed(ui.thumbs, key = { _, t -> t.uri.toString() }) { _, t ->
                        val selected = t.uri in ui.selected
                        Box(
                            Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .combinedClickable(
                                    onClick = {
                                        if (viewModel.hasSelectedItems()) {
                                            if (selected || viewModel.canSelectMore()) {
                                                viewModel.toggleSelect(t.uri)
                                            }
                                        } else {
                                            viewModel.previewFromCarousel(t.uri, t.isVideo)
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelect(t.uri) }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx).data(t.uri).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (t.isVideo) {
                                Icon(Icons.Outlined.Videocam, null,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp),
                                    tint = Color.White)
                            }
                            if (selected) {
                                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .35f)))
                                Box(
                                    Modifier.padding(6.dp).size(18.dp).clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                        }
                    }
                }
            }
            
            // Camera controls: gallery / shutter / switch
            Box(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 18.dp)) {
                // Gallery button: hide when long pressing
                if (!ui.longPressingToRecord) {
                    IconButton(onClick = { showGallery = true }, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Outlined.Collections, null, tint = Color.White)
                    }
                }

                // Shutter button with WhatsApp-style design
                Box(
                    Modifier
                        .size(if (ui.longPressingToRecord) 96.dp else 84.dp)
                        .clip(CircleShape)
                        .background(
                            if (ui.longPressingToRecord || ui.recording) Color.Transparent
                            else Color.White.copy(alpha = .15f)
                        )
                        .align(Alignment.Center)
                        .then(shutterModifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (ui.longPressingToRecord) {
                        // WhatsApp longpress design: white ring + small red dot
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                            }
                        }
                    } else if (ui.recording) {
                        // Recording design: white ring + red square/circle
                        Box(
                            Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(
                                            if (ui.mode == CameraMode.Video) RoundedCornerShape(4.dp)
                                            else CircleShape
                                        )
                                        .background(Color.Red)
                                )
                            }
                        }
                    } else {
                        // Normal design: white circle
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                // Camera switch button: hide when long pressing
                if (!ui.longPressingToRecord) {
                    IconButton(onClick = { viewModel.switchCamera(controller) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Outlined.Cameraswitch, null, tint = Color.White)
                    }
                }
            }

            // Mode selector: only show if BOTH types are allowed
            if (ui.config.mediaType == MediaType.BOTH) {
                Row(
                    Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha=.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Video",
                        color = if (ui.mode==CameraMode.Video) Color.White else Color.White.copy(alpha=.7f),
                        modifier = Modifier.clickable { viewModel.setMode(CameraMode.Video) })
                    Text("Foto",
                        color = if (ui.mode==CameraMode.Photo) Color.White else Color.White.copy(alpha=.7f),
                        modifier = Modifier.clickable { viewModel.setMode(CameraMode.Photo) })
                }
            }

            // Send button
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End) {
                Button(onClick = { sendUris(ui.selected) }, enabled = ui.selected.isNotEmpty()) {
                    val maxText = if (ui.config.maxSelection == Int.MAX_VALUE) "" else "/${ui.config.maxSelection}"
                    Text("Enviar (${ui.selected.size}$maxText)")
                }
            }
        }

        // Gallery bottom sheet
        if (showGallery) {
            ModalBottomSheet(
                onDismissRequest = { showGallery = false },
                contentWindowInsets = { WindowInsets.navigationBarsIgnoringVisibility }
            ) {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                
                // Show tabs based on configuration
                if (ui.config.mediaType == MediaType.BOTH) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(tab == 0, { tab = 0 }) { Text("Todos", Modifier.padding(12.dp)) }
                        Tab(tab == 1, { tab = 1 }) { Text("Fotos", Modifier.padding(12.dp)) }
                        Tab(tab == 2, { tab = 2 }) { Text("Videos", Modifier.padding(12.dp)) }
                    }
                } else {
                    val title = when (ui.config.mediaType) {
                        MediaType.PHOTO_ONLY -> "Fotos"
                        MediaType.VIDEO_ONLY -> "Videos"
                        else -> "Media"
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                }
                
                // Filter items based on config and tab
                val configFilteredItems = when (ui.config.mediaType) {
                    MediaType.PHOTO_ONLY -> ui.gallery.filter { !it.isVideo }
                    MediaType.VIDEO_ONLY -> ui.gallery.filter { it.isVideo }
                    MediaType.BOTH -> ui.gallery
                }
                val gridItems = when (tab) {
                    1 -> configFilteredItems.filter { !it.isVideo }
                    2 -> configFilteredItems.filter { it.isVideo }
                    else -> configFilteredItems
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxHeight(0.6f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    itemsIndexed(gridItems, key = { _, t -> t.uri.toString() }) { _, t ->
                        val selected = t.uri in ui.selected
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(Color(0x11000000))
                                .combinedClickable(
                                    onClick = {
                                        if (viewModel.hasSelectedItems()) {
                                            if (selected || viewModel.canSelectMore()) {
                                                viewModel.toggleSelect(t.uri)
                                            }
                                        } else {
                                            viewModel.previewFromCarousel(t.uri, t.isVideo)
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelect(t.uri) }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx).data(t.uri).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (t.isVideo) {
                                Icon(Icons.Outlined.Videocam, null,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp),
                                    tint = Color.White)
                            }
                            if (selected) {
                                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .35f)))
                                Box(
                                    Modifier.padding(6.dp).size(20.dp).clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewModel.clearSelection(); showGallery = false }) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { sendUris(ui.selected); showGallery = false },
                        enabled = ui.selected.isNotEmpty()) { 
                        val maxText = if (ui.config.maxSelection == Int.MAX_VALUE) "" else "/${ui.config.maxSelection}"
                        Text("Añadir (${ui.selected.size}$maxText)") 
                    }
                }
            }
        }

        // Image preview with crop
        ui.previewImage?.let { src ->
            ImageReviewWithCropOverlay(
                src = src,
                onClose = { viewModel.dismissImagePreview() },
                onUse = { croppedUri ->
                    viewModel.saveImageAndSend(croppedUri) { permanentUri ->
                        if (permanentUri != null) {
                            sendUris(listOf(permanentUri))
                        } else {
                            viewModel.dismissImagePreview()
                        }
                    }
                }
            )
        }

        // Video preview with trim
        ui.previewVideo?.let { src ->
            VideoReviewOverlay(
                src = src,
                onClose = { viewModel.dismissVideoPreview() },
                onSaveTrim = { sMs, eMs ->
                    viewModel.trimVideoAndSave(src, sMs * 1000, eMs * 1000) { permanentUri ->
                        if (permanentUri != null) {
                            sendUris(listOf(permanentUri))
                        } else {
                            viewModel.dismissVideoPreview()
                        }
                    }
                }
            )
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose { performCleanup() }
    }
}

@Composable
private fun ImageReviewWithCropOverlay(
    src: Uri,
    onClose: () -> Unit,
    onUse: (Uri) -> Unit,
    aspect: Float? = null
) {
    var showCropper by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .zIndex(1f)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(src).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            IconButton(onClick = { showCropper = true }) {
                Icon(Icons.Outlined.Crop, null, tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Button(onClick = { onUse(src) }) {
                Text("Usar foto")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Check, null)
            }
        }
    }

    if (showCropper) {
        CropperFullScreen(
            src = src,
            onCancel = { showCropper = false },
            onCropped = { uri -> onUse(uri) },
            aspect = aspect
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoReviewOverlay(
    src: Uri,
    onClose: () -> Unit,
    onSaveTrim: (startMs: Long, endMs: Long) -> Unit
) {
    val ctx = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(ctx).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var durationMs by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var range by remember(durationMs) { mutableStateOf(0f..(durationMs / 1000f)) }
    val vMax = (durationMs / 1000f).coerceAtLeast(0.1f)

    fun applyClip() {
        val s = (range.start * 1000).toLong()
        val e = (range.endInclusive * 1000).toLong()
        val item = MediaItem.Builder()
            .setUri(src)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(s)
                    .setEndPositionMs(e)
                    .build()
            ).build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = !paused
    }

    LaunchedEffect(src) {
        exo.setMediaItem(MediaItem.fromUri(src))
        exo.prepare()
        while (durationMs <= 0L) { durationMs = exo.duration; delay(50) }
        range = 0f..(durationMs / 1000f)
        applyClip()
    }

    LaunchedEffect(range) { if (durationMs > 0) applyClip() }

    LaunchedEffect(range, paused) {
        if (paused) return@LaunchedEffect
        val s = (range.start * 1000).toLong()
        val e = (range.endInclusive * 1000).toLong()
        snapshotFlow { exo.currentPosition }.collect { pos ->
            if (pos >= (e - 10)) exo.seekTo(s)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .75f)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .zIndex(1f)
                .systemBarsPadding()
        ) {

            Column(Modifier.align(Alignment.BottomCenter).fillMaxSize().padding(16.dp)) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1F)
                        .fillMaxHeight(0.85f),
                    factory = { c ->
                        PlayerView(c).apply {
                            player = exo

                            setShowFastForwardButton(false)
                            setShowRewindButton(false)
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowSubtitleButton(false)
                            setShowShuffleButton(false)

                            findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.isVisible = false
                        }
                    }
                )

                RangeSlider(
                    value = range,
                    onValueChange = { r ->
                        val s = r.start.coerceIn(0f, vMax)
                        val e = r.endInclusive.coerceIn(0f, vMax)
                        range = min(s, e)..max(s, e)
                    },
                    valueRange = 0f..vMax
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {
                        onSaveTrim(
                            (range.start * 1000).toLong(),
                            (range.endInclusive * 1000).toLong()
                        )
                    }) { Text("Usar") }
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { exo.release() } }
}