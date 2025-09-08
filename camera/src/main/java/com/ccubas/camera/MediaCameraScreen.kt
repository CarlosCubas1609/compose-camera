package com.ccubas.camera

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ccubas.camera.components.CropperFullScreen
import com.ccubas.composecamera.models.CameraMode
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * The main screen for the media camera, providing a camera preview, capture controls,
 * and access to the media gallery.
 *
 * @param onDone Callback invoked when the user confirms the selection of media, returning a list of URIs.
 * @param onClose Callback invoked when the user closes the camera screen.
 * @param config The configuration for the media camera.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun MediaCameraScreen(
    onDone: (List<Uri>) -> Unit,
    onClose: () -> Unit,
    config: MediaCameraConfig = MediaCameraConfig()
) {
    val ctx = LocalContext.current
    val vm: MediaCameraViewModel = viewModel(
        factory = MediaCameraViewModelFactory(ctx.applicationContext)
    )
    // Configure the ViewModel at the beginning
    LaunchedEffect(config) {
        vm.setConfig(config)
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val exec = rememberSaveable(ctx) { ContextCompat.getMainExecutor(ctx) }
    val ui by vm.ui.collectAsState()

    val imageLoader = rememberSaveable {
        ImageLoader.Builder(ctx).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    LaunchedEffect(Unit) { vm.loadThumbs() }

    // Set initial mode based on configuration
    LaunchedEffect(config.mediaType) {
        when (config.mediaType) {
            MediaType.PHOTO_ONLY -> vm.setMode(CameraMode.Photo)
            MediaType.VIDEO_ONLY -> vm.setMode(CameraMode.Video)
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
    LaunchedEffect(showGallery) { if (showGallery) vm.loadGallery() }

    val longPressMs = 250L
    val hasAudio = remember {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    val performCleanup = {
        vm.cleanupOnDispose()
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

    // --- helpers ---
    fun formatTimer(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

    // --- Shutter ---
    val shutterModifier =
        Modifier.pointerInput(ui.mode, longPressMs, hasAudio, ui.config.mediaType) {
            awaitEachGesture {
                awaitFirstDown()
                // Don't mark anything at the beginning, only after long press

                if (ui.mode == CameraMode.Photo) {
                    // If PHOTO_ONLY, only allow photos (no long press for video)
                    if (ui.config.mediaType == MediaType.PHOTO_ONLY) {
                        waitForUpOrCancellation()?.let {
                            vm.capturePhoto(controller, exec)
                        }
                    } else {
                        // Normal behavior: tap = photo, long press = video
                        val up = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() }
                        if (up != null) {
                            vm.capturePhoto(controller, exec)
                        } else {
                            // After 250ms -> activate WhatsApp design and zoom with gestures
                            vm.setLongPressingToRecord(true)
                            vm.startRecording(controller, exec, hasAudio)

                            var initialY = 0f
                            var initialZoom = ui.zoomRatio
                            var hasStartedDrag = false

                            // Capture initial position
                            val initialEvent = awaitPointerEvent()
                            if (initialEvent.changes.isNotEmpty()) {
                                initialY = initialEvent.changes[0].position.y
                                initialZoom = ui.zoomRatio
                            }

                            // Detect zoom gestures during recording
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break

                                // Detect vertical movement for zoom
                                val currentChange = event.changes.firstOrNull()
                                if (currentChange != null && currentChange.pressed) {
                                    val currentY = currentChange.position.y
                                    val deltaY = initialY - currentY // Inverted: up = positive

                                    // Only apply zoom if there is significant movement
                                    if (kotlin.math.abs(deltaY) > 20f) {
                                        hasStartedDrag = true
                                        // Zoom sensitivity: every 100px = 1x zoom
                                        val zoomDelta = deltaY / 100f
                                        val newZoom = (initialZoom + zoomDelta).coerceIn(1f, 10f)
                                        vm.updateZoom(newZoom, controller)
                                    }
                                }
                            }

                            vm.stopRecording()
                            vm.setLongPressingToRecord(false)
                        }
                    }
                } else {
                    // Video Mode
                    waitForUpOrCancellation()?.let {
                        if (vm.isRecording()) vm.stopRecording()
                        else vm.startRecording(controller, exec, hasAudio)
                    }
                }
            }
        }



    Box(Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }
        )


        // TOP: close / flash and, if recording, timer at the center
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            if (ui.recSeconds > 0 && ui.recording) {
                Row(
                    Modifier.align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.width(6.dp))
                    Text(formatTimer(ui.recSeconds), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(
                onClick = { if (ui.recording) vm.toggleTorch(controller) else vm.toggleFlash(controller) },
                modifier = Modifier.align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                val on = if (ui.recording) ui.torchOn else ui.flashMode == ImageCapture.FLASH_MODE_ON
                Icon(if (on) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, null, tint = Color.White)
            }
        }

        // BOTTOM
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Carousel at the top: only in Photo mode and hide when long-pressing
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

                        val request = if (t.isVideo) {
                            ImageRequest.Builder(ctx)
                                .data(t.uri)
                                .setParameter(VideoFrameDecoder.VIDEO_FRAME_MICROS_KEY, 1_000_000L) // 1s
                                .setParameter(
                                    VideoFrameDecoder.VIDEO_FRAME_OPTION_KEY,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                )
                                .build()
                        } else {
                            ImageRequest.Builder(ctx).data(t.uri).build()
                        }
                        Box(
                            Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .combinedClickable(
                                    onClick = {
                                        if (vm.hasSelectedItems()) {
                                            vm.toggleSelect(t.uri)
                                        } else {
                                            vm.previewFromCarousel(t.uri, t.isVideo)
                                        }
                                    },
                                    onLongClick = { vm.toggleSelect(t.uri) }
                                )
                        ) {
                            AsyncImage(
                                model = request,
                                imageLoader = imageLoader,
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

            // Controls: gallery / shutter / switch
            Box(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 18.dp)) {
                // Gallery button: hide when long-pressing
                if (!ui.longPressingToRecord) {
                    IconButton(onClick = { showGallery = true }, modifier = Modifier.align(Alignment.CenterStart)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Outlined.Collections, null, tint = Color.White)
                    }
                }

                // Shutter button with WhatsApp design on long press
                Box(
                    Modifier
                        .size(if (ui.longPressingToRecord) 96.dp else 84.dp) // Larger button during long press
                        .clip(CircleShape)
                        .background(
                            if (ui.longPressingToRecord || ui.recording) Color.Transparent // No background when recording
                            else Color.White.copy(alpha = .15f) // Normal white background
                        )
                        .align(Alignment.Center)
                        .then(shutterModifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (ui.longPressingToRecord) {
                        // Long press design in photo mode: white ring + black background + red dot
                        Box(
                            Modifier
                                .size(96.dp) // Larger button
                                .clip(CircleShape)
                                .background(Color.White) // Outer white ring
                                .padding(6.dp), // Thickness of the white ring
                            contentAlignment = Alignment.Center
                        ) {
                            // Inner black background
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(20.dp), // More padding = smaller red dot
                                contentAlignment = Alignment.Center
                            ) {
                                // Small red dot in the center
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                            }
                        }
                    } else if (ui.recording) {
                        // Design during recording: white circle + red square
                        Box(
                            Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color.White) // White border
                                .padding(6.dp), // Border thickness
                            contentAlignment = Alignment.Center
                        ) {
                            // Transparent/black background
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(12.dp), // Space for the square
                                contentAlignment = Alignment.Center
                            ) {
                                // Inner red square
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(
                                            if (ui.mode == CameraMode.Video) RoundedCornerShape(4.dp) // Rounded square
                                            else CircleShape // Circle for photo
                                        )
                                        .background(Color.Red)
                                        .padding(20.dp), // More padding = smaller red square
                                )
                            }
                        }
                    } else {
                        // Normal design: white button
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                // Switch camera button: hide when long-pressing
                if (!ui.longPressingToRecord) {
                    IconButton(onClick = { vm.switchCamera(controller) }, modifier = Modifier.align(Alignment.CenterEnd)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Outlined.Cameraswitch, null, tint = Color.White)
                    }
                }
            }

            // Modes (Video / Photo) - show based on configuration
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
                        modifier = Modifier.clickable { vm.setMode(CameraMode.Video) })
                    Text("Foto",
                        color = if (ui.mode==CameraMode.Photo) Color.White else Color.White.copy(alpha=.7f),
                        modifier = Modifier.clickable { vm.setMode(CameraMode.Photo) })
                }
            }

            // Optional send selection
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End) {
                Button(onClick = { sendUris(ui.selected) }, enabled = ui.selected.isNotEmpty()) {
                    val maxText = if (ui.config.maxSelection == Int.MAX_VALUE) "" else "/${ui.config.maxSelection}"
                    Text("Enviar (${ui.selected.size}$maxText)")
                }
            }
        }

        // Gallery BottomSheet (multi-selection)
        if (showGallery) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var galleryFilter by remember { mutableStateOf("Recientes") }

            ModalBottomSheet(
                onDismissRequest = { vm.clearSelection(); showGallery = false },
                sheetState = sheetState,
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = { WindowInsets.navigationBarsIgnoringVisibility }
            ) {
                Scaffold(
                    topBar = {
                        var showMenu by remember { mutableStateOf(false) }
                        CenterAlignedTopAppBar(
                            title = {
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { showMenu = true }
                                    ) {
                                        Text(galleryFilter, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Filter")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(text = { Text("Recientes") }, onClick = { galleryFilter = "Recientes"; showMenu = false })
                                        if (ui.config.mediaType != MediaType.VIDEO_ONLY) {
                                            DropdownMenuItem(text = { Text("Fotos") }, onClick = { galleryFilter = "Fotos"; showMenu = false })
                                        }
                                        if (ui.config.mediaType != MediaType.PHOTO_ONLY) {
                                            DropdownMenuItem(text = { Text("Videos") }, onClick = { galleryFilter = "Videos"; showMenu = false })
                                        }
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { vm.clearSelection(); showGallery = false }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Close"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                            windowInsets = WindowInsets(0)
                        )
                    },
                    bottomBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { sendUris(ui.selected); showGallery = false },
                                enabled = ui.selected.isNotEmpty()
                            ) {
                                val maxText = if (ui.config.maxSelection == Int.MAX_VALUE) "" else "/${ui.config.maxSelection}"
                                Text("Añadir (${ui.selected.size}$maxText)")
                            }
                        }
                    }
                ) { innerPadding ->
                    val gridItems = remember(galleryFilter, ui.gallery) {
                        when (galleryFilter) {
                            "Fotos" -> ui.gallery.filter { !it.isVideo }
                            "Videos" -> ui.gallery.filter { it.isVideo }
                            else -> ui.gallery
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                            )
                            .consumeWindowInsets(innerPadding)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            itemsIndexed(gridItems, key = { _, t -> t.uri.toString() }) { _, t ->
                                val selected = t.uri in ui.selected
                                val request = if (t.isVideo) {
                                    ImageRequest.Builder(ctx)
                                        .data(t.uri)
                                        .setParameter(VideoFrameDecoder.VIDEO_FRAME_MICROS_KEY, 1_000_000L) // frame al 1s
                                        .build()
                                } else {
                                    ImageRequest.Builder(ctx)
                                        .data(t.uri)
                                        .build()
                                }
                                Box(
                                    Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x11000000))
                                        .clickable { vm.toggleSelect(t.uri) }
                                ) {
                                    AsyncImage(
                                        model = request,
                                        imageLoader = imageLoader,
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
                                        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                                        Box(
                                            Modifier.padding(8.dp).size(24.dp).clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preview of the recently taken photo
        ui.previewImage?.let { src ->
            ImageReviewWithCropOverlay(
                src = src,
                onClose = { vm.dismissImagePreview() },
                onUse = { croppedUri ->
                    vm.saveImageAndSend(croppedUri) { permanentUri ->
                        if (permanentUri != null) {
                            sendUris(listOf(permanentUri))
                        } else {
                            vm.dismissImagePreview()
                        }
                    }
                }
            )
        }

        // Preview + Crop of the recently recorded/selected video
        ui.previewVideo?.let { src ->
            VideoReviewOverlay(
                src = src,
                onClose = { vm.dismissVideoPreview() },
                onSaveTrim = { sMs, eMs ->
                    vm.trimVideoAndSave(src, sMs * 1000, eMs * 1000) { permanentUri ->
                        if (permanentUri != null) {
                            sendUris(listOf(permanentUri))
                        } else {
                            vm.dismissVideoPreview()
                        }
                    }
                }
            )
        }
    }

    // Total cleanup when destroying the composable
    DisposableEffect(Unit) {
        onDispose {
            performCleanup()
        }
    }
}

/**
 * A private composable for reviewing and cropping a captured image.
 *
 * @param src The URI of the image to review.
 * @param onClose Callback invoked when the user closes the review overlay.
 * @param onUse Callback invoked when the user confirms using the (potentially cropped) image.
 * @param aspect The fixed aspect ratio to use for cropping, or null for freeform.
 */
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
        // Simple preview (without cropping)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(src).build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Top: close / crop
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            IconButton(onClick = { showCropper = true }, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                Icon(Icons.Outlined.Crop, null, tint = Color.White)
            }
        }

        // Bottom: use (original by default)
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

    // Full-screen modal cropper (locked)
    if (showCropper) {
        CropperFullScreen(
            src = src,
            onCancel = { showCropper = false },
            onCropped = { uri -> onUse(uri) },
            aspect = aspect
        )
    }
}

/**
 * A private composable for reviewing and trimming a captured video.
 *
 * @param src The URI of the video to review.
 * @param onClose Callback invoked when the user closes the review overlay.
 * @param onSaveTrim Callback invoked when the user confirms the trimmed video, providing start and end times in milliseconds.
 */
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

    // Load full duration and set range to 100% (default full video)
    LaunchedEffect(src) {
        exo.setMediaItem(MediaItem.fromUri(src))
        exo.prepare()
        while (durationMs <= 0L) { durationMs = exo.duration; delay(50) }
        range = 0f..(durationMs / 1000f)
        applyClip()
    }

    LaunchedEffect(range) { if (durationMs > 0) applyClip() }

    // loop within the range
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
                .background(Color.Black)     // <- opaque
                .zIndex(1f)                  // <- above the camera
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

            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { exo.release() } }
}