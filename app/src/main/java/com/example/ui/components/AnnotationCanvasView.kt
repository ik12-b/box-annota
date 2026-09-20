package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnnotationBox
import com.example.model.MIN_BOX_SIZE_NORM
import com.example.model.LabelClass
import com.example.model.TouchHandle
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate950

/**
 * Rotates [point] by [degrees] (clockwise, matching DrawScope.rotate) around
 * [pivot], both in the same pixel space. Used to find where a box's corner
 * actually ends up on screen once its own rotation is applied, since pointer
 * input (hit-testing, dragging) happens outside the DrawScope that renders
 * the visual rotation and has to redo that math by hand.
 */
private fun rotatePoint(point: Offset, pivot: Offset, degrees: Float): Offset {
    if (degrees == 0f) return point
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    val dx = point.x - pivot.x
    val dy = point.y - pivot.y
    return Offset(pivot.x + dx * cos - dy * sin, pivot.y + dx * sin + dy * cos)
}

/** Same rotation as [rotatePoint] but for a direction/delta vector (no pivot). */
private fun rotateVector(vector: Offset, degrees: Float): Offset {
    if (degrees == 0f) return vector
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    return Offset(vector.x * cos - vector.y * sin, vector.x * sin + vector.y * cos)
}

/** True if [screenPoint] (canvas pixels) falls inside [box]'s rotated rectangle. */
private fun isPointInRotatedBox(screenPoint: Offset, box: AnnotationBox, canvasW: Float, canvasH: Float): Boolean {
    val pxX = box.x * canvasW
    val pxY = box.y * canvasH
    val pxW = box.width * canvasW
    val pxH = box.height * canvasH
    if (box.rotation == 0f) {
        return screenPoint.x in pxX..(pxX + pxW) && screenPoint.y in pxY..(pxY + pxH)
    }
    val pivot = Offset(pxX + pxW / 2f, pxY + pxH / 2f)
    // Un-rotate the tap point into the box's own local frame instead of
    // rotating the box — cheaper, and the containment check then stays a
    // plain axis-aligned range check.
    val local = rotatePoint(screenPoint, pivot, -box.rotation)
    return local.x in pxX..(pxX + pxW) && local.y in pxY..(pxY + pxH)
}

/**
 * Which corner/edge-midpoint a resize handle anchors from: [anchorLocal] is
 * the point OPPOSITE the dragged handle (stays fixed on screen), signX/signY
 * give the drag direction along each local axis (0 = that axis doesn't move
 * at all), and freeW/freeH say whether width/height are allowed to change —
 * false for the axis an edge-midpoint handle intentionally leaves untouched,
 * so dragging just the right edge changes width only, never height.
 */
private data class ResizeSpec(
    val anchorLocal: Offset,
    val signX: Float,
    val signY: Float,
    val freeW: Boolean,
    val freeH: Boolean
)

private fun resizeRotatedBox(
    initial: AnnotationBox,
    handle: TouchHandle,
    currentScreenPoint: Offset,
    canvasW: Float,
    canvasH: Float
): AnnotationBox {
    val pxX = initial.x * canvasW
    val pxY = initial.y * canvasH
    val pxW = initial.width * canvasW
    val pxH = initial.height * canvasH
    val pivot = Offset(pxX + pxW / 2f, pxY + pxH / 2f)
    val rot = initial.rotation

    val spec = when (handle) {
        TouchHandle.BOTTOM_RIGHT -> ResizeSpec(Offset(pxX, pxY), 1f, 1f, freeW = true, freeH = true)
        TouchHandle.TOP_LEFT -> ResizeSpec(Offset(pxX + pxW, pxY + pxH), -1f, -1f, freeW = true, freeH = true)
        TouchHandle.TOP_RIGHT -> ResizeSpec(Offset(pxX, pxY + pxH), 1f, -1f, freeW = true, freeH = true)
        TouchHandle.BOTTOM_LEFT -> ResizeSpec(Offset(pxX + pxW, pxY), -1f, 1f, freeW = true, freeH = true)
        // Edge midpoints: only one dimension is "free" — the box's other
        // dimension and its position along that axis never move.
        TouchHandle.RIGHT -> ResizeSpec(Offset(pxX, pxY + pxH / 2f), 1f, 0f, freeW = true, freeH = false)
        TouchHandle.LEFT -> ResizeSpec(Offset(pxX + pxW, pxY + pxH / 2f), -1f, 0f, freeW = true, freeH = false)
        TouchHandle.BOTTOM -> ResizeSpec(Offset(pxX + pxW / 2f, pxY), 0f, 1f, freeW = false, freeH = true)
        TouchHandle.TOP -> ResizeSpec(Offset(pxX + pxW / 2f, pxY + pxH), 0f, -1f, freeW = false, freeH = true)
        else -> return initial
    }

    val anchorScreen = rotatePoint(spec.anchorLocal, pivot, rot)
    val deltaLocal = rotateVector(currentScreenPoint - anchorScreen, -rot)

    val minPx = MIN_BOX_SIZE_NORM * canvasW
    val minPy = MIN_BOX_SIZE_NORM * canvasH
    val newWpx = if (spec.freeW) (deltaLocal.x * spec.signX).coerceAtLeast(minPx) else pxW
    val newHpx = if (spec.freeH) (deltaLocal.y * spec.signY).coerceAtLeast(minPy) else pxH

    val centerOffsetLocal = Offset(
        if (spec.signX != 0f) spec.signX * newWpx / 2f else 0f,
        if (spec.signY != 0f) spec.signY * newHpx / 2f else 0f
    )
    val newPivotScreen = anchorScreen + rotateVector(centerOffsetLocal, rot)

    val newXpx = newPivotScreen.x - newWpx / 2f
    val newYpx = newPivotScreen.y - newHpx / 2f

    val newX = (newXpx / canvasW).coerceIn(0f, 1f - MIN_BOX_SIZE_NORM)
    val newY = (newYpx / canvasH).coerceIn(0f, 1f - MIN_BOX_SIZE_NORM)
    val newW = (newWpx / canvasW).coerceIn(MIN_BOX_SIZE_NORM, 1f)
    val newH = (newHpx / canvasH).coerceIn(MIN_BOX_SIZE_NORM, 1f)

    return initial.copy(x = newX, y = newY, width = newW, height = newH)
}

@Composable
fun AnnotationCanvasView(
    bitmap: Bitmap?,
    isLoading: Boolean,
    hasDocument: Boolean = true,
    isRestoringSession: Boolean = false,
    onOpenPdf: (() -> Unit)? = null,
    onRestoreFromBackup: (() -> Unit)? = null,
    boxes: List<AnnotationBox>,
    classes: List<LabelClass>,
    activeClassId: Int,
    selectedBoxIds: Set<String>,
    primarySelectedBoxId: String?,
    isCrosshairEnabled: Boolean,
    isSnappingEnabled: Boolean,
    isAlignmentGridEnabled: Boolean = false,
    zoomScale: Float,
    onBoxAdded: (AnnotationBox) -> Unit,
    onBoxUpdated: (AnnotationBox) -> Unit,
    onBoxSelected: (String) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleTouchRadiusPx = with(density) { 26.dp.toPx() }
    val handleDrawRadiusPx = with(density) { 5.dp.toPx() }

    val latestBoxes = androidx.compose.runtime.rememberUpdatedState(boxes)

    var crosshairPos by remember { mutableStateOf<Offset?>(null) }
    var activeHandle by remember { mutableStateOf(TouchHandle.NONE) }
    var activeDragBoxId by remember { mutableStateOf<String?>(null) }
    var initialBoxState by remember { mutableStateOf<AnnotationBox?>(null) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var currentDrawBox by remember { mutableStateOf<AnnotationBox?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950),
        contentAlignment = Alignment.Center
    ) {
        if (isRestoringSession) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF6366F1))
                Text(
                    text = "Memulihkan sesi sebelumnya...",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 70.dp)
                )
            }
            return@Box
        }

        if (!hasDocument) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        enabled = onOpenPdf != null
                    ) { onOpenPdf?.invoke() }
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Belum ada PDF yang dibuka",
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Ketuk di sini atau gunakan tombol \"Buka PDF\" di toolbar untuk mulai memberi anotasi.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (onRestoreFromBackup != null) {
                        Text(
                            text = "Baru install ulang? Pulihkan data lama dari folder backup.",
                            color = Color(0xFF818CF8),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { onRestoreFromBackup.invoke() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            return@Box
        }

        if (bitmap == null || isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF6366F1))
                Text(
                    text = "Merender halaman...",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 70.dp)
                )
            }
            return@Box
        }

        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            val calculatedWidth = if (containerWidth / bitmapAspect < containerHeight) {
                containerWidth * zoomScale
            } else {
                (containerHeight * bitmapAspect) * zoomScale
            }

            // Horizontal Alignment Guide Lines across viewport (unrotated)
            if (isAlignmentGridEnabled) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("alignment_grid_guide")
                ) {
                    val stepPx = 36.dp.toPx()
                    var y = stepPx
                    val gridColor = Color(0x5538BDF8) // subtle cyan/sky blue
                    val dashStroke = Stroke(
                        width = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                    while (y < size.height) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                            pathEffect = dashStroke.pathEffect
                        )
                        y += stepPx
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(calculatedWidth)
                    .aspectRatio(bitmapAspect)
                    .shadow(16.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .testTag("annotation_canvas_container")
            ) {
                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                // Image layer: intentionally NOT rotated. The page bitmap always
                // stays upright — rotation is a per-box label attribute, not a
                // page-level view transform.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("annotation_image_layer")
                ) {
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }

                // Box/overlay layer: carries the crosshair guides, bounding boxes
                // and drag handles, plus all pointer input. The layer itself is
                // NOT rotated — each box is spun individually around its own
                // center using its own stored rotation, inside the draw pass below.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("annotation_box_layer")
                        .pointerInput(selectedBoxIds, isCrosshairEnabled, isSnappingEnabled) {
                            detectTapGestures { tapOffset ->
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()

                                // Check if tapped inside any box (topmost first),
                                // accounting for each box's own rotation.
                                val tappedBox = latestBoxes.value.asReversed().find { b ->
                                    isPointInRotatedBox(tapOffset, b, canvasW, canvasH)
                                }

                                if (tappedBox != null) {
                                    onBoxSelected(tappedBox.id)
                                } else {
                                    onClearSelection()
                                }
                            }
                        }
                        .pointerInput(primarySelectedBoxId, isSnappingEnabled) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val boxes = latestBoxes.value
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()

                                    dragStartOffset = startOffset
                                    crosshairPos = startOffset

                                    // 1. Check if primary selected box handles were hit —
                                    // using the handles' actual ROTATED screen positions,
                                    // since that's where they're drawn.
                                    val primaryBox = boxes.find { it.id == primarySelectedBoxId }
                                    if (primaryBox != null) {
                                        val pxX = primaryBox.x * canvasW
                                        val pxY = primaryBox.y * canvasH
                                        val pxW = primaryBox.width * canvasW
                                        val pxH = primaryBox.height * canvasH
                                        val pivot = Offset(pxX + pxW / 2f, pxY + pxH / 2f)
                                        val rot = primaryBox.rotation

                                        val tl = rotatePoint(Offset(pxX, pxY), pivot, rot)
                                        val tr = rotatePoint(Offset(pxX + pxW, pxY), pivot, rot)
                                        val bl = rotatePoint(Offset(pxX, pxY + pxH), pivot, rot)
                                        val br = rotatePoint(Offset(pxX + pxW, pxY + pxH), pivot, rot)
                                        // Edge midpoints — dragging one of these resizes only
                                        // that single axis (width for left/right, height for
                                        // top/bottom) instead of both at once like a corner does.
                                        val topMid = rotatePoint(Offset(pxX + pxW / 2f, pxY), pivot, rot)
                                        val bottomMid = rotatePoint(Offset(pxX + pxW / 2f, pxY + pxH), pivot, rot)
                                        val leftMid = rotatePoint(Offset(pxX, pxY + pxH / 2f), pivot, rot)
                                        val rightMid = rotatePoint(Offset(pxX + pxW, pxY + pxH / 2f), pivot, rot)

                                        val candidates = listOf(
                                            tl to TouchHandle.TOP_LEFT,
                                            tr to TouchHandle.TOP_RIGHT,
                                            bl to TouchHandle.BOTTOM_LEFT,
                                            br to TouchHandle.BOTTOM_RIGHT,
                                            topMid to TouchHandle.TOP,
                                            bottomMid to TouchHandle.BOTTOM,
                                            leftMid to TouchHandle.LEFT,
                                            rightMid to TouchHandle.RIGHT
                                        )
                                        // Pick whichever handle the touch is actually closest to
                                        // (not just "first within radius") — on a small box the
                                        // corner and edge-midpoint hit zones can overlap, and this
                                        // keeps the closer one winning instead of always the corner.
                                        val handle = candidates
                                            .map { (pos, h) -> (startOffset - pos).getDistance() to h }
                                            .filter { (dist, _) -> dist <= handleTouchRadiusPx }
                                            .minByOrNull { (dist, _) -> dist }
                                            ?.second ?: TouchHandle.NONE

                                        if (handle != TouchHandle.NONE) {
                                            activeHandle = handle
                                            activeDragBoxId = primaryBox.id
                                            initialBoxState = primaryBox.copy()
                                            return@detectDragGestures
                                        }
                                    }

                                    // 2. Check if clicked inside an existing box (move mode),
                                    // accounting for each box's own rotation.
                                    val hitBox = boxes.asReversed().find { b ->
                                        isPointInRotatedBox(startOffset, b, canvasW, canvasH)
                                    }

                                    if (hitBox != null) {
                                        onBoxSelected(hitBox.id)
                                        activeHandle = TouchHandle.BODY
                                        activeDragBoxId = hitBox.id
                                        initialBoxState = hitBox.copy()
                                        return@detectDragGestures
                                    }

                                    // 3. Otherwise, start drawing a new box
                                    activeHandle = TouchHandle.NONE
                                    activeDragBoxId = null
                                    initialBoxState = null
                                    val normX = (startOffset.x / canvasW).coerceIn(0f, 1f)
                                    val normY = (startOffset.y / canvasH).coerceIn(0f, 1f)
                                    currentDrawBox = AnnotationBox(
                                        classId = activeClassId,
                                        x = normX,
                                        y = normY,
                                        width = 0f,
                                        height = 0f
                                    )
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()
                                    crosshairPos = change.position

                                    val curNormX = (change.position.x / canvasW).coerceIn(0f, 1f)
                                    val curNormY = (change.position.y / canvasH).coerceIn(0f, 1f)
                                    val startNormX = (dragStartOffset.x / canvasW).coerceIn(0f, 1f)
                                    val startNormY = (dragStartOffset.y / canvasH).coerceIn(0f, 1f)

                                    val initial = initialBoxState
                                    if (activeDragBoxId != null && initial != null) {
                                        when (activeHandle) {
                                            TouchHandle.BODY -> {
                                                // Translation is rotation-invariant, so this
                                                // needs no rotation-aware handling.
                                                val deltaX = curNormX - startNormX
                                                val deltaY = curNormY - startNormY
                                                val newX = (initial.x + deltaX).coerceIn(0f, 1f - initial.width)
                                                val newY = (initial.y + deltaY).coerceIn(0f, 1f - initial.height)
                                                onBoxUpdated(initial.copy(x = newX, y = newY))
                                            }
                                            TouchHandle.BOTTOM_RIGHT, TouchHandle.TOP_LEFT,
                                            TouchHandle.TOP_RIGHT, TouchHandle.BOTTOM_LEFT,
                                            TouchHandle.TOP, TouchHandle.BOTTOM,
                                            TouchHandle.LEFT, TouchHandle.RIGHT -> {
                                                // Resizing a rotated box by dragging a corner or
                                                // edge midpoint needs to keep the OPPOSITE
                                                // corner/edge fixed on screen and work in the
                                                // box's own (rotated) axes — a plain axis-aligned
                                                // resize would shear a rotated box instead of
                                                // resizing it. Edge midpoints only free up one
                                                // dimension (see resizeRotatedBox's ResizeSpec),
                                                // which is what makes width-only/height-only
                                                // resize possible.
                                                onBoxUpdated(
                                                    resizeRotatedBox(
                                                        initial = initial,
                                                        handle = activeHandle,
                                                        currentScreenPoint = change.position,
                                                        canvasW = canvasW,
                                                        canvasH = canvasH
                                                    )
                                                )
                                            }
                                            TouchHandle.NONE -> {}
                                        }
                                    } else {
                                        // Drawing new box
                                        val minX = minOf(startNormX, curNormX)
                                        val minY = minOf(startNormY, curNormY)
                                        val w = kotlin.math.abs(curNormX - startNormX)
                                        val h = kotlin.math.abs(curNormY - startNormY)

                                        currentDrawBox = AnnotationBox(
                                            classId = activeClassId,
                                            x = minX,
                                            y = minY,
                                            width = w,
                                            height = h
                                        )
                                    }
                                },
                                onDragEnd = {
                                    val drawn = currentDrawBox
                                    if (drawn != null && drawn.width >= MIN_BOX_SIZE_NORM && drawn.height >= MIN_BOX_SIZE_NORM) {
                                        onBoxAdded(drawn)
                                    }
                                    currentDrawBox = null
                                    activeDragBoxId = null
                                    activeHandle = TouchHandle.NONE
                                    initialBoxState = null
                                },
                                onDragCancel = {
                                    currentDrawBox = null
                                    activeDragBoxId = null
                                    activeHandle = TouchHandle.NONE
                                    initialBoxState = null
                                }
                            )
                        }
                ) {
                    val canvasW = size.width
                    val canvasH = size.height

                    // 1. Draw Crosshair guides if enabled
                    if (isCrosshairEnabled && crosshairPos != null) {
                        val pos = crosshairPos!!
                        val crosshairColor = Color(0x776366F1)
                        val stroke = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))

                        drawLine(
                            color = crosshairColor,
                            start = Offset(pos.x, 0f),
                            end = Offset(pos.x, canvasH),
                            strokeWidth = 1.5f,
                            pathEffect = stroke.pathEffect
                        )
                        drawLine(
                            color = crosshairColor,
                            start = Offset(0f, pos.y),
                            end = Offset(canvasW, pos.y),
                            strokeWidth = 1.5f,
                            pathEffect = stroke.pathEffect
                        )
                    }

                    // 2. Draw Existing Bounding Boxes
                    boxes.forEach { box ->
                        val cls = classes.find { it.id == box.classId } ?: LabelClass(box.classId, "Unknown", 0xFF6366F1L)
                        if (!cls.visible) return@forEach

                        val isSelected = box.id in selectedBoxIds
                        val isPrimary = box.id == primarySelectedBoxId
                        val boxColor = cls.composeColor

                        val pxX = box.x * canvasW
                        val pxY = box.y * canvasH
                        val pxW = box.width * canvasW
                        val pxH = box.height * canvasH

                        // Every box carries its own rotation now (for labeling
                        // skewed/vertical text), so all of them are drawn rotated
                        // around their own center — not just the selected one.
                        val boxRotationDeg = box.rotation
                        val boxPivot = Offset(pxX + pxW / 2f, pxY + pxH / 2f)

                        rotate(degrees = boxRotationDeg, pivot = boxPivot) {
                            // Fill kept very light, and the border as thin/translucent
                            // as still reasonably visible, so the box outline doesn't
                            // bury small text underneath it while labeling.
                            drawRect(
                                color = boxColor.copy(alpha = if (isSelected) 0.16f else 0.06f),
                                topLeft = Offset(pxX, pxY),
                                size = Size(pxW, pxH)
                            )

                            // Border Stroke
                            drawRect(
                                color = boxColor.copy(alpha = if (isSelected) 0.85f else 0.55f),
                                topLeft = Offset(pxX, pxY),
                                size = Size(pxW, pxH),
                                style = Stroke(width = if (isSelected) 1.6f else 1f)
                            )

                            // Handles for Primary Selection: 4 corners (resize both
                            // dimensions) + 4 edge midpoints (resize width OR height
                            // only) — small bars on the edges hint at their single-axis
                            // drag direction.
                            if (isPrimary) {
                                val corners = listOf(
                                    Offset(pxX, pxY),
                                    Offset(pxX + pxW, pxY),
                                    Offset(pxX, pxY + pxH),
                                    Offset(pxX + pxW, pxY + pxH)
                                )
                                corners.forEach { h ->
                                    drawCircle(color = Color.White, radius = handleDrawRadiusPx, center = h)
                                    drawCircle(
                                        color = boxColor,
                                        radius = handleDrawRadiusPx,
                                        center = h,
                                        style = Stroke(width = 2f)
                                    )
                                }

                                val edgeBarLong = handleDrawRadiusPx * 2.2f
                                val edgeBarShort = handleDrawRadiusPx * 0.9f
                                fun drawEdgeBar(center: Offset, horizontal: Boolean) {
                                    val size = if (horizontal) Size(edgeBarLong, edgeBarShort) else Size(edgeBarShort, edgeBarLong)
                                    val topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f)
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = topLeft,
                                        size = size,
                                        cornerRadius = CornerRadius(edgeBarShort / 2f)
                                    )
                                    drawRoundRect(
                                        color = boxColor,
                                        topLeft = topLeft,
                                        size = size,
                                        cornerRadius = CornerRadius(edgeBarShort / 2f),
                                        style = Stroke(width = 1.6f)
                                    )
                                }
                                drawEdgeBar(Offset(pxX + pxW / 2f, pxY), horizontal = true)       // TOP: width fixed, drag = height
                                drawEdgeBar(Offset(pxX + pxW / 2f, pxY + pxH), horizontal = true) // BOTTOM
                                drawEdgeBar(Offset(pxX, pxY + pxH / 2f), horizontal = false)      // LEFT: height fixed, drag = width
                                drawEdgeBar(Offset(pxX + pxW, pxY + pxH / 2f), horizontal = false) // RIGHT
                            }
                        }
                    }

                    // 3. Draw Active Drawing Box Preview
                    currentDrawBox?.let { dBox ->
                        val cls = classes.find { it.id == activeClassId } ?: LabelClass(activeClassId, "Unknown", 0xFF6366F1L)
                        val pxX = dBox.x * canvasW
                        val pxY = dBox.y * canvasH
                        val pxW = dBox.width * canvasW
                        val pxH = dBox.height * canvasH

                        drawRect(
                            color = cls.composeColor.copy(alpha = 0.14f),
                            topLeft = Offset(pxX, pxY),
                            size = Size(pxW, pxH)
                        )
                        drawRect(
                            color = cls.composeColor.copy(alpha = 0.85f),
                            topLeft = Offset(pxX, pxY),
                            size = Size(pxW, pxH),
                            style = Stroke(
                                width = 1.4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f))
                            )
                        )
                    }
                }
            }
        }
    }
}
