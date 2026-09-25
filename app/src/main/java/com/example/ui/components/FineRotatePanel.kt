package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.util.Locale

@Composable
fun FineRotatePanel(
    currentRotation: Float,
    isAlignmentGridEnabled: Boolean,
    onRotateFine: (Float) -> Unit,
    onSetRotation: (Float) -> Unit,
    onResetRotation: () -> Unit,
    onRotate90: () -> Unit,
    onToggleAlignmentGrid: () -> Unit,
    onClose: () -> Unit,
    onNudgeBox: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onResizeBox: (dWidth: Float, dHeight: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(16.dp, RoundedCornerShape(14.dp))
            .border(1.dp, Slate700.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .testTag("fine_rotate_panel"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title, Subtitle, Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Amber400.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Atur Box: Posisi, Ukuran & Rotasi",
                            color = Slate100,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Panel ini tetap di atas kanvas — box tetap terlihat sambil disesuaikan",
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("close_rotate_panel_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Panel Rotasi",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Position (nudge) + Size — compact, single-tap adjustments that
            // stay visible against the canvas below since this whole panel
            // is a small top-anchored card, not a full-screen menu.
            Text(text = "POSISI", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BoxAdjustButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    onClick = { onNudgeBox(-BOX_ADJUST_STEP, 0f) },
                    tag = "panel_nudge_left_btn",
                    modifier = Modifier.weight(1f)
                )
                BoxAdjustButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    onClick = { onNudgeBox(0f, -BOX_ADJUST_STEP) },
                    tag = "panel_nudge_up_btn",
                    modifier = Modifier.weight(1f)
                )
                BoxAdjustButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    onClick = { onNudgeBox(0f, BOX_ADJUST_STEP) },
                    tag = "panel_nudge_down_btn",
                    modifier = Modifier.weight(1f)
                )
                BoxAdjustButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    onClick = { onNudgeBox(BOX_ADJUST_STEP, 0f) },
                    tag = "panel_nudge_right_btn",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = "UKURAN", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Lebar", color = Slate400, fontSize = 10.sp, modifier = Modifier.width(34.dp))
                    BoxAdjustButton(
                        icon = Icons.Default.Remove,
                        onClick = { onResizeBox(-BOX_ADJUST_STEP, 0f) },
                        tag = "panel_width_minus_btn",
                        modifier = Modifier.weight(1f)
                    )
                    BoxAdjustButton(
                        icon = Icons.Default.Add,
                        onClick = { onResizeBox(BOX_ADJUST_STEP, 0f) },
                        tag = "panel_width_plus_btn",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Tinggi", color = Slate400, fontSize = 10.sp, modifier = Modifier.width(34.dp))
                    BoxAdjustButton(
                        icon = Icons.Default.Remove,
                        onClick = { onResizeBox(0f, -BOX_ADJUST_STEP) },
                        tag = "panel_height_minus_btn",
                        modifier = Modifier.weight(1f)
                    )
                    BoxAdjustButton(
                        icon = Icons.Default.Add,
                        onClick = { onResizeBox(0f, BOX_ADJUST_STEP) },
                        tag = "panel_height_plus_btn",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(color = Slate800)
            Spacer(modifier = Modifier.height(10.dp))

            Text(text = "ROTASI", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            // Angle display & Status banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate950, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formattedAngle = if (currentRotation > 0f) {
                    "+${String.format(Locale.US, "%.1f", currentRotation)}°"
                } else {
                    "${String.format(Locale.US, "%.1f", currentRotation)}°"
                }

                val statusText = when {
                    kotlin.math.abs(currentRotation) < 0.05f -> "Lurus (0.0°)"
                    currentRotation > 0f -> "Miring Kanan"
                    else -> "Miring Kiri"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sudut: ",
                        color = Slate400,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formattedAngle,
                        color = if (kotlin.math.abs(currentRotation) < 0.05f) Emerald400 else Amber400,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (kotlin.math.abs(currentRotation) < 0.05f) Emerald400.copy(alpha = 0.15f) else Amber400.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = if (kotlin.math.abs(currentRotation) < 0.05f) Emerald400 else Amber400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Reset button
                FilledTonalButton(
                    onClick = onResetRotation,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Slate800,
                        contentColor = Slate100
                    ),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("reset_rotation_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = Slate100,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reset 0°", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Continuous Slider (-20° to +20°)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "-20°", color = Slate400, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Slider(
                    value = currentRotation.coerceIn(-20f, 20f),
                    onValueChange = { onSetRotation(it) },
                    valueRange = -20f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = Amber400,
                        activeTrackColor = Amber400,
                        inactiveTrackColor = Slate800
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("rotation_slider")
                )
                Text(text = "+20°", color = Slate400, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Stepper buttons for incremental fine adjustments
            Text(
                text = "Putar Dikit-Dikit (Micro Steppers):",
                color = Slate400,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FineStepButton(label = "-5.0°", delta = -5.0f, onClick = { onRotateFine(-5.0f) }, modifier = Modifier.weight(1f))
                FineStepButton(label = "-1.0°", delta = -1.0f, onClick = { onRotateFine(-1.0f) }, modifier = Modifier.weight(1f))
                FineStepButton(label = "-0.5°", delta = -0.5f, onClick = { onRotateFine(-0.5f) }, modifier = Modifier.weight(1f))
                FineStepButton(label = "-0.1°", delta = -0.1f, onClick = { onRotateFine(-0.1f) }, modifier = Modifier.weight(1f))
                FineStepButton(label = "+0.1°", delta = 0.1f, onClick = { onRotateFine(0.1f) }, modifier = Modifier.weight(1f))
                FineStepButton(label = "+0.5°", delta = 0.5f, onClick = { onRotateFine(0.5f) }, modifier = Modifier.weight(1f))
                FineStepButton(label = "+1.0°", delta = 1.0f, onClick = { onRotateFine(1.0f) }, modifier = Modifier.weight(1f))
                FineStepButton(label = "+5.0°", delta = 5.0f, onClick = { onRotateFine(5.0f) }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tools & Grid Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle Horizontal Alignment Grid
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleAlignmentGrid() }
                        .background(if (isAlignmentGridEnabled) Indigo600.copy(alpha = 0.25f) else Slate800)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("toggle_grid_guide_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = if (isAlignmentGridEnabled) Indigo400 else Slate400,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAlignmentGridEnabled) "Garis Panduan Aktif" else "Tampilkan Garis Panduan",
                        color = if (isAlignmentGridEnabled) Indigo400 else Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Optional 90 degree flip
                OutlinedButton(
                    onClick = onRotate90,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Slate800.copy(alpha = 0.5f),
                        contentColor = Slate100
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("rotate_90_flip_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = null,
                        tint = Slate100,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Putar +90°", fontSize = 10.sp)
                }
            }
        }
    }
}

/** Normalized step (fraction of page width/height) per position/size button tap. */
private const val BOX_ADJUST_STEP = 0.01f

@Composable
private fun BoxAdjustButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .height(30.dp)
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

@Composable
private fun FineStepButton(
    label: String,
    delta: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositive = delta > 0f
    val isMicro = kotlin.math.abs(delta) <= 0.5f
    val highlightColor = if (isPositive) Amber400 else Indigo400

    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(5.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isMicro) Slate800 else Slate950,
            contentColor = highlightColor
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 0.dp),
        modifier = modifier
            .height(28.dp)
            .testTag("step_btn_${label.replace(".", "_").replace("+", "p").replace("-", "m")}")
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isMicro) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = highlightColor
        )
    }
}
