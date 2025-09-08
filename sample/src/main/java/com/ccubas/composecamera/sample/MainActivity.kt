package com.ccubas.composecamera.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType
import com.ccubas.composecamera.rememberMediaCameraLauncher
import com.ccubas.composecamera.sample.ui.theme.ComposeCameraSampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeCameraSampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SampleScreen()
                }
            }
        }
    }
}

@Composable
fun SampleScreen() {
    var capturedUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    
    // Different camera configurations
    val photoOnlyLauncher = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            mediaType = MediaType.PHOTO_ONLY,
            maxSelection = 3,
            saveToMediaStore = true
        )
    )
    
    val videoOnlyLauncher = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            mediaType = MediaType.VIDEO_ONLY,
            maxSelection = 1,
            saveToMediaStore = false
        )
    )
    
    val whatsappStyleLauncher = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            mediaType = MediaType.BOTH,
            maxSelection = Int.MAX_VALUE,
            saveToMediaStore = false
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Compose Camera Sample",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Try different camera configurations:",
            style = MaterialTheme.typography.bodyLarge
        )
        
        // Camera buttons
        Button(
            onClick = { 
                photoOnlyLauncher.launch { uris ->
                    capturedUris = uris
                    Log.d("Camera", "Photo only captured: ${uris.size} items")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Photo Only (max 3, save to MediaStore)")
        }
        
        Button(
            onClick = { 
                videoOnlyLauncher.launch { uris ->
                    capturedUris = uris
                    Log.d("Camera", "Video only captured: ${uris.size} items")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Video Only (max 1, temporary URIs)")
        }
        
        Button(
            onClick = { 
                whatsappStyleLauncher.launch { uris ->
                    capturedUris = uris
                    Log.d("Camera", "WhatsApp style captured: ${uris.size} items")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("WhatsApp Style (unlimited, temporary)")
        }
        
        // Results
        if (capturedUris.isNotEmpty()) {
            Text(
                text = "Captured ${capturedUris.size} items:",
                style = MaterialTheme.typography.titleMedium
            )
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(capturedUris) { uri ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uri.toString(),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            Button(
                onClick = { capturedUris = emptyList() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Results")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ComposeCameraSampleTheme {
        SampleScreen()
    }
}