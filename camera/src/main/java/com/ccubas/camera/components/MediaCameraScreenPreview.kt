package com.ccubas.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.ccubas.camera.components.camera.*
import com.ccubas.composecamera.models.CameraMode
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType

/**
 * Preview completo del MediaCameraScreen que simula la interfaz completa
 * sin dependencias complejas como la cámara real.
 */
@Composable
fun MediaCameraScreenPreview(
    showGallery: Boolean = false,
    showImageReview: Boolean = false,
    showVideoReview: Boolean = false,
    cameraMode: CameraMode = CameraMode.Photo,
    mediaType: MediaType = MediaType.BOTH,
    isRecording: Boolean = false,
    recordingSeconds: Int = 0,
    isLongPressing: Boolean = false
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    // Mock data
    val mockThumbnails = remember {
        listOf(
            MediaThumbnail("content://media/1".toUri(), false),
            MediaThumbnail("content://media/2".toUri(), true),
            MediaThumbnail("content://media/3".toUri(), false),
            MediaThumbnail("content://media/4".toUri(), true),
        )
    }
    
    val mockGalleryItems = remember {
        List(12) { index ->
            MediaThumbnail(
                "content://media/gallery_$index".toUri(), 
                index % 3 == 0 // Every 3rd item is video
            )
        }
    }
    
    val selectedUris = remember {
        listOf("content://media/2".toUri(), "content://media/4".toUri())
    }
    
    val config = MediaCameraConfig(mediaType = mediaType, maxSelection = 10)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        
        // MOCK CAMERA PREVIEW
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Camera Preview",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge
            )
        }

        // TOP BAR
        CameraTopBar(
            isRecording = isRecording,
            recordingSeconds = recordingSeconds,
            flashMode = 0, // OFF
            torchOn = false,
            onClose = {},
            onFlashTorchToggle = {}
        )

        // BOTTOM
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // MEDIA CAROUSEL: only in Photo mode and hide when long-pressing
            if (cameraMode == CameraMode.Photo && !isLongPressing) {
                MediaCarousel(
                    thumbnails = mockThumbnails,
                    selectedUris = selectedUris,
                    imageLoader = imageLoader,
                    onItemClick = { _, _ -> },
                    onItemLongClick = { },
                    onSwipeUp = { }
                )
            }

            // CAMERA CONTROLS
            CameraControls(
                isLongPressing = isLongPressing,
                isRecording = isRecording,
                cameraMode = cameraMode,
                shutterModifier = Modifier,
                onGalleryClick = { },
                onSwitchCamera = { }
            )

            // MODE SWITCHER
            ModeSwitcher(
                currentMode = cameraMode,
                mediaType = mediaType,
                onModeChange = { },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // SEND BUTTON
            SendButton(
                selectedUris = selectedUris,
                maxSelection = config.maxSelection,
                onSend = { }
            )
        }

        // MEDIA GALLERY
        if (showGallery) {
            MediaGallery(
                galleryItems = mockGalleryItems,
                selectedUris = selectedUris,
                config = config,
                imageLoader = imageLoader,
                onDismiss = { },
                onToggleSelect = { },
                onSendSelection = { }
            )
        }

        // IMAGE REVIEW
        if (showImageReview) {
            ImageReviewWithCropOverlay(
                src = "content://media/sample_image.jpg".toUri(),
                onClose = { },
                onUse = { }
            )
        }

        // VIDEO REVIEW
        if (showVideoReview) {
            VideoReviewOverlay(
                src = "content://media/sample_video.mp4".toUri(),
                onClose = { },
                onSaveTrim = { _, _ -> }
            )
        }
    }
}

// ===== PREVIEWS =====

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenNormalPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Photo,
            mediaType = MediaType.BOTH,
            isRecording = false,
            isLongPressing = false
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenRecordingPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Video,
            mediaType = MediaType.BOTH,
            isRecording = true,
            recordingSeconds = 45,
            isLongPressing = false
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenLongPressingPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Photo,
            mediaType = MediaType.BOTH,
            isRecording = true,
            recordingSeconds = 8,
            isLongPressing = true
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenPhotoOnlyPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Photo,
            mediaType = MediaType.PHOTO_ONLY,
            isRecording = false,
            isLongPressing = false
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenVideoOnlyPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Video,
            mediaType = MediaType.VIDEO_ONLY,
            isRecording = false,
            isLongPressing = false
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenWithGalleryPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Photo,
            mediaType = MediaType.BOTH,
            showGallery = true
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenWithImageReviewPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Photo,
            mediaType = MediaType.BOTH,
            showImageReview = true
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun MediaCameraScreenWithVideoReviewPreview() {
    MaterialTheme {
        MediaCameraScreenPreview(
            cameraMode = CameraMode.Video,
            mediaType = MediaType.BOTH,
            showVideoReview = true
        )
    }
}

/**
 * Preview interactivo que permite cambiar entre diferentes estados
 */
@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun InteractiveMediaCameraScreenPreview() {
    var currentState by remember { mutableStateOf("normal") }
    var cameraMode by remember { mutableStateOf(CameraMode.Photo) }
    var isRecording by remember { mutableStateOf(false) }
    
    MaterialTheme {
        Box {
            when (currentState) {
                "gallery" -> MediaCameraScreenPreview(showGallery = true)
                "image_review" -> MediaCameraScreenPreview(showImageReview = true)
                "video_review" -> MediaCameraScreenPreview(showVideoReview = true)
                "recording" -> MediaCameraScreenPreview(
                    isRecording = true,
                    recordingSeconds = 23,
                    cameraMode = cameraMode
                )
                "long_pressing" -> MediaCameraScreenPreview(
                    isLongPressing = true,
                    isRecording = true,
                    recordingSeconds = 5
                )
                else -> MediaCameraScreenPreview(cameraMode = cameraMode)
            }
            
            // Control buttons (only visible in normal preview)
            if (currentState == "normal") {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(onClick = { currentState = "gallery" }) { Text("G") }
                    Button(onClick = { currentState = "image_review" }) { Text("I") }
                    Button(onClick = { currentState = "video_review" }) { Text("V") }
                    Button(onClick = { currentState = "recording" }) { Text("R") }
                    Button(onClick = { currentState = "long_pressing" }) { Text("L") }
                    Button(onClick = { 
                        cameraMode = if (cameraMode == CameraMode.Photo) CameraMode.Video else CameraMode.Photo 
                    }) { 
                        Text(if (cameraMode == CameraMode.Photo) "P" else "V") 
                    }
                }
            } else {
                // Back button for other states
                Button(
                    onClick = { currentState = "normal" },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text("Back")
                }
            }
        }
    }
}