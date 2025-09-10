WhatsApp-Style Camera Library for Jetpack Compose - Compose Camera

I've been working on a WhatsApp-inspired camera library built entirely with Jetpack Compose and wanted to share it      
with the community.

What is Compose Camera?

Compose Camera is a modern camera library that brings the familiar WhatsApp camera experience to Android apps, with     
advanced features like zoom gestures, media selection, and built-in editing capabilities.

Key Features:

- WhatsApp-style UI with longpress video recording
- Zoom gestures during recording (vertical drag)
- Flexible configuration - photo only, video only, or both
- Media selection with configurable limits
- Built-in gallery with carousel view
- Photo/video cropping and trimming
- Smart carousel that hides during recording
- 100% Jetpack Compose - no View wrapping needed

Installation:

Add JitPack to your build.gradle.kts:

allprojects {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

Add the dependency:

dependencies {
    implementation("com.github.CarlosCubas1609:compose-camera:1.0.6")
}

Basic Usage:

@Composable
fun MyScreen() {
    val cameraLauncher = rememberMediaCameraLauncher()

    Button(
        onClick = {
            cameraLauncher.launch { uris ->
                // Handle captured/selected media URIs
                uris.forEach { uri ->
                    Log.d("Camera", "Media: $uri")
                }
            }
        }
    ) {
        Text("Open Camera")
    }
}

Advanced Configuration:

@Composable
fun ConfiguredCamera() {
    val cameraLauncher = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            mediaType = MediaType.PHOTO_ONLY,  // Only photos
            maxSelection = 5,                   // Max 5 items
            saveToMediaStore = true            // Save permanently
        )
    )

    // Use the launcher...
}

User Interactions:

Photo Mode:
- Tap - Capture photo
- Long press - Start video recording with WhatsApp-style UI
- Drag up/down during recording - Zoom in/out

Gallery:
- Tap thumbnail - Preview with crop/trim options
- Long press thumbnail - Start selection mode
- Swipe up on carousel - Open full gallery

Why I Built This:

After working with existing camera libraries, I found they either required complex setup, used deprecated View
components, or didn't provide the modern UX that users expect. Compose Camera solves these issues by:

1. Pure Compose Integration - No AndroidView wrappers needed
2. Familiar UX - WhatsApp-style interface users already know
3. Flexible Configuration - Works for any media selection use case
4. Modern Architecture - Built with latest Compose and CameraX APIs
5. Minimal Setup - Just add dependency and you're ready to go

Technical Requirements:

- Minimum SDK: 24
- Compose BOM: 2023.10.01+
- CameraX: 1.3.0+