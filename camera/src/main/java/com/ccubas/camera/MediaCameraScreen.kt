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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ccubas.camera.components.CropperFullScreen
import com.ccubas.composecamera.models.CameraMode
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

// ===================== SCREEN (multi-selección + carrusel) =====================
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
    // Configurar el ViewModel al inicio
    LaunchedEffect(config) {
        vm.setConfig(config)
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val exec = remember(ctx) { ContextCompat.getMainExecutor(ctx) }
    val ui by vm.ui.collectAsState()

    LaunchedEffect(Unit) { vm.loadThumbs() }

    // Configurar modo inicial según configuración
    LaunchedEffect(config.mediaType) {
        when (config.mediaType) {
            MediaType.PHOTO_ONLY -> vm.setMode(CameraMode.Photo)
            MediaType.VIDEO_ONLY -> vm.setMode(CameraMode.Video)
            MediaType.BOTH -> { /* mantener modo actual */ }
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

    // --- Disparador ---
    val shutterModifier =
        Modifier.pointerInput(ui.mode, longPressMs, hasAudio, ui.config.mediaType) {
            awaitEachGesture {
                awaitFirstDown()
                // No marcar nada al inicio, solo después del longpress

                if (ui.mode == CameraMode.Photo) {
                    // Si es PHOTO_ONLY, solo permitir fotos (no longpress para video)
                    if (ui.config.mediaType == MediaType.PHOTO_ONLY) {
                        waitForUpOrCancellation()?.let {
                            vm.capturePhoto(controller, exec)
                        }
                    } else {
                        // Comportamiento normal: tap = foto, longpress = video
                        val up = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() }
                        if (up != null) {
                            vm.capturePhoto(controller, exec)
                        } else {
                            // Después de 250ms -> activar diseño WhatsApp y zoom con gestos
                            vm.setLongPressingToRecord(true)
                            vm.startRecording(controller, exec, hasAudio)

                            var initialY = 0f
                            var initialZoom = ui.zoomRatio
                            var hasStartedDrag = false

                            // Capturar posición inicial
                            val initialEvent = awaitPointerEvent()
                            if (initialEvent.changes.isNotEmpty()) {
                                initialY = initialEvent.changes[0].position.y
                                initialZoom = ui.zoomRatio
                            }

                            // Detectar gestos de zoom durante grabación
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break

                                // Detectar movimiento vertical para zoom
                                val currentChange = event.changes.firstOrNull()
                                if (currentChange != null && currentChange.pressed) {
                                    val currentY = currentChange.position.y
                                    val deltaY = initialY - currentY // Invertido: arriba = positivo

                                    // Solo aplicar zoom si hay movimiento significativo
                                    if (kotlin.math.abs(deltaY) > 20f) {
                                        hasStartedDrag = true
                                        // Sensibilidad del zoom: cada 100px = 1x zoom
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
                    // Modo Video
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


        // TOP: cerrar / flash y, si graba, timer al centro
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp)
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
                onClick = { if (ui.recording) vm.toggleTorch(controller) else vm.toggleFlash(controller) },
                modifier = Modifier.align(Alignment.TopEnd)
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
            // Carrusel arriba: solo en modo Foto y ocultar cuando se mantiene longpress
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
                                        if (vm.hasSelectedItems()) {
                                            // Si ya hay elementos seleccionados y se puede seleccionar más
                                            if (selected || vm.canSelectMore()) {
                                                vm.toggleSelect(t.uri)
                                            }
                                        } else {
                                            // Si no hay elementos seleccionados, previsualizar
                                            vm.previewFromCarousel(t.uri, t.isVideo)
                                        }
                                    },
                                    onLongClick = { vm.toggleSelect(t.uri) }
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

            // Controles: galería / disparador / switch
            Box(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 18.dp)) {
                // Botón galería: ocultar cuando se mantiene longpress
                if (!ui.longPressingToRecord) {
                    IconButton(onClick = { showGallery = true }, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Outlined.Collections, null, tint = Color.White)
                    }
                }

                // Botón disparador con diseño WhatsApp cuando longpress
                Box(
                    Modifier
                        .size(if (ui.longPressingToRecord) 96.dp else 84.dp) // Botón más grande durante longpress
                        .clip(CircleShape)
                        .background(
                            if (ui.longPressingToRecord || ui.recording) Color.Transparent // Sin fondo cuando graba
                            else Color.White.copy(alpha = .15f) // Fondo blanco normal
                        )
                        .align(Alignment.Center)
                        .then(shutterModifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (ui.longPressingToRecord) {
                        // Diseño longpress en modo foto como foto_1: aro blanco + fondo negro + punto rojo
                        Box(
                            Modifier
                                .size(96.dp) // Botón más grande
                                .clip(CircleShape)
                                .background(Color.White) // Aro blanco exterior
                                .padding(6.dp), // Grosor del aro blanco
                            contentAlignment = Alignment.Center
                        ) {
                            // Fondo negro interior
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(20.dp), // Más padding = punto rojo más pequeño
                                contentAlignment = Alignment.Center
                            ) {
                                // Punto rojo pequeño en el centro
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                            }
                        }
                    } else if (ui.recording) {
                        // Diseño durante grabación como foto_2: círculo blanco + cuadrado rojo
                        Box(
                            Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color.White) // Borde blanco
                                .padding(6.dp), // Grosor del borde
                            contentAlignment = Alignment.Center
                        ) {
                            // Fondo transparente/negro
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(12.dp), // Espacio para el cuadrado
                                contentAlignment = Alignment.Center
                            ) {
                                // Cuadrado rojo interior
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(
                                            if (ui.mode == CameraMode.Video) RoundedCornerShape(4.dp) // Cuadrado redondeado
                                            else CircleShape // Círculo para foto
                                        )
                                        .background(Color.Red)
                                        .padding(20.dp), // Más padding = cuadrado rojo más pequeño
                                )
                            }
                        }
                    } else {
                        // Diseño normal: botón blanco
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                // Botón cambiar cámara: ocultar cuando se mantiene longpress
                if (!ui.longPressingToRecord) {
                    IconButton(onClick = { vm.switchCamera(controller) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Outlined.Cameraswitch, null, tint = Color.White)
                    }
                }
            }

            // Modos (solo Video / Foto) - mostrar según configuración
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

            // Enviar selección opcional
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End) {
                Button(onClick = { sendUris(ui.selected) }, enabled = ui.selected.isNotEmpty()) {
                    val maxText = if (ui.config.maxSelection == Int.MAX_VALUE) "" else "/${ui.config.maxSelection}"
                    Text("Enviar (${ui.selected.size}$maxText)")
                }
            }
        }

        // BottomSheet galería (multi selección) — igual que tenías
        if (showGallery) {
            ModalBottomSheet(
                onDismissRequest = { showGallery = false },
                contentWindowInsets = { WindowInsets.navigationBarsIgnoringVisibility }
            ) {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                // Mostrar tabs según configuración
                if (ui.config.mediaType == MediaType.BOTH) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(tab == 0, { tab = 0 }) { Text("Todos", Modifier.padding(12.dp)) }
                        Tab(tab == 1, { tab = 1 }) { Text("Fotos", Modifier.padding(12.dp)) }
                        Tab(tab == 2, { tab = 2 }) { Text("Videos", Modifier.padding(12.dp)) }
                    }
                } else {
                    // Si es solo foto o solo video, no mostrar tabs
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
                // Filtrar por configuración primero, luego por tab
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
                                        if (vm.hasSelectedItems()) {
                                            // Si ya hay elementos seleccionados y se puede seleccionar más
                                            if (selected || vm.canSelectMore()) {
                                                vm.toggleSelect(t.uri)
                                            }
                                        } else {
                                            // Si no hay elementos seleccionados, previsualizar
                                            vm.previewFromCarousel(t.uri, t.isVideo)
                                        }
                                    },
                                    onLongClick = { vm.toggleSelect(t.uri) }
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
                    TextButton(onClick = { vm.clearSelection(); showGallery = false }) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { sendUris(ui.selected); showGallery = false },
                        enabled = ui.selected.isNotEmpty()) {
                        val maxText = if (ui.config.maxSelection == Int.MAX_VALUE) "" else "/${ui.config.maxSelection}"
                        Text("Añadir (${ui.selected.size}$maxText)")
                    }
                }
            }
        }

        // Preview de la foto recién tomada
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

        // Preview + Recorte del video recién grabado/seleccionado
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

    // Limpieza total al destruir el composable
    DisposableEffect(Unit) {
        onDispose {
            performCleanup()
        }
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
        // Preview simple (sin recorte)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(src).build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Top: cerrar / recortar
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

        // Bottom: usar (original por defecto)
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

    // Cropper modal a pantalla completa (bloqueado)
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

    // Carga duración completa y deja el rango al 100% (por defecto video completo)
    LaunchedEffect(src) {
        exo.setMediaItem(MediaItem.fromUri(src))
        exo.prepare()
        while (durationMs <= 0L) { durationMs = exo.duration; delay(50) }
        range = 0f..(durationMs / 1000f)
        applyClip()
    }

    LaunchedEffect(range) { if (durationMs > 0) applyClip() }

    // loop dentro del rango
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
                .background(Color.Black)     // <- opaco
                .zIndex(1f)                  // <- por encima de la cámara
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