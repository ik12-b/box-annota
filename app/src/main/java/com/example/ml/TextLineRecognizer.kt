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
import java.nio.ByteOrder
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

    // Channel count (1 = grayscale, 3 = RGB) and target resize height, both
    // read from the model's own input shape [batch, channels, height, width]
    // in ensureSession(). Hardcoding these to the bundled Muharaf model's
    // values (1, 120) is exactly what produced the "Got invalid dimensions"
    // ORT_INVALID_ARGUMENT error for any other model shape (e.g. a 3-channel,
    // height-48 custom model) — so they must always come from the graph.
    private var inputChannels: Int = 1
    private var inputHeight: Int = TARGET_HEIGHT
    // Non-null only if the model's width dim is static (not -1/dynamic/symbolic)
    // — some custom exports require an exact fixed width instead of a
    // variable one, unlike the bundled Muharaf model.
    private var inputWidthFixed: Int? = null

    // --- Per-model preprocessing tuning, all reset on useCustomModel()/
    // useDefaultModel() since a new model almost certainly needs its own
    // values rather than silently inheriting the previous model's. ---

    // Per-channel normalization applied AFTER scaling pixels to 0..1 (and
    // after invertColors, if set): value = (value - mean[c]) / std[c].
    // Null (the default) means "leave as plain 0..1", matching the original
    // behavior. Array length must equal inputChannels when set.
    private var normMean: FloatArray? = null
    private var normStd: FloatArray? = null
    private var swapRedBlue: Boolean = false
    private var invertColors: Boolean = false
    // Some CTC models put the blank token at the LAST class index instead of
    // index 0 (the Kraken/Muharaf convention this class was originally built
    // around). false = blank at index 0 (default).
    private var blankAtEnd: Boolean = false

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
        resetPreprocessingTuning()
    }

    fun useDefaultModel() {
        closeSessionOnly()
        customModelFile = null
        loadFailed = false
        loadDefaultCodecFromAssets()
        resetQuantization()
        resetPreprocessingTuning()
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
     * Sets per-channel normalization applied after pixels are scaled to
     * 0..1: `value = (value - mean[c]) / std[c]`. Pass null to reset to
     * plain 0..1 (the default). Arrays must have [inputChannels] entries —
     * call this after the model is loaded (i.e. after the first [recognize]
     * call, or check [currentInputChannels]) so the length is known.
     * Example: ImageNet-style normalization for a 3-channel model is
     * `setNormalization(floatArrayOf(0.485f,0.456f,0.406f), floatArrayOf(0.229f,0.224f,0.225f))`.
     * A model expecting -1..1 instead of 0..1 is `setNormalization(floatArrayOf(0.5f), floatArrayOf(0.5f))`.
     */
    fun setNormalization(mean: FloatArray?, std: FloatArray?) {
        require((mean == null) == (std == null)) { "mean dan std harus sama-sama null atau sama-sama diisi" }
        if (mean != null && std != null) {
            require(mean.size == std.size) { "mean dan std harus punya panjang yang sama" }
            require(std.all { it != 0f }) { "std tidak boleh 0" }
        }
        normMean = mean
        normStd = std
    }

    /** true = build the RGB tensor's channels as B,G,R instead of R,G,B. Ignored for 1-channel models. */
    fun setChannelOrder(bgr: Boolean) {
        swapRedBlue = bgr
    }

    /** true = invert pixel values (1 - value) after 0..1 scaling, before normalization — for models trained on light-ink-on-dark-page images. */
    fun setInvertColors(invert: Boolean) {
        invertColors = invert
    }

    /** true = the model's CTC blank class is the LAST index instead of index 0. */
    fun setBlankAtEnd(atEnd: Boolean) {
        blankAtEnd = atEnd
    }

    /** Input channel count detected from the loaded model (1 or 3); valid once a model has been loaded. */
    val currentInputChannels: Int
        get() = inputChannels

    /** Resets all per-model tuning ([setNormalization], [setChannelOrder], [setInvertColors], [setBlankAtEnd]) to defaults. */
    fun resetPreprocessingTuning() {
        normMean = null
        normStd = null
        swapRedBlue = false
        invertColors = false
        blankAtEnd = false
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
            val inputTensorInfo = newSession.inputInfo[inputName]?.info as? TensorInfo
            inputType = inputTensorInfo?.type ?: OnnxJavaType.FLOAT

            // Expected layout: [batch, channels, height, width]. channels and
            // height are normally fixed (static) dims even when batch/width
            // are dynamic (-1 or symbolic) — that's what the "Expected: 3" /
            // "Expected: 48" in an ORT_INVALID_ARGUMENT error is reporting.
            // Fall back to the Muharaf defaults only if the model's shape
            // doesn't actually pin these dims down.
            val shape = inputTensorInfo?.shape
            inputChannels = shape?.getOrNull(1)?.takeIf { it > 0 }?.toInt() ?: 1
            inputHeight = shape?.getOrNull(2)?.takeIf { it > 0 }?.toInt() ?: TARGET_HEIGHT
            inputWidthFixed = shape?.getOrNull(3)?.takeIf { it > 0 }?.toInt()

            env = environment
            session = newSession
            newSession
        } catch (_: Throwable) {
            loadFailed = true
            null
        }
    }

    // ---------------------------------------------------------------------
    // fp32 -> 16-bit float bit patterns (no dependency on OrtUtil version)
    // ---------------------------------------------------------------------

    /** IEEE 754 binary16, round-to-nearest. */
    private fun floatToFp16Bits(value: Float): Short {
        val bits = java.lang.Float.floatToRawIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        val expRaw = (bits ushr 23) and 0xFF
        var mant = bits and 0x7FFFFF
        if (expRaw == 0xFF) { // Inf / NaN
            return (sign or 0x7C00 or (if (mant != 0) 0x200 else 0)).toShort()
        }
        val exp = expRaw - 127 + 15
        if (exp >= 31) return (sign or 0x7C00).toShort() // overflow -> Inf
        if (exp <= 0) {                                   // subnormal / underflow
            if (exp < -10) return sign.toShort()
            mant = mant or 0x800000
            val shift = 14 - exp
            var half = mant shr shift
            if (((mant shr (shift - 1)) and 1) != 0) half += 1
            return (sign or half).toShort()
        }
        var half = sign or (exp shl 10) or (mant shr 13)
        if ((mant and 0x1000) != 0) half += 1 // carry correctly bumps the exponent
        return half.toShort()
    }

    /** bfloat16 = top 16 bits of fp32, round-to-nearest-even. */
    private fun floatToBf16Bits(value: Float): Short {
        if (value.isNaN()) return 0x7FC0.toShort()
        val bits = java.lang.Float.floatToRawIntBits(value)
        val rounded = bits + 0x7FFF + ((bits ushr 16) and 1)
        return (rounded ushr 16).toShort()
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

            // onnxruntime-android 1.19.2 has no createTensor(FloatBuffer, shape, type)
            // overload, so pack the 16-bit values ourselves into a direct
            // native-order ByteBuffer and pass the raw bytes with the type.
            OnnxJavaType.FLOAT16, OnnxJavaType.BFLOAT16 -> {
                val isFp16 = inputType == OnnxJavaType.FLOAT16
                val bb = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
                for (i in 0 until n) {
                    bb.putShort(if (isFp16) floatToFp16Bits(gray[i]) else floatToBf16Bits(gray[i]))
                }
                bb.rewind()
                OnnxTensor.createTensor(environment, bb, shape, inputType)
            }

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
            // resize to the target height THIS MODEL declares in its own
            // input shape (not a hardcoded constant — see inputHeight in
            // ensureSession), preserving aspect ratio, in either grayscale
            // (1 channel) or RGB (3 channels) per inputChannels, plus
            // whatever per-model tuning was set via setNormalization /
            // setChannelOrder / setInvertColors (see those for details).
            val targetH = inputHeight
            val channels = inputChannels
            val scale = targetH.toFloat() / bitmap.height.toFloat()
            var newW = (bitmap.width * scale).roundToInt().coerceIn(8, MAX_WIDTH)
            val newH = targetH

            // Some models need an EXACT width, not a variable one. Scale to
            // fit within it preserving aspect ratio, then letterbox: pad the
            // remainder on the right with white (paper background), or if
            // the scaled line is wider than the model allows, crop from the
            // left edge (keeps the start of the line, which usually matters
            // more than the tail for a partially-cut recognition run).
            val fixedW = inputWidthFixed
            val resizeW = if (fixedW != null) newW.coerceAtMost(fixedW) else newW
            if (fixedW != null) newW = fixedW

            var working = Bitmap.createScaledBitmap(bitmap, resizeW, newH, true)
            if (fixedW != null && resizeW != fixedW) {
                val padded = Bitmap.createBitmap(fixedW, newH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(padded)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawBitmap(working, 0f, 0f, null)
                if (working !== bitmap) working.recycle()
                working = padded
            }
            scaled = working

            val pixels = IntArray(newW * newH)
            scaled.getPixels(pixels, 0, newW, 0, 0, newW, newH)

            val mean = normMean
            val std = normStd
            if (mean != null && mean.size != channels) {
                return@withContext RecognitionResult.Failure(
                    "setNormalization: panjang mean/std (${mean.size}) tidak sama dengan channel model ($channels)."
                )
            }

            fun normalize(v: Float, c: Int): Float =
                if (mean != null && std != null) (v - mean[c]) / std[c] else v

            // NCHW, channel-planar: (R or gray) plane, then G plane, then B plane.
            val planeSize = newW * newH
            val chw = FloatArray(planeSize * channels)
            when (channels) {
                1 -> for (i in pixels.indices) {
                    val px = pixels[i]
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    var v = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    if (invertColors) v = 1f - v
                    chw[i] = normalize(v, 0)
                }
                3 -> for (i in pixels.indices) {
                    val px = pixels[i]
                    var rv = ((px shr 16) and 0xFF) / 255f
                    var gv = ((px shr 8) and 0xFF) / 255f
                    var bv = (px and 0xFF) / 255f
                    if (invertColors) { rv = 1f - rv; gv = 1f - gv; bv = 1f - bv }
                    if (swapRedBlue) { val t = rv; rv = bv; bv = t }
                    chw[i] = normalize(rv, 0)
                    chw[planeSize + i] = normalize(gv, 1)
                    chw[2 * planeSize + i] = normalize(bv, 2)
                }
                else -> return@withContext RecognitionResult.Failure(
                    "Model butuh $channels channel input, hanya 1 (grayscale) atau 3 (RGB) yang didukung."
                )
            }

            val environment = this@TextLineRecognizer.env
                ?: return@withContext RecognitionResult.Failure("Model belum siap.")
            val tensor = buildInputTensor(
                environment,
                chw,
                longArrayOf(1, channels.toLong(), newH.toLong(), newW.toLong())
            )
            inputTensor = tensor

            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val outputTensor = result[0] as? OnnxTensor
                    ?: return@withContext RecognitionResult.Failure("Output model tidak terbaca.")
                val outShape = outputTensor.info.shape
                if (outShape.size < 2) {
                    return@withContext RecognitionResult.Failure(
                        "Bentuk output model tidak sesuai (rank ${outShape.size}, minimal 2)."
                    )
                }
                // The codec must have exactly one entry per non-blank class —
                // a size mismatch means this codec almost certainly wasn't
                // built for this exact model, and every decoded character
                // from here on would be shifted/wrong. Refuse rather than
                // silently emitting misaligned text.
                val expectedVocab = activeCodec.size + 1

                // Find which output axis IS the vocab/class axis by matching
                // its size to expectedVocab exactly — this works regardless
                // of the model's output layout ([1,vocab,1,seq], [1,seq,vocab],
                // [1,vocab,seq], NHWC-ish variants, etc.), because we never
                // guess: an axis either matches the codec size or it doesn't.
                val vocabAxis = outShape.indices.firstOrNull { outShape[it].toInt() == expectedVocab }
                    ?: return@withContext RecognitionResult.Failure(
                        "Tidak ada dimensi output yang cocok dengan ukuran codec: model menghasilkan " +
                            "bentuk ${outShape.joinToString(prefix = "[", postfix = "]")}, tapi codec " +
                            "punya ${activeCodec.size} baris (butuh dimensi bernilai $expectedVocab). " +
                            "Kemungkinan codec ini bukan untuk model ini, atau layout output model " +
                            "tidak didukung."
                    )
                // The sequence axis: the other axis with size > 1. (All
                // remaining axes — batch, and any singleton dims — are
                // expected to be exactly 1; their index is always 0 and
                // contributes nothing to the flat offset below.)
                val seqAxis = outShape.indices
                    .filter { it != vocabAxis && outShape[it] > 1 }
                    .maxByOrNull { outShape[it] }
                    ?: return@withContext RecognitionResult.Failure(
                        "Tidak ditemukan dimensi urutan (sequence) pada output model " +
                            "${outShape.joinToString(prefix = "[", postfix = "]")}."
                    )
                val vocabSize = outShape[vocabAxis].toInt()
                val seqLen = outShape[seqAxis].toInt()
                if (vocabSize <= 0 || seqLen <= 0) {
                    return@withContext RecognitionResult.Failure("Output model kosong.")
                }
                // Every other axis (batch, and any extra singleton dims) must
                // be size 1 — the flat-index math below assumes that. A
                // third axis with size > 1 would mean a layout with more
                // structure than a plain [.., vocab, .., seq, ..] recognizer
                // output, which this decoder doesn't know how to interpret;
                // refuse rather than silently reading the wrong elements.
                val strayAxis = outShape.indices.firstOrNull {
                    it != vocabAxis && it != seqAxis && outShape[it] > 1
                }
                if (strayAxis != null) {
                    return@withContext RecognitionResult.Failure(
                        "Bentuk output model ${outShape.joinToString(prefix = "[", postfix = "]")} punya " +
                            "lebih dari 2 dimensi berukuran >1 (di luar batch); layout ini tidak didukung."
                    )
                }

                // Row-major strides over the FULL output shape, so we can
                // index (class, timestep) correctly no matter where those
                // two axes sit among any extra singleton dims.
                val strides = LongArray(outShape.size)
                var acc = 1L
                for (i in outShape.indices.reversed()) {
                    strides[i] = acc
                    acc *= outShape[i].coerceAtLeast(1)
                }
                val total = acc.toInt()
                val logits = readScores(outputTensor, total)
                val vocabStride = strides[vocabAxis]
                val seqStride = strides[seqAxis]

                val blankIndex = if (blankAtEnd) vocabSize - 1 else 0
                var prevClass = -1
                val sb = StringBuilder()
                for (t in 0 until seqLen) {
                    var bestClass = 0
                    var bestScore = Float.NEGATIVE_INFINITY
                    for (c in 0 until vocabSize) {
                        val v = logits[(c * vocabStride + t * seqStride).toInt()]
                        if (v > bestScore) {
                            bestScore = v
                            bestClass = c
                        }
                    }
                    // Greedy CTC decode: collapse consecutive repeats, drop blank.
                    if (bestClass != prevClass && bestClass != blankIndex) {
                        val charIndex = if (blankAtEnd) bestClass else bestClass - 1
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
        inputChannels = 1
        inputHeight = TARGET_HEIGHT
        inputWidthFixed = null
    }

    fun close() {
        closeSessionOnly()
        env = null
    }
}
