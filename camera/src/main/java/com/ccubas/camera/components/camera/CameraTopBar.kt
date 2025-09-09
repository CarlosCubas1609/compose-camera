package com.ccubas.camera.components.camera

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun CameraTopBar(
    isRecording: Boolean = false,
    recordingSeconds: Int = 0,
    flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    torchOn: Boolean = false,
    onClose: () -> Unit = {},
    onFlashTorchToggle: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(Icons.Outlined.Close, null, tint = Color.White)
        }

        // Recording timer (centered, only shown when recording)
        if (recordingSeconds > 0 && isRecording) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatTimer(recordingSeconds),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Flash/Torch button
        IconButton(
            onClick = onFlashTorchToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            val isOn = if (isRecording) torchOn else flashMode == ImageCapture.FLASH_MODE_ON
            Icon(
                imageVector = if (isOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

private fun formatTimer(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun CameraTopBarPreview() {
    CameraTopBar(
        isRecording = false,
        recordingSeconds = 0,
        flashMode = ImageCapture.FLASH_MODE_OFF,
        torchOn = false
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun CameraTopBarRecordingPreview() {
    CameraTopBar(
        isRecording = true,
        recordingSeconds = 125, // 2:05
        flashMode = ImageCapture.FLASH_MODE_OFF,
        torchOn = true
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun CameraTopBarFlashOnPreview() {
    CameraTopBar(
        isRecording = false,
        recordingSeconds = 0,
        flashMode = ImageCapture.FLASH_MODE_ON,
        torchOn = false
    )
}