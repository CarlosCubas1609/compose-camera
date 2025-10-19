package com.ccubas.composecamera

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ccubas.camera.rememberMediaCameraLauncher
import com.ccubas.camera.rememberMediaCameraBitmapLauncher
import com.ccubas.camera.MediaCameraBitmapResult
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.ui.theme.ComposeCameraTheme

/**
 * The main activity for the sample application.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeCameraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MainContent()
                    }
                }
            }
        }
    }
}

/**
 * The main content of the sample application.
 * It displays a button to open the camera and shows the captured media.
 */
@Composable
fun MainContent() {
    var capturedMedia by rememberSaveable { mutableStateOf<List<Uri>?>(null) }
    var capturedBitmapResult by remember { mutableStateOf<MediaCameraBitmapResult?>(null) }

    // Example 1: Basic usage with URIs (existing)
    val pickerBasic = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            saveToMediaStore = false,
        )
    )

    // Example 2: Bitmap Launcher - receive Bitmaps (photos) and temporary URIs (videos)
    // IMPORTANT: Videos are temporary and cleaned up when dialog closes!
    // Process/upload them immediately in the callback.
    val pickerBitmaps = rememberMediaCameraBitmapLauncher(
        config = MediaCameraConfig()
    )

    // Example 3: With Bitmap callback - receive Bitmap before saving
    val pickerWithBitmap = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            saveToMediaStore = false,
            onBitmapCaptured = { bitmap ->
                // Process bitmap here (e.g., upload to server, apply filters, etc.)
                Log.d("MainActivity", "Bitmap captured: ${bitmap.width}x${bitmap.height}")
                // You can compress, send to API, or any custom processing
            }
        )
    )

    // Example 4: Save to MediaStore with custom directory
    val pickerWithCustomDir = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            saveToMediaStore = true,
            customSaveDirectory = "Pictures/MyAppPhotos", // Custom directory path
            onBitmapCaptured = { bitmap ->
                // Bitmap callback + save to custom directory
                Log.d("MainActivity", "Bitmap captured and will be saved to custom directory")
            }
        )
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (capturedMedia.isNullOrEmpty() && capturedBitmapResult == null) {
            Text("Aún no has capturado nada.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("Media Capturada:", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp)) {
                // Show URIs if available
                capturedMedia?.let { uris ->
                    items(uris) { uri ->
                        Column(modifier = Modifier.padding(8.dp)) {
                            AsyncImage(model = uri, contentDescription = null)
                            Text(uri.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                // Show Bitmap result if available
                capturedBitmapResult?.let { result ->
                    // Show bitmaps (photos)
                    items(result.bitmaps.size) { index ->
                        val bitmap: Bitmap = result.bitmaps[index]
                        Column(modifier = Modifier.padding(8.dp)) {
                            AsyncImage(model = bitmap, contentDescription = null)
                            Text("Bitmap (Photo): ${bitmap.width}x${bitmap.height}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // Show video URIs
                    items(result.videoUris) { uri ->
                        Column(modifier = Modifier.padding(8.dp)) {
                            AsyncImage(model = uri, contentDescription = null)
                            Text("Video URI: $uri", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Button for basic usage with URIs
        Button(onClick = {
            capturedBitmapResult = null
            pickerBasic.launch { uris ->
                capturedMedia = uris
            }
        }, modifier = Modifier.padding(16.dp)) {
            Text("1. URIs (Básico)")
        }

        // Button for Bitmap Launcher (NO DISK for photos, TEMP for videos)
        Button(onClick = {
            capturedMedia = null
            pickerBitmaps.launch { result ->
                capturedBitmapResult = result
                Log.d("MainActivity", "Received ${result.bitmaps.size} photos, ${result.videoUris.size} videos")

                // Example: Process immediately
                // result.bitmaps.forEach { uploadPhotoToServer(it) }
                // result.videoUris.forEach { uploadVideoToServer(it) } // Do this before dialog closes!
            }
        }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("2. Bitmaps + Videos (Temporal)")
        }

        // Button with bitmap callback + URIs
        Button(onClick = {
            capturedBitmapResult = null
            pickerWithBitmap.launch { uris ->
                capturedMedia = uris
            }
        }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("3. Bitmap Callback + URIs")
        }

        // Button with custom directory
        Button(onClick = {
            capturedBitmapResult = null
            pickerWithCustomDir.launch { uris ->
                capturedMedia = uris
            }
        }, modifier = Modifier.padding(16.dp)) {
            Text("4. MediaStore + Custom Dir")
        }
    }
}