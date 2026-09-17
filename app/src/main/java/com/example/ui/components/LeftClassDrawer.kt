package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LabelClass
import com.example.ui.theme.Amber400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Indigo950
import com.example.ui.theme.Rose400
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun LeftClassDrawer(
    classes: List<LabelClass>,
    activeClassId: Int,
    onClassSelected: (Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onOpenAddClassDialog: () -> Unit,
    onOpenPresetDialog: () -> Unit,
    onCloseDrawer: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    currentRotation: Float = 0f,
    onRotateFine: (Float) -> Unit = {},
    onResetRotation: () -> Unit = {},
    onOpenRotatePanel: () -> Unit = {},
    onRotateLeft: () -> Unit = { onRotateFine(-0.5f) },
    onRotateRight: () -> Unit = { onRotateFine(0.5f) },
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(290.dp)
            .background(Slate900)
            .padding(12.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "KELAS LABEL",
                    color = Slate100,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            IconButton(
                onClick = onCloseDrawer,
                modifier = Modifier.size(28.dp).testTag("close_class_drawer_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = Slate400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset & Add Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenPresetDialog,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Slate800,
                    contentColor = Slate100
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("preset_classes_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Preset", fontSize = 11.sp)
            }

            Button(
                onClick = onOpenAddClassDialog,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("add_class_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Tambah", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Slate800)
        Spacer(modifier = Modifier.height(8.dp))

        // Class List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(classes) { index, cls ->
                val isActive = cls.id == activeClassId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Indigo950 else Slate800.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = if (isActive) Indigo500 else Slate800,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onClassSelected(cls.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("class_item_${cls.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(cls.composeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cls.name,
                            color = if (isActive) Color.White else Slate100,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleVisibility(cls.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (cls.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = if (cls.visible) Slate400 else Slate700,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "[${index + 1}]",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Slate800)
        Spacer(modifier = Modifier.height(8.dp))

        // Shortcut Quick Panel
        Text(
            text = "Shortcut Cepat:",
            color = Slate400,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        val rotText = if (kotlin.math.abs(currentRotation) < 0.05f) "0.0°"
            else if (currentRotation > 0f) "+${String.format(java.util.Locale.US, "%.1f", currentRotation)}°"
            else "${String.format(java.util.Locale.US, "%.1f", currentRotation)}°"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = { onRotateFine(-0.5f) },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Slate800.copy(alpha = 0.6f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .testTag("drawer_rotate_minus_step_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.RotateLeft,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "-0.5°", fontSize = 10.sp, color = Amber400)
            }

            OutlinedButton(
                onClick = onResetRotation,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Slate800.copy(alpha = 0.6f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(32.dp)
                    .testTag("drawer_rotate_reset_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = Slate100,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = rotText, fontSize = 10.sp, color = Slate100, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { onRotateFine(0.5f) },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Slate800.copy(alpha = 0.6f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .testTag("drawer_rotate_plus_step_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "+0.5°", fontSize = 10.sp, color = Amber400)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = onOpenRotatePanel,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Slate800.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag("drawer_open_rotate_panel_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = Amber400,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Panel Rotasi Presisi & Grid", fontSize = 11.sp, color = Amber400)
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = onDeleteSelected,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Slate800.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Rose400,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Hapus Box Terpilih", fontSize = 10.sp, color = Rose400)
        }
    }
}
