package com.example.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Runs a text-detection ONNX model (a DB / Differentiable-Binarization
 * detector — the bundled PP-OCRv6-tiny manuscript model by default, or a
 * user-supplied .onnx file, see [useCustomModel]) over a page bitmap and
 * turns its output probability map into a list of normalized bounding boxes,
 * so labeling can start from a set of pre-detected line boxes instead of
 * drawing every one by hand.
 *
 * Every public entry point is defensive on purpose: a broken/incompatible
 * model, an OOM during inference, or any other failure returns an empty list
 * instead of throwing, so a detection problem never force-closes the app —
 * it just behaves as if nothing was detected, and the user can still draw
 * boxes manually. This matters more once a user can swap in an arbitrary
 * model file: a custom model with an unexpected shape/output layout must
 * degrade gracefully, not crash.
 */
class TextLineDetector(private val context: Context) {

    companion object {
        private const val MODEL_ASSET_PATH = "models/text_det_manuscript.onnx"
        private const val DEFAULT_MODEL_LABEL = "Model Default (Manuskrip)"
        private const val MAX_SIDE = 960
        private const val STRIDE = 32
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var loadFailed = false
    // Discovered from the loaded model itself rather than hardcoded, since a
    // custom uploaded model may not name its input tensor "x" the way the
    // bundled PaddleOCR-style model does.
    private var inputName: String = "x"

    /** File of a user-supplied replacement model, or null to use the bundled default. */
    private var customModelFile: File? = null

    val isCustomModel: Boolean
        get() = customModelFile != null

    val currentModelLabel: String
        get() = customModelFile?.name ?: DEFAULT_MODEL_LABEL

    /**
     * Switches detection over to [file] (an .onnx model on disk) starting
     * from the next [detect] call. Loading is lazy and happens on the
     * background dispatcher inside [detect]/[ensureSession] — this call
     * itself just closes the currently-loaded session and points at the new
     * file, it doesn't validate the model.
     */
    fun useCustomModel(file: File) {
        closeSessionOnly()
        customModelFile = file
        loadFailed = false
    }

    /** Reverts to the bundled default model starting from the next [detect] call. */
    fun useDefaultModel() {
        closeSessionOnly()
        customModelFile = null
        loadFailed = false
    }

    private fun ensureSession(): OrtSession? {
        session?.let { return it }
        if (loadFailed) return null
        return try {
            val modelFile = customModelFile
            val bytes = if (modelFile != null) {
                modelFile.readBytes()
            } else {
                context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
            }
            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            }
            val newSession = environment.createSession(bytes, options)
            inputName = newSession.inputNames.firstOrNull() ?: "x"
            env = environment
            session = newSession
            newSession
        } catch (_: Throwable) {
            // Missing/corrupt file, unsupported ops, OOM loading weights, an
            // incompatible custom model, etc. — detection just becomes
            // unavailable rather than crashing the app. If this was a custom
            // model, the caller (ViewModel) surfaces this and offers to fall
            // back to the default; it is never silently retried in a loop.
            loadFailed = true
            null
        }
    }

    /**
     * @return normalized (0..1) rectangles relative to [bitmap], one per detected
     * text line/region. Empty on any failure.
     */
    suspend fun detect(bitmap: Bitmap, threshold: Float = 0.3f): List<RectF> = withContext(Dispatchers.Default) {
        val activeSession = ensureSession() ?: return@withContext emptyList()
        if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext emptyList()

        var resized: Bitmap? = null
        var inputTensor: OnnxTensor? = null
        try {
            val origW = bitmap.width
            val origH = bitmap.height
            val longSide = max(origW, origH).toFloat()
            val ratio = if (longSide > MAX_SIDE) MAX_SIDE / longSide else 1f
            val newW = roundToStride((origW * ratio)).coerceAtLeast(STRIDE)
            val newH = roundToStride((origH * ratio)).coerceAtLeast(STRIDE)

            resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            val pixels = IntArray(newW * newH)
            resized.getPixels(pixels, 0, newW, 0, 0, newW, newH)

            val chwBuffer = FloatBuffer.allocate(3 * newW * newH)
            for (c in 0 until 3) {
                val mean = MEAN[c]
                val std = STD[c]
                for (i in pixels.indices) {
                    val px = pixels[i]
                    val channelVal = when (c) {
                        0 -> (px shr 16) and 0xFF // R
                        1 -> (px shr 8) and 0xFF  // G
                        else -> px and 0xFF       // B
                    }
                    chwBuffer.put((channelVal / 255f - mean) / std)
                }
            }
            chwBuffer.rewind()

            val env = this@TextLineDetector.env ?: return@withContext emptyList()
            val tensor = OnnxTensor.createTensor(env, chwBuffer, longArrayOf(1, 3, newH.toLong(), newW.toLong()))
            inputTensor = tensor

            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val outputTensor = result[0] as? OnnxTensor ?: return@withContext emptyList()
                val shape = outputTensor.info.shape
                if (shape.size != 4) return@withContext emptyList()
                val outH = shape[2].toInt()
                val outW = shape[3].toInt()
                if (outH <= 0 || outW <= 0) return@withContext emptyList()

                val probBuffer = outputTensor.floatBuffer
                val mask = BooleanArray(outW * outH)
                for (i in mask.indices) {
                    mask[i] = probBuffer.get(i) > threshold
                }

                extractBoxesFromMask(mask, outW, outH)
            }
        } catch (_: OutOfMemoryError) {
            emptyList()
        } catch (_: Throwable) {
            // A custom model with an incompatible input/output shape lands
            // here too (wrong rank, mismatched tensor name never resolved,
            // etc.) — same graceful "nothing detected" outcome, never a crash.
            emptyList()
        } finally {
            try { inputTensor?.close() } catch (_: Exception) {}
            if (resized != null && resized !== bitmap && !resized.isRecycled) resized.recycle()
        }
    }

    /** Connected-component labeling (4-connectivity BFS) over the binary mask. */
    private fun extractBoxesFromMask(mask: BooleanArray, w: Int, h: Int): List<RectF> {
        val visited = BooleanArray(w * h)
        val minPixels = max(9, (w * h) / 250_000)
        val boxes = mutableListOf<RectF>()
        val queue = ArrayDeque<Int>()
        val maxBoxes = 500 // safety cap against a pathological/noisy mask

        for (start in mask.indices) {
            if (boxes.size >= maxBoxes) break
            if (!mask[start] || visited[start]) continue

            queue.clear()
            queue.add(start)
            visited[start] = true
            var minX = start % w
            var maxX = minX
            var minY = start / w
            var maxY = minY
            var count = 0

            while (queue.isNotEmpty()) {
                val idx = queue.poll()
                val x = idx % w
                val y = idx / w
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                // 4-connected neighbors
                if (x > 0) { val n = idx - 1; if (mask[n] && !visited[n]) { visited[n] = true; queue.add(n) } }
                if (x < w - 1) { val n = idx + 1; if (mask[n] && !visited[n]) { visited[n] = true; queue.add(n) } }
                if (y > 0) { val n = idx - w; if (mask[n] && !visited[n]) { visited[n] = true; queue.add(n) } }
                if (y < h - 1) { val n = idx + w; if (mask[n] && !visited[n]) { visited[n] = true; queue.add(n) } }
            }

            if (count < minPixels) continue

            // Approximate DB's "unclip" step (the mask is trained to be smaller
            // than the true text region) by expanding the box outward a bit.
            val boxW = (maxX - minX + 1).toFloat()
            val boxH = (maxY - minY + 1).toFloat()
            val marginX = boxW * 0.15f
            val marginY = boxH * 0.25f

            val nx0 = ((minX - marginX) / w).coerceIn(0f, 1f)
            val ny0 = ((minY - marginY) / h).coerceIn(0f, 1f)
            val nx1 = ((maxX + 1 + marginX) / w).coerceIn(0f, 1f)
            val ny1 = ((maxY + 1 + marginY) / h).coerceIn(0f, 1f)

            if ((nx1 - nx0) < 0.003f || (ny1 - ny0) < 0.003f) continue
            // Skip near-full-page detections — almost certainly a bad threshold
            // hit rather than an actual single text line.
            if ((nx1 - nx0) > 0.97f && (ny1 - ny0) > 0.97f) continue

            boxes.add(RectF(nx0, ny0, nx1, ny1))
        }
        return boxes
    }

    private fun roundToStride(value: Float): Int {
        val r = (value / STRIDE).roundToInt() * STRIDE
        return if (r <= 0) STRIDE else r
    }

    private fun closeSessionOnly() {
        try { session?.close() } catch (_: Exception) {}
        session = null
    }

    fun close() {
        closeSessionOnly()
        // OrtEnvironment is a process-wide singleton — do not close it here.
        env = null
    }
}
