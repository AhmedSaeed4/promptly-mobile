package com.promptly.mobile

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object GroqApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun transcribe(file: File, apiKey: String, model: String = "whisper-large-v3"): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "text")
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $raw")
            }
            return raw.trim()
        }
    }
}