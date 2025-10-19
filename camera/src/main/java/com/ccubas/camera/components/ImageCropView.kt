package com.ccubas.camera.components


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Rect as UiRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Represents the hit area on the crop rectangle's handles or body.
 */
enum class Hit { NONE, LT, RT, LB, RB, T, B, L, R, BODY }

/**
 * State holder for the [ImageCropper] composable, managing the image, crop rectangle, and user interactions.
 *
 * @param context The application context.
 * @param imageUri The URI of the image to be cropped.
 * @param startAspect The initial aspect ratio for the crop rectangle, if any.
 */
@Stable
class CropperState(private val context: Context, private val imageUri: Uri, startAspect: Float? = null) {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    private var viewSize by mutableStateOf(IntSize.Zero)
    private var bmpW by mutableIntStateOf(0)
    private var bmpH by mutableIntStateOf(0)

    /**
     * The current cropping rectangle in the view's coordinates.
     */
    var cropRect by mutableStateOf(UiRect(0f, 0f, 0f, 0f))
        internal set

    /**
     * The fixed aspect ratio for the crop rectangle. If null, the aspect ratio is free.
     */
    var aspectRatio: Float? by mutableStateOf(startAspect)

    /**
     * Decodes the bitmap from the given [imageUri], correcting its orientation based on EXIF data.
     *
     * @param forceSoftware Whether to force software rendering for the bitmap.
     * @return The decoded and oriented [Bitmap], or null if decoding fails.
     */
    private fun decodeOrientedBitmap(forceSoftware: Boolean): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, imageUri)
            ImageDecoder.decodeBitmap(src) { dec, _, _ ->
                if (forceSoftware) dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                dec.isMutableRequired = false
            }
        } else {
            val raw = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream)
                ?: return null
            val exif = context.contentResolver.openInputStream(imageUri)?.use(::ExifInterface)
            val m = android.graphics.Matrix().apply {
                when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL   -> preScale(1f, -1f)
                }
            }
            if (!m.isIdentity) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { raw.recycle() }
            else raw
        }
    } catch (_: Exception) { null }

    /**
     * The loaded bitmap, derived state from the [imageUri].
     */
    val bitmap: Bitmap? by derivedStateOf {
        decodeOrientedBitmap(forceSoftware = false)
    }

    /**
     * Calculates the largest possible rectangle with a given aspect ratio that fits inside a bounding box.
     */
    private fun maxRectInside(b: UiRect, ar: Float): UiRect {
        val bw = b.width; val bh = b.height
        val cx = (b.left + b.right) / 2f
        val cy = (b.top + b.bottom) / 2f
        val (w, h) = if (bw / bh > ar) bh * ar to bh else bw to bw / ar
        return UiRect(cx - w/2, cy - h/2, cx + w/2, cy + h/2)
    }

    /**
     * Callback to be invoked when the size of the [ImageCropper] composable changes.
     *
     * @param size The new size of the composable.
     */
    fun onSizeChanged(size: IntSize) {
        viewSize = size
        bitmap?.let {
            bmpW = it.width; bmpH = it.height
            // reset before calculating bounds
            scale = 1f; offset = Offset.Zero

            val b = contentBounds()                // full visible image
            cropRect = aspectRatio?.let { ar ->    // if there's an aspect ratio, max rect with that AR
                maxRectInside(b, ar)
            } ?: b
        }
    }

    /**
     * Calculates the visible bounds of the bitmap within the view, considering pan and zoom.
     */
    private fun contentBounds(): UiRect {
        if (viewSize.width == 0 || viewSize.height == 0 || bmpW == 0 || bmpH == 0) {
            return UiRect(0f, 0f, 0f, 0f)
        }
        val viewW = viewSize.width.toFloat()
        val viewH = viewSize.height.toFloat()
        val bmpAspect = bmpW.toFloat() / bmpH.toFloat()
        val viewAspect = viewW / viewH

        val (visualW, visualH) =
            if (bmpAspect > viewAspect) viewW to (viewW / bmpAspect)
            else (viewH * bmpAspect) to viewH

        val w = visualW * scale
        val h = visualH * scale
        val x = (viewW - w) / 2f + offset.x
        val y = (viewH - h) / 2f + offset.y
        return UiRect(x, y, x + w, y + h)
    }

    /**
     * Moves the crop rectangle within the bounds of the displayed image.
     */
    private fun moveWithin(r: UiRect, drag: Offset): UiRect {
        val b = contentBounds()
        var dx = drag.x
        var dy = drag.y
        if (r.left + dx < b.left)   dx = b.left - r.left
        if (r.right + dx > b.right) dx = b.right - r.right
        if (r.top + dy < b.top)     dy = b.top - r.top
        if (r.bottom + dy > b.bottom) dy = b.bottom - r.bottom
        return UiRect(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
    }

    /**
     * Resizes the crop rectangle based on user drag gestures, optionally maintaining a fixed aspect ratio.
     *
     * @param r The current crop rectangle.
     * @param hit The area of the rectangle that was hit (e.g., a corner or an edge).
     * @param drag The drag offset.
     * @param aspect The aspect ratio to maintain, or null for freeform resizing.
     * @return The new, resized crop rectangle.
     */
    fun resizeWithAspect(
        r: UiRect,
        hit: Hit,
        drag: Offset,
        aspect: Float?
    ): UiRect {
        val b = contentBounds()
        val minSide = 64f

        if (hit == Hit.NONE || hit == Hit.BODY) return moveWithin(r, drag)

        fun clampL(x: Float, right: Float) = x.coerceIn(b.left, right - minSide)
        fun clampT(y: Float, bottom: Float) = y.coerceIn(b.top, bottom - minSide)
        fun clampR(x: Float, left: Float) = x.coerceIn(left + minSide, b.right)
        fun clampB(y: Float, top: Float) = y.coerceIn(top + minSide, b.bottom)

        if (aspect == null) {
            return when (hit) {
                Hit.LT -> UiRect(clampL(r.left + drag.x, r.right), clampT(r.top + drag.y, r.bottom), r.right, r.bottom)
                Hit.RT -> UiRect(r.left, clampT(r.top + drag.y, r.bottom), clampR(r.right + drag.x, r.left), r.bottom)
                Hit.LB -> UiRect(clampL(r.left + drag.x, r.right), r.top, r.right, clampB(r.bottom + drag.y, r.top))
                Hit.RB -> UiRect(r.left, r.top, clampR(r.right + drag.x, r.left), clampB(r.bottom + drag.y, r.top))
                Hit.T  -> UiRect(r.left, clampT(r.top + drag.y, r.bottom), r.right, r.bottom)
                Hit.B  -> UiRect(r.left, r.top, r.right, clampB(r.bottom + drag.y, r.top))
                Hit.L  -> UiRect(clampL(r.left + drag.x, r.right), r.top, r.right, r.bottom)
                Hit.R  -> UiRect(r.left, r.top, clampR(r.right + drag.x, r.left), r.bottom)
                else   -> r
            }
        }

        // With fixed aspect ratio
        val ar = aspect
        fun corner(anchorX: Float, anchorY: Float, moveLeft: Boolean, moveTop: Boolean, dx: Float, dy: Float): UiRect {
            val signX = if (moveLeft) -1f else 1f
            val signY = if (moveTop) -1f else 1f

            var w = abs(if (moveLeft) (anchorX - (r.left + dx)) else ((r.right + dx) - anchorX))
            var h = abs(if (moveTop) (anchorY - (r.top + dy)) else ((r.bottom + dy) - anchorY))
            if (w / max(h, 1f) > ar) h = w / ar else w = h * ar

            val maxW = if (signX < 0) (anchorX - b.left) else (b.right - anchorX)
            val maxH = if (signY < 0) (anchorY - b.top) else (b.bottom - anchorY)

            w = w.coerceIn(minSide, maxW)
            h = (w / ar).coerceAtLeast(minSide)
            if (h > maxH) { h = maxH; w = (h * ar).coerceAtLeast(minSide) }

            val left   = if (signX < 0) anchorX - w else anchorX
            val right  = if (signX < 0) anchorX else anchorX + w
            val top    = if (signY < 0) anchorY - h else anchorY
            val bottom = if (signY < 0) anchorY else anchorY + h
            return UiRect(left, top, right, bottom)
        }

        fun edgeVertical(anchorY: Float, moveTop: Boolean, dy: Float): UiRect {
            val signY = if (moveTop) -1f else 1f
            var h = abs(if (moveTop) (anchorY - (r.top + dy)) else ((r.bottom + dy) - anchorY))
            h = h.coerceAtLeast(minSide / ar)
            val maxH = if (signY < 0) (anchorY - b.top) else (b.bottom - anchorY)
            h = h.coerceAtMost(maxH)
            var w = h * ar
            val cx = (r.left + r.right) / 2f
            val maxW = 2f * min(cx - b.left, b.right - cx)
            if (w > maxW) { w = maxW; h = w / ar }
            val left = (cx - w / 2f).coerceAtLeast(b.left)
            val right = (cx + w / 2f).coerceAtMost(b.right)
            val top = if (signY < 0) anchorY - h else anchorY
            val bottom = if (signY < 0) anchorY else anchorY + h
            return UiRect(left, top, right, bottom)
        }

        fun edgeHorizontal(anchorX: Float, moveLeft: Boolean, dx: Float): UiRect {
            val signX = if (moveLeft) -1f else 1f
            var w = abs(if (moveLeft) (anchorX - (r.left + dx)) else ((r.right + dx) - anchorX))
            w = w.coerceAtLeast(minSide)
            val maxW = if (signX < 0) (anchorX - b.left) else (b.right - anchorX)
            w = w.coerceAtMost(maxW)
            var h = w / ar
            val cy = (r.top + r.bottom) / 2f
            val maxH = 2f * min(cy - b.top, b.bottom - cy)
            if (h > maxH) { h = maxH; w = h * ar }
            val top = (cy - h / 2f).coerceAtLeast(b.top)
            val bottom = (cy + h / 2f).coerceAtMost(b.bottom)
            val left = if (signX < 0) anchorX - w else anchorX
            val right = if (signX < 0) anchorX else anchorX + w
            return UiRect(left, top, right, bottom)
        }

        return when (hit) {
            Hit.LT -> corner(r.right, r.bottom, moveLeft = true,  moveTop = true,  dx = drag.x, dy = drag.y)
            Hit.RT -> corner(r.left,  r.bottom, moveLeft = false, moveTop = true,  dx = drag.x, dy = drag.y)
            Hit.LB -> corner(r.right, r.top,    moveLeft = true,  moveTop = false, dx = drag.x, dy = drag.y)
            Hit.RB -> corner(r.left,  r.top,    moveLeft = false, moveTop = false, dx = drag.x, dy = drag.y)
            Hit.T  -> edgeVertical(anchorY = r.bottom, moveTop = true,  dy = drag.y)
            Hit.B  -> edgeVertical(anchorY = r.top,    moveTop = false, dy = drag.y)
            Hit.L  -> edgeHorizontal(anchorX = r.right, moveLeft = true,  dx = drag.x)
            Hit.R  -> edgeHorizontal(anchorX = r.left,  moveLeft = false, dx = drag.x)
            else   -> r
        }
    }

    /**
     * Crops the image based on the current [cropRect], saves it to a temporary file, and returns its URI.
     *
     * @return The [Uri] of the cropped image file, or null if cropping fails.
     */
    suspend fun crop(): Uri? = withContext(Dispatchers.IO) {
        val base = decodeOrientedBitmap(forceSoftware = true) ?: return@withContext null
        if (viewSize.width == 0 || viewSize.height == 0) return@withContext null

        val bmpW = base.width
        val bmpH = base.height

        // Map view coordinates to bitmap coordinates
        val viewW = viewSize.width.toFloat()
        val viewH = viewSize.height.toFloat()
        val (visualW, visualH) =
            if (bmpW.toFloat() / bmpH > viewW / viewH) viewW to (viewW * bmpH / bmpW)
            else (viewH * bmpW / bmpH) to viewH

        val finalW = visualW * scale
        val finalH = visualH * scale
        if (finalW <= 1f || finalH <= 1f) { base.recycle(); return@withContext null }
        val finalX = (viewW - finalW) / 2f + offset.x
        val finalY = (viewH - finalH) / 2f + offset.y

        val l = ((cropRect.left   - finalX) / finalW) * bmpW
        val t = ((cropRect.top    - finalY) / finalH) * bmpH
        val r = ((cropRect.right  - finalX) / finalW) * bmpW
        val b = ((cropRect.bottom - finalY) / finalH) * bmpH

        // Clamp strictly and create sub-bitmap safely
        val left   = floor(l).toInt().coerceIn(0, bmpW - 1)
        val top    = floor(t).toInt().coerceIn(0, bmpH - 1)
        val right  = ceil(r).toInt().coerceIn(left + 1, bmpW)
        val bottom = ceil(b).toInt().coerceIn(top + 1, bmpH)

        val outBmp = try {
            Bitmap.createBitmap(base, left, top, right - left, bottom - top)
        } catch (_: Throwable) {
            base.recycle(); return@withContext null
        }
        base.recycle()

        // Save the cropped bitmap
        val out = File(context.cacheDir, "CROP_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { outBmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        Uri.fromFile(out)
    }
}

/**
 * Creates and remembers a [CropperState] for the [ImageCropper].
 *
 * @param imageUri The URI of the image to be cropped.
 * @param aspectRatio The initial aspect ratio for the crop rectangle, if any.
 * @return A remembered [CropperState] instance.
 */
@Composable
fun rememberCropperState(imageUri: Uri, aspectRatio: Float? = null): CropperState {
    val ctx = LocalContext.current
    return remember(imageUri, ctx, aspectRatio) { CropperState(ctx, imageUri, aspectRatio) }
}

/**
 * A composable that displays an image with a draggable and resizable crop overlay.
 *
 * @param state The [CropperState] that holds the state of the cropper.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun ImageCropper(
    state: CropperState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.onSizeChanged(it) }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    state.scale = (state.scale * zoom).coerceIn(0.2f, 10f)
                    state.offset += pan
                }
            }
    ) {
        state.bitmap?.let { bmp ->
            Canvas(Modifier.fillMaxSize()) {
                val r = state.cropRect

                val viewW = size.width
                val viewH = size.height
                val bmpW = bmp.width.toFloat()
                val bmpH = bmp.height.toFloat()
                val bmpAspect = bmpW / bmpH
                val viewAspect = viewW / viewH
                val (visualW, visualH) =
                    if (bmpAspect > viewAspect) viewW to (viewW / bmpAspect)
                    else (viewH * bmpAspect) to viewH

                val dstW = visualW * state.scale
                val dstH = visualH * state.scale
                val dstX = (viewW - dstW) / 2f + state.offset.x
                val dstY = (viewH - dstH) / 2f + state.offset.y

                drawImage(
                    image = bmp.asImageBitmap(),
                    dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
                    dstSize   = IntSize(dstW.toInt(), dstH.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )

                val mask = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, viewW, viewH))
                    addRect(Rect(r.left, r.top, r.right, r.bottom))
                }
                drawPath(path = mask, color = Color.Black.copy(alpha = 0.55f))

                drawRect(
                    color = Color.White,
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width, r.height),
                    style = Stroke(width = 2.dp.toPx())
                )

                val hs = 16.dp.toPx()
                fun handle(cx: Float, cy: Float) =
                    drawRect(Color.White, Offset(cx - hs/2, cy - hs/2), Size(hs, hs))
                handle(r.left, r.top); handle(r.right, r.top)
                handle(r.left, r.bottom); handle(r.right, r.bottom)
                handle((r.left + r.right)/2, r.top); handle((r.left + r.right)/2, r.bottom)
                handle(r.left, (r.top + r.bottom)/2); handle(r.right, (r.top + r.bottom)/2)
            }
            CropGestureLayer(state)
        }
    }
}

/**
 * A full-screen composable for cropping an image (Bitmap version).
 *
 * @param src The Bitmap of the source image to crop.
 * @param onCancel Callback invoked when the user cancels the crop operation.
 * @param onCropped Callback invoked with the Bitmap of the cropped image when the user confirms.
 * @param aspect The fixed aspect ratio to use for cropping, or null for freeform.
 */
@Composable
fun CropperFullScreen(
    src: Bitmap,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit,
    aspect: Float? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Create temporary Uri from Bitmap to pass to existing cropper state
    val tempUri = remember(src) {
        val tempFile = File(context.cacheDir, "crop_temp_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            src.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        Uri.fromFile(tempFile)
    }

    val state = rememberCropperState(imageUri = tempUri, aspectRatio = aspect)
    BackHandler(enabled = true) { /* block back press */ }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .zIndex(3f)
    ) {
        ImageCropper(state = state, modifier = Modifier.fillMaxSize())

        // Top bar
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White)
            }
        }

        // "Use" button
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Button(onClick = {
                scope.launch {
                    val croppedUri = state.crop()
                    if (croppedUri != null) {
                        // Convert cropped Uri back to Bitmap
                        val inputStream = context.contentResolver.openInputStream(croppedUri)
                        val croppedBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()

                        // Delete temp files
                        File(tempUri.path!!).delete()
                        File(croppedUri.path!!).delete()

                        onCropped(croppedBitmap)
                    }
                }
            }) {
                Text("Usar")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Check, null)
            }
        }
    }
}


/**
 * A private composable that handles drag gestures for resizing the crop rectangle.
 *
 * @param state The [CropperState] that holds the crop rectangle state.
 */
@Composable
private fun CropGestureLayer(state: CropperState) {
    val padPx = with(LocalDensity.current) { 24.dp.toPx() }
    val active = remember { mutableStateOf(Hit.NONE) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state, padPx) {
                detectDragGestures(
                    onDragStart = { pos -> active.value = hitTest(state.cropRect, pos, padPx) },
                    onDrag = { change, drag ->
                        val r = state.cropRect
                        val a = active.value
                        state.cropRect = state.resizeWithAspect(r, a, drag, state.aspectRatio)
                        change.consume()
                    },
                    onDragEnd = { active.value = Hit.NONE }
                )
            }
    )
}

/**
 * A private helper function to determine which part of the crop rectangle was touched.
 *
 * @param r The crop rectangle.
 * @param p The position of the touch event.
 * @param pad The touch padding around the handles.
 * @return The [Hit] value representing the touched area.
 */
private fun hitTest(r: UiRect, p: Offset, pad: Float): Hit {
    fun near(x: Float, y: Float) = abs(p.x - x) <= pad && abs(p.y - y) <= pad
    return when {
        near(r.left, r.top) -> Hit.LT
        near(r.right, r.top) -> Hit.RT
        near(r.left, r.bottom) -> Hit.LB
        near(r.right, r.bottom) -> Hit.RB
        abs(p.y - r.top) <= pad && p.x in r.left - pad..r.right + pad -> Hit.T
        abs(p.y - r.bottom) <= pad && p.x in r.left - pad..r.right + pad -> Hit.B
        abs(p.x - r.left) <= pad && p.y in r.top - pad..r.bottom + pad -> Hit.L
        abs(p.x - r.right) <= pad && p.y in r.top - pad..r.bottom + pad -> Hit.R
        p.x in r.left..r.right && p.y in r.top..r.bottom -> Hit.BODY
        else -> Hit.NONE
    }
}