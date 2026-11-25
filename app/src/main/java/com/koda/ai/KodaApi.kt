package com.koda.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Параметры API, как в Koda CLI 1.3.6:
 *   базовый адрес: https://api.kodacode.ru/v1
 *   авторизация:   Authorization: Bearer <API-ключ>
 * Ключ больше не хранится в коде — его вводит пользователь при входе.
 */
object KodaConfig {
    const val BASE_URL = "https://api.kodacode.ru/v1"
    const val MODEL = "koda-base"
    const val SYSTEM_PROMPT = "Ты — KodaAI, полезный ассистент. Отвечай по-русски, понятно и по делу."
}

data class ChatMessage(
    val text: String,
    val fromUser: Boolean
)

object KodaApi {

    /**
     * Проверяет ключ запросом GET /models.
     * true — ключ принят, false — сервер его отклонил.
     * Сетевые ошибки бросаются как исключения.
     */
    suspend fun verifyKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val conn = (URL("${KodaConfig.BASE_URL}/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            when (val code = conn.responseCode) {
                in 200..299 -> true
                401, 403 -> false
                else -> error("Сервер вернул HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Отправляет сообщение и стримит ответ (SSE).
     * Каждый фрагмент текста передаётся в onDelta.
     */
    suspend fun stream(
        apiKey: String,
        history: List<ChatMessage>,
        message: String,
        onDelta: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val conn = (URL("${KodaConfig.BASE_URL}/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", KodaConfig.SYSTEM_PROMPT))
            history.takeLast(20).forEach { m ->
                put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("content", m.text))
            }
            put(JSONObject().put("role", "user").put("content", message))
        }

        val body = JSONObject()
            .put("model", KodaConfig.MODEL)
            .put("messages", messages)
            .put("stream", true)

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                if (code == 401 || code == 403) {
                    error("Ключ отклонён сервером. Выйдите и войдите заново.")
                }
                val details = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Сервер вернул HTTP $code: $details")
            }

            conn.inputStream.bufferedReader().use { reader ->
                for (raw in reader.lineSequence()) {
                    val line = raw.trim()
                    if (!line.startsWith("data:")) continue

                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break

                    val delta = JSONObject(payload)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")

                    if (!delta.isNullOrEmpty()) onDelta(delta)
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
