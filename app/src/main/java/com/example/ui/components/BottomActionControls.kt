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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose400
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

/**
 * The full action set used to fit in a single 22-button horizontally-scrollable
 * row, which was fine on a wide/landscape screen but on a narrow portrait phone
 * meant scrolling through most of it to reach anything past Undo/Redo — the
 * opposite of compact. Only the actions used on essentially every box (page
 * nav, Lanjut, Deteksi, Selesai, Undo/Redo, Hapus) stay in the always-visible
 * row now; everything else (copy/paste/duplicate, fine rotation, zoom,
 * guide/snap, jump-to-first/last) moved into the "Lainnya" (More) menu.
 */
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
    isDetectingBoxes: Boolean = false,
    onAutoDetect: () -> Unit = {},
    hasAnyBoxes: Boolean = false,
    onFinishLabeling: () -> Unit = {},
    onResizeBoxWidth: (Float) -> Unit = {},
    onResizeBoxHeight: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showMoreMenu by remember { mutableStateOf(false) }

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

        DividerBar()

        // Auto-detect text-line boxes via the on-device ONNX model
        QuickActionButton(
            icon = Icons.Default.AutoAwesome,
            label = if (isDetectingBoxes) "..." else "Deteksi",
            tint = Indigo400,
            enabled = !isDetectingBoxes,
            onClick = onAutoDetect,
            tag = "bottom_autodetect_btn"
        )

        // Crops every box into a line image and saves it for Transcription
        // mode — does NOT switch mode. Mode switching only ever happens via
        // the dedicated toggle in the top toolbar.
        QuickActionButton(
            icon = Icons.Default.Translate,
            label = "Simpan",
            tint = Emerald400,
            enabled = hasAnyBoxes,
            onClick = onFinishLabeling,
            tag = "bottom_finish_labeling_btn"
        )

        DividerBar()

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

        // Delete stays inline (frequent, destructive — worth keeping one tap away)
        QuickActionButton(
            icon = Icons.Default.Delete,
            label = "Hapus",
            tint = Rose400,
            enabled = hasSelectedBox,
            onClick = onDelete,
            tag = "bottom_delete_btn"
        )

        DividerBar()

        // Everything below is used far less often per-box than the actions
        // above, so it lives behind "Lainnya" instead of eating scroll space.
        Box {
            QuickActionButton(
                icon = Icons.Default.MoreVert,
                label = "Lainnya",
                onClick = { showMoreMenu = true },
                tag = "bottom_more_btn"
            )

            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                modifier = Modifier.background(Slate900)
            ) {
                Text(
                    text = "BOX",
                    color = Slate700,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                DropdownMenuItem(
                    text = { Text("Salin", color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Slate400) },
                    enabled = hasSelectedBox,
                    onClick = { onCopy(); showMoreMenu = false },
                    modifier = Modifier.testTag("bottom_copy_btn")
                )
                DropdownMenuItem(
                    text = { Text("Tempel", color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Slate400) },
                    onClick = { onPaste(); showMoreMenu = false },
                    modifier = Modifier.testTag("bottom_paste_btn")
                )
                DropdownMenuItem(
                    text = { Text("Duplikat", color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.DynamicFeed, contentDescription = null, tint = Slate400) },
                    enabled = hasSelectedBox,
                    onClick = { onDuplicate(); showMoreMenu = false },
                    modifier = Modifier.testTag("bottom_duplicate_btn")
                )

                HorizontalDivider(color = Slate800)
                Text(
                    text = "ROTASI HALUS",
                    color = Slate700,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                // Kept as a single row of taps (menu stays open) since fine
                // rotation is naturally a repeated-tap adjustment.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
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
                        onClick = { onToggleRotatePanel(); showMoreMenu = false },
                        tag = "bottom_rotate_panel_toggle_btn"
                    )
                    QuickActionButton(
                        icon = Icons.Default.RotateRight,
                        label = "+0.5°",
                        tint = Amber400,
                        onClick = { onRotateFine(0.5f) },
                        tag = "bottom_rotate_plus_step_btn"
                    )
                }

                HorizontalDivider(color = Slate800)
                Text(
                    text = "UKURAN BOX",
                    color = Slate700,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                // A dedicated button to grow/shrink WIDTH only, and a separate
                // one for HEIGHT only — the buttons are the point here (an
                // alternative to dragging the box's edge handles), so each one
                // is spelled out with a full label rather than a bare icon.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Lebar",
                        color = Slate400,
                        fontSize = 11.sp,
                        modifier = Modifier.width(42.dp)
                    )
                    QuickActionButton(
                        icon = Icons.Default.Remove,
                        label = "Kecil",
                        enabled = hasSelectedBox,
                        onClick = { onResizeBoxWidth(-BOX_SIZE_STEP) },
                        tag = "bottom_width_minus_btn"
                    )
                    QuickActionButton(
                        icon = Icons.Default.Add,
                        label = "Besar",
                        tint = Indigo400,
                        enabled = hasSelectedBox,
                        onClick = { onResizeBoxWidth(BOX_SIZE_STEP) },
                        tag = "bottom_width_plus_btn"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Tinggi",
                        color = Slate400,
                        fontSize = 11.sp,
                        modifier = Modifier.width(42.dp)
                    )
                    QuickActionButton(
                        icon = Icons.Default.Remove,
                        label = "Kecil",
                        enabled = hasSelectedBox,
                        onClick = { onResizeBoxHeight(-BOX_SIZE_STEP) },
                        tag = "bottom_height_minus_btn"
                    )
                    QuickActionButton(
                        icon = Icons.Default.Add,
                        label = "Besar",
                        tint = Indigo400,
                        enabled = hasSelectedBox,
                        onClick = { onResizeBoxHeight(BOX_SIZE_STEP) },
                        tag = "bottom_height_plus_btn"
                    )
                }

                HorizontalDivider(color = Slate800)
                Text(
                    text = "TAMPILAN",
                    color = Slate700,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    QuickActionButton(
                        icon = Icons.Default.Remove,
                        label = "Zoom-",
                        onClick = onZoomOut,
                        tag = "bottom_zoom_out_btn"
                    )
                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 2.dp)
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
                }
                DropdownMenuItem(
                    text = { Text(if (isCrosshairEnabled) "Guide: Aktif" else "Guide: Nonaktif", color = if (isCrosshairEnabled) Indigo400 else Slate400) },
                    leadingIcon = { Icon(Icons.Outlined.FilterCenterFocus, contentDescription = null, tint = if (isCrosshairEnabled) Indigo400 else Slate400) },
                    onClick = onToggleCrosshair,
                    modifier = Modifier.testTag("bottom_crosshair_btn")
                )
                DropdownMenuItem(
                    text = { Text(if (isSnappingEnabled) "Snap: Aktif" else "Snap: Nonaktif", color = if (isSnappingEnabled) Indigo400 else Slate400) },
                    leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = if (isSnappingEnabled) Indigo400 else Slate400) },
                    onClick = onToggleSnapping,
                    modifier = Modifier.testTag("bottom_snap_btn")
                )

                HorizontalDivider(color = Slate800)
                DropdownMenuItem(
                    text = { Text("Halaman Awal", color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.FirstPage, contentDescription = null, tint = Slate400) },
                    enabled = currentPage > 1,
                    onClick = { onFirstPage(); showMoreMenu = false },
                    modifier = Modifier.testTag("bottom_first_page_btn")
                )
                DropdownMenuItem(
                    text = { Text("Halaman Akhir", color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.LastPage, contentDescription = null, tint = Slate400) },
                    enabled = currentPage < totalPages,
                    onClick = { onLastPage(); showMoreMenu = false },
                    modifier = Modifier.testTag("bottom_last_page_btn")
                )
            }
        }
    }
}

/** Normalized step (fraction of page width/height) per width/height button tap. */
private const val BOX_SIZE_STEP = 0.02f

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
