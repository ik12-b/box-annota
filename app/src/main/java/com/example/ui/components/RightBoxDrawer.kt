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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnnotationBox
import com.example.model.LabelClass
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber950
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo950
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose950
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.util.Locale

@Composable
fun RightBoxDrawer(
    currentPage: Int,
    boxes: List<AnnotationBox>,
    classes: List<LabelClass>,
    selectedBoxIds: Set<String>,
    primarySelectedBox: AnnotationBox?,
    hasOverlap: Boolean,
    totalBoxesAllPages: Int,
    onBoxSelected: (String) -> Unit,
    onDeleteBox: (String) -> Unit,
    onNudgeBox: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onResizeBox: (dWidth: Float, dHeight: Float) -> Unit = { _, _ -> },
    onResetProject: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
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
                    imageVector = Icons.Default.ListAlt,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BOX HALAMAN $currentPage",
                    color = Slate100,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Slate800, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${boxes.size} Box",
                        color = Slate100,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.size(28.dp).testTag("close_boxes_drawer_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Overlap Warning Banner
        if (hasOverlap) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Amber950.copy(alpha = 0.8f))
                    .border(1.dp, Amber400.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Terdeteksi box tumpang tindih berlebihan (IoU > 35%)!",
                        color = Amber400,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider(color = Slate800)
        Spacer(modifier = Modifier.height(8.dp))

        // List of boxes
        if (boxes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Belum ada Bounding Box",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tarik jari di atas dokumen untuk membuat box baru.",
                        color = Slate400.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(boxes) { index, box ->
                    val isSelected = box.id in selectedBoxIds
                    val cls = classes.find { it.id == box.classId } ?: LabelClass(box.classId, "Unknown", 0xFF6366F1L)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Indigo950 else Slate800.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Indigo500 else Slate800,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onBoxSelected(box.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("box_item_${box.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(cls.composeColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "#${index + 1} ${cls.name}",
                                    color = Slate100,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "[%.2f, %.2f, %.2f, %.2f]",
                                        box.x, box.y, box.width, box.height
                                    ),
                                    color = Slate400,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        IconButton(
                            onClick = { onDeleteBox(box.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Box",
                                tint = Rose400,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Slate800)
        Spacer(modifier = Modifier.height(8.dp))

        // Selected Box Inspector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Slate950)
                .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = Indigo400,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE NATIVE INFO",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                val primaryCls = primarySelectedBox?.let { b -> classes.find { it.id == b.classId } }
                Box(
                    modifier = Modifier
                        .background(Slate800, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = primaryCls?.name ?: "None",
                        color = primaryCls?.composeColor ?: Slate400,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (primarySelectedBox != null) {
                val b = primarySelectedBox
                Text(
                    text = "X: ${String.format(Locale.US, "%.3f", b.x)}   Y: ${String.format(Locale.US, "%.3f", b.y)}\n" +
                            "W: ${String.format(Locale.US, "%.3f", b.width)}   H: ${String.format(Locale.US, "%.3f", b.height)}",
                    color = Slate100,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Slate800)
                Spacer(modifier = Modifier.height(8.dp))

                // Move/resize via buttons — an alternative to dragging on the
                // canvas for small, precise adjustments that are fiddly to
                // land exactly right with a fingertip.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "POSISI",
                            color = Slate400,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        NudgeButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            onClick = { onNudgeBox(0f, -NUDGE_STEP) },
                            tag = "nudge_up_btn"
                        )
                        Row {
                            NudgeButton(
                                icon = Icons.Default.KeyboardArrowLeft,
                                onClick = { onNudgeBox(-NUDGE_STEP, 0f) },
                                tag = "nudge_left_btn"
                            )
                            Spacer(modifier = Modifier.width(28.dp))
                            NudgeButton(
                                icon = Icons.Default.KeyboardArrowRight,
                                onClick = { onNudgeBox(NUDGE_STEP, 0f) },
                                tag = "nudge_right_btn"
                            )
                        }
                        NudgeButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            onClick = { onNudgeBox(0f, NUDGE_STEP) },
                            tag = "nudge_down_btn"
                        )
                    }

                    Column {
                        Text(
                            text = "UKURAN",
                            color = Slate400,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Lebar",
                                color = Slate400,
                                fontSize = 10.sp,
                                modifier = Modifier.width(38.dp)
                            )
                            NudgeButton(
                                icon = Icons.Default.Remove,
                                onClick = { onResizeBox(-NUDGE_STEP, 0f) },
                                tag = "resize_width_minus_btn"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            NudgeButton(
                                icon = Icons.Default.Add,
                                onClick = { onResizeBox(NUDGE_STEP, 0f) },
                                tag = "resize_width_plus_btn"
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tinggi",
                                color = Slate400,
                                fontSize = 10.sp,
                                modifier = Modifier.width(38.dp)
                            )
                            NudgeButton(
                                icon = Icons.Default.Remove,
                                onClick = { onResizeBox(0f, -NUDGE_STEP) },
                                tag = "resize_height_minus_btn"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            NudgeButton(
                                icon = Icons.Default.Add,
                                onClick = { onResizeBox(0f, NUDGE_STEP) },
                                tag = "resize_height_plus_btn"
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Pilih bounding box untuk melihat info koordinat dan kelas aktif.",
                    color = Slate400,
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Document stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Total Box Semua Hal:", color = Slate400, fontSize = 11.sp)
            Text(
                text = "$totalBoxesAllPages",
                color = Indigo400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reset project button
        OutlinedButton(
            onClick = onResetProject,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Slate800.copy(alpha = 0.5f),
                contentColor = Rose400
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .testTag("reset_project_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Rose400,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Mulai Baru (Hapus Sesi)", fontSize = 11.sp)
        }
    }
}

/** Normalized step (fraction of page width/height) per button tap. */
private const val NUDGE_STEP = 0.01f

@Composable
private fun NudgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .background(Slate800, RoundedCornerShape(6.dp))
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Indigo400,
            modifier = Modifier.size(16.dp)
        )
    }
}
