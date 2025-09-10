package com.ccubas.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import androidx.camera.core.*
import androidx.camera.view.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import com.ccubas.camera.components.*
import com.ccubas.camera.components.camera.CameraControls
import com.ccubas.camera.components.camera.CameraTopBar
import com.ccubas.camera.components.camera.MediaCarousel
import com.ccubas.camera.components.camera.MediaGallery
import com.ccubas.camera.components.camera.MediaThumbnail
import com.ccubas.camera.components.camera.ModeSwitcher
import com.ccubas.composecamera.models.CameraMode
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * UI state data for MediaCameraContent preview purposes
 */
data class MediaCameraUIState(
    val recording: Boolean = false,
    val recSeconds: Int = 0,
    val flashMode: Int = 0,
    val torchOn: Boolean = false,
    val mode: CameraMode = CameraMode.Photo,
    val longPressingToRecord: Boolean = false,
    val thumbs: List<MediaThumbnail> = emptyList(),
    val selected: List<Uri> = emptyList(),
    val config: MediaCameraConfig = MediaCameraConfig(),
    val gallery: List<MediaThumbnail> = emptyList(),
    val previewImage: Uri? = null,
    val previewVideo: Uri? = null
)

/**
 * Content component of MediaCameraScreen that can be previewed
 * Contains all UI elements without ViewModel or Camera dependencies
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MediaCameraContent(
    ui: MediaCameraUIState,
    imageLoader: ImageLoader,
    shutterModifier: Modifier = Modifier,
    showGallery: Boolean = false,
    onClose: () -> Unit = {},
    onFlashTorchToggle: () -> Unit = {},
    onItemClick: (Uri, Boolean) -> Unit = { _, _ -> },
    onItemLongClick: (Uri) -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onGalleryClick: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onModeChange: (CameraMode) -> Unit = {},
    onSend: (List<Uri>) -> Unit = {},
    onDismissGallery: () -> Unit = {},
    onToggleSelect: (Uri) -> Unit = {},
    onSendSelection: (List<Uri>) -> Unit = {},
    onImagePreviewClose: () -> Unit = {},
    onImagePreviewUse: (Uri) -> Unit = {},
    onVideoPreviewClose: () -> Unit = {},
    onVideoPreviewSave: (Long, Long) -> Unit = { _, _ -> },
    cameraPreviewContent: @Composable () -> Unit = {
        // Mock camera preview for previews
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Camera Preview",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
) {
    Box(Modifier
        .fillMaxSize()
        .background(Color.Black)) {

        // CAMERA PREVIEW
        cameraPreviewContent()

        // TOP BAR
        CameraTopBar(
            isRecording = ui.recording,
            recordingSeconds = ui.recSeconds,
            flashMode = ui.flashMode,
            torchOn = ui.torchOn,
            onClose = onClose,
            onFlashTorchToggle = onFlashTorchToggle
        )

        // BOTTOM
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // MEDIA CAROUSEL: only in Photo mode and hide when long-pressing
            if (ui.mode == CameraMode.Photo && !ui.longPressingToRecord) {
                MediaCarousel(
                    thumbnails = ui.thumbs,
                    selectedUris = ui.selected,
                    imageLoader = imageLoader,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onSwipeUp = onSwipeUp
                )
            }

            // CAMERA CONTROLS
            CameraControls(
                isLongPressing = ui.longPressingToRecord,
                isRecording = ui.recording,
                cameraMode = ui.mode,
                shutterModifier = shutterModifier,
                onGalleryClick = onGalleryClick,
                onSwitchCamera = onSwitchCamera
            )

            // MODE SWITCHER
            ModeSwitcher(
                currentMode = ui.mode,
                mediaType = ui.config.mediaType,
                onModeChange = onModeChange,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp)
            )

            // SEND BUTTON removido - ahora es el botón flotante verde sobre el carousel
        }

        // MEDIA GALLERY
        if (showGallery) {
            MediaGallery(
                galleryItems = ui.gallery,
                selectedUris = ui.selected,
                config = ui.config,
                imageLoader = imageLoader,
                onDismiss = onDismissGallery,
                onToggleSelect = onToggleSelect,
                onSendSelection = onSendSelection
            )
        }

        // Preview of the recently taken photo
        ui.previewImage?.let { src ->
            ImageReviewWithCropOverlay(
                src = src,
                onClose = onImagePreviewClose,
                onUse = onImagePreviewUse
            )
        }

        // Preview + Crop of the recently recorded/selected video
        ui.previewVideo?.let { src ->
            VideoReviewOverlay(
                src = src,
                onClose = onVideoPreviewClose,
                onSaveTrim = onVideoPreviewSave
            )
        }

        // BOTÓN VERDE FLOTANTE SOBRE EL CAROUSEL (reemplaza SendButton)
        if (ui.mode == CameraMode.Photo && !ui.longPressingToRecord && ui.selected.isNotEmpty()) {
            val maxText = if (ui.config.maxSelection == Int.MAX_VALUE) "" else "/${ui.config.maxSelection}"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 210.dp, end = 10.dp) // Sobre el carousel
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)) // Verde como en la imagen
                    .clickable { onSend(ui.selected) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Send selected items",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${ui.selected.size}$maxText",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

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
    val exec = remember(ctx) { ContextCompat.getMainExecutor(ctx) }
    val ui by vm.ui.collectAsState()

    val imageLoader = remember {
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



    MediaCameraContent(
        ui = MediaCameraUIState(
            recording = ui.recording,
            recSeconds = ui.recSeconds,
            flashMode = ui.flashMode,
            torchOn = ui.torchOn,
            mode = ui.mode,
            longPressingToRecord = ui.longPressingToRecord,
            thumbs = ui.thumbs.map { MediaThumbnail(it.uri, it.isVideo) },
            selected = ui.selected,
            config = ui.config,
            gallery = ui.gallery.map { MediaThumbnail(it.uri, it.isVideo) },
            previewImage = ui.previewImage,
            previewVideo = ui.previewVideo
        ),
        imageLoader = imageLoader,
        shutterModifier = shutterModifier,
        showGallery = showGallery,
        onClose = onClose,
        onFlashTorchToggle = {
            if (ui.recording) vm.toggleTorch(controller)
            else vm.toggleFlash(controller)
        },
        onItemClick = { uri, isVideo ->
            if (vm.hasSelectedItems()) {
                vm.toggleSelect(uri)
            } else {
                vm.previewFromCarousel(uri, isVideo)
            }
        },
        onItemLongClick = { uri -> vm.toggleSelect(uri) },
        onSwipeUp = { showGallery = true },
        onGalleryClick = { showGallery = true },
        onSwitchCamera = { vm.switchCamera(controller) },
        onModeChange = { mode -> vm.setMode(mode) },
        onSend = { uris -> sendUris(uris) },
        onDismissGallery = {
            vm.clearSelection()
            showGallery = false
        },
        onToggleSelect = { uri -> vm.toggleSelect(uri) },
        onSendSelection = { uris ->
            sendUris(uris)
            showGallery = false
        },
        onImagePreviewClose = { vm.dismissImagePreview() },
        onImagePreviewUse = { croppedUri ->
            vm.saveImageAndSend(croppedUri) { permanentUri ->
                if (permanentUri != null) {
                    sendUris(listOf(permanentUri))
                } else {
                    vm.dismissImagePreview()
                }
            }
        },
        onVideoPreviewClose = { vm.dismissVideoPreview() },
        onVideoPreviewSave = { sMs, eMs ->
            ui.previewVideo?.let { src ->
                vm.trimVideoAndSave(src, sMs * 1000, eMs * 1000) { permanentUri ->
                    if (permanentUri != null) {
                        sendUris(listOf(permanentUri))
                    } else {
                        vm.dismissVideoPreview()
                    }
                }
            }
        },
        cameraPreviewContent = {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PreviewView(it).apply {
                        this.controller = controller
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }
            )
        }
    )

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
            .zIndex(2f)
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)     // <- opaque
            .zIndex(2f)                  // <- above the camera
            .systemBarsPadding()
    ) {

        Column(Modifier
            .align(Alignment.BottomCenter)
            .fillMaxSize()
            .padding(16.dp)) {
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

        IconButton(onClick = onClose, modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(Icons.Outlined.Close, null, tint = Color.White)
        }
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }
}

// ===== PREVIEWS DEL MEDIA CAMERA CONTENT =====

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraContentPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    val mockThumbnails = remember {
        listOf(
            MediaThumbnail("content://media/1".toUri(), false),
            MediaThumbnail("content://media/2".toUri(), true),
            MediaThumbnail("content://media/3".toUri(), false),
            MediaThumbnail("content://media/4".toUri(), true),
        )
    }

    MaterialTheme {
        MediaCameraContent(
            ui = MediaCameraUIState(
                recording = false,
                mode = CameraMode.Photo,
                thumbs = mockThumbnails,
                selected = listOf("content://media/2".toUri()),
                config = MediaCameraConfig()
            ),
            imageLoader = imageLoader
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraContentRecordingPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    MaterialTheme {
        MediaCameraContent(
            ui = MediaCameraUIState(
                recording = true,
                recSeconds = 45,
                mode = CameraMode.Video,
                config = MediaCameraConfig()
            ),
            imageLoader = imageLoader
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraContentLongPressingPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    MaterialTheme {
        MediaCameraContent(
            ui = MediaCameraUIState(
                recording = true,
                recSeconds = 8,
                mode = CameraMode.Photo,
                longPressingToRecord = true,
                config = MediaCameraConfig()
            ),
            imageLoader = imageLoader
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraContentPhotoOnlyPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    val mockThumbnails = remember {
        listOf(
            MediaThumbnail("content://media/1".toUri(), false),
            MediaThumbnail("content://media/2".toUri(), false),
            MediaThumbnail("content://media/3".toUri(), false),
        )
    }

    MaterialTheme {
        MediaCameraContent(
            ui = MediaCameraUIState(
                mode = CameraMode.Photo,
                thumbs = mockThumbnails,
                config = MediaCameraConfig(mediaType = MediaType.PHOTO_ONLY)
            ),
            imageLoader = imageLoader
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraContentWithGalleryPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    val mockGalleryItems = remember {
        List(12) { index ->
            MediaThumbnail(
                "content://media/gallery_$index".toUri(), 
                index % 3 == 0
            )
        }
    }

    MaterialTheme {
        MediaCameraContent(
            ui = MediaCameraUIState(
                mode = CameraMode.Photo,
                gallery = mockGalleryItems,
                selected = listOf("content://media/gallery_2".toUri()),
                config = MediaCameraConfig()
            ),
            imageLoader = imageLoader,
            showGallery = true
        )
    }
}