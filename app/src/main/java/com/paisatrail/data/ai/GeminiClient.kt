package com.paisatrail.data.ai

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional helper that is only ever called when the user has pasted their own Gemini API key
 * in Settings. Uses plain HttpURLConnection so the app needs no networking library.
 * Every failure (no network, bad key, quota, unexpected reply) returns null and the caller
 * falls back to the offline result.
 */
@Singleton
class GeminiClient @Inject constructor() {

    suspend fun generate(apiKey: String, model: String, prompt: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || model.isBlank()) return@withContext null
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
            val body = JSONObject().put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            )
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@withContext null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text").trim().ifEmpty { null }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
