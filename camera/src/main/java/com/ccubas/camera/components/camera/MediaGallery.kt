package com.ccubas.camera.components.camera

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaGallery(
    galleryItems: List<MediaThumbnail>,
    selectedUris: List<Uri>,
    config: MediaCameraConfig,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onToggleSelect: (Uri) -> Unit,
    onSendSelection: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var galleryFilter by remember { mutableStateOf("Recientes") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = { WindowInsets.navigationBarsIgnoringVisibility }
    ) {
        Scaffold(
            topBar = {
                GalleryTopBar(
                    currentFilter = galleryFilter,
                    config = config,
                    onFilterChange = { galleryFilter = it },
                    onDismiss = onDismiss
                )
            },
            bottomBar = {
                GalleryBottomBar(
                    selectedCount = selectedUris.size,
                    maxSelection = config.maxSelection,
                    onSendSelection = { onSendSelection(selectedUris) }
                )
            }
        ) { innerPadding ->
            val gridItems = remember(galleryFilter, galleryItems) {
                when (galleryFilter) {
                    "Fotos" -> galleryItems.filter { !it.isVideo }
                    "Videos" -> galleryItems.filter { it.isVideo }
                    else -> galleryItems
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = innerPadding.calculateTopPadding())
                    .consumeWindowInsets(innerPadding)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    itemsIndexed(
                        items = gridItems,
                        key = { _, thumbnail -> thumbnail.uri.toString() }
                    ) { _, thumbnail ->
                        GalleryGridItem(
                            thumbnail = thumbnail,
                            isSelected = thumbnail.uri in selectedUris,
                            imageLoader = imageLoader,
                            onToggleSelect = { onToggleSelect(thumbnail.uri) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    currentFilter: String,
    config: MediaCameraConfig,
    onFilterChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    CenterAlignedTopAppBar(
        title = {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showMenu = true }
                ) {
                    Text(currentFilter, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Filter")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Recientes") },
                        onClick = { onFilterChange("Recientes"); showMenu = false }
                    )
                    if (config.mediaType != MediaType.VIDEO_ONLY) {
                        DropdownMenuItem(
                            text = { Text("Fotos") },
                            onClick = { onFilterChange("Fotos"); showMenu = false }
                        )
                    }
                    if (config.mediaType != MediaType.PHOTO_ONLY) {
                        DropdownMenuItem(
                            text = { Text("Videos") },
                            onClick = { onFilterChange("Videos"); showMenu = false }
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close"
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        windowInsets = WindowInsets(0)
    )
}

@Composable
private fun GalleryBottomBar(
    selectedCount: Int,
    maxSelection: Int,
    onSendSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = onSendSelection,
            enabled = selectedCount > 0
        ) {
            val maxText = if (maxSelection == Int.MAX_VALUE) "" else "/$maxSelection"
            Text("Añadir ($selectedCount$maxText)")
        }
    }
}

@Composable
private fun GalleryGridItem(
    thumbnail: MediaThumbnail,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    onToggleSelect: () -> Unit
) {
    val ctx = LocalContext.current
    
    val request = if (thumbnail.isVideo) {
        ImageRequest.Builder(ctx)
            .data(thumbnail.uri)
            .setParameter(VideoFrameDecoder.VIDEO_FRAME_MICROS_KEY, 1_000_000L)
            .build()
    } else {
        ImageRequest.Builder(ctx)
            .data(thumbnail.uri)
            .build()
    }

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x11000000))
            .clickable { onToggleSelect() }
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            )
            Box(
                Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun MediaGalleryContentPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }
    
    val mockGalleryItems = remember {
        listOf(
            MediaThumbnail("content://media/1".toUri(), false),
            MediaThumbnail("content://media/2".toUri(), true),
            MediaThumbnail("content://media/3".toUri(), false),
            MediaThumbnail("content://media/4".toUri(), true),
            MediaThumbnail("content://media/5".toUri(), false),
            MediaThumbnail("content://media/6".toUri(), false),
        )
    }
    
    val selectedUris = remember {
        listOf(
            "content://media/2".toUri(),
            "content://media/4".toUri()
        )
    }

    val config = MediaCameraConfig()

    // Preview del contenido del Scaffold sin el ModalBottomSheet
    Scaffold(
        topBar = {
            GalleryTopBar(
                currentFilter = "Recientes",
                config = config,
                onFilterChange = {},
                onDismiss = {}
            )
        },
        bottomBar = {
            GalleryBottomBar(
                selectedCount = selectedUris.size,
                maxSelection = config.maxSelection,
                onSendSelection = {}
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                itemsIndexed(
                    items = mockGalleryItems,
                    key = { _, thumbnail -> thumbnail.uri.toString() }
                ) { _, thumbnail ->
                    GalleryGridItem(
                        thumbnail = thumbnail,
                        isSelected = thumbnail.uri in selectedUris,
                        imageLoader = imageLoader,
                        onToggleSelect = {}
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 60)
@Composable
fun GalleryTopBarPreview() {
    GalleryTopBar(
        currentFilter = "Recientes",
        config = MediaCameraConfig(),
        onFilterChange = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 80)
@Composable
fun GalleryBottomBarPreview() {
    GalleryBottomBar(
        selectedCount = 3,
        maxSelection = 10,
        onSendSelection = {}
    )
}

@Preview(showBackground = true, widthDp = 120, heightDp = 240)
@Composable
fun GalleryGridItemPreview() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Item no seleccionado
        GalleryGridItem(
            thumbnail = MediaThumbnail("content://media/1".toUri(), false),
            isSelected = false,
            imageLoader = imageLoader,
            onToggleSelect = {}
        )
        
        // Item seleccionado
        GalleryGridItem(
            thumbnail = MediaThumbnail("content://media/2".toUri(), true),
            isSelected = true,
            imageLoader = imageLoader,
            onToggleSelect = {}
        )
    }
}