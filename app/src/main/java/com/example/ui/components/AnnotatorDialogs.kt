package com.example.ui.components

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.model.ExportFormat
import com.example.model.LabelClass
import com.example.model.LabelPresets
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Indigo950
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.io.File

private val PALETTE_COLORS = listOf(
    0xFFEF4444L, // Red
    0xFFEC4899L, // Pink
    0xFF8B5CF6L, // Purple
    0xFF6366F1L, // Indigo
    0xFF3B82F6L, // Blue
    0xFF06B6D4L, // Cyan
    0xFF10B981L, // Emerald
    0xFFF59E0BL, // Amber
    0xFFF97316L  // Orange
)

@Composable
fun AddClassDialog(
    onDismiss: () -> Unit,
    onSaveClass: (name: String, colorValue: Long) -> Unit
) {
    var className by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PALETTE_COLORS[3]) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier.fillMaxWidth().testTag("add_class_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tambah Kelas Label Baru",
                        color = Slate100,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Batal", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Nama Kelas (contoh: Title, Paragraph, Total Amount, Table):",
                    color = Slate400,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    placeholder = { Text("Masukkan nama kelas...", color = Slate700, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate950,
                        unfocusedContainerColor = Slate950,
                        focusedBorderColor = Indigo500,
                        unfocusedBorderColor = Slate800
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_class_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Pilih Warna Bounding Box:", color = Slate400, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PALETTE_COLORS) { colorVal ->
                        val isPicked = colorVal == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .border(
                                    width = if (isPicked) 3.dp else 1.dp,
                                    color = if (isPicked) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorVal }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (className.isNotBlank()) {
                                onSaveClass(className, selectedColor)
                            }
                        },
                        enabled = className.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_class_confirm_btn")
                    ) {
                        Text("Simpan Kelas")
                    }
                }
            }
        }
    }
}

@Composable
fun PresetClassesDialog(
    onDismiss: () -> Unit,
    onSelectPreset: (List<LabelClass>) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier.fillMaxWidth().testTag("preset_classes_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = Indigo400)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Preset Kelas Label",
                            color = Slate100,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                PresetCard(
                    title = "1. Dokumen & Artikel Umum",
                    description = "Title, Subtitle, Paragraph, Table, Image, Footer",
                    onClick = { onSelectPreset(LabelPresets.GENERAL) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PresetCard(
                    title = "2. Struk, Tagihan, & Faktur (Invoices)",
                    description = "Vendor Name, Invoice Date, Customer Detail, Line Item, Total Amount, Signature",
                    onClick = { onSelectPreset(LabelPresets.INVOICE) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PresetCard(
                    title = "3. Formulir & Dokumen Identitas",
                    description = "Field Label, Input Value, Checkbox, Signature, Official Stamp",
                    onClick = { onSelectPreset(LabelPresets.FORM) }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Slate950)
            .border(1.dp, Slate800, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Text(text = title, color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = description, color = Slate400, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun ExportDatasetDialog(
    totalPages: Int,
    isExporting: Boolean,
    exportProgress: Float,
    exportStatus: String,
    exportedZipFile: File?,
    onDismiss: () -> Unit,
    onStartExport: (
        format: ExportFormat,
        startPage: Int,
        endPage: Int,
        includeImages: Boolean,
        onlyAnnotated: Boolean,
        splitDataset: Boolean,
        trainRatio: Float
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.YOLO) }
    var startPageStr by remember { mutableStateOf("1") }
    var endPageStr by remember { mutableStateOf(totalPages.toString()) }
    var includeImages by remember { mutableStateOf(true) }
    var onlyAnnotated by remember { mutableStateOf(false) }
    var splitDataset by remember { mutableStateOf(true) }
    var trainRatioPct by remember { mutableFloatStateOf(80f) }

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier.fillMaxWidth().testTag("export_dataset_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Indigo400)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export Dataset OCR",
                            color = Slate100,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isExporting) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate400)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Format selection
                Text(text = "Format Anotasi:", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormat.values().forEach { fmt ->
                        val isSel = fmt == selectedFormat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Indigo950 else Slate950)
                                .border(1.dp, if (isSel) Indigo500 else Slate800, RoundedCornerShape(8.dp))
                                .clickable { selectedFormat = fmt }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = fmt.displayName,
                                    color = if (isSel) Color.White else Slate100,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = fmt.extension, color = Slate400, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Page Range Box
                Box(
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
                        Text(text = "Rentang Halaman:", color = Slate100, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = startPageStr,
                                onValueChange = { startPageStr = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Slate100,
                                    unfocusedTextColor = Slate100,
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900,
                                    focusedBorderColor = Indigo500,
                                    unfocusedBorderColor = Slate800
                                ),
                                modifier = Modifier.width(54.dp).height(42.dp)
                            )
                            Text(text = " s/d ", color = Slate400, fontSize = 11.sp)
                            OutlinedTextField(
                                value = endPageStr,
                                onValueChange = { endPageStr = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Slate100,
                                    unfocusedTextColor = Slate100,
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900,
                                    focusedBorderColor = Indigo500,
                                    unfocusedBorderColor = Slate800
                                ),
                                modifier = Modifier.width(54.dp).height(42.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Options: Include images, only annotated
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeImages,
                        onCheckedChange = { includeImages = it },
                        colors = CheckboxDefaults.colors(checkedColor = Indigo500)
                    )
                    Text(text = "Sertakan Gambar Halaman (.png)", color = Slate100, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = onlyAnnotated,
                        onCheckedChange = { onlyAnnotated = it },
                        colors = CheckboxDefaults.colors(checkedColor = Indigo500)
                    )
                    Text(text = "Hanya halaman dengan Bounding Box", color = Slate100, fontSize = 11.sp)
                }

                // Train / Val split
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = splitDataset,
                        onCheckedChange = { splitDataset = it },
                        colors = CheckboxDefaults.colors(checkedColor = Indigo500)
                    )
                    Text(text = "Susun dataset siap-latih (Train/Val)", color = Slate100, fontSize = 11.sp)
                }

                if (splitDataset) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = trainRatioPct,
                            onValueChange = { trainRatioPct = it },
                            valueRange = 50f..95f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = Indigo500,
                                activeTrackColor = Indigo500,
                                inactiveTrackColor = Slate800
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${trainRatioPct.toInt()}% / ${(100 - trainRatioPct).toInt()}%",
                            color = Indigo400,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Progress Bar
                if (isExporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = exportStatus, color = Slate400, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        color = Indigo500,
                        trackColor = Slate800,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }

                // Exported Finished & Share Button
                if (exportedZipFile != null && !isExporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Emerald400.copy(alpha = 0.15f))
                            .border(1.dp, Emerald400.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(text = "ZIP Berhasil Dibuat!", color = Emerald400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = exportedZipFile.name, color = Slate100, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    shareZipFile(context, exportedZipFile)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().height(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Slate950, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Bagikan / Simpan File ZIP", color = Slate950, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isExporting,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Tutup")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val startP = startPageStr.toIntOrNull() ?: 1
                            val endP = endPageStr.toIntOrNull() ?: totalPages
                            onStartExport(
                                selectedFormat,
                                startP,
                                endP,
                                includeImages,
                                onlyAnnotated,
                                splitDataset,
                                trainRatioPct / 100f
                            )
                        },
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_export_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isExporting) "Memproses..." else "Download ZIP")
                    }
                }
            }
        }
    }
}

private fun shareZipFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Dataset ZIP"))
    } catch (_: Exception) {}
}
