package com.example.util

import android.content.Context
import android.graphics.Bitmap
import com.example.model.AnnotationBox
import com.example.model.ExportFormat
import com.example.model.LabelClass
import com.example.pdf.PdfManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DatasetExporter(private val context: Context) {

    suspend fun exportDataset(
        pdfManager: PdfManager,
        baseFileName: String,
        format: ExportFormat,
        startPage: Int,
        endPage: Int,
        includeImages: Boolean,
        onlyAnnotated: Boolean,
        splitDataset: Boolean,
        trainRatio: Float,
        classes: List<LabelClass>,
        pageAnnotations: Map<Int, List<AnnotationBox>>,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val totalPages = pdfManager.pageCount
        val sPage = startPage.coerceIn(1, totalPages)
        val ePage = endPage.coerceIn(sPage, totalPages)

        val pagesToExport = mutableListOf<Int>()
        for (p in sPage..ePage) {
            val hasBoxes = !pageAnnotations[p].isNullOrEmpty()
            if (!onlyAnnotated || hasBoxes) {
                pagesToExport.add(p)
            }
        }

        if (pagesToExport.isEmpty()) {
            throw IllegalArgumentException("Tidak ada halaman yang memenuhi kriteria ekspor.")
        }

        val effectiveSplit = splitDataset && pagesToExport.size > 1
        val pageSplitMap = mutableMapOf<Int, String>()
        if (effectiveSplit) {
            val shuffled = pagesToExport.shuffled()
            val trainCount = (shuffled.size * trainRatio).toInt().coerceIn(1, shuffled.size - 1)
            shuffled.forEachIndexed { idx, pNum ->
                pageSplitMap[pNum] = if (idx < trainCount) "train" else "val"
            }
        } else {
            pagesToExport.forEach { pNum ->
                pageSplitMap[pNum] = "all"
            }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputZip = File(context.cacheDir, "${baseFileName}_dataset_${format.name.lowercase()}_$timestamp.zip")
        if (outputZip.exists()) outputZip.delete()

        ZipOutputStream(FileOutputStream(outputZip)).use { zip ->
            // Write classes.txt
            val classesContent = classes.joinToString("\n") { it.name }
            zipWriteFile(zip, "classes.txt", classesContent.toByteArray())

            // Data structures for COCO
            val cocoCategories = JSONArray()
            classes.forEach { c ->
                cocoCategories.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                })
            }

            fun createCocoShell() = JSONObject().apply {
                put("info", JSONObject().apply {
                    put("description", "PDF Precision OCR Bounding Box Dataset")
                    put("date_created", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                })
                put("categories", cocoCategories)
                put("images", JSONArray())
                put("annotations", JSONArray())
            }

            val cocoBuckets = mapOf(
                "all" to createCocoShell(),
                "train" to createCocoShell(),
                "val" to createCocoShell()
            )
            val cocoAnnoCounters = mutableMapOf("all" to 1, "train" to 1, "val" to 1)
            val vocTrainList = mutableListOf<String>()
            val vocValList = mutableListOf<String>()

            for ((index, pageNum) in pagesToExport.withIndex()) {
                val progress = (index.toFloat() / pagesToExport.size.toFloat())
                onProgress(progress, "Merender halaman $pageNum (${index + 1}/${pagesToExport.size})...")

                val subset = pageSplitMap[pageNum] ?: "all"
                val baseImgName = "${baseFileName}_page_${String.format(Locale.US, "%04d", pageNum)}"

                // Render page bitmap at 1600px width for high dataset training quality.
                // Rotation is a per-box property now (for labeling individually
                // skewed/vertical text), not a whole-page deskew, so the page image
                // itself is exported upright — each box's own angle travels with
                // its annotation entry below instead.
                val bitmap = pdfManager.renderPage(pageNum - 1, targetWidthPx = 1600)
                val width = bitmap?.width ?: 1600
                val height = bitmap?.height ?: 2200

                // Write Image file if requested
                if (includeImages && bitmap != null) {
                    val imgSubdir = when {
                        format == ExportFormat.PASCAL_VOC -> "JPEGImages"
                        effectiveSplit -> "images/$subset"
                        else -> "images"
                    }
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
                    zipWriteFile(zip, "$imgSubdir/$baseImgName.png", stream.toByteArray())
                }

                val boxes = pageAnnotations[pageNum] ?: emptyList()

                when (format) {
                    ExportFormat.YOLO -> {
                        val yoloSubdir = if (effectiveSplit) "labels/$subset" else "labels"
                        // Standard YOLO is 5 columns (class, xc, yc, w, h); a 6th
                        // rotation_deg column is appended so a box's angle isn't
                        // silently lost — plain YOLO parsers reading only the
                        // first 5 columns are unaffected, OBB-aware tooling can
                        // read the extra value.
                        val yoloLines = boxes.map { b ->
                            String.format(
                                Locale.US,
                                "%d %.6f %.6f %.6f %.6f %.2f",
                                b.classId,
                                b.xCenter,
                                b.yCenter,
                                b.width,
                                b.height,
                                b.rotation
                            )
                        }
                        zipWriteFile(zip, "$yoloSubdir/$baseImgName.txt", yoloLines.joinToString("\n").toByteArray())
                    }

                    ExportFormat.COCO -> {
                        val cocoObj = cocoBuckets[subset] ?: cocoBuckets["all"]!!
                        val imagesArr = cocoObj.getJSONArray("images")
                        val annotationsArr = cocoObj.getJSONArray("annotations")

                        imagesArr.put(JSONObject().apply {
                            put("id", pageNum)
                            put("file_name", "$baseImgName.png")
                            put("width", width)
                            put("height", height)
                        })

                        boxes.forEach { b ->
                            val pxX = (b.x * width).toInt()
                            val pxY = (b.y * height).toInt()
                            val pxW = (b.width * width).toInt()
                            val pxH = (b.height * height).toInt()
                            val annoId = cocoAnnoCounters[subset] ?: 1
                            cocoAnnoCounters[subset] = annoId + 1

                            val bboxArr = JSONArray().apply {
                                put(pxX); put(pxY); put(pxW); put(pxH)
                            }
                            annotationsArr.put(JSONObject().apply {
                                put("id", annoId)
                                put("image_id", pageNum)
                                put("category_id", b.classId)
                                put("bbox", bboxArr)
                                put("area", pxW * pxH)
                                put("rotation", b.rotation)
                                put("iscrowd", 0)
                            })
                        }
                    }

                    ExportFormat.PASCAL_VOC -> {
                        val xml = buildString {
                            appendLine("<annotation>")
                            appendLine("  <filename>$baseImgName.png</filename>")
                            appendLine("  <size>")
                            appendLine("    <width>$width</width>")
                            appendLine("    <height>$height</height>")
                            appendLine("    <depth>3</depth>")
                            appendLine("  </size>")
                            boxes.forEach { b ->
                                val cls = classes.find { it.id == b.classId }?.name ?: "text"
                                val xMin = (b.x * width).toInt()
                                val yMin = (b.y * height).toInt()
                                val xMax = ((b.x + b.width) * width).toInt()
                                val yMax = ((b.y + b.height) * height).toInt()
                                appendLine("  <object>")
                                appendLine("    <name>$cls</name>")
                                appendLine("    <rotation>${String.format(Locale.US, "%.2f", b.rotation)}</rotation>")
                                appendLine("    <bndbox>")
                                appendLine("      <xmin>$xMin</xmin>")
                                appendLine("      <ymin>$yMin</ymin>")
                                appendLine("      <xmax>$xMax</xmax>")
                                appendLine("      <ymax>$yMax</ymax>")
                                appendLine("    </bndbox>")
                                appendLine("  </object>")
                            }
                            appendLine("</annotation>")
                        }
                        zipWriteFile(zip, "Annotations/$baseImgName.xml", xml.toByteArray())

                        if (effectiveSplit) {
                            if (subset == "train") vocTrainList.add(baseImgName)
                            else vocValList.add(baseImgName)
                        }
                    }
                }
            }

            // Manifests & Configs
            when (format) {
                ExportFormat.YOLO -> {
                    val namesFormatted = classes.joinToString(", ") { "'${it.name.replace("'", "\\'")}'" }
                    val yaml = """
                        # Generated by PDF Annotator Pro for YOLO Training
                        path: .
                        train: ${if (effectiveSplit) "images/train" else "images"}
                        val: ${if (effectiveSplit) "images/val" else "images"}

                        nc: ${classes.size}
                        names: [$namesFormatted]
                    """.trimIndent()
                    zipWriteFile(zip, "data.yaml", yaml.toByteArray())
                }

                ExportFormat.COCO -> {
                    if (effectiveSplit) {
                        val trainJson = (cocoBuckets["train"] ?: cocoBuckets["all"])?.toString(2) ?: "{}"
                        val valJson = (cocoBuckets["val"] ?: cocoBuckets["all"])?.toString(2) ?: "{}"
                        zipWriteFile(zip, "annotations/instances_train.json", trainJson.toByteArray())
                        zipWriteFile(zip, "annotations/instances_val.json", valJson.toByteArray())
                    } else {
                        val allJson = (cocoBuckets["all"] ?: cocoBuckets["train"])?.toString(2) ?: "{}"
                        zipWriteFile(zip, "annotations.json", allJson.toByteArray())
                    }
                }

                ExportFormat.PASCAL_VOC -> {
                    if (effectiveSplit) {
                        zipWriteFile(zip, "ImageSets/Main/train.txt", vocTrainList.joinToString("\n").toByteArray())
                        zipWriteFile(zip, "ImageSets/Main/val.txt", vocValList.joinToString("\n").toByteArray())
                    }
                }
            }

            // README
            val totalBoxes = pagesToExport.sumOf { pageAnnotations[it]?.size ?: 0 }
            val trainCount = pagesToExport.count { pageSplitMap[it] == "train" }
            val valCount = pagesToExport.count { pageSplitMap[it] == "val" }
            val splitNote = if (effectiveSplit) " (Train: $trainCount, Val: $valCount)" else ""

            val readme = """
                # $baseFileName — OCR & Text Detection Dataset
                
                - Format: ${format.displayName}
                - Dibuat pada: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
                - Total Halaman: ${pagesToExport.size}$splitNote
                - Total Bounding Box: $totalBoxes
                - Jumlah Kelas: ${classes.size}
                - Daftar Kelas: ${classes.joinToString(", ") { it.name }}
                
                ## Struktur Direktori
                ${
                when (format) {
                    ExportFormat.YOLO -> if (effectiveSplit) "images/train, images/val, labels/train, labels/val, data.yaml" else "images/, labels/, data.yaml, classes.txt"
                    ExportFormat.COCO -> if (effectiveSplit) "images/train, images/val, annotations/instances_train.json, instances_val.json" else "images/, annotations.json"
                    ExportFormat.PASCAL_VOC -> "Annotations/, JPEGImages/, ImageSets/Main/"
                }
            }
                
                Dataset ini siap digunakan langsung untuk pelatihan model Machine Learning / Computer Vision.

                ## Rotasi Box
                Box yang diputar (untuk teks miring/vertikal) menyertakan sudut rotasinya
                dalam derajat, searah jarum jam, relatif ke pusat box:
                - YOLO: kolom ke-6 tambahan (`rotation_deg`) setelah `class xc yc w h` —
                  parser YOLO standar yang hanya membaca 5 kolom pertama tidak terpengaruh.
                - COCO: field `"rotation"` pada tiap objek `annotations`.
                - Pascal VOC: tag `<rotation>` di dalam tiap `<object>`.
            """.trimIndent()
            zipWriteFile(zip, "README.md", readme.toByteArray())
        }

        onProgress(1f, "Selesai mengompresi dataset ZIP.")
        outputZip
    }

    private fun zipWriteFile(zip: ZipOutputStream, entryName: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(data)
        zip.closeEntry()
    }
}
