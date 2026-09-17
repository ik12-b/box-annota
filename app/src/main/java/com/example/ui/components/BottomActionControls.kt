package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose400
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun BottomActionControls(
    currentPage: Int,
    totalPages: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelectedBox: Boolean,
    isCrosshairEnabled: Boolean,
    isSnappingEnabled: Boolean,
    zoomScale: Float,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onFirstPage: () -> Unit,
    onLastPage: () -> Unit,
    onAutoAdvance: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    currentRotation: Float = 0f,
    onRotateFine: (Float) -> Unit = {},
    onToggleRotatePanel: () -> Unit = {},
    onRotateLeft: () -> Unit = { onRotateFine(-0.5f) },
    onRotateRight: () -> Unit = { onRotateFine(0.5f) },
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomFit: () -> Unit,
    onToggleCrosshair: () -> Unit,
    onToggleSnapping: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Slate900)
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Prev Page
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = "Prev",
            enabled = currentPage > 1,
            onClick = onPrevPage,
            tag = "bottom_prev_btn"
        )

        // Page Indicator
        Box(
            modifier = Modifier
                .background(Slate800, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$currentPage/$totalPages",
                color = Indigo400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Next Page
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = "Next",
            enabled = currentPage < totalPages,
            onClick = onNextPage,
            tag = "bottom_next_btn"
        )

        DividerBar()

        // Auto-Advance ("Lanjut")
        QuickActionButton(
            icon = Icons.Default.CheckCircle,
            label = "Lanjut",
            tint = Emerald400,
            onClick = onAutoAdvance,
            tag = "bottom_autoadvance_btn"
        )

        // Undo & Redo
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = "Undo",
            enabled = canUndo,
            onClick = onUndo,
            tag = "bottom_undo_btn"
        )
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            label = "Redo",
            enabled = canRedo,
            onClick = onRedo,
            tag = "bottom_redo_btn"
        )

        DividerBar()

        // Box actions: Copy, Paste, Duplicate, Delete
        QuickActionButton(
            icon = Icons.Default.ContentCopy,
            label = "Salin",
            enabled = hasSelectedBox,
            onClick = onCopy,
            tag = "bottom_copy_btn"
        )
        QuickActionButton(
            icon = Icons.Default.ContentPaste,
            label = "Tempel",
            onClick = onPaste,
            tag = "bottom_paste_btn"
        )
        QuickActionButton(
            icon = Icons.Default.DynamicFeed,
            label = "Duplikat",
            enabled = hasSelectedBox,
            onClick = onDuplicate,
            tag = "bottom_duplicate_btn"
        )
        QuickActionButton(
            icon = Icons.Default.Delete,
            label = "Hapus",
            tint = Rose400,
            enabled = hasSelectedBox,
            onClick = onDelete,
            tag = "bottom_delete_btn"
        )

        DividerBar()

        // Fine Rotation Controls (Micro-steppers & Panel toggle)
        QuickActionButton(
            icon = Icons.Default.RotateLeft,
            label = "-0.5°",
            tint = Amber400,
            onClick = { onRotateFine(-0.5f) },
            tag = "bottom_rotate_minus_step_btn"
        )

        val angleLabel = if (kotlin.math.abs(currentRotation) < 0.05f) "0.0°"
            else if (currentRotation > 0f) "+${String.format(Locale.US, "%.1f", currentRotation)}°"
            else "${String.format(Locale.US, "%.1f", currentRotation)}°"

        QuickActionButton(
            icon = Icons.Default.Tune,
            label = angleLabel,
            tint = if (kotlin.math.abs(currentRotation) < 0.05f) Slate400 else Amber400,
            onClick = onToggleRotatePanel,
            tag = "bottom_rotate_panel_toggle_btn"
        )

        QuickActionButton(
            icon = Icons.Default.RotateRight,
            label = "+0.5°",
            tint = Amber400,
            onClick = { onRotateFine(0.5f) },
            tag = "bottom_rotate_plus_step_btn"
        )

        DividerBar()

        // Zoom Controls
        QuickActionButton(
            icon = Icons.Default.Remove,
            label = "Zoom-",
            onClick = onZoomOut,
            tag = "bottom_zoom_out_btn"
        )
        Text(
            text = "${(zoomScale * 100).toInt()}%",
            color = Slate400,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        QuickActionButton(
            icon = Icons.Default.Add,
            label = "Zoom+",
            onClick = onZoomIn,
            tag = "bottom_zoom_in_btn"
        )
        QuickActionButton(
            icon = Icons.Default.Fullscreen,
            label = "Fit",
            onClick = onZoomFit,
            tag = "bottom_zoom_fit_btn"
        )

        DividerBar()

        // Crosshair alignment guide
        QuickActionButton(
            icon = Icons.Outlined.FilterCenterFocus,
            label = "Guide",
            tint = if (isCrosshairEnabled) Indigo400 else Slate400,
            onClick = onToggleCrosshair,
            tag = "bottom_crosshair_btn"
        )

        // Snapping toggle
        QuickActionButton(
            icon = Icons.Default.CenterFocusStrong,
            label = "Snap",
            tint = if (isSnappingEnabled) Indigo400 else Slate400,
            onClick = onToggleSnapping,
            tag = "bottom_snap_btn"
        )

        DividerBar()

        // First & Last Page
        QuickActionButton(
            icon = Icons.Default.FirstPage,
            label = "Awal",
            enabled = currentPage > 1,
            onClick = onFirstPage,
            tag = "bottom_first_page_btn"
        )
        QuickActionButton(
            icon = Icons.Default.LastPage,
            label = "Akhir",
            enabled = currentPage < totalPages,
            onClick = onLastPage,
            tag = "bottom_last_page_btn"
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Slate400,
    enabled: Boolean = true,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(horizontal = 1.dp)
            .testTag(tag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else Slate700,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = if (enabled) tint else Slate700,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DividerBar() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(Slate800)
    )
}
