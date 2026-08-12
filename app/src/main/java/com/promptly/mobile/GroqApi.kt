package com.promptly.mobile

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object GroqApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun transcribe(
        file: File,
        apiKey: String,
        model: String = "whisper-large-v3",
        language: String = "en"
    ): okhttp3.Call {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "text")
            .addFormDataPart("language", language)
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

        return client.newCall(request)
    }

    fun translate(
        text: String,
        apiKey: String,
        sourceLanguage: String,
        targetLanguage: String
    ): okhttp3.Call {
        val json = org.json.JSONObject()
            .put("model", "llama-3.3-70b-versatile")
            .put("temperature", 0.2)
            .put(
                "messages",
                org.json.JSONArray()
                    .put(
                        org.json.JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "You are a professional translator. Translate the user's text from " +
                                    "$sourceLanguage to $targetLanguage. Output ONLY the translation — no " +
                                    "explanations, no quotation marks, no notes."
                            )
                    )
                    .put(
                        org.json.JSONObject()
                            .put("role", "user")
                            .put("content", text)
                    )
            )

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request)
    }
}