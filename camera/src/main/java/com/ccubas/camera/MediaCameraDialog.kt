package com.ccubas.camera.utils

import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ccubas.camera.MediaCameraScreen
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
