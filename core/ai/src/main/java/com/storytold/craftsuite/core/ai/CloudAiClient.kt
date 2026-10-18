package com.storytold.craftsuite.core.ai

import com.storytold.craftsuite.core.tools.ToolDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AiToolCall(
    val callId: String,
    val name: String,
    val arguments: JsonObject
)

sealed class AiResponse {
    data class TextMessage(val text: String) : AiResponse()
    data class ToolCalls(val calls: List<AiToolCall>, val thoughtProcess: String? = null) : AiResponse()
    data class Error(val code: String, val message: String, val isRateLimit: Boolean = false) : AiResponse()
}

@Serializable
data class AgentMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<AiToolCall>? = null
)

data class AiConfig(
    val apiKey: String = "",
    val endpointUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    val modelName: String = "gemini-1.5-flash",
    val maxTokens: Int = 2048,
    val timeoutMs: Int = 30_000
)

interface CloudAiClient {
    suspend fun sendChat(
        messages: List<AgentMessage>,
        tools: List<ToolDefinition>,
        config: AiConfig
    ): AiResponse
}

class StandardCloudAiClient : CloudAiClient {

    override suspend fun sendChat(
        messages: List<AgentMessage>,
        tools: List<ToolDefinition>,
        config: AiConfig
    ): AiResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext AiResponse.Error(
                code = "NO_API_KEY",
                message = "Cloud AI API key is not configured. Please enter your API key in AI Settings."
            )
        }

        try {
            val url = URL(config.endpointUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = config.timeoutMs
                readTimeout = config.timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }

            // Convert tools to standard function calling schema
            val toolsJson = JsonArray(tools.map { tool ->
                buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parametersSchema)
                    })
                }
            })

            // Build request body
            val requestBody = buildJsonObject {
                put("model", config.modelName)
                put("max_tokens", config.maxTokens)
                put("messages", JsonArray(messages.map { msg ->
                    buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                        msg.toolCallId?.let { put("tool_call_id", it) }
                        msg.toolCalls?.let { tcs ->
                            put("tool_calls", JsonArray(tcs.map { tc ->
                                buildJsonObject {
                                    put("id", tc.callId)
                                    put("type", "function")
                                    put("function", buildJsonObject {
                                        put("name", tc.name)
                                        put("arguments", tc.arguments.toString())
                                    })
                                }
                            }))
                        }
                    }
                }))
                if (tools.isNotEmpty()) {
                    put("tools", toolsJson)
                    put("tool_choice", "auto")
                }
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == 429) {
                return@withContext AiResponse.Error("RATE_LIMIT", "AI API rate limit exceeded. Please retry shortly.", isRateLimit = true)
            }

            val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

            if (responseCode !in 200..299) {
                return@withContext AiResponse.Error("HTTP_$responseCode", "AI service returned HTTP $responseCode: $responseText")
            }

            val json = Json.parseToJsonElement(responseText).jsonObject
            val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val message = choice?.get("message")?.jsonObject
                ?: return@withContext AiResponse.Error("PARSE_ERROR", "Invalid response structure from AI model.")

            val toolCalls = message["tool_calls"]?.jsonArray
            if (toolCalls != null && toolCalls.isNotEmpty()) {
                val parsedCalls = toolCalls.mapNotNull { tcElement ->
                    val tcObj = tcElement.jsonObject
                    val id = tcObj["id"]?.jsonPrimitive?.content ?: "call_${System.currentTimeMillis()}"
                    val fnObj = tcObj["function"]?.jsonObject ?: return@mapNotNull null
                    val name = fnObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val argsStr = fnObj["arguments"]?.jsonPrimitive?.content ?: "{}"
                    val argsJson = try {
                        Json.parseToJsonElement(argsStr).jsonObject
                    } catch (e: Exception) {
                        JsonObject(emptyMap())
                    }
                    AiToolCall(id, name, argsJson)
                }
                return@withContext AiResponse.ToolCalls(parsedCalls)
            }

            val content = message["content"]?.jsonPrimitive?.content ?: ""
            AiResponse.TextMessage(content)
        } catch (e: java.net.SocketTimeoutException) {
            AiResponse.Error("TIMEOUT", "Request to AI service timed out after ${config.timeoutMs}ms.")
        } catch (e: Exception) {
            AiResponse.Error("NETWORK_ERROR", "Network connection to AI provider failed: ${e.localizedMessage}")
        }
    }
}

class MockCloudAiClient(
    private val responseProvider: (List<AgentMessage>, List<ToolDefinition>) -> AiResponse
) : CloudAiClient {
    override suspend fun sendChat(
        messages: List<AgentMessage>,
        tools: List<ToolDefinition>,
        config: AiConfig
    ): AiResponse = responseProvider(messages, tools)
}
