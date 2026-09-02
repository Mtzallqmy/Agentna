package com.mtzallqmy.agentna.runtime

import com.mtzallqmy.agentna.security.SecureApiKeyStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ProviderMessage(val role: String, val content: String)

class ProviderClient(
    private val keyStore: SecureApiKeyStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    suspend fun complete(
        provider: String,
        model: String,
        systemPrompt: String,
        messages: List<ProviderMessage>
    ): String {
        val normalized = provider.lowercase()
        val apiKey = keyStore.getApiKey(normalized)
            ?: throw ProviderException("No API key configured for ${ProviderCatalog.definition(normalized).displayName}")
        return when (normalized) {
            "openai" -> openAiResponses(apiKey, model, systemPrompt, messages)
            "gemini" -> geminiGenerateContent(apiKey, model, systemPrompt, messages)
            "anthropic" -> anthropicMessages(apiKey, model, systemPrompt, messages)
            "xai" -> xAiChatCompletions(apiKey, model, systemPrompt, messages)
            else -> throw ProviderException("Unsupported provider: $provider")
        }
    }

    private fun openAiResponses(key: String, model: String, system: String, messages: List<ProviderMessage>): String {
        val input = JSONArray().put(responseInput("system", system))
        messages.takeLast(MAX_HISTORY).forEach { input.put(responseInput(normalizeRole(it.role), it.content)) }
        val body = JSONObject()
            .put("model", model)
            .put("input", input)
            .put("max_output_tokens", MAX_OUTPUT_TOKENS)
        val json = postJson(
            url = "https://api.openai.com/v1/responses",
            keyHeaders = mapOf("Authorization" to "Bearer $key"),
            body = body
        )
        json.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = json.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text" && part.optString("text").isNotBlank()) {
                    return part.getString("text")
                }
            }
        }
        throw ProviderException("OpenAI returned no text output")
    }

    private fun xAiChatCompletions(key: String, model: String, system: String, messages: List<ProviderMessage>): String {
        val payloadMessages = JSONArray().put(chatMessage("system", system))
        messages.takeLast(MAX_HISTORY).forEach { payloadMessages.put(chatMessage(normalizeRole(it.role), it.content)) }
        val body = JSONObject()
            .put("model", model)
            .put("messages", payloadMessages)
            .put("max_tokens", MAX_OUTPUT_TOKENS)
        val json = postJson(
            url = "https://api.x.ai/v1/chat/completions",
            keyHeaders = mapOf("Authorization" to "Bearer $key"),
            body = body
        )
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw ProviderException("xAI returned no text output")
    }

    private fun geminiGenerateContent(key: String, model: String, system: String, messages: List<ProviderMessage>): String {
        val contents = JSONArray()
        messages.takeLast(MAX_HISTORY).forEach { message ->
            contents.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("maxOutputTokens", MAX_OUTPUT_TOKENS)
                    .put("responseMimeType", "text/plain")
            )
        val json = postJson(
            url = "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent",
            keyHeaders = mapOf("x-goog-api-key" to key),
            body = body
        )
        val parts = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: JSONArray()
        val combined = buildString {
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotBlank()) append(text)
            }
        }
        return combined.takeIf { it.isNotBlank() }
            ?: throw ProviderException("Gemini returned no text output")
    }

    private fun anthropicMessages(key: String, model: String, system: String, messages: List<ProviderMessage>): String {
        val payloadMessages = JSONArray()
        messages.takeLast(MAX_HISTORY).forEach { message ->
            payloadMessages.put(chatMessage(normalizeRole(message.role), message.content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("system", system)
            .put("max_tokens", MAX_OUTPUT_TOKENS)
            .put("messages", payloadMessages)
        val json = postJson(
            url = "https://api.anthropic.com/v1/messages",
            keyHeaders = mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01"
            ),
            body = body
        )
        val content = json.optJSONArray("content") ?: JSONArray()
        val combined = buildString {
            for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                if (item.optString("type") == "text") append(item.optString("text"))
            }
        }
        return combined.takeIf { it.isNotBlank() }
            ?: throw ProviderException("Anthropic returned no text output")
    }

    private fun postJson(url: String, keyHeaders: Map<String, String>, body: JSONObject): JSONObject {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Agentna/1.0 (Android)")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        keyHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ProviderException(providerError(response.code, responseText))
                }
                if (responseText.isBlank()) throw ProviderException("Provider returned an empty response")
                return JSONObject(responseText)
            }
        } catch (e: ProviderException) {
            throw e
        } catch (e: IOException) {
            throw ProviderException("Network error: ${e.message ?: "request failed"}")
        } catch (e: Exception) {
            throw ProviderException("Provider response error: ${e.message ?: "invalid response"}")
        }
    }

    private fun providerError(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            when (val error = json.opt("error")) {
                is JSONObject -> error.optString("message")
                is String -> error
                else -> json.optString("message")
            }
        }.getOrNull().orEmpty().replace(Regex("[\\r\\n]+"), " ").take(300)
        return if (detail.isBlank()) "Provider request failed (HTTP $code)" else "Provider request failed (HTTP $code): $detail"
    }

    private fun responseInput(role: String, text: String) = JSONObject()
        .put("role", role)
        .put("content", JSONArray().put(JSONObject().put("type", "input_text").put("text", text)))

    private fun chatMessage(role: String, text: String) = JSONObject().put("role", role).put("content", text)

    private fun normalizeRole(role: String): String = if (role == "assistant") "assistant" else "user"

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_HISTORY = 30
        private const val MAX_OUTPUT_TOKENS = 8192
    }
}

class ProviderException(message: String) : Exception(message)
