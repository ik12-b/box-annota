package com.example.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

/** Smallest allowed box dimension, normalized (0..1) to the page's width/height. */
const val MIN_BOX_SIZE_NORM = 0.015f

data class AnnotationBox(
    val id: String = UUID.randomUUID().toString(),
    val classId: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    // Per-box visual angle in degrees (clockwise), for labeling text that is
    // itself skewed or vertical on the page — independent per box, so
    // selecting a different box never resets or overwrites another box's angle.
    val rotation: Float = 0f
) {
    val right: Float get() = (x + width).coerceAtMost(1f)
    val bottom: Float get() = (y + height).coerceAtMost(1f)
    val xCenter: Float get() = x + width / 2f
    val yCenter: Float get() = y + height / 2f

    fun calculateIoU(other: AnnotationBox): Float {
        val xA = maxOf(this.x, other.x)
        val yA = maxOf(this.y, other.y)
        val xB = minOf(this.right, other.right)
        val yB = minOf(this.bottom, other.bottom)

        val interArea = maxOf(0f, xB - xA) * maxOf(0f, yB - yA)
        if (interArea <= 0f) return 0f

        val boxAArea = this.width * this.height
        val boxBArea = other.width * other.height
        val unionArea = boxAArea + boxBArea - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }
}

data class LabelClass(
    val id: Int,
    val name: String,
    val colorValue: Long,
    val visible: Boolean = true
) {
    val composeColor: Color get() = Color(colorValue)
}

object LabelPresets {
    val GENERAL = listOf(
        LabelClass(0, "Title / Header", 0xFFEF4444L),
        LabelClass(1, "Subtitle", 0xFFEC4899L),
        LabelClass(2, "Paragraph", 0xFF3B82F6L),
        LabelClass(3, "Table / Grid", 0xFF10B981L),
        LabelClass(4, "Image / Figure", 0xFFF59E0BL),
        LabelClass(5, "Footer / Caption", 0xFF8B5CF6L),
        // Ornamental border/frame around a page or text block. Its box is
        // expected to overlap whatever text classes sit inside it — that's
        // normal nested structure in a detection dataset (see checkOverlap(),
        // which only warns on same-class overlap), not a labeling mistake.
        LabelClass(6, "Bingkai / Border", 0xFF06B6D4L)
    )

    val INVOICE = listOf(
        LabelClass(0, "Vendor Name / Logo", 0xFFEF4444L),
        LabelClass(1, "Invoice Date / No", 0xFFF59E0BL),
        LabelClass(2, "Customer Detail", 0xFF3B82F6L),
        LabelClass(3, "Line Item List", 0xFF10B981L),
        LabelClass(4, "Total / Tax Amount", 0xFF8B5CF6L),
        LabelClass(5, "Signature / Stamp", 0xFF06B6D4L)
    )

    val FORM = listOf(
        LabelClass(0, "Form Field Label", 0xFF3B82F6L),
        LabelClass(1, "Input Value Text", 0xFF10B981L),
        LabelClass(2, "Checkbox / Option", 0xFFF59E0BL),
        LabelClass(3, "Signature Area", 0xFFEF4444L),
        LabelClass(4, "Official Stamp", 0xFF8B5CF6L)
    )
}

enum class ExportFormat(val displayName: String, val extension: String) {
    YOLO("YOLO", ".txt"),
    COCO("COCO", ".json"),
    PASCAL_VOC("Pascal VOC", ".xml")
}

/** Which top-level workflow the app is currently showing. */
enum class AppMode {
    LABELING,
    TRANSCRIPTION
}

/**
 * One cropped line image waiting to be transcribed. Created from a bounding box
 * once labeling is marked "Selesai" — [sourceBoxId] ties it back to the
 * [AnnotationBox] it was cropped from so re-running the crop step can preserve
 * any text already typed.
 */
data class TranscriptionLine(
    val id: String,
    val sourceBoxId: String,
    val pageNumber: Int,
    val classId: Int,
    val cropFilePath: String,
    val text: String = ""
)

/** Which engine powers automatic transcription in Transcription mode. */
enum class TranscriptionEngine {
    GEMINI,
    ON_DEVICE
}

enum class TouchHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    // Edge midpoints — dragging these resizes only ONE dimension (width for
    // LEFT/RIGHT, height for TOP/BOTTOM) instead of both at once like a
    // corner drag does.
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    BODY
}
