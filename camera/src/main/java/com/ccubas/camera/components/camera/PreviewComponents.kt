package com.ccubas.camera.components.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/**
 * Composable de prueba que muestra todos los componentes de preview
 * en una interfaz de demostración.
 */
@Composable
fun PreviewComponentsDemo() {
    var showImageReview by remember { mutableStateOf(false) }
    var showVideoReview by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Preview Components Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(
            onClick = { showImageReview = true }
        ) {
            Text("Show Image Review")
        }

        Button(
            onClick = { showVideoReview = true }
        ) {
            Text("Show Video Review")
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Click buttons to test preview components",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }

    // Image Review Overlay (disabled - requires actual Bitmap)
//    if (showImageReview) {
//        ImageReviewWithCropOverlay(
//            src = "content://media/sample_image.jpg".toUri(),
//            onClose = { showImageReview = false },
//            onUse = { showImageReview = false }
//        )
//    }

    // Video Review Overlay
    if (showVideoReview) {
        VideoReviewOverlay(
            src = "content://media/sample_video.mp4".toUri(),
            onClose = { showVideoReview = false },
            onSaveTrim = { _, _ -> showVideoReview = false }
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun PreviewComponentsDemoPreview() {
    MaterialTheme {
        PreviewComponentsDemo()
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun ImageReviewDemoPreview() {
    var showCropper by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (showCropper) {
            // Mock del ImageReview sin dependencias externas
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { showCropper = false }
                        ) {
                            Text("✕", color = Color.White)
                        }
                        IconButton(onClick = {}) {
                            Text("⚆", color = Color.White)
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Image Preview", color = Color.White)
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { showCropper = false }) {
                            Text("Usar foto")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = { showCropper = true }) {
                    Text("Show Image Review")
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun VideoReviewDemoPreview() {
    var showVideoReview by remember { mutableStateOf(false) }
    
    if (showVideoReview) {
        // Mock del VideoReview
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { showVideoReview = false }
                ) {
                    Text("✕", color = Color.White)
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Video Player", color = Color.White)
                }
                
                var mockRange by remember { mutableStateOf(5f..25f) }
                
                RangeSlider(
                    value = mockRange,
                    onValueChange = { mockRange = it },
                    valueRange = 0f..60f
                )
                
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { showVideoReview = false }) {
                        Text("Usar")
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = { showVideoReview = true }) {
                Text("Show Video Review")
            }
        }
    }
}