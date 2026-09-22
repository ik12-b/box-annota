package com.example.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
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
 */
class TextLineRecognizer(private val context: Context) {

    companion object {
        private const val MODEL_ASSET_PATH = "models/text_rec_muharaf.onnx"
        private const val DEFAULT_CODEC_ASSET_PATH = "models/muharaf_codec.txt"
        private const val DEFAULT_MODEL_LABEL = "Muharaf Arabic HTR (default)"
        private const val TARGET_HEIGHT = 120
        private const val MAX_WIDTH = 2400
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var loadFailed = false
    private var inputName: String = "input"

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
    }

    fun useDefaultModel() {
        closeSessionOnly()
        customModelFile = null
        loadFailed = false
        loadDefaultCodecFromAssets()
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
            inputName = newSession.inputNames.firstOrNull() ?: "input"
            env = environment
            session = newSession
            newSession
        } catch (_: Throwable) {
            loadFailed = true
            null
        }
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

        var gray: Bitmap? = null
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

            gray = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            val pixels = IntArray(newW * newH)
            gray.getPixels(pixels, 0, newW, 0, 0, newW, newH)

            val buffer = FloatBuffer.allocate(newW * newH)
            for (px in pixels) {
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                buffer.put(lum)
            }
            buffer.rewind()

            val environment = this@TextLineRecognizer.env
                ?: return@withContext RecognitionResult.Failure("Model belum siap.")
            val tensor = OnnxTensor.createTensor(
                environment,
                buffer,
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

                val logits = outputTensor.floatBuffer
                // Layout is [1, vocab, 1, seq] (row-major): class c, timestep t
                // sits at c * seqLen + t.
                var prevClass = -1
                val sb = StringBuilder()
                for (t in 0 until seqLen) {
                    var bestClass = 0
                    var bestScore = Float.NEGATIVE_INFINITY
                    for (c in 0 until vocabSize) {
                        val v = logits.get(c * seqLen + t)
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
        } catch (_: OutOfMemoryError) {
            RecognitionResult.Failure("Gambar terlalu besar untuk diproses.")
        } catch (e: Exception) {
            RecognitionResult.Failure("Gagal mengenali teks: ${e.message ?: "kesalahan tidak diketahui"}")
        } finally {
            try { inputTensor?.close() } catch (_: Exception) {}
            if (gray != null && gray !== bitmap && !gray.isRecycled) gray.recycle()
        }
    }

    private fun closeSessionOnly() {
        try { session?.close() } catch (_: Exception) {}
        session = null
    }

    fun close() {
        closeSessionOnly()
        env = null
    }
}
