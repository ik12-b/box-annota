package com.example.ml

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import kotlin.math.roundToInt

sealed class RecognitionResult {
    data class Success(val text: String) : RecognitionResult()
    /** Model loaded fine, but no character codec is configured — see [TextLineRecognizer]'s class doc. */
    object NoCodecConfigured : RecognitionResult()
    data class Failure(val message: String) : RecognitionResult()
}

/**
 * Runs an on-device text-line recognition (OCR) ONNX model — a CRNN/CTC-style
 * recognizer, the bundled Muharaf Arabic handwriting model by default, or a
 * swapped-in custom .onnx (see [useCustomModel]) — over a single cropped line
 * image and decodes it into text via greedy CTC decoding, as a fully offline
 * alternative to the Gemini API.
 *
 * Critical difference from [com.example.ml.TextLineDetector]: a recognition
 * model's output is a sequence of CLASS INDICES, meaningless without its
 * "codec" — the list mapping each class index to an actual character. That
 * codec is embedded in some model formats but is NOT preserved by a plain
 * ONNX export (confirmed empty for the bundled Muharaf model: Kraken, the
 * training framework it was exported from, stores the codec inside its own
 * .mlmodel checkpoint format, not in ONNX metadata). Guessing this mapping
 * would produce fluent-looking but WRONG text — worse than no output at all
 * for a research transcription dataset, since it looks trustworthy while
 * being silently incorrect. So [recognize] never guesses: without an
 * explicitly supplied codec (see [setCodec]) it returns
 * [RecognitionResult.NoCodecConfigured] rather than emitting text.
 *
 * ## Supported tensor precisions
 * The model's INPUT and OUTPUT element types are read from the ONNX graph at
 * session-creation time and handled automatically:
 *
 *  - FLOAT (fp32), FLOAT16 (fp16), BFLOAT16 (bf16), DOUBLE (fp64)
 *  - INT8, UINT8, INT16, INT32, INT64 (fully-quantized graphs whose I/O is integer)
 *
 * Note on INT4 / INT2 and other low-bit formats: those are almost always
 * *weight-only* quantization, where the weights are packed inside the graph
 * but the model's input/output tensors stay float — such models work here
 * with no special handling, as long as the ONNX Runtime build has kernels
 * for the ops. The ONNX Runtime Java API itself has no INT4/INT2 tensor type
 * (see [OnnxJavaType]), so a graph whose *external* input/output is int4/int2
 * is reported as a clear Failure instead of being fed wrong data.
 *
 * For integer-input models, pixels are quantized as
 * `q = round(gray / scale) + zeroPoint` (gray in 0..1), defaulting to
 * scale = 1/255 and zeroPoint = 0 (UINT8/INT16/INT32/INT64) or -128 (INT8),
 * i.e. raw 0..255 pixel values. If the model was calibrated differently, call
 * [setInputQuantization] with its real scale/zero-point.
 */
class TextLineRecognizer(private val context: Context) {

    companion object {
        private const val MODEL_ASSET_PATH = "models/text_rec_muharaf.onnx"
        private const val DEFAULT_CODEC_ASSET_PATH = "models/muharaf_codec.txt"
        private const val DEFAULT_MODEL_LABEL = "Muharaf Arabic HTR (default)"
        private const val TARGET_HEIGHT = 120
        private const val MAX_WIDTH = 2400
    }

    private class UnsupportedTypeException(message: String) : Exception(message)

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var loadFailed = false
    private var inputName: String = "input"

    /** Element type of the model's input tensor, detected in [ensureSession]. */
    private var inputType: OnnxJavaType = OnnxJavaType.FLOAT

    // Only used when the model's input tensor is an integer type.
    private var inputQuantScale: Float = 1f / 255f
    private var inputQuantZeroPoint: Int? = null

    private var customModelFile: File? = null
    private var codec: List<String>? = null

    init {
        // The bundled default model's codec (extracted from the original
        // Kraken .mlmodel's embedded metadata — see the class doc) ships as
        // a plain asset, so the default model works out of the box without
        // the user needing to supply anything.
        loadDefaultCodecFromAssets()
    }

    val isCustomModel: Boolean
        get() = customModelFile != null

    val currentModelLabel: String
        get() = customModelFile?.name ?: DEFAULT_MODEL_LABEL

    val hasCodec: Boolean
        get() = !codec.isNullOrEmpty()

    val codecSize: Int
        get() = codec?.size ?: 0

    /** Input precision of the loaded model (e.g. "FLOAT16"); null until the first recognize() call. */
    val inputTypeLabel: String?
        get() = if (session != null) inputType.name else null

    private fun loadDefaultCodecFromAssets() {
        try {
            val lines = context.assets.open(DEFAULT_CODEC_ASSET_PATH).bufferedReader().use { it.readLines() }
                .filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) codec = lines
        } catch (_: Exception) {
            // No bundled codec asset, or unreadable — recognize() will simply
            // report NoCodecConfigured until one is supplied explicitly.
        }
    }

    /**
     * Switches recognition over to [file]. The bundled default model's codec
     * almost certainly does NOT match a different model's class layout, so
     * the codec is cleared here — a fresh one for this specific model must
     * be supplied via [setCodec] before recognition will run.
     */
    fun useCustomModel(file: File) {
        closeSessionOnly()
        customModelFile = file
        loadFailed = false
        codec = null
        resetQuantization()
    }

    fun useDefaultModel() {
        closeSessionOnly()
        customModelFile = null
        loadFailed = false
        loadDefaultCodecFromAssets()
        resetQuantization()
    }

    /**
     * One entry per output class, in class-index order starting at class 1
     * (class 0 is always the CTC blank token and must NOT have an entry here
     * — this is the standard layout used by kraken-derived dict.txt/codec
     * files: "dict.txt entry i is class i + 1, class 0 is the blank").
     */
    fun setCodec(entries: List<String>) {
        codec = entries.ifEmpty { null }
    }

    fun clearCodec() {
        codec = null
    }

    /**
     * Overrides the quantization parameters used to build an INTEGER input
     * tensor (ignored for float-type inputs). `q = round(gray / scale) + zeroPoint`.
     */
    fun setInputQuantization(scale: Float, zeroPoint: Int) {
        require(scale > 0f) { "scale harus > 0" }
        inputQuantScale = scale
        inputQuantZeroPoint = zeroPoint
    }

    private fun resetQuantization() {
        inputQuantScale = 1f / 255f
        inputQuantZeroPoint = null
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
                // Batch transcription now parallelizes at the *request* level
                // (see AnnotatorViewModel.autoTranscribeAllRemaining — several
                // concurrent recognize() calls sharing this same session,
                // which ONNX Runtime's Run() is documented as safe for). Each
                // individual inference is single-threaded on purpose so that
                // running several of them at once doesn't oversubscribe the
                // CPU with more total threads than cores.
                setIntraOpNumThreads(1)
            }
            val newSession = environment.createSession(bytes, options)
            inputName = newSession.inputNames.firstOrNull() ?: "input"
            inputType = (newSession.inputInfo[inputName]?.info as? TensorInfo)?.type
                ?: OnnxJavaType.FLOAT
            env = environment
            session = newSession
            newSession
        } catch (_: Throwable) {
            loadFailed = true
            null
        }
    }

    // ---------------------------------------------------------------------
    // Input: gray (0..1) -> tensor of whatever type the model expects
    // ---------------------------------------------------------------------

    private fun quantize(gray: Float, zeroPoint: Int, lo: Int, hi: Int): Int =
        ((gray / inputQuantScale).roundToInt() + zeroPoint).coerceIn(lo, hi)

    private fun buildInputTensor(
        environment: OrtEnvironment,
        gray: FloatArray,
        shape: LongArray
    ): OnnxTensor {
        val n = gray.size
        return when (inputType) {
            OnnxJavaType.FLOAT ->
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(gray), shape)

            // Buffer holds fp32 values; ONNX Runtime converts them to fp16/bf16.
            OnnxJavaType.FLOAT16, OnnxJavaType.BFLOAT16 ->
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(gray), shape, inputType)

            OnnxJavaType.DOUBLE -> {
                val d = DoubleArray(n) { gray[it].toDouble() }
                OnnxTensor.createTensor(environment, DoubleBuffer.wrap(d), shape)
            }

            OnnxJavaType.UINT8 -> {
                val zp = inputQuantZeroPoint ?: 0
                val b = ByteArray(n) { quantize(gray[it], zp, 0, 255).toByte() }
                OnnxTensor.createTensor(environment, ByteBuffer.wrap(b), shape, OnnxJavaType.UINT8)
            }

            OnnxJavaType.INT8 -> {
                val zp = inputQuantZeroPoint ?: -128
                val b = ByteArray(n) { quantize(gray[it], zp, -128, 127).toByte() }
                OnnxTensor.createTensor(environment, ByteBuffer.wrap(b), shape, OnnxJavaType.INT8)
            }

            OnnxJavaType.INT16 -> {
                val zp = inputQuantZeroPoint ?: 0
                val s = ShortArray(n) { quantize(gray[it], zp, -32768, 32767).toShort() }
                OnnxTensor.createTensor(environment, ShortBuffer.wrap(s), shape)
            }

            OnnxJavaType.INT32 -> {
                val zp = inputQuantZeroPoint ?: 0
                val a = IntArray(n) { quantize(gray[it], zp, Int.MIN_VALUE, Int.MAX_VALUE) }
                OnnxTensor.createTensor(environment, IntBuffer.wrap(a), shape)
            }

            OnnxJavaType.INT64 -> {
                val zp = inputQuantZeroPoint ?: 0
                val a = LongArray(n) { quantize(gray[it], zp, Int.MIN_VALUE, Int.MAX_VALUE).toLong() }
                OnnxTensor.createTensor(environment, LongBuffer.wrap(a), shape)
            }

            else -> throw UnsupportedTypeException(
                "Tipe input model tidak didukung: $inputType. Didukung: FLOAT, FLOAT16, BFLOAT16, " +
                    "DOUBLE, INT8, UINT8, INT16, INT32, INT64. (INT4/INT2 murni pada input/output " +
                    "tidak didukung ONNX Runtime Java; bobot INT4/INT2 di dalam graf tetap aman " +
                    "selama input/output-nya float.)"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Output: any tensor type -> FloatArray of scores (used only for argmax)
    // ---------------------------------------------------------------------

    /**
     * Copies the first [total] elements of [tensor] into a FloatArray.
     * Integer outputs are left in their raw (quantized) domain: greedy CTC
     * only needs argmax, and dequantization with a positive scale is monotonic,
     * so the argmax is unchanged. UINT8 is read unsigned so ordering stays correct.
     */
    private fun readScores(tensor: OnnxTensor, total: Int): FloatArray {
        val out = FloatArray(total)
        when (val type = tensor.info.type) {
            // getFloatBuffer() converts fp16/bf16 to fp32 for us.
            OnnxJavaType.FLOAT, OnnxJavaType.FLOAT16, OnnxJavaType.BFLOAT16 -> {
                val b = tensor.floatBuffer
                for (i in 0 until total) out[i] = b.get(i)
            }
            OnnxJavaType.DOUBLE -> {
                val b = tensor.doubleBuffer
                for (i in 0 until total) out[i] = b.get(i).toFloat()
            }
            OnnxJavaType.INT8 -> {
                val b = tensor.byteBuffer
                for (i in 0 until total) out[i] = b.get(i).toFloat()
            }
            OnnxJavaType.UINT8 -> {
                val b = tensor.byteBuffer
                for (i in 0 until total) out[i] = (b.get(i).toInt() and 0xFF).toFloat()
            }
            OnnxJavaType.INT16 -> {
                val b = tensor.shortBuffer
                for (i in 0 until total) out[i] = b.get(i).toFloat()
            }
            OnnxJavaType.INT32 -> {
                val b = tensor.intBuffer
                for (i in 0 until total) out[i] = b.get(i).toFloat()
            }
            OnnxJavaType.INT64 -> {
                val b = tensor.longBuffer
                for (i in 0 until total) out[i] = b.get(i).toFloat()
            }
            else -> throw UnsupportedTypeException(
                "Tipe output model tidak didukung: $type. Didukung: FLOAT, FLOAT16, BFLOAT16, " +
                    "DOUBLE, INT8, UINT8, INT16, INT32, INT64."
            )
        }
        return out
    }

    /**
     * Recognizes the text in [bitmap] (a single cropped line image). Never
     * throws: any failure — missing codec, bad/incompatible model, OOM, a
     * malformed output shape — comes back as a typed [RecognitionResult]
     * instead of a crash or a fabricated guess at the text.
     */
    suspend fun recognize(bitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        val activeCodec = codec
        if (activeCodec.isNullOrEmpty()) {
            return@withContext RecognitionResult.NoCodecConfigured
        }
        val activeSession = ensureSession()
            ?: return@withContext RecognitionResult.Failure("Gagal memuat model pengenalan teks.")
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext RecognitionResult.Failure("Gambar potongan baris tidak valid.")
        }

        var scaled: Bitmap? = null
        var inputTensor: OnnxTensor? = null
        try {
            // Best-effort default preprocessing for a CRNN/CTC line recognizer:
            // fixed target height preserving aspect ratio, single-channel
            // grayscale, normalized to 0..1. A swapped-in custom model trained
            // with different preprocessing (inverted ink/background, different
            // target height, mean/std normalization, etc.) may need this tuned.
            val scale = TARGET_HEIGHT.toFloat() / bitmap.height.toFloat()
            val newW = (bitmap.width * scale).roundToInt().coerceIn(8, MAX_WIDTH)
            val newH = TARGET_HEIGHT

            scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            val pixels = IntArray(newW * newH)
            scaled.getPixels(pixels, 0, newW, 0, 0, newW, newH)

            val gray = FloatArray(newW * newH)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                gray[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            }

            val environment = this@TextLineRecognizer.env
                ?: return@withContext RecognitionResult.Failure("Model belum siap.")
            val tensor = buildInputTensor(
                environment,
                gray,
                longArrayOf(1, 1, newH.toLong(), newW.toLong())
            )
            inputTensor = tensor

            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val outputTensor = result[0] as? OnnxTensor
                    ?: return@withContext RecognitionResult.Failure("Output model tidak terbaca.")
                val shape = outputTensor.info.shape
                if (shape.size != 4) {
                    return@withContext RecognitionResult.Failure(
                        "Bentuk output model tidak sesuai (rank ${shape.size}, diharapkan 4)."
                    )
                }
                val vocabSize = shape[1].toInt()
                val seqLen = shape[3].toInt()
                if (vocabSize <= 0 || seqLen <= 0) {
                    return@withContext RecognitionResult.Failure("Output model kosong.")
                }
                // The codec must have exactly one entry per non-blank class —
                // a size mismatch means this codec almost certainly wasn't
                // built for this exact model, and every decoded character
                // from here on would be shifted/wrong. Refuse rather than
                // silently emitting misaligned text.
                val expectedCodecSize = vocabSize - 1
                if (activeCodec.size != expectedCodecSize) {
                    return@withContext RecognitionResult.Failure(
                        "Codec tidak cocok dengan model: model ini punya $vocabSize kelas keluaran " +
                            "(butuh codec $expectedCodecSize baris), tapi codec yang dimuat punya " +
                            "${activeCodec.size} baris. Kemungkinan codec ini bukan untuk model ini."
                    )
                }

                val logits = readScores(outputTensor, vocabSize * seqLen)
                // Layout is [1, vocab, 1, seq] (row-major): class c, timestep t
                // sits at c * seqLen + t.
                var prevClass = -1
                val sb = StringBuilder()
                for (t in 0 until seqLen) {
                    var bestClass = 0
                    var bestScore = Float.NEGATIVE_INFINITY
                    for (c in 0 until vocabSize) {
                        val v = logits[c * seqLen + t]
                        if (v > bestScore) {
                            bestScore = v
                            bestClass = c
                        }
                    }
                    // Greedy CTC decode: collapse consecutive repeats, drop blank (class 0).
                    if (bestClass != prevClass && bestClass != 0) {
                        val charIndex = bestClass - 1
                        if (charIndex in activeCodec.indices) {
                            sb.append(activeCodec[charIndex])
                        }
                    }
                    prevClass = bestClass
                }

                RecognitionResult.Success(sb.toString().trim())
            }
        } catch (e: UnsupportedTypeException) {
            RecognitionResult.Failure(e.message ?: "Tipe tensor model tidak didukung.")
        } catch (_: OutOfMemoryError) {
            RecognitionResult.Failure("Gambar terlalu besar untuk diproses.")
        } catch (e: Exception) {
            RecognitionResult.Failure("Gagal mengenali teks: ${e.message ?: "kesalahan tidak diketahui"}")
        } finally {
            try { inputTensor?.close() } catch (_: Exception) {}
            if (scaled != null && scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun closeSessionOnly() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        inputType = OnnxJavaType.FLOAT
    }

    fun close() {
        closeSessionOnly()
        env = null
    }
}
