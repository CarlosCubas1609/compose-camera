package com.ccubas.camera

import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ccubas.camera.components.DialogScaffold
import com.ccubas.camera.components.MediaCameraPermissionsGate
import com.ccubas.composecamera.models.MediaCameraConfig

// ===== Dialog full-screen que reemplaza a MediaCameraSurface =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCameraDialog(
    onResult: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
    config: MediaCameraConfig = MediaCameraConfig()
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogScaffold {
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

// ====== Launcher estilo rememberLauncherForActivityResult (List<Uri>) ======
@Stable
class MediaCameraLauncher internal constructor(
    val launch: ((List<Uri>) -> Unit) -> Unit
)

@Composable
fun rememberMediaCameraLauncher(
    config: MediaCameraConfig = MediaCameraConfig()
): MediaCameraLauncher {
    var open by remember { mutableStateOf(false) }
    var cb by remember { mutableStateOf<(List<Uri>) -> Unit>({ _ -> }) }

    if (open) {
        MediaCameraDialog(
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
