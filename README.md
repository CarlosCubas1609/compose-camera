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
- **Configurable storage** - temporary URIs or permanent MediaStore
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
    implementation("com.github.CarlosCubas1609:compose-camera:1.0.6")
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
            mediaType = MediaType.PHOTO_ONLY,  // Only photos
            maxSelection = 5,                   // Max 5 items
            saveToMediaStore = false           // Use temporary URIs
        )
    )
    
    // Use the launcher...
}
```

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

### MediaType

- `MediaType.PHOTO_ONLY` - Only photos can be captured/selected
- `MediaType.VIDEO_ONLY` - Only videos can be captured/selected  
- `MediaType.BOTH` - Both photos and videos (default)

### Storage Options

- `saveToMediaStore = false` - Returns temporary URIs (default)
- `saveToMediaStore = true` - Saves to MediaStore permanently

### Selection Limits

- `maxSelection = Int.MAX_VALUE` - Unlimited selection (default)
- `maxSelection = 5` - Limit to 5 items

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

### Photo-only camera with limit

```kotlin
val photoLauncher = rememberMediaCameraLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.PHOTO_ONLY,
        maxSelection = 3,
        saveToMediaStore = true
    )
)
```

### Video-only camera

```kotlin  
val videoLauncher = rememberMediaCameraLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.VIDEO_ONLY,
        maxSelection = 1,
        saveToMediaStore = false
    )
)
```

### WhatsApp-style camera

```kotlin
val whatsappLauncher = rememberMediaCameraLauncher(
    config = MediaCameraConfig(
        mediaType = MediaType.BOTH,
        maxSelection = Int.MAX_VALUE,
        saveToMediaStore = false
    )
)
```

## 🐛 Known Issues

- Video trimming requires API 21+
- Some devices may have different zoom sensitivities
- Audio recording requires RECORD_AUDIO permission

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
![Demo 1](docs/giffs/giff_1.mp4)
![Demo 2](docs/giffs/giff_2.mp4)

---

Made with ❤️ by [ccubas](https://github.com/CarlosCubas1609)
