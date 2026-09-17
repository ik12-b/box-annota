package com.example.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class AnnotationBox(
    val id: String = UUID.randomUUID().toString(),
    val classId: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
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
        LabelClass(5, "Footer / Caption", 0xFF8B5CF6L)
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

enum class TouchHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    BODY
}

data class ProjectSession(
    val version: String = "2.0",
    val fileName: String,
    val totalPages: Int,
    val pageRotations: Map<Int, Int>,
    val classes: List<LabelClass>,
    val pageAnnotations: Map<Int, List<AnnotationBox>>
)
