package com.ccubas.composecamera.models

import android.net.Uri

/**
 * Represents a media thumbnail
 * 
 * @param uri The URI of the media file
 * @param isVideo Whether this thumbnail represents a video file
 */
data class Thumb(
    val uri: Uri, 
    val isVideo: Boolean
)