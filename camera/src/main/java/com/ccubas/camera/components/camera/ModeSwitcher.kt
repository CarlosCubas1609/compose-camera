package com.ccubas.camera.components.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ccubas.composecamera.models.CameraMode
import com.ccubas.composecamera.models.MediaType

@Composable
fun ModeSwitcher(
    currentMode: CameraMode,
    mediaType: MediaType,
    onModeChange: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Only show the switcher if both modes are available
    if (mediaType == MediaType.BOTH) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModeButton(
                text = "Video",
                isSelected = currentMode == CameraMode.Video,
                onClick = { onModeChange(CameraMode.Video) }
            )
            ModeButton(
                text = "Foto",
                isSelected = currentMode == CameraMode.Photo,
                onClick = { onModeChange(CameraMode.Photo) }
            )
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
        modifier = Modifier.clickable { onClick() },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun ModeSwitcherPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ModeSwitcher(
            currentMode = CameraMode.Photo,
            mediaType = MediaType.BOTH,
            onModeChange = {}
        )
        
        ModeSwitcher(
            currentMode = CameraMode.Video,
            mediaType = MediaType.BOTH,
            onModeChange = {}
        )
        
        // This should not render anything
        ModeSwitcher(
            currentMode = CameraMode.Photo,
            mediaType = MediaType.PHOTO_ONLY,
            onModeChange = {}
        )
        
        // This should not render anything  
        ModeSwitcher(
            currentMode = CameraMode.Video,
            mediaType = MediaType.VIDEO_ONLY,
            onModeChange = {}
        )
    }
}