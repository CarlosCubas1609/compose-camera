package com.ccubas.composecamera.models

import android.graphics.Bitmap
import android.net.Uri

/**
 * Represents a media thumbnail
 *
 * @param uri The URI of the media file
 * @param isVideo Whether this thumbnail represents a video file
 * @param dateAdded The date when the media was added (for sorting)
 * @param thumbnail The thumbnail bitmap for videos
 */
data class Thumb(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long = 0L,
    val thumbnail: Bitmap? = null
)