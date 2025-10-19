# Compose Camera 📸

A powerful WhatsApp-style camera library for Jetpack Compose with advanced features like zoom gestures, media selection, and built-in cropping.

## ✨ Features

- **WhatsApp-style UI** with longpress for video recording
- **Zoom gestures** during video recording (vertical drag)
- **Flexible configuration** - photo only, video only, or both
- **Media selection** with configurable limits
- **Built-in gallery** with carousel view
- **Photo/video cropping** and trimming
- **Smart carousel** that hides during recording
- **Configurable storage** - temporary URIs, permanent MediaStore, or custom directories
- **Bitmap launcher** - work with photos in memory without disk I/O
- **Bitmap callback** - receive photos before saving for processing/upload
- **In-memory photo handling** - no temporary files for photos
- **100% Jetpack Compose** - no View wrapping

## 🚀 Installation

### JitPack (Recommended)

Add JitPack repository to your project's `build.gradle.kts`:

```kotlin
allprojects {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("com.github.CarlosCubas1609:compose-camera:latest")
}
```

### Local Build

1. Clone this repository
2. Build the library: `./gradlew :camera:build`
3. Include the AAR in your project

## 📱 Usage

### Basic Usage

```kotlin
@Composable
fun MyScreen() {
    val cameraLauncher = rememberMediaCameraLauncher()
    
    Button(
        onClick = { 
            cameraLauncher.launch { uris ->
                // Handle selected/captured media URIs
                uris.forEach { uri -> 
                    Log.d("Camera", "Media: $uri")
                }
            }
        }
    ) {
        Text("Open Camera")
    }
}
```

### Advanced Configuration

```kotlin
@Composable
fun ConfiguredCamera() {
    val cameraLauncher = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            mediaType = MediaType.PHOTO_ONLY,          // Only photos
            maxSelection = 5,                           // Max 5 items
            saveToMediaStore = true,                    // Save to MediaStore
            customSaveDirectory = "Pictures/MyApp",     // Custom directory
            onBitmapCaptured = { bitmap ->              // Bitmap callback
                // Process bitmap before saving (upload, filters, etc.)
                uploadToServer(bitmap)
            }
        )
    )

    // Use the launcher...
}
```

### Bitmap Launcher (In-Memory Photos)

For scenarios where you want to work with photos in memory without saving to disk:

```kotlin
@Composable
fun InMemoryCamera() {
    val bitmapLauncher = rememberMediaCameraBitmapLauncher(
        config = MediaCameraConfig(
            mediaType = MediaType.BOTH,
            maxSelection = 10
        )
    )

    Button(
        onClick = {
            bitmapLauncher.launch { result ->
                // Photos as Bitmaps (in memory, no disk I/O)
                result.bitmaps.forEach { bitmap ->
                    uploadPhotoToServer(bitmap)
                    // or apply filters, compress, etc.
                }

                // Videos as temporary URIs (automatically cleaned up)
                result.videoUris.forEach { uri ->
                    uploadVideoToServer(uri) // Process immediately!
                }
            }
        }
    ) {
        Text("Open Camera")
    }
}
```

**Important:** Video URIs in `MediaCameraBitmapResult` are temporary files that are automatically cleaned up when the camera dialog closes. Process or copy them immediately in the callback.

### Direct Component Usage

```kotlin
@Composable
fun CameraScreen() {
    MediaCameraScreen(
        onDone = { uris -> /* Handle URIs */ },
        onClose = { /* Handle close */ },
        config = MediaCameraConfig(
            mediaType = MediaType.BOTH,
            maxSelection = 10,
            saveToMediaStore = true
        )
    )
}
```

## ⚙️ Configuration Options

### MediaCameraConfig Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `mediaType` | `MediaType` | `BOTH` | Type of media allowed (PHOTO_ONLY, VIDEO_ONLY, BOTH) |
| `maxSelection` | `Int` | `Int.MAX_VALUE` | Maximum number of items that can be selected |
| `saveToMediaStore` | `Boolean` | `false` | Whether to save permanently to MediaStore |
| `customSaveDirectory` | `String?` | `null` | Custom directory path when saving to MediaStore (e.g., "Pictures/MyApp") |
| `onBitmapCaptured` | `(Bitmap) -> Unit?` | `null` | Callback invoked with captured Bitmap before saving |

### MediaType Options

- `MediaType.PHOTO_ONLY` - Only photos can be captured/selected
- `MediaType.VIDEO_ONLY` - Only videos can be captured/selected
- `MediaType.BOTH` - Both photos and videos (default)

### Storage Modes

#### Standard Launcher (`rememberMediaCameraLauncher`)

| `saveToMediaStore` | Behavior | Files Location | Cleanup |
|--------------------|----------|----------------|---------|
| `false` (default) | Returns URIs to files in app's private storage | `filesDir` | Client responsible |
| `true` | Returns URIs from MediaStore (visible in gallery) | MediaStore (Pictures/Movies) | System managed |

#### Bitmap Launcher (`rememberMediaCameraBitmapLauncher`)

| Media Type | Format | Storage | Cleanup |
|------------|--------|---------|---------|
| Photos | `Bitmap` | In-memory only | Automatic (GC) |
| Videos | `Uri` | Temporary (`cacheDir`) | Automatic (on dialog close) |

### Custom Save Directory

When `saveToMediaStore = true`, you can specify a custom directory:

```kotlin
config = MediaCameraConfig(
    saveToMediaStore = true,
    customSaveDirectory = "Pictures/MyApp"  // Photos go here
    // or
    customSaveDirectory = "DCIM/MyCamera"   // Alternative location
)
```

If not specified, uses default system directories:
- Photos: `Pictures/`
- Videos: `Movies/`

## 🎮 User Interactions

### Photo Mode
- **Tap** - Capture photo
- **Long press** - Start video recording with WhatsApp-style UI
- **Drag up/down during recording** - Zoom in/out

### Video Mode  
- **Tap** - Start/stop video recording
- **Long press** - Not available in video mode

### Gallery
- **Tap thumbnail** - Preview with crop/trim options
- **Long press thumbnail** - Start selection mode
- **Tap after long press** - Add/remove from selection
- **Swipe up on carousel** - Open full gallery

## 🔧 Customization

The library uses Material 3 theming and follows your app's design system automatically. Colors, typography, and shapes are inherited from your theme.

## 📋 Requirements

- **Minimum SDK**: 24
- **Target SDK**: 34
- **Compose BOM**: 2023.10.01+
- **CameraX**: 1.3.0+

## 🔐 Permissions

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
    android:maxSdkVersion="28" />
```

The library automatically handles permission requests.

## 🎯 Examples

### 1. Basic Photo Upload to Server

```kotlin
val cameraLauncher = rememberMediaCameraLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.PHOTO_ONLY,
        maxSelection = 3,
        saveToMediaStore = false,
        onBitmapCaptured = { bitmap ->
            // Upload bitmap immediately after capture
            uploadToServer(bitmap)
        }
    )
)

// Returns URIs after saving (for local display)
cameraLauncher.launch { uris ->
    displayPhotos(uris)
}
```

### 2. In-Memory Photo Processing

```kotlin
val bitmapLauncher = rememberMediaCameraBitmapLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.PHOTO_ONLY,
        maxSelection = 5
    )
)

bitmapLauncher.launch { result ->
    // Process bitmaps directly (no disk I/O)
    result.bitmaps.forEach { bitmap ->
        val filtered = applyFilter(bitmap)
        uploadToServer(filtered)
    }
}
```

### 3. Save to Custom Gallery Folder

```kotlin
val galleryLauncher = rememberMediaCameraLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.BOTH,
        saveToMediaStore = true,
        customSaveDirectory = "Pictures/MyApp"
    )
)

galleryLauncher.launch { uris ->
    // Photos/videos saved to Pictures/MyApp/
    // and visible in system gallery
}
```

### 4. WhatsApp-Style Temporary Media

```kotlin
val whatsappLauncher = rememberMediaCameraBitmapLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.BOTH,
        maxSelection = Int.MAX_VALUE
    )
)

whatsappLauncher.launch { result ->
    // Photos: Bitmaps in memory
    result.bitmaps.forEach { sendToChat(it) }

    // Videos: Temporary URIs (upload before dialog closes!)
    result.videoUris.forEach { uploadVideo(it) }
}
```

### 5. Video Recording with Processing

```kotlin
val videoLauncher = rememberMediaCameraLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.VIDEO_ONLY,
        maxSelection = 1,
        saveToMediaStore = true,
        customSaveDirectory = "Movies/MyApp"
    )
)

videoLauncher.launch { uris ->
    val videoUri = uris.firstOrNull()
    videoUri?.let { processAndUpload(it) }
}
```

## 🚀 Launchers Comparison

The library provides two launcher types for different use cases:

### `rememberMediaCameraLauncher` - Standard Launcher

**Returns:** `List<Uri>`

**Best for:**
- Saving photos/videos to device storage
- Displaying media in your app
- Standard camera functionality

**Behavior:**
- `saveToMediaStore = false`: Files saved to app's private `filesDir`
- `saveToMediaStore = true`: Files saved to MediaStore (visible in gallery)
- `onBitmapCaptured`: Optional callback to process bitmaps before saving
- `customSaveDirectory`: Specify custom MediaStore directory

### `rememberMediaCameraBitmapLauncher` - Bitmap Launcher

**Returns:** `MediaCameraBitmapResult(bitmaps: List<Bitmap>, videoUris: List<Uri>)`

**Best for:**
- Uploading media to servers
- Applying filters/processing before saving
- Apps that don't need local file persistence
- Temporary media sharing (like messaging apps)

**Behavior:**
- **Photos**: Returned as `Bitmap` in memory (no disk I/O)
- **Videos**: Returned as temporary URIs in `cacheDir`
- **Cleanup**: All files automatically cleaned up when dialog closes
- **⚠️ Important**: Process videos immediately - they're deleted after callback

**Comparison:**

| Feature | Standard Launcher | Bitmap Launcher |
|---------|------------------|-----------------|
| Photo format | URI | Bitmap (in-memory) |
| Video format | URI | URI (temporary) |
| File persistence | Configurable | None (photos) / Temporary (videos) |
| Cleanup | Manual (saveToMediaStore=false) | Automatic |
| Best for | File storage | Upload/Processing |
| Disk I/O (photos) | Yes | No |

## 🐛 Known Issues

- Video trimming requires API 21+
- Some devices may have different zoom sensitivities
- Audio recording requires RECORD_AUDIO permission
- Bitmap launcher videos must be processed immediately before dialog closes

## 🤝 Contributing

1. Fork the project
2. Create your feature branch
3. Make your changes
4. Add tests if applicable  
5. Submit a pull request

## 📄 License

```
MIT License

Copyright (c) 2024 Ccubas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 🙏 Acknowledgments

- Google CameraX team for the camera APIs
- WhatsApp for the UI/UX inspiration
- Jetpack Compose team for the amazing UI toolkit

### Demo
![Demo](docs/gif/gif.gif)

---

Made with ❤️ by [ccubas](https://github.com/CarlosCubas1609)
