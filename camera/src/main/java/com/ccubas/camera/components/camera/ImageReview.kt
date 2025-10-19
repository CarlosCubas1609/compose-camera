package com.ccubas.camera.components.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ccubas.camera.components.CropperFullScreen

@Composable
fun ImageReviewWithCropOverlay(
    src: Bitmap,
    onClose: () -> Unit = {},
    onUse: (Bitmap) -> Unit = {},
    aspect: Float? = null
) {
    var showCropper by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .zIndex(1f)
    ) {
        // Simple preview (without cropping)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(src).build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Top: close / crop
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            IconButton(
                onClick = { showCropper = true },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Outlined.Crop, null, tint = Color.White)
            }
        }

        // Bottom: use (original by default)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Button(onClick = { onUse(src) }) {
                Text("Usar foto")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Check, null)
            }
        }
    }

    // Full-screen modal cropper (locked)
    if (showCropper) {
        CropperFullScreen(
            src = src,
            onCancel = { showCropper = false },
            onCropped = { bitmap -> onUse(bitmap) },
            aspect = aspect
        )
    }
}

// Preview disabled - requires actual Bitmap
// @Preview(showBackground = true, widthDp = 400, heightDp = 600)
// @Composable
// fun ImageReviewPreview() {
//     ImageReviewWithCropOverlay(
//         src = "content://media/sample_image.jpg".toUri(),
//         onClose = {},
//         onUse = {}
//     )
// }

// Preview disabled - requires actual Bitmap
// @Preview(showBackground = true, widthDp = 400, heightDp = 600)
// @Composable
// fun ImageReviewWithCropperPreview() {
//     var showCropper by remember { mutableStateOf(true) }
//
//     Box(modifier = Modifier.fillMaxSize()) {
//         ImageReviewWithCropOverlay(
//             src = "content://media/sample_image.jpg".toUri(),
//             onClose = {},
//             onUse = {}
//         )
//
//         // Mock cropper overlay for preview
/*        if (showCropper) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cropper Interface",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    }*/ // End commented preview
// } // End ImageReviewWithCropperPreview