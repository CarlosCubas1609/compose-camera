package com.ccubas.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ccubas.camera.components.DialogScaffold
import com.ccubas.camera.components.MediaCameraPermissionsGate
import com.ccubas.composecamera.models.MediaCameraConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * Result data for the bitmap launcher that contains both bitmaps (for photos) and URIs (for videos).
 *
 * @property bitmaps List of captured/selected photos as Bitmaps in memory (not saved to disk)
 * @property videoUris List of captured/selected videos as temporary URIs in cacheDir.
 * These files are automatically cleaned up when the camera dialog is closed.
 * If you need to persist them, copy or upload them immediately upon receiving this result.
 */
data class MediaCameraBitmapResult(
    val bitmaps: List<Bitmap> = emptyList(),
    val videoUris: List<Uri> = emptyList()
)

/**
 * A stable launcher for the media camera dialog that returns Bitmaps for photos and URIs for videos.
 * Useful when you want to work with images in memory without saving to disk.
 *
 * @property launch A function to launch the camera dialog, taking a callback to handle the results.
 */
@Stable
class MediaCameraBitmapLauncher internal constructor(
    val launch: ((MediaCameraBitmapResult) -> Unit) -> Unit
)

/**
 * Creates and remembers a [MediaCameraBitmapLauncher] that returns Bitmaps for photos
 * and temporary URIs for videos.
 *
 * This launcher is designed for scenarios where you want to work with media temporarily
 * without persisting files to disk:
 * - **Photos**: Returned as Bitmaps in memory (no disk I/O)
 * - **Videos**: Returned as temporary URIs in cacheDir
 *
 * **Important**: Video files are automatically cleaned up when the camera dialog closes.
 * If you need to persist them (e.g., upload to server, save to permanent location),
 * you must copy or process them immediately in the callback.
 *
 * The launcher automatically:
 * - Sets `saveToMediaStore = false`
 * - Manages `onBitmapCaptured` internally for photo collection
 * - Detects photo vs video by MIME type
 * - Cleans up all temporary files on dialog dismissal
 *
 * @param config The configuration for the media camera. The `saveToMediaStore` will be
 * automatically set to false, and `onBitmapCaptured` will be managed internally.
 * @return A remembered [MediaCameraBitmapLauncher] instance.
 *
 * @sample
 * ```kotlin
 * val picker = rememberMediaCameraBitmapLauncher()
 *
 * Button(onClick = {
 *     picker.launch { result ->
 *         // Process photos immediately (in memory)
 *         result.bitmaps.forEach { bitmap ->
 *             uploadToServer(bitmap) // or apply filters, etc.
 *         }
 *
 *         // Copy or upload videos immediately (temporary files!)
 *         result.videoUris.forEach { uri ->
 *             uploadVideoToServer(uri) // Must process before dialog closes
 *         }
 *     }
 * })
 * ```
 */
@Composable
fun rememberMediaCameraBitmapLauncher(
    config: MediaCameraConfig = MediaCameraConfig()
): MediaCameraBitmapLauncher {
    var open by remember { mutableStateOf(false) }
    var cb by remember { mutableStateOf<(MediaCameraBitmapResult) -> Unit>({ _ -> }) }
    val bitmaps = remember { mutableListOf<Bitmap>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (open) {
        MediaCameraDialog(
            onResult = { uris ->
                // Convert URIs to Bitmaps for photos, keep URIs for videos
                scope.launch {
                    val allBitmaps = mutableListOf<Bitmap>()
                    val videoUris = mutableListOf<Uri>()

                    // Add captured bitmaps first
                    allBitmaps.addAll(bitmaps)

                    // Process URIs: convert photos to bitmaps, keep video URIs
                    uris.forEach { uri ->
                        try {
                            withContext(Dispatchers.IO) {
                                val mimeType = context.contentResolver.getType(uri)
                                if (mimeType?.startsWith("video/") == true) {
                                    // It's a video, keep as URI
                                    videoUris.add(uri)
                                } else {
                                    // It's a photo, convert to Bitmap
                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                        BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                                            allBitmaps.add(bitmap)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip if can't load
                        }
                    }

                    // Return result with both bitmaps and video URIs
                    withContext(Dispatchers.Main) {
                        cb(MediaCameraBitmapResult(
                            bitmaps = allBitmaps,
                            videoUris = videoUris
                        ))
                        bitmaps.clear()
                    }
                }
            },
            onDismiss = {
                open = false
                bitmaps.clear()
            },
            config = config.copy(
                saveToMediaStore = false,
                onBitmapCaptured = { bitmap ->
                    // Collect bitmaps as they're captured
                    bitmaps.add(bitmap)
                }
            )
        )
    }
    return remember {
        MediaCameraBitmapLauncher { onResult ->
            cb = onResult
            bitmaps.clear()
            open = true
        }
    }
}