package com.example.util

import android.content.Context
import com.example.model.LabelClass
import com.example.model.TranscriptionLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports the current transcription-mode work as its own line-recognition
 * dataset (crop images + a tab-separated label file, the layout PaddleOCR-style
 * recognition training expects) — deliberately a completely separate output
 * from [DatasetExporter]'s bounding-box dataset, so saving in one mode never
 * touches or overwrites the other mode's result.
 */
class TranscriptionExporter(private val context: Context) {

    suspend fun exportDataset(
        baseFileName: String,
        lines: List<TranscriptionLine>,
        classes: List<LabelClass>,
        onlyFilled: Boolean,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val toExport = if (onlyFilled) lines.filter { it.text.isNotBlank() } else lines
        if (toExport.isEmpty()) {
            throw IllegalArgumentException("Tidak ada baris transkripsi untuk diekspor.")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputZip = File(context.cacheDir, "${baseFileName}_transcription_$timestamp.zip")
        if (outputZip.exists()) outputZip.delete()

        ZipOutputStream(FileOutputStream(outputZip)).use { zip ->
            val labelLines = mutableListOf<String>()

            for ((index, line) in toExport.withIndex()) {
                val progress = index.toFloat() / toExport.size.toFloat()
                onProgress(progress, "Menyalin baris ${index + 1}/${toExport.size}...")

                val cropFile = File(line.cropFilePath)
                if (!cropFile.exists()) continue

                val imgEntryName = "images/${cropFile.name}"
                FileInputStream(cropFile).use { input ->
                    zip.putNextEntry(ZipEntry(imgEntryName))
                    input.copyTo(zip)
                    zip.closeEntry()
                }

                // PaddleOCR recognition label format: <relative image path>\t<transcription>
                // Tabs/newlines inside the transcription would break the format, so
                // they're normalized to single spaces.
                val safeText = line.text.replace("\t", " ").replace("\n", " ").trim()
                labelLines.add("$imgEntryName\t$safeText")
            }

            zipWriteFile(zip, "labels.txt", labelLines.joinToString("\n").toByteArray())
            zipWriteFile(zip, "classes.txt", classes.joinToString("\n") { it.name }.toByteArray())

            val filledCount = lines.count { it.text.isNotBlank() }
            val readme = """
                # $baseFileName — Transcription Line Dataset

                - Dibuat pada: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
                - Total baris diekspor: ${toExport.size}
                - Baris dengan teks terisi: $filledCount / ${lines.size}

                ## Struktur Direktori
                images/ — potongan gambar per baris (dipotong dari bounding box mode labeling)
                labels.txt — format PaddleOCR: `images/<file>.jpg<TAB><teks transkripsi>` per baris

                Dataset ini terpisah dari dataset bounding box (mode Labeling) dan siap
                digunakan untuk melatih model pengenalan teks (text recognition / OCR).
            """.trimIndent()
            zipWriteFile(zip, "README.md", readme.toByteArray())
        }

        onProgress(1f, "Selesai mengompresi dataset transkripsi.")
        outputZip
    }

    private fun zipWriteFile(zip: ZipOutputStream, entryName: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(data)
        zip.closeEntry()
    }
}
