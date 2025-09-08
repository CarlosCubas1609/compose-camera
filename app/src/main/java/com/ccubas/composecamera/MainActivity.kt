package com.ccubas.composecamera

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ccubas.camera.rememberMediaCameraLauncher
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
    val picker = rememberMediaCameraLauncher(
        config = MediaCameraConfig(
            saveToMediaStore = false,
        )
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (capturedMedia.isNullOrEmpty()) {
            Text("Aún no has capturado nada.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("Media Capturada:", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp)) {
                items(capturedMedia ?: listOf()) { uri ->
                    Column(modifier = Modifier.padding(8.dp)) {
                        AsyncImage(model = uri, contentDescription = null)
                        Text(uri.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Button(onClick = { picker.launch { uris ->
            capturedMedia = uris
        } }, modifier = Modifier.padding(16.dp)) {
            Text("Abrir Cámara")
        }
    }
}