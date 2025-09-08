package com.ccubas.camera

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.ccubas.camera.components.MediaCameraPermissionsGate
import com.ccubas.composecamera.models.MediaCameraConfig

@Composable
fun MediaCameraSurface(
    onResult: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
    config: MediaCameraConfig = MediaCameraConfig()
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10000000f),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            MediaCameraPermissionsGate {
                MediaCameraScreen(
                    onDone = { uris -> onResult(uris); onDismiss() },
                    onClose = onDismiss,
                    config = config
                )
            }
        }
    }
}