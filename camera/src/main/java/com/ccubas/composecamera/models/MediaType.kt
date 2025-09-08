package com.ccubas.composecamera.models

/**
 * Defines the type of media that can be captured
 */
enum class MediaType {
    /**
     * Only photos can be captured and selected
     */
    PHOTO_ONLY,
    
    /**
     * Only videos can be captured and selected
     */
    VIDEO_ONLY,
    
    /**
     * Both photos and videos can be captured and selected
     */
    BOTH
}