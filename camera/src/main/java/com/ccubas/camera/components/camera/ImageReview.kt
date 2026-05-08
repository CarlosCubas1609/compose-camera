package com.ccubas.camera.components.camera

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.ccubas.camera.components.editor.ImageEditorScreen

@Composable
fun ImageReviewWithCropOverlay(
    src: Bitmap,
    onClose: () -> Unit = {},
    onUse: (Bitmap) -> Unit = {},
    aspect: Float? = null
) {
    ImageEditorScreen(
        src = src,
        onClose = onClose,
        onUse = onUse,
        aspect = aspect
    )
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