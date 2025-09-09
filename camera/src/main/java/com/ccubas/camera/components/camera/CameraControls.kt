package com.ccubas.camera.components.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ccubas.composecamera.models.CameraMode

@Composable
fun CameraControls(
    isLongPressing: Boolean = false,
    isRecording: Boolean = false,
    cameraMode: CameraMode = CameraMode.Photo,
    shutterModifier: Modifier = Modifier,
    onGalleryClick: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 18.dp)
    ) {
        // Gallery button: hide when long-pressing
        if (!isLongPressing) {
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Collections, null, tint = Color.White)
            }
        }

        // Shutter button with WhatsApp design on long press
        ShutterButton(
            isLongPressing = isLongPressing,
            isRecording = isRecording,
            cameraMode = cameraMode,
            modifier = Modifier
                .align(Alignment.Center)
                .then(shutterModifier)
        )

        // Switch camera button: hide when long-pressing
        if (!isLongPressing) {
            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Cameraswitch, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isLongPressing: Boolean,
    isRecording: Boolean,
    cameraMode: CameraMode,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(if (isLongPressing) 100.dp else 84.dp)
            .clip(CircleShape)
            .background(
                if (isLongPressing || isRecording) Color.Transparent
                else Color.White.copy(alpha = .15f)
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLongPressing -> {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(30.dp)
                            .background(Color.Red, CircleShape)
                    )
                }
            }
            isRecording -> {
                // Design during recording: white circle + red square/circle
                Box(
                    Modifier
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .clip(
                                if (cameraMode == CameraMode.Video)
                                    RoundedCornerShape(4.dp)
                                else
                                    CircleShape
                            )
                            .background(Color.Red)
                    )
                }
            }
            else -> {
                // Normal design: white button
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 400, heightDp = 120)
@Composable
fun CameraControlsPreview() {
    Surface(
        modifier = Modifier.size(400.dp, 120.dp),
        color = Color.Black
    ) {
        CameraControls(
            isLongPressing = false,
            isRecording = false,
            cameraMode = CameraMode.Photo
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 400, heightDp = 120)
@Composable
fun CameraControlsLongPressingPreview() {
    Surface(
        modifier = Modifier.size(400.dp, 120.dp),
        color = Color.Black
    ) {
        CameraControls(
            isLongPressing = true,
            isRecording = false,
            cameraMode = CameraMode.Photo
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 400, heightDp = 120)
@Composable
fun CameraControlsRecordingPhotoPreview() {
    Surface(
        modifier = Modifier.size(400.dp, 120.dp),
        color = Color.Black
    ) {
        CameraControls(
            isLongPressing = false,
            isRecording = true,
            cameraMode = CameraMode.Photo
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 400, heightDp = 120)
@Composable
fun CameraControlsRecordingVideoPreview() {
    Surface(
        modifier = Modifier.size(400.dp, 120.dp),
        color = Color.Black
    ) {
        CameraControls(
            isLongPressing = false,
            isRecording = true,
            cameraMode = CameraMode.Video
        )
    }
}