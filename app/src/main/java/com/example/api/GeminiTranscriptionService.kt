package com.example.api

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val text: String) : GeminiResult()
    data class Failure(val message: String) : GeminiResult()
}

/**
 * Calls the Gemini API (generateContent, REST) to transcribe a single cropped
 * line image. Talks to Google's servers directly over HTTPS with the user's own
 * API key — nothing here is bundled with or proxied through this app's own
 * infrastructure (there isn't any). Every failure mode (no key, no network, bad
 * key, rate limit, safety block, unparseable response) is turned into a
 * [GeminiResult.Failure] with a human-readable Indonesian message instead of
 * throwing, so a flaky API call never crashes the app.
 */
class GeminiTranscriptionService {

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        private const val ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

        private val PROMPT = """
            Anda adalah asisten transkripsi naskah/manuskrip yang sangat teliti.
            Gambar berikut adalah potongan SATU baris teks dari sebuah naskah atau dokumen.
            Tuliskan HANYA teks yang tertulis pada gambar tersebut, persis apa adanya:
            - Jangan menerjemahkan.
            - Jangan mengoreksi ejaan atau tata bahasa.
            - Pertahankan tanda baca dan tanda diakritik jika ada.
            - Jangan tambahkan label, tanda kutip, catatan, atau penjelasan apa pun.
            Jika gambar kosong, buram total, atau sama sekali tidak terbaca, kembalikan
            string kosong saja.
        """.trimIndent()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeImage(
        imageFile: File,
        apiKey: String,
        modelName: String = DEFAULT_MODEL
    ): GeminiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiResult.Failure("API key Gemini belum diisi. Buka pengaturan Gemini terlebih dahulu.")
        }
        if (!imageFile.exists()) {
            return@withContext GeminiResult.Failure("File gambar potongan baris tidak ditemukan.")
        }

        try {
            val base64Image = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply { put("text", PROMPT) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                })
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(String.format(ENDPOINT_TEMPLATE, modelName))
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext GeminiResult.Failure(parseErrorMessage(response.code, responseText))
                }

                val json = try {
                    JSONObject(responseText)
                } catch (_: Exception) {
                    null
                } ?: return@withContext GeminiResult.Failure("Gagal membaca respons dari Gemini.")

                val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason", "")
                if (!blockReason.isNullOrEmpty()) {
                    return@withContext GeminiResult.Failure("Gambar ditolak oleh filter keamanan Gemini ($blockReason).")
                }

                val text = extractText(json)
                if (text == null) {
                    GeminiResult.Failure("Gemini tidak mengembalikan teks yang bisa dibaca.")
                } else {
                    GeminiResult.Success(text)
                }
            }
        } catch (_: UnknownHostException) {
            GeminiResult.Failure("Tidak ada koneksi internet.")
        } catch (e: IOException) {
            GeminiResult.Failure("Gagal terhubung ke Gemini API: ${e.message ?: "jaringan bermasalah"}.")
        } catch (_: OutOfMemoryError) {
            GeminiResult.Failure("Gambar potongan terlalu besar untuk diproses.")
        } catch (e: Exception) {
            GeminiResult.Failure("Terjadi kesalahan tak terduga: ${e.message ?: "tidak diketahui"}.")
        }
    }

    private fun extractText(json: JSONObject): String? {
        return try {
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            sb.toString().trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        }
        return when (code) {
            400 -> "Permintaan ke Gemini tidak valid" + (detail?.let { ": $it" } ?: ".")
            401, 403 -> "API key Gemini tidak valid atau tidak diizinkan."
            404 -> "Model Gemini tidak ditemukan. Periksa nama model di pengaturan."
            429 -> "Batas kuota/rate limit Gemini API tercapai. Coba lagi nanti."
            in 500..599 -> "Server Gemini sedang bermasalah. Coba lagi nanti."
            else -> "Gemini API error ($code)" + (detail?.let { ": $it" } ?: ".")
        }
    }
}
