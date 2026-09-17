package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfManager(private val context: Context) {

    private var currentFileDescriptor: ParcelFileDescriptor? = null
    private var currentPdfRenderer: PdfRenderer? = null
    var currentPdfFile: File? = null
        private set

    val pageCount: Int
        get() = currentPdfRenderer?.pageCount ?: 0

    suspend fun loadSamplePdf(): File = withContext(Dispatchers.IO) {
        val sampleFile = File(context.cacheDir, "sample_invoice_document.pdf")
        if (!sampleFile.exists() || sampleFile.length() == 0L) {
            createSamplePdfFile(sampleFile)
        }
        openPdfFile(sampleFile)
        sampleFile
    }

    suspend fun openPdfFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "imported_document_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream from Uri: $uri")

        openPdfFile(tempFile)
        tempFile
    }

    suspend fun openPdfFile(file: File) = withContext(Dispatchers.IO) {
        close()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        currentFileDescriptor = pfd
        currentPdfRenderer = renderer
        currentPdfFile = file
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int = 1200): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = currentPdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        val page = renderer.openPage(pageIndex)
        val pageWidth = page.width
        val pageHeight = page.height

        val scale = targetWidthPx.toFloat() / pageWidth.toFloat()
        val targetHeightPx = (pageHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        bitmap
    }

    fun close() {
        try {
            currentPdfRenderer?.close()
        } catch (_: Exception) {}
        try {
            currentFileDescriptor?.close()
        } catch (_: Exception) {}
        currentPdfRenderer = null
        currentFileDescriptor = null
    }

    private fun createSamplePdfFile(outputFile: File) {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // PAGE 1: Invoice / Faktur
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard pt
        val page1 = doc.startPage(pageInfo1)
        val canvas1 = page1.canvas
        canvas1.drawColor(Color.WHITE)

        // Header Background
        paint.color = Color.rgb(238, 242, 255)
        canvas1.drawRect(RectF(30f, 30f, 565f, 110f), paint)

        // Title
        paint.color = Color.rgb(30, 27, 75)
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas1.drawText("ACME TECH GLOBAL - TAX INVOICE", 45f, 65f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(71, 85, 105)
        canvas1.drawText("Invoice No: INV-2026-08942   |   Date: 17 September 2026   |   Currency: USD", 45f, 92f, paint)

        // Vendor & Customer details
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas1.drawText("Billed To:", 45f, 140f, paint)
        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(51, 65, 85)
        canvas1.drawText("Cyber Dynamics Pte. Ltd.", 45f, 160f, paint)
        canvas1.drawText("Attn: Muhammad Iqbal / Finance Division", 45f, 178f, paint)
        canvas1.drawText("88 Marina Bay Boulevard #14-02, Singapore", 45f, 196f, paint)

        // Items Table
        paint.color = Color.rgb(241, 245, 249)
        canvas1.drawRect(RectF(40f, 220f, 555f, 250f), paint)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas1.drawText("ITEM DESCRIPTION", 50f, 238f, paint)
        canvas1.drawText("QTY", 320f, 238f, paint)
        canvas1.drawText("UNIT PRICE", 390f, 238f, paint)
        canvas1.drawText("AMOUNT", 490f, 238f, paint)

        val items = listOf(
            Triple("Cloud AI Server Dedicated Cluster GPU-A100", "2", "$ 3,200.00"),
            Triple("Dataset Annotation Pipeline License Pro", "1", "$ 1,450.00"),
            Triple("High-Performance Optical Character Recognition SDK", "1", "$ 890.00"),
            Triple("Enterprise Support SLA (24/7 Priority Access)", "12", "$ 2,400.00")
        )

        var curY = 280f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(30, 41, 59)
        for (item in items) {
            canvas1.drawText(item.first, 50f, curY, paint)
            canvas1.drawText(item.second, 325f, curY, paint)
            canvas1.drawText(item.third, 480f, curY, paint)
            paint.color = Color.rgb(226, 232, 240)
            canvas1.drawLine(40f, curY + 12f, 555f, curY + 12f, paint)
            paint.color = Color.rgb(30, 41, 59)
            curY += 34f
        }

        // Summary Box
        paint.color = Color.rgb(248, 250, 252)
        canvas1.drawRoundRect(RectF(320f, curY + 20f, 555f, curY + 130f), 8f, 8f, paint)
        paint.color = Color.rgb(71, 85, 105)
        canvas1.drawText("Subtotal:", 335f, curY + 50f, paint)
        canvas1.drawText("$ 7,940.00", 475f, curY + 50f, paint)
        canvas1.drawText("VAT / Tax (11%):", 335f, curY + 75f, paint)
        canvas1.drawText("$ 873.40", 485f, curY + 75f, paint)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = Color.rgb(79, 70, 229)
        canvas1.drawText("Total Due:", 335f, curY + 110f, paint)
        canvas1.drawText("$ 8,813.40", 465f, curY + 110f, paint)

        // Signature Box
        paint.color = Color.rgb(241, 245, 249)
        canvas1.drawRoundRect(RectF(45f, curY + 20f, 220f, curY + 130f), 8f, 8f, paint)
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas1.drawText("Authorized Signature & Stamp:", 55f, curY + 45f, paint)
        paint.color = Color.rgb(30, 27, 75)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas1.drawText("ACME OFFICIAL", 65f, curY + 85f, paint)
        paint.color = Color.rgb(148, 163, 184)
        canvas1.drawLine(55f, curY + 105f, 210f, curY + 105f, paint)

        doc.finishPage(page1)

        // PAGE 2: Research Report & Paragraphs
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = doc.startPage(pageInfo2)
        val canvas2 = page2.canvas
        canvas2.drawColor(Color.WHITE)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas2.drawText("Deep Learning OCR & Text Localization Benchmark", 45f, 70f, paint)

        paint.textSize = 12f
        paint.color = Color.rgb(99, 102, 241)
        canvas2.drawText("Section 1: Abstract & Executive Summary", 45f, 105f, paint)

        paint.textSize = 10.5f
        paint.color = Color.rgb(51, 65, 85)
        paint.isFakeBoldText = false
        val paragraph1 = listOf(
            "Document layout analysis and object-detection-based OCR pipelines have witnessed dramatic",
            "advancements with convolutional neural networks and vision transformer backbones. Standard",
            "datasets require precise bounding boxes encapsulating titles, subheadings, dense paragraphs,",
            "tabular grids, graphic illustrations, and legal sign-off stamps."
        )
        var pY = 135f
        for (line in paragraph1) {
            canvas2.drawText(line, 45f, pY, paint)
            pY += 18f
        }

        paint.textSize = 12f
        paint.isFakeBoldText = true
        paint.color = Color.rgb(15, 23, 42)
        canvas2.drawText("Section 2: Methodology & Evaluation Metrics", 45f, pY + 25f, paint)

        paint.textSize = 10.5f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(51, 65, 85)
        val paragraph2 = listOf(
            "Precision (P), Recall (R), and mean Average Precision (mAP@0.5:0.95) serve as standard benchmarks.",
            "IoU thresholding is applied to assess box boundary alignment. When annotating multi-lingual",
            "receipts and scientific literature, magnetic boundary snapping reduces human labelling latency",
            "by more than 40 percent while eliminating false overlaps between adjacent text segments."
        )
        pY += 50f
        for (line in paragraph2) {
            canvas2.drawText(line, 45f, pY, paint)
            pY += 18f
        }

        // Mock Diagram / Figure Box
        pY += 30f
        paint.color = Color.rgb(238, 242, 255)
        canvas2.drawRoundRect(RectF(45f, pY, 550f, pY + 160f), 12f, 12f, paint)
        paint.color = Color.rgb(99, 102, 241)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas2.drawText("FIGURE 1: Pipeline Architecture & Bounding Box Extractor", 80f, pY + 40f, paint)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(71, 85, 105)
        canvas2.drawText("[Input PDF Page] -> [Rasterizer 300DPI] -> [YOLOv8 Text Detector] -> [Dataset Export]", 80f, pY + 80f, paint)
        canvas2.drawText("Training subsets automatically divided into train/val partitions with standardized coordinates.", 80f, pY + 110f, paint)

        doc.finishPage(page2)

        // PAGE 3: Registration Form & Identification
        val pageInfo3 = PdfDocument.PageInfo.Builder(595, 842, 3).create()
        val page3 = doc.startPage(pageInfo3)
        val canvas3 = page3.canvas
        canvas3.drawColor(Color.WHITE)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas3.drawText("OFFICIAL REGISTRATION & VERIFICATION FORM", 45f, 70f, paint)

        paint.textSize = 11f
        paint.color = Color.rgb(100, 116, 139)
        paint.isFakeBoldText = false
        canvas3.drawText("Form ID: REG-OCR-2026-X   |   Confidential Government Document", 45f, 95f, paint)

        val formFields = listOf(
            "Full Legal Name" to "MUHAMMAD IQBAL",
            "National ID / Passport Number" to "A9382104928104",
            "Company / Organization" to "AI STUDIO RESEARCH LABS",
            "Designation / Title" to "LEAD DATASET ENGINEER",
            "Email Address" to "muhamadikbal12122007@gmail.com",
            "Verification Status" to "[X] APPROVED AND CERTIFIED"
        )

        var fY = 140f
        for (field in formFields) {
            paint.color = Color.rgb(71, 85, 105)
            paint.textSize = 10f
            paint.isFakeBoldText = true
            canvas3.drawText(field.first.uppercase(), 45f, fY, paint)

            paint.color = Color.rgb(241, 245, 249)
            canvas3.drawRect(RectF(45f, fY + 6f, 545f, fY + 36f), paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 11f
            paint.isFakeBoldText = false
            canvas3.drawText(field.second, 55f, fY + 26f, paint)
            fY += 54f
        }

        doc.finishPage(page3)

        FileOutputStream(outputFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()
    }
}
