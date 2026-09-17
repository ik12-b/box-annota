package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Indigo950
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun AnnotatorToolbar(
    currentPage: Int,
    totalPages: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFirstPage: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onLastPage: () -> Unit,
    onAutoAdvance: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    currentRotation: Float = 0f,
    onToggleRotatePanel: () -> Unit = {},
    onOpenPdf: () -> Unit,
    onExportDataset: () -> Unit,
    onSaveProject: () -> Unit,
    onToggleLeftDrawer: () -> Unit,
    onToggleRightDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Slate900)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left section: Drawer trigger & App Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onToggleLeftDrawer,
                modifier = Modifier.testTag("toggle_class_drawer_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = "Kelas Label",
                    tint = Indigo400
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Indigo600, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "PDF Annotator",
                color = Slate100,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .background(Indigo950, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "PRO",
                    color = Indigo400,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Center / Right actions: Open PDF, Save, Export, and Box Drawer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onOpenPdf,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Slate800,
                    contentColor = Slate100
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp).testTag("open_pdf_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(16.dp)
                )
                Text(text = " Buka PDF", fontSize = 12.sp)
            }

            // Fine rotation / deskew trigger
            val hasActiveRotation = kotlin.math.abs(currentRotation) >= 0.05f
            IconButton(
                onClick = onToggleRotatePanel,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (hasActiveRotation) Amber400.copy(alpha = 0.2f) else Slate800,
                    contentColor = if (hasActiveRotation) Amber400 else Slate100
                ),
                modifier = Modifier
                    .size(36.dp)
                    .testTag("toolbar_toggle_rotate_panel_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Rotasi Presisi & Deskew",
                    tint = if (hasActiveRotation) Amber400 else Slate100,
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = onExportDataset,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp).testTag("export_dataset_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(text = " Export", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            IconButton(
                onClick = onToggleRightDrawer,
                modifier = Modifier.testTag("toggle_boxes_drawer_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Daftar Box",
                    tint = Slate100
                )
            }
        }
    }
}
