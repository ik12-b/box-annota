package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TranscriptionLine
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Text

@Composable
fun TranscriptionModeScreen(
    currentIndex: Int,
    totalLines: Int,
    filledCount: Int,
    line: TranscriptionLine?,
    className: String,
    isAutoTranscribing: Boolean = false,
    autoTranscribeCurrent: Int = 0,
    autoTranscribeTotal: Int = 0,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onTextChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenGeminiSettings: () -> Unit = {},
    onAutoTranscribeCurrent: () -> Unit = {},
    onAutoTranscribeAll: () -> Unit = {},
    onCancelAutoTranscribe: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.background(Slate950),
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Slate900)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("transcription_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke Labeling", tint = Slate100)
                    }
                    Icon(Icons.Default.Translate, contentDescription = null, tint = Indigo400, modifier = Modifier.size(18.dp))
                    Text(text = "Mode Transkripsi", color = Slate100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier.background(Slate800, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${(currentIndex + 1).coerceAtMost(totalLines)}/$totalLines · $filledCount terisi",
                            color = Indigo400,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onOpenGeminiSettings,
                        modifier = Modifier.size(32.dp).testTag("gemini_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan Gemini", tint = Slate400, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onAutoTranscribeAll,
                        enabled = !isAutoTranscribing,
                        modifier = Modifier.size(32.dp).testTag("gemini_auto_all_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Transkripsi Otomatis Semua", tint = Indigo400, modifier = Modifier.size(18.dp))
                    }
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp).testTag("transcription_export_btn")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(text = " Export", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Non-blocking batch progress — the app stays fully usable while a
            // Gemini batch call is in flight, since each request is a network
            // round-trip that can take a while.
            if (isAutoTranscribing && autoTranscribeTotal > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate800)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-transkripsi Gemini: $autoTranscribeCurrent/$autoTranscribeTotal",
                            color = Indigo400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { if (autoTranscribeTotal > 0) autoTranscribeCurrent.toFloat() / autoTranscribeTotal else 0f },
                            color = Indigo500,
                            trackColor = Slate950,
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                    IconButton(onClick = onCancelAutoTranscribe, modifier = Modifier.size(28.dp).testTag("gemini_cancel_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Batalkan", tint = Slate400, modifier = Modifier.size(16.dp))
                    }
                }
            }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    enabled = currentIndex > 0,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("transcription_prev_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = " Sebelumnya", fontSize = 12.sp)
                }
                Button(
                    onClick = onNext,
                    enabled = currentIndex < totalLines - 1,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("transcription_next_btn")
                ) {
                    Text(text = "Selanjutnya ", fontSize = 12.sp, color = Slate950, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Slate950, modifier = Modifier.size(16.dp))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            LinearProgressIndicator(
                progress = { if (totalLines > 0) (currentIndex + 1f) / totalLines else 0f },
                color = Indigo500,
                trackColor = Slate800,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (line == null) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Tidak ada baris untuk ditranskripsi.", color = Slate400, fontSize = 13.sp)
                }
                return@Column
            }

            // Cropped line image preview. Bounded (not weight(1f)/fillMaxSize)
            // so a small or thin crop doesn't get stranded in a huge box full
            // of dead space — the box sizes to a sensible range and the rest
            // of the screen stays compact around it instead.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 280.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                var bitmap by remember(line.cropFilePath) { mutableStateOf<Bitmap?>(null) }
                var failed by remember(line.cropFilePath) { mutableStateOf(false) }

                LaunchedEffect(line.cropFilePath) {
                    bitmap = null
                    failed = false
                    val decoded = withContext(Dispatchers.IO) {
                        try { BitmapFactory.decodeFile(line.cropFilePath) } catch (_: Exception) { null }
                    }
                    if (decoded != null) bitmap = decoded else failed = true
                }

                when {
                    bitmap != null -> Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Potongan baris",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    failed -> Text(text = "Gagal memuat gambar potongan.", color = Color.Red, fontSize = 12.sp)
                    else -> CircularProgressIndicator(color = Indigo500)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.background(Indigo600.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = className, color = Indigo400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Halaman ${line.pageNumber}", color = Slate400, fontSize = 10.sp)
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onAutoTranscribeCurrent,
                    enabled = !isAutoTranscribing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Indigo400),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp).testTag("gemini_auto_current_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                    Text(text = " Isi Otomatis", fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = line.text,
                onValueChange = onTextChange,
                placeholder = { Text("Ketik transkripsi teks pada gambar di atas...", color = Slate700, fontSize = 13.sp) },
                minLines = 2,
                maxLines = 5,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, textAlign = TextAlign.Start),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100,
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900,
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = Slate800
                ),
                modifier = Modifier.fillMaxWidth().testTag("transcription_text_input")
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
