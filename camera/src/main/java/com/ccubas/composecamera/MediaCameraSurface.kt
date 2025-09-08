package com.ccubas.composecamera

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ccubas.composecamera.components.MediaCameraPermissionsGate
import com.ccubas.composecamera.models.MediaCameraConfig

/**
 * Full-screen camera surface with permission handling
 */
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

/**
 * Camera launcher for easy integration
 */
@Stable
class MediaCameraLauncher internal constructor(
    val launch: ((List<Uri>) -> Unit) -> Unit
)

/**
 * Remember a camera launcher with configuration
 * 
 * @param config Configuration for the camera behavior
 * @return MediaCameraLauncher instance
 */
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