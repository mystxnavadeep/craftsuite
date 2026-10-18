package com.storytold.craftsuite.core.tools

import com.storytold.craftsuite.core.model.EditorType
import com.storytold.craftsuite.core.safety.SafetyLimits
import com.storytold.craftsuite.core.safety.SafetyValidator
import com.storytold.craftsuite.core.safety.UserConfirmationHandler
import com.storytold.craftsuite.core.safety.ValidationResult
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: JsonObject,
    val supportedEditors: List<EditorType> = listOf(EditorType.WORD, EditorType.GRID, EditorType.DECK)
)

@Serializable
data class ToolResult(
    val toolName: String,
    val isSuccess: Boolean,
    val content: String,
    val data: JsonElement? = null,
    val error: String? = null
) {
    companion object {
        fun success(toolName: String, content: String, data: JsonElement? = null): ToolResult =
            ToolResult(toolName = toolName, isSuccess = true, content = content, data = data)

        fun error(toolName: String, errorMessage: String): ToolResult =
            ToolResult(toolName = toolName, isSuccess = false, content = errorMessage, error = errorMessage)
    }
}

interface ToolHandler {
    suspend fun execute(toolName: String, arguments: JsonObject): ToolResult
}

class ToolRegistry {
    private val tools = mutableMapOf<String, Pair<ToolDefinition, ToolHandler>>()

    fun registerTool(definition: ToolDefinition, handler: ToolHandler) {
        tools[definition.name] = Pair(definition, handler)
    }

    fun getTool(name: String): ToolDefinition? = tools[name]?.first

    fun getAvailableTools(activeEditor: EditorType): List<ToolDefinition> {
        return tools.values
            .map { it.first }
            .filter { it.supportedEditors.contains(activeEditor) }
    }

    fun getAllTools(): List<ToolDefinition> = tools.values.map { it.first }

    suspend fun dispatch(
        toolName: String,
        arguments: JsonObject,
        activeEditor: EditorType,
        confirmationHandler: UserConfirmationHandler,
        limits: SafetyLimits = SafetyLimits(),
        timeoutMs: Long = 15_000L
    ): ToolResult {
        val entry = tools[toolName] ?: return ToolResult.error(
            toolName,
            "Tool '$toolName' is not registered in the application tool registry."
        )

        val (definition, handler) = entry

        // 1. Authorization check: Is this tool permitted for the active editor?
        if (!definition.supportedEditors.contains(activeEditor)) {
            return ToolResult.error(
                toolName,
                "Tool '$toolName' is only supported for ${definition.supportedEditors.joinToString { it.displayName }}, but active editor is ${activeEditor.displayName}."
            )
        }

        // 2. Strict Schema Validation (required arguments and additional properties)
        val schemaValidation = validateAgainstSchema(definition.parametersSchema, arguments)
        if (schemaValidation != null) {
            return ToolResult.error(toolName, "Schema validation failed: $schemaValidation")
        }

        // 3. Application Safety & Limit Assessment
        val argMap = arguments.mapValues { (_, v) -> jsonElementToAny(v) }
        when (val safetyResult = SafetyValidator.assessToolCallSafety(toolName, argMap, limits)) {
            is ValidationResult.Invalid -> {
                return ToolResult.error(toolName, "Operation blocked by safety policy: ${safetyResult.reason}")
            }
            is ValidationResult.RequiresConfirmation -> {
                val approved = confirmationHandler.confirmAction(safetyResult.action)
                if (!approved) {
                    return ToolResult.error(
                        toolName,
                        "Action was cancelled by user: ${safetyResult.action.title}"
                    )
                }
            }
            is ValidationResult.Valid -> {
                // Proceed
            }
        }

        // 4. Bounded Execution Time
        return try {
            withTimeout(timeoutMs) {
                handler.execute(toolName, arguments)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ToolResult.error(toolName, "Tool execution timed out after ${timeoutMs}ms")
        } catch (e: Exception) {
            ToolResult.error(toolName, "Tool execution failed: ${e.message}")
        }
    }

    private fun validateAgainstSchema(schema: JsonObject, arguments: JsonObject): String? {
        val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val additionalProperties = schema["additionalProperties"]?.jsonPrimitive?.booleanOrNull ?: true

        // Check required fields
        for (req in required) {
            if (!arguments.containsKey(req)) {
                return "Missing required parameter '$req'"
            }
        }

        // Check additionalProperties: false
        if (!additionalProperties) {
            for (key in arguments.keys) {
                if (!properties.containsKey(key)) {
                    return "Unexpected property '$key'. Additional properties are disallowed."
                }
            }
        }

        return null
    }

    private fun jsonElementToAny(elem: JsonElement): Any? {
        return when (elem) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (elem.isString) elem.content
                else elem.booleanOrNull ?: elem.longOrNull ?: elem.doubleOrNull
            }
            is JsonArray -> elem.map { jsonElementToAny(it) }
            is JsonObject -> elem.mapValues { jsonElementToAny(it.value) }
        }
    }
}
