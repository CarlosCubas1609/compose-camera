package com.ccubas.camera.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
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

// ===== Launcher estilo rememberLauncherForActivityResult (List<Uri>) =====
@Stable
class MediaCameraLauncher internal constructor(
    val launch: ((List<Uri>) -> Unit) -> Unit
)

@Composable
fun rememberMediaCameraLauncher(
    config: MediaCameraConfig = MediaCameraConfig()
): MediaCameraLauncher {
    var open by remember { mutableStateOf(false) }
    var cb by remember { mutableStateOf<(List<Uri>) -> Unit>({_ -> }) }

    if (open) {
        MediaCameraSurface(
            onResult = { uris -> cb(uris) },
            onDismiss = { open = false },
            config = config
        )
    }
    return remember {
        MediaCameraLauncher { onResult ->
            cb = onResult
            open = true
        }
    }
}