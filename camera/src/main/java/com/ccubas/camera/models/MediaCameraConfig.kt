package com.ccubas.composecamera.models

import android.graphics.Bitmap
import android.net.Uri

/**
 * Configuration for the MediaCamera component
 *
 * @param mediaType Type of media allowed (photo only, video only, or both)
 * @param maxSelection Maximum number of items that can be selected (default: unlimited)
 * @param saveToMediaStore Whether to save captured media to MediaStore permanently (default: false for temporary URIs)
 * @param customSaveDirectory Custom directory path for saving media when saveToMediaStore is true (default: Pictures/Videos)
 * @param onBitmapCaptured Callback invoked with the captured/edited Bitmap before saving to file. Allows client to handle Bitmap directly.
 */
data class MediaCameraConfig(
    val mediaType: MediaType = MediaType.BOTH,
    val maxSelection: Int = Int.MAX_VALUE,
    val saveToMediaStore: Boolean = false,
    val customSaveDirectory: String? = null,
    val onBitmapCaptured: ((Bitmap) -> Unit)? = null
)