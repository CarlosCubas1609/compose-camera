package com.ccubas.camera.components.camera

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoReviewOverlay(
    src: Uri,
    onClose: () -> Unit = {},
    onSaveTrim: (startMs: Long, endMs: Long) -> Unit = { _, _ -> }
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

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { exo.release() } }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun VideoReviewPreview() {
    // Mock video review overlay without actual video player for preview
    Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .75f)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Mock video player area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1F)
                        .fillMaxHeight(0.85f)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Video Player Area",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mock range slider
                var mockRange by remember { mutableStateOf(10f..30f) }
                RangeSlider(
                    value = mockRange,
                    onValueChange = { mockRange = it },
                    valueRange = 0f..60f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {}) { 
                        Text("Usar") 
                    }
                }
            }

            IconButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 100)
@Composable
fun VideoTrimControlsPreview() {
    var mockRange by remember { mutableStateOf(5f..25f) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Trim Video: ${mockRange.start.toInt()}s - ${mockRange.endInclusive.toInt()}s",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        RangeSlider(
            value = mockRange,
            onValueChange = { mockRange = it },
            valueRange = 0f..60f
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {}) { 
                Text("Usar") 
            }
        }
    }
}