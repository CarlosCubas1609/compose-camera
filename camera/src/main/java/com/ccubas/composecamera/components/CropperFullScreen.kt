package com.ccubas.composecamera.components

import android.graphics.*
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen image cropper component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropperFullScreen(
    src: Uri,
    onCancel: () -> Unit,
    onCropped: (Uri) -> Unit,
    aspect: Float? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    
    var cropRect by remember { 
        mutableStateOf(
            Rect(
                offset = Offset(50f, 100f),
                size = Size(300f, 300f)
            )
        ) 
    }
    
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var isProcessing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20000000f),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image
            AsyncImage(
                model = ImageRequest.Builder(context).data(src).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { result ->
                    val drawable = result.drawable
                    imageSize = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
                    
                    // Initialize crop rect to center
                    val screenWidth = configuration.screenWidthDp * density.density
                    val screenHeight = configuration.screenHeightDp * density.density
                    val cropSize = min(screenWidth, screenHeight) * 0.7f
                    
                    cropRect = Rect(
                        offset = Offset(
                            (screenWidth - cropSize) / 2f,
                            (screenHeight - cropSize) / 2f
                        ),
                        size = Size(cropSize, cropSize)
                    )
                }
            )
            
            // Crop overlay
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            val newRect = Rect(
                                offset = Offset(
                                    (cropRect.left + dragAmount.x).coerceIn(0f, size.width - cropRect.width),
                                    (cropRect.top + dragAmount.y).coerceIn(0f, size.height - cropRect.height)
                                ),
                                size = cropRect.size
                            )
                            cropRect = newRect
                        }
                    }
            ) {
                drawCropOverlay(cropRect)
            }
            
            // Top controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, null, tint = Color.White)
                }
                
                IconButton(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            scope.launch {
                                val croppedUri = cropImage(context, src, cropRect, imageSize)
                                croppedUri?.let { onCropped(it) }
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.Check, null, tint = Color.White)
                    }
                }
            }
            
            // Instructions
            Text(
                "Drag to position the crop area",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            )
        }
    }
}

private fun DrawScope.drawCropOverlay(cropRect: Rect) {
    // Darken outside area
    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        size = Size(size.width, cropRect.top)
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(0f, cropRect.bottom),
        size = Size(size.width, size.height - cropRect.bottom)
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(0f, cropRect.top),
        size = Size(cropRect.left, cropRect.height)
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(cropRect.right, cropRect.top),
        size = Size(size.width - cropRect.right, cropRect.height)
    )
    
    // Draw crop border
    drawRect(
        color = Color.White,
        topLeft = cropRect.topLeft,
        size = cropRect.size,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
    
    // Draw grid lines
    val gridColor = Color.White.copy(alpha = 0.5f)
    val gridStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    
    // Vertical lines
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left + cropRect.width / 3, cropRect.top),
        end = Offset(cropRect.left + cropRect.width / 3, cropRect.bottom),
        strokeWidth = gridStroke.width
    )
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left + cropRect.width * 2 / 3, cropRect.top),
        end = Offset(cropRect.left + cropRect.width * 2 / 3, cropRect.bottom),
        strokeWidth = gridStroke.width
    )
    
    // Horizontal lines
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, cropRect.top + cropRect.height / 3),
        end = Offset(cropRect.right, cropRect.top + cropRect.height / 3),
        strokeWidth = gridStroke.width
    )
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, cropRect.top + cropRect.height * 2 / 3),
        end = Offset(cropRect.right, cropRect.top + cropRect.height * 2 / 3),
        strokeWidth = gridStroke.width
    )
}

private suspend fun cropImage(
    context: android.content.Context,
    sourceUri: Uri,
    cropRect: Rect,
    originalSize: IntSize
): Uri? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        
        // Calculate crop coordinates relative to original image
        val scaleX = bitmap.width.toFloat() / originalSize.width
        val scaleY = bitmap.height.toFloat() / originalSize.height
        
        val cropX = (cropRect.left * scaleX).toInt().coerceIn(0, bitmap.width)
        val cropY = (cropRect.top * scaleY).toInt().coerceIn(0, bitmap.height)
        val cropWidth = (cropRect.width * scaleX).toInt().coerceAtMost(bitmap.width - cropX)
        val cropHeight = (cropRect.height * scaleY).toInt().coerceAtMost(bitmap.height - cropY)
        
        val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
        bitmap.recycle()
        
        // Save cropped image
        val tempFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(tempFile)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        croppedBitmap.recycle()
        
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}