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

/**
 * A full-screen dialog that displays the [MediaCameraScreen].
 *
 * @param onResult Callback invoked when the user confirms the selection of media, returning a list of URIs.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 * @param config The configuration for the media camera.
 */
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

/**
 * A stable launcher for the media camera dialog, providing a type-safe way to launch the camera
 * and receive a result, similar to `rememberLauncherForActivityResult`.
 *
 * @property launch A function to launch the camera dialog, taking a callback to handle the result.
 */
@Stable
class MediaCameraLauncher internal constructor(
    val launch: ((List<Uri>) -> Unit) -> Unit
)

/**
 * Creates and remembers a [MediaCameraLauncher].
 *
 * This launcher is the recommended way to use the camera. It handles the lifecycle of the
 * camera dialog and provides a simple callback for receiving the results.
 *
 * @param config The configuration for the media camera. Note that if `saveToMediaStore` is set
 * to `false` (the default), the returned URIs will point to temporary files. The library
 * will not delete these files automatically, making the caller responsible for their management.
 * @return A remembered [MediaCameraLauncher] instance.
 */
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