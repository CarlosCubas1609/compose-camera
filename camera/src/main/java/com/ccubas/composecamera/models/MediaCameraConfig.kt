package com.ccubas.composecamera.models

/**
 * Configuration for the MediaCamera component
 * 
 * @param mediaType Type of media allowed (photo only, video only, or both)
 * @param maxSelection Maximum number of items that can be selected (default: unlimited)
 * @param saveToMediaStore Whether to save captured media to MediaStore permanently (default: false for temporary URIs)
 */
data class MediaCameraConfig(
    val mediaType: MediaType = MediaType.BOTH,
    val maxSelection: Int = Int.MAX_VALUE,
    val saveToMediaStore: Boolean = false
)