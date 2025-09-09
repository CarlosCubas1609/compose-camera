package com.ccubas.camera.components.camera

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import androidx.core.net.toUri

data class MediaThumbnail(
    val uri: Uri,
    val isVideo: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCarousel(
    modifier: Modifier = Modifier,
    thumbnails: List<MediaThumbnail> = emptyList(),
    selectedUris: List<Uri> = emptyList(),
    imageLoader: ImageLoader,
    onItemClick: (Uri, Boolean) -> Unit = { _, _ -> },
    onItemLongClick: (Uri) -> Unit = {},
    onSwipeUp: () -> Unit = {}
) {
    val swipeUpThreshold = remember { 60f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, drag ->
                        if (drag < -swipeUpThreshold) {
                            onSwipeUp()
                        }
                    }
                }
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            itemsIndexed(
                items = thumbnails,
                key = { _, thumbnail -> thumbnail.uri.toString() }
            ) { _, thumbnail ->
                MediaThumbnailItem(
                    thumbnail = thumbnail,
                    isSelected = thumbnail.uri in selectedUris,
                    imageLoader = imageLoader,
                    onClick = { onItemClick(thumbnail.uri, thumbnail.isVideo) },
                    onLongClick = { onItemLongClick(thumbnail.uri) }
                )
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumbnailItem(
    thumbnail: MediaThumbnail,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ctx = LocalContext.current
    
    val request = if (thumbnail.isVideo) {
        ImageRequest.Builder(ctx)
            .data(thumbnail.uri)
            .setParameter(VideoFrameDecoder.VIDEO_FRAME_MICROS_KEY, 1_000_000L) // 1s
            .setParameter(
                VideoFrameDecoder.VIDEO_FRAME_OPTION_KEY,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            .build()
    } else {
        ImageRequest.Builder(ctx).data(thumbnail.uri).build()
    }

    Box(
        Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x33000000))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        if (thumbnail.isVideo) {
            Icon(
                Icons.Outlined.Videocam, 
                null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(16.dp),
                tint = Color.White
            )
        }
        
        if (isSelected) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = .35f))
            )
            Box(
                Modifier
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun MediaCarouselPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }
    
    // Mock thumbnails for preview
    val mockThumbnails = remember {
        listOf(
            MediaThumbnail("content://media/1".toUri(), false),
            MediaThumbnail("content://media/2".toUri(), true),
            MediaThumbnail("content://media/3".toUri(), false),
            MediaThumbnail("content://media/4".toUri(), true),
        )
    }
    
    val selectedUris = remember {
        listOf("content://media/2".toUri())
    }

    Column {
        MediaCarousel(
            thumbnails = mockThumbnails,
            selectedUris = selectedUris,
            imageLoader = imageLoader
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun MediaCarouselEmptyPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    MediaCarousel(
        thumbnails = emptyList(),
        selectedUris = emptyList(),
        imageLoader = imageLoader
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun MediaCarouselWithFloatingButtonPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }
    
    val mockThumbnails = remember {
        listOf(
            MediaThumbnail("content://media/1".toUri(), false),
            MediaThumbnail("content://media/2".toUri(), true),
            MediaThumbnail("content://media/3".toUri(), false),
            MediaThumbnail("content://media/4".toUri(), true),
        )
    }
    
    val selectedUris = remember {
        listOf(
            "content://media/2".toUri(),
            "content://media/4".toUri(),
            "content://media/1".toUri()
        )
    }

    Column {
        MediaCarousel(
            thumbnails = mockThumbnails,
            selectedUris = selectedUris,
            imageLoader = imageLoader
        )
    }
}