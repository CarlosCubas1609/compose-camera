package com.ccubas.camera.components.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ccubas.camera.components.CropperFullScreen
import com.ccubas.camera.components.MinimalSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ===== MODEL =====

private var nextAnnoId: Long = 1L

sealed class Annotation {
    abstract val id: Long
    abstract val color: Color
    abstract val strokeWidthBmp: Float

    data class RectAnno(
        override val id: Long = nextAnnoId++,
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        override val color: Color,
        override val strokeWidthBmp: Float
    ) : Annotation()

    data class CircleAnno(
        override val id: Long = nextAnnoId++,
        var cx: Float,
        var cy: Float,
        var rx: Float,
        var ry: Float,
        override val color: Color,
        override val strokeWidthBmp: Float
    ) : Annotation()

    data class StrokeAnno(
        override val id: Long = nextAnnoId++,
        val points: MutableList<Offset> = mutableListOf(),
        override val color: Color,
        override val strokeWidthBmp: Float
    ) : Annotation()
}

enum class EditorTool { Crop, Square, Circle, Pencil }

private enum class HandleHit { NONE, BODY, LT, RT, LB, RB }

@Stable
class EditorState {
    val annotations = mutableStateListOf<Annotation>()
    var tool by mutableStateOf(EditorTool.Pencil)
    var color by mutableStateOf(Color(0xFFFF3B30))
    /** Stroke width in view-pixels (UI semantic). Converted to bmp on creation. */
    var strokeWidthView by mutableFloatStateOf(8f)
    /** Id of the currently focused (movable/resizable) shape. Null = none. */
    var activeId by mutableStateOf<Long?>(null)

    fun undo() {
        if (annotations.isNotEmpty()) {
            val removed = annotations.removeAt(annotations.lastIndex)
            if (removed.id == activeId) activeId = null
        }
    }

    fun add(anno: Annotation) {
        annotations.add(anno)
        if (anno !is Annotation.StrokeAnno) activeId = anno.id
    }

    fun replace(index: Int, anno: Annotation) {
        annotations[index] = anno
    }

    fun delete(id: Long) {
        annotations.removeAll { it.id == id }
        if (activeId == id) activeId = null
    }
}

@Composable
fun rememberEditorState(): EditorState = remember { EditorState() }

// ===== COORD MAPPING =====

private data class ImgRect(
    val viewLeft: Float,
    val viewTop: Float,
    val viewW: Float,
    val viewH: Float,
    val bmpW: Int,
    val bmpH: Int
) {
    val bmpToViewScale: Float get() = if (bmpW > 0) viewW / bmpW else 1f
    val viewToBmpScale: Float get() = if (viewW > 0f) bmpW.toFloat() / viewW else 1f

    fun bmpToView(p: Offset) = Offset(
        viewLeft + p.x * bmpToViewScale,
        viewTop + p.y * bmpToViewScale
    )

    fun viewToBmp(p: Offset) = Offset(
        (p.x - viewLeft) * viewToBmpScale,
        (p.y - viewTop) * viewToBmpScale
    )

    /** True if the view-space point is inside the rendered image area. */
    fun containsView(p: Offset) =
        p.x in viewLeft..(viewLeft + viewW) && p.y in viewTop..(viewTop + viewH)
}

private fun fitImageRect(viewSize: IntSize, bmpW: Int, bmpH: Int): ImgRect {
    if (viewSize.width == 0 || viewSize.height == 0 || bmpW == 0 || bmpH == 0) {
        return ImgRect(0f, 0f, 0f, 0f, bmpW, bmpH)
    }
    val vw = viewSize.width.toFloat()
    val vh = viewSize.height.toFloat()
    val bAR = bmpW.toFloat() / bmpH
    val vAR = vw / vh
    val (w, h) = if (bAR > vAR) vw to (vw / bAR) else (vh * bAR) to vh
    val x = (vw - w) / 2f
    val y = (vh - h) / 2f
    return ImgRect(x, y, w, h, bmpW, bmpH)
}

// ===== HIT TEST =====

private fun rectFromAnno(r: Annotation.RectAnno) =
    androidx.compose.ui.geometry.Rect(
        min(r.left, r.right), min(r.top, r.bottom),
        max(r.left, r.right), max(r.top, r.bottom)
    )

private fun circleBoundsFromAnno(c: Annotation.CircleAnno) =
    androidx.compose.ui.geometry.Rect(
        c.cx - c.rx, c.cy - c.ry, c.cx + c.rx, c.cy + c.ry
    )

private fun hitTestHandle(
    bounds: androidx.compose.ui.geometry.Rect,
    pBmp: Offset,
    handlePadBmp: Float
): HandleHit {
    fun near(x: Float, y: Float) =
        abs(pBmp.x - x) <= handlePadBmp && abs(pBmp.y - y) <= handlePadBmp
    return when {
        near(bounds.left, bounds.top) -> HandleHit.LT
        near(bounds.right, bounds.top) -> HandleHit.RT
        near(bounds.left, bounds.bottom) -> HandleHit.LB
        near(bounds.right, bounds.bottom) -> HandleHit.RB
        pBmp.x in bounds.left..bounds.right && pBmp.y in bounds.top..bounds.bottom -> HandleHit.BODY
        else -> HandleHit.NONE
    }
}

// ===== EDITOR SCREEN =====

@Composable
fun ImageEditorScreen(
    src: Bitmap,
    onClose: () -> Unit = {},
    onUse: (Bitmap) -> Unit = {},
    aspect: Float? = null
) {
    var baseBitmap by remember(src) { mutableStateOf(src) }
    val state = rememberEditorState()
    val scope = rememberCoroutineScope()
    var showCropper by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }

    BackHandler(enabled = !showCropper) { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .zIndex(1f)
    ) {
        EditorCanvas(
            bitmap = baseBitmap,
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 200.dp)
        )

        // Top: close + undo
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
                onClick = { state.undo() },
                enabled = state.annotations.isNotEmpty(),
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Undo,
                    null,
                    tint = if (state.annotations.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
        }

        // Bottom: toolbar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 8.dp)
        ) {
            ColorPickerRow(state)
            StrokeWidthSlider(state)
            ToolbarRow(
                state = state,
                onCropClick = { showCropper = true },
                onUseClick = {
                    if (processing) return@ToolbarRow
                    processing = true
                    scope.launch {
                        val flat = withContext(Dispatchers.Default) {
                            flattenToBitmap(baseBitmap, state.annotations)
                        }
                        onUse(flat)
                    }
                },
                useEnabled = !processing
            )
        }
    }

    if (showCropper) {
        CropperFullScreen(
            src = baseBitmap,
            onCancel = { showCropper = false },
            onCropped = { cropped ->
                // Sin tracking del rect de crop, las anotaciones existentes podrían
                // quedar desplazadas, así que se descartan al recortar.
                if (state.annotations.isNotEmpty()) {
                    state.annotations.clear()
                    state.activeId = null
                }
                baseBitmap = cropped
                showCropper = false
            },
            aspect = aspect
        )
    }
}

// ===== CANVAS =====

@Composable
private fun EditorCanvas(
    bitmap: Bitmap,
    state: EditorState,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val img = remember(viewSize, bitmap) { fitImageRect(viewSize, bitmap.width, bitmap.height) }

    // Track ongoing gesture across pointer events
    var dragMode by remember { mutableStateOf(HandleHit.NONE) }
    var draftAnno by remember { mutableStateOf<Annotation?>(null) }

    // Live pencil stroke — points held in a SnapshotStateList so each append
    // notifies Compose for repaint without going through `state.annotations`
    // (avoids recomposing every existing annotation per point).
    val livePoints = remember { mutableStateListOf<Offset>() }
    var liveColor by remember { mutableStateOf(Color.Transparent) }
    var liveStrokeBmp by remember { mutableFloatStateOf(0f) }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Box(
        modifier
            .onSizeChanged { viewSize = it }
            .pointerInput(state.tool, bitmap, viewSize) {
                if (state.tool == EditorTool.Crop) return@pointerInput
                detectDragGestures(
                    onDragStart = { posView ->
                        if (!img.containsView(posView)) {
                            dragMode = HandleHit.NONE
                            draftAnno = null
                            return@detectDragGestures
                        }
                        val pBmp = img.viewToBmp(posView)

                        when (state.tool) {
                            EditorTool.Pencil -> {
                                livePoints.clear()
                                livePoints.add(pBmp)
                                liveColor = state.color
                                liveStrokeBmp = (state.strokeWidthView * img.viewToBmpScale)
                                    .coerceAtLeast(1f)
                                draftAnno = null
                                dragMode = HandleHit.BODY
                            }
                            EditorTool.Square, EditorTool.Circle -> {
                                val handlePad = 24f * img.viewToBmpScale

                                // 1. Active shape's corner handles take priority (resize)
                                val activeIdx = state.annotations
                                    .indexOfLast { it.id == state.activeId }
                                if (activeIdx >= 0) {
                                    val a = state.annotations[activeIdx]
                                    val bounds = when (a) {
                                        is Annotation.RectAnno -> rectFromAnno(a)
                                        is Annotation.CircleAnno -> circleBoundsFromAnno(a)
                                        else -> null
                                    }
                                    if (bounds != null) {
                                        val hit = hitTestHandle(bounds, pBmp, handlePad)
                                        if (hit != HandleHit.NONE && hit != HandleHit.BODY) {
                                            dragMode = hit
                                            draftAnno = a
                                            return@detectDragGestures
                                        }
                                    }
                                }

                                // 2. Body of any shape (top-most first): reselect & move
                                for (i in state.annotations.indices.reversed()) {
                                    val a = state.annotations[i]
                                    val bounds = when (a) {
                                        is Annotation.RectAnno -> rectFromAnno(a)
                                        is Annotation.CircleAnno -> circleBoundsFromAnno(a)
                                        else -> null
                                    }
                                    if (bounds != null && bounds.contains(pBmp)) {
                                        state.activeId = a.id
                                        dragMode = HandleHit.BODY
                                        draftAnno = a
                                        return@detectDragGestures
                                    }
                                }

                                // 3. Empty area: create a new shape from this point
                                val sw = (state.strokeWidthView * img.viewToBmpScale).coerceAtLeast(1f)
                                val anno: Annotation = if (state.tool == EditorTool.Square) {
                                    Annotation.RectAnno(
                                        left = pBmp.x, top = pBmp.y,
                                        right = pBmp.x, bottom = pBmp.y,
                                        color = state.color, strokeWidthBmp = sw
                                    )
                                } else {
                                    Annotation.CircleAnno(
                                        cx = pBmp.x, cy = pBmp.y, rx = 0f, ry = 0f,
                                        color = state.color, strokeWidthBmp = sw
                                    )
                                }
                                state.add(anno)
                                draftAnno = anno
                                dragMode = HandleHit.RB
                            }
                            EditorTool.Crop -> Unit
                        }
                    },
                    onDrag = { change, drag ->
                        val curBmp = img.viewToBmp(change.position)

                        if (livePoints.isNotEmpty()) {
                            livePoints.add(curBmp)
                            change.consume()
                            return@detectDragGestures
                        }

                        val anno = draftAnno ?: return@detectDragGestures
                        val dragBmp = Offset(drag.x * img.viewToBmpScale, drag.y * img.viewToBmpScale)
                        when (anno) {
                            is Annotation.RectAnno -> {
                                val idx = state.annotations.indexOfFirst { it.id == anno.id }
                                if (idx >= 0) {
                                    val updated = applyRectDrag(anno, dragMode, dragBmp, curBmp)
                                    state.replace(idx, updated)
                                    draftAnno = updated
                                }
                            }
                            is Annotation.CircleAnno -> {
                                val idx = state.annotations.indexOfFirst { it.id == anno.id }
                                if (idx >= 0) {
                                    val updated = applyCircleDrag(anno, dragMode, dragBmp, curBmp)
                                    state.replace(idx, updated)
                                    draftAnno = updated
                                }
                            }
                            is Annotation.StrokeAnno -> Unit
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (livePoints.isNotEmpty()) {
                            if (livePoints.size > 1) {
                                state.annotations.add(
                                    Annotation.StrokeAnno(
                                        points = livePoints.toMutableList(),
                                        color = liveColor,
                                        strokeWidthBmp = liveStrokeBmp
                                    )
                                )
                            }
                            livePoints.clear()
                        }
                        when (val anno = draftAnno) {
                            is Annotation.RectAnno -> {
                                val w = abs(anno.right - anno.left)
                                val h = abs(anno.bottom - anno.top)
                                if (w < 4f || h < 4f) {
                                    state.annotations.removeAll { it.id == anno.id }
                                    state.activeId = null
                                }
                            }
                            is Annotation.CircleAnno -> {
                                if (anno.rx < 2f || anno.ry < 2f) {
                                    state.annotations.removeAll { it.id == anno.id }
                                    state.activeId = null
                                }
                            }
                            else -> Unit
                        }
                        dragMode = HandleHit.NONE
                        draftAnno = null
                    },
                    onDragCancel = {
                        livePoints.clear()
                        dragMode = HandleHit.NONE
                        draftAnno = null
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Draw bitmap (Fit)
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(img.viewLeft.toInt(), img.viewTop.toInt()),
                dstSize = IntSize(img.viewW.toInt(), img.viewH.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High
            )

            // Draw annotations on top, mapped to view coords
            val s = img.bmpToViewScale
            state.annotations.forEach { anno ->
                when (anno) {
                    is Annotation.RectAnno -> {
                        val r = rectFromAnno(anno)
                        val tl = img.bmpToView(Offset(r.left, r.top))
                        val br = img.bmpToView(Offset(r.right, r.bottom))
                        drawRect(
                            color = anno.color,
                            topLeft = tl,
                            size = Size(br.x - tl.x, br.y - tl.y),
                            style = Stroke(width = (anno.strokeWidthBmp * s).coerceAtLeast(1f))
                        )
                    }
                    is Annotation.CircleAnno -> {
                        val center = img.bmpToView(Offset(anno.cx, anno.cy))
                        // Use the average of rx/ry scaled — for a true circle keep aspect 1:1.
                        // We render an axis-aligned ellipse via drawOval through a Path
                        val left = center.x - anno.rx * s
                        val top = center.y - anno.ry * s
                        val right = center.x + anno.rx * s
                        val bottom = center.y + anno.ry * s
                        val path = Path().apply {
                            addOval(
                                androidx.compose.ui.geometry.Rect(left, top, right, bottom)
                            )
                        }
                        drawPath(
                            path = path,
                            color = anno.color,
                            style = Stroke(width = (anno.strokeWidthBmp * s).coerceAtLeast(1f))
                        )
                    }
                    is Annotation.StrokeAnno -> {
                        if (anno.points.isEmpty()) return@forEach
                        val path = Path().apply {
                            val first = img.bmpToView(anno.points.first())
                            moveTo(first.x, first.y)
                            for (i in 1 until anno.points.size) {
                                val p = img.bmpToView(anno.points[i])
                                lineTo(p.x, p.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = anno.color,
                            style = Stroke(
                                width = (anno.strokeWidthBmp * s).coerceAtLeast(1f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // Live stroke (Pencil in progress) — `livePoints` is a SnapshotStateList,
            // so each append auto-invalidates this draw scope.
            if (livePoints.isNotEmpty()) {
                val livePath = Path().apply {
                    val first = img.bmpToView(livePoints[0])
                    moveTo(first.x, first.y)
                    for (i in 1 until livePoints.size) {
                        val p = img.bmpToView(livePoints[i])
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = livePath,
                    color = liveColor,
                    style = Stroke(
                        width = (liveStrokeBmp * s).coerceAtLeast(1f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw handles for the active shape
            val active = state.annotations.lastOrNull { it.id == state.activeId }
            if (active != null && state.tool != EditorTool.Pencil && state.tool != EditorTool.Crop) {
                val bounds = when (active) {
                    is Annotation.RectAnno -> rectFromAnno(active)
                    is Annotation.CircleAnno -> circleBoundsFromAnno(active)
                    else -> null
                }
                if (bounds != null) {
                    val tl = img.bmpToView(Offset(bounds.left, bounds.top))
                    val br = img.bmpToView(Offset(bounds.right, bounds.bottom))
                    val rectV = androidx.compose.ui.geometry.Rect(tl.x, tl.y, br.x, br.y)
                    val handleR = 10f
                    val handleStroke = 2.5f
                    val handleRing = Color(0xFF007AFF)
                    fun handle(cx: Float, cy: Float) {
                        // Soft drop shadow for contrast on light backgrounds
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = handleR + 1.5f,
                            center = Offset(cx, cy + 1f)
                        )
                        // White fill
                        drawCircle(
                            color = Color.White,
                            radius = handleR,
                            center = Offset(cx, cy)
                        )
                        // Blue ring
                        drawCircle(
                            color = handleRing,
                            radius = handleR,
                            center = Offset(cx, cy),
                            style = Stroke(width = handleStroke)
                        )
                    }
                    // Selection outline
                    val outline = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(rectV)
                    }
                    drawPath(
                        outline,
                        color = Color.White.copy(alpha = 0.7f),
                        style = Stroke(width = 1.5f)
                    )
                    handle(rectV.left, rectV.top)
                    handle(rectV.right, rectV.top)
                    handle(rectV.left, rectV.bottom)
                    handle(rectV.right, rectV.bottom)
                }
            }
        }

        // Overlay action buttons (delete + check) clustered at the active
        // shape's top-right corner. Real composables so taps don't conflict
        // with the parent's drag detector (taps stay below touch-slop).
        val activeForButtons = state.annotations.lastOrNull { it.id == state.activeId }
        if (activeForButtons != null &&
            (state.tool == EditorTool.Square || state.tool == EditorTool.Circle)
        ) {
            val bounds = when (activeForButtons) {
                is Annotation.RectAnno -> rectFromAnno(activeForButtons)
                is Annotation.CircleAnno -> circleBoundsFromAnno(activeForButtons)
                else -> null
            }
            if (bounds != null && img.viewW > 0f) {
                val density = LocalDensity.current
                val btnSizePx = with(density) { 36.dp.toPx() }
                val gapPx = with(density) { 8.dp.toPx() }
                val yGapPx = with(density) { 12.dp.toPx() }

                val trV = img.bmpToView(Offset(bounds.right, bounds.top))
                // Place both buttons just above the top edge, right edge
                // aligned with the shape's right edge.
                val cy = trV.y - btnSizePx / 2f - yGapPx
                val checkCx = trV.x - btnSizePx / 2f
                val deleteCx = checkCx - btnSizePx - gapPx

                ShapeActionButton(
                    icon = Icons.Outlined.Close,
                    bgColor = Color(0xFFFF3B30),
                    onClick = { state.delete(activeForButtons.id) },
                    centerView = Offset(deleteCx, cy)
                )
                ShapeActionButton(
                    icon = Icons.Outlined.Check,
                    bgColor = Color(0xFF34C759),
                    onClick = { state.activeId = null },
                    centerView = Offset(checkCx, cy)
                )
            }
        }
    }
}

@Composable
private fun ShapeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    onClick: () -> Unit,
    centerView: Offset
) {
    val sizeDp = 36.dp
    Box(
        Modifier
            .offset {
                val half = sizeDp.toPx() / 2f
                IntOffset(
                    (centerView.x - half).toInt(),
                    (centerView.y - half).toInt()
                )
            }
            .size(sizeDp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// ===== DRAG HELPERS =====

private fun applyRectDrag(
    anno: Annotation.RectAnno,
    mode: HandleHit,
    drag: Offset,
    cursorBmp: Offset
): Annotation.RectAnno {
    return when (mode) {
        HandleHit.BODY -> anno.copy(
            left = anno.left + drag.x, top = anno.top + drag.y,
            right = anno.right + drag.x, bottom = anno.bottom + drag.y
        )
        HandleHit.LT -> anno.copy(left = cursorBmp.x, top = cursorBmp.y)
        HandleHit.RT -> anno.copy(right = cursorBmp.x, top = cursorBmp.y)
        HandleHit.LB -> anno.copy(left = cursorBmp.x, bottom = cursorBmp.y)
        HandleHit.RB -> anno.copy(right = cursorBmp.x, bottom = cursorBmp.y)
        HandleHit.NONE -> anno
    }
}

private fun applyCircleDrag(
    anno: Annotation.CircleAnno,
    mode: HandleHit,
    drag: Offset,
    cursorBmp: Offset
): Annotation.CircleAnno {
    return when (mode) {
        HandleHit.BODY -> anno.copy(cx = anno.cx + drag.x, cy = anno.cy + drag.y)
        HandleHit.LT, HandleHit.RT, HandleHit.LB, HandleHit.RB -> {
            // Center stays; radius is half the distance from cursor to opposite corner via center.
            // Simpler model: cursor is the new corner; recompute rx/ry from |cursor - center|.
            anno.copy(
                rx = abs(cursorBmp.x - anno.cx).coerceAtLeast(1f),
                ry = abs(cursorBmp.y - anno.cy).coerceAtLeast(1f)
            )
        }
        HandleHit.NONE -> anno
    }
}

// ===== TOOLBAR =====

@Composable
private fun ToolbarRow(
    state: EditorState,
    onCropClick: () -> Unit,
    onUseClick: () -> Unit,
    useEnabled: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(
                icon = Icons.Outlined.Crop,
                selected = false, // crop launches modal — never sticky
                onClick = onCropClick
            )
            Spacer(Modifier.width(4.dp))
            ToolButton(
                icon = Icons.Outlined.CropSquare,
                selected = state.tool == EditorTool.Square,
                onClick = { state.tool = EditorTool.Square }
            )
            Spacer(Modifier.width(4.dp))
            ToolButton(
                icon = Icons.Outlined.Circle,
                selected = state.tool == EditorTool.Circle,
                onClick = { state.tool = EditorTool.Circle }
            )
            Spacer(Modifier.width(4.dp))
            ToolButton(
                icon = Icons.Outlined.Brush,
                selected = state.tool == EditorTool.Pencil,
                onClick = { state.tool = EditorTool.Pencil; state.activeId = null }
            )
        }
        Button(
            onClick = onUseClick,
            enabled = useEnabled
        ) {
            Text("Usar foto")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Check, null)
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White)
    }
}

private val PRESET_COLORS = listOf(
    Color(0xFFFF3B30), // red
    Color(0xFFFF9500), // orange
    Color(0xFFFFCC00), // yellow
    Color(0xFF34C759), // green
    Color(0xFF007AFF), // blue
    Color(0xFFAF52DE), // purple
    Color(0xFFFFFFFF), // white
    Color(0xFF000000)  // black
)

@Composable
private fun ColorPickerRow(state: EditorState) {
    LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(PRESET_COLORS) { c ->
            val selected = c == state.color
            Box(
                Modifier
                    .size(if (selected) 32.dp else 26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { state.color = c }
            )
        }
    }
}

@Composable
private fun StrokeWidthSlider(state: EditorState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 24.dp, height = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size((state.strokeWidthView / 2f).coerceIn(2f, 14f).dp)
                    .clip(CircleShape)
                    .background(state.color)
            )
        }
        Spacer(Modifier.width(8.dp))
        MinimalSlider(
            value = state.strokeWidthView,
            onValueChange = { state.strokeWidthView = it },
            valueRange = 2f..28f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ===== FLATTEN =====

private fun flattenToBitmap(base: Bitmap, annotations: List<Annotation>): Bitmap {
    if (annotations.isEmpty()) return base
    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    annotations.forEach { anno ->
        paint.color = anno.color.toArgb()
        paint.strokeWidth = anno.strokeWidthBmp.coerceAtLeast(1f)
        when (anno) {
            is Annotation.RectAnno -> {
                val r = rectFromAnno(anno)
                canvas.drawRect(r.left, r.top, r.right, r.bottom, paint)
            }
            is Annotation.CircleAnno -> {
                canvas.drawOval(
                    anno.cx - anno.rx, anno.cy - anno.ry,
                    anno.cx + anno.rx, anno.cy + anno.ry,
                    paint
                )
            }
            is Annotation.StrokeAnno -> {
                if (anno.points.size < 2) {
                    if (anno.points.size == 1) {
                        val p = anno.points[0]
                        canvas.drawPoint(p.x, p.y, paint.apply { style = Paint.Style.FILL })
                        paint.style = Paint.Style.STROKE
                    }
                    return@forEach
                }
                val path = AndroidPath().apply {
                    moveTo(anno.points[0].x, anno.points[0].y)
                    for (i in 1 until anno.points.size) {
                        lineTo(anno.points[i].x, anno.points[i].y)
                    }
                }
                canvas.drawPath(path, paint)
            }
        }
    }
    return out
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
