package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Wraps [PdfRenderer]. android.graphics.pdf.PdfRenderer is NOT thread-safe and only
 * allows one open Page at a time per renderer instance — calling openPage()/close()
 * from more than one coroutine concurrently (e.g. the user flicking through pages
 * quickly) throws and crashes the app. Every operation that touches the renderer or
 * its file descriptor goes through [mutex] so calls are always serialized.
 */
class PdfManager(private val context: Context) {

    private val mutex = Mutex()

    private var currentFileDescriptor: ParcelFileDescriptor? = null
    private var currentPdfRenderer: PdfRenderer? = null
    var currentPdfFile: File? = null
        private set

    val pageCount: Int
        get() = currentPdfRenderer?.pageCount ?: 0

    /**
     * Persistent (non-cache) location for the document currently being annotated.
     * filesDir is used instead of cacheDir because the OS is free to wipe cacheDir
     * under storage pressure — that was silently deleting the user's document and
     * forcing the session restore to fail. Only one document is kept at a time so
     * this doesn't grow unbounded.
     */
    private val documentsDir: File
        get() = File(context.filesDir, "documents").apply { mkdirs() }

    suspend fun openPdfFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val targetFile = File(documentsDir, "current_document.pdf")
        val tmpFile = File(documentsDir, "current_document.pdf.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tmpFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Tidak bisa membuka file dari Uri: $uri")

        if (targetFile.exists()) targetFile.delete()
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }

        openPdfFile(targetFile)
        targetFile
    }

    suspend fun openPdfFile(file: File) = mutex.withLock {
        withContext(Dispatchers.IO) {
            closeLocked()
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            currentFileDescriptor = pfd
            currentPdfRenderer = renderer
            currentPdfFile = file
        }
    }

    /**
     * Renders one page. Guarded by [mutex] so concurrent calls (rapid page
     * switching) queue up instead of racing on the same PdfRenderer instance.
     * Returns null instead of throwing on any failure — including OOM under
     * memory pressure — so a bad render never force-closes the app.
     */
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int = 1200): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val renderer = currentPdfRenderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            try {
                val page = renderer.openPage(pageIndex)
                try {
                    val pageWidth = page.width
                    val pageHeight = page.height
                    val scale = targetWidthPx.toFloat() / pageWidth.toFloat()
                    val targetHeightPx = (pageHeight * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    // Always close the native Page, even if render() throws or the
                    // coroutine is cancelled mid-render — otherwise the renderer is
                    // left with a page "stuck" open and the *next* openPage() call
                    // throws IllegalStateException, crashing the app on the very
                    // next page switch.
                    try { page.close() } catch (_: Exception) {}
                }
            } catch (_: OutOfMemoryError) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun close() {
        currentPdfRenderer?.let {
            try { it.close() } catch (_: Exception) {}
        }
        try { currentFileDescriptor?.close() } catch (_: Exception) {}
        currentPdfRenderer = null
        currentFileDescriptor = null
    }

    private fun closeLocked() {
        try { currentPdfRenderer?.close() } catch (_: Exception) {}
        try { currentFileDescriptor?.close() } catch (_: Exception) {}
        currentPdfRenderer = null
        currentFileDescriptor = null
    }
}
