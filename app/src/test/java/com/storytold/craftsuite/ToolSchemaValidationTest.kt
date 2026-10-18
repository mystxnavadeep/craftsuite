package com.storytold.craftsuite

import com.storytold.craftsuite.core.model.EditorType
import com.storytold.craftsuite.core.safety.AlwaysApproveConfirmationHandler
import com.storytold.craftsuite.core.tools.StandardToolDefinitions
import com.storytold.craftsuite.core.tools.ToolRegistry
import com.storytold.craftsuite.feature.wordcraft.WordEngineAdapterImpl
import com.storytold.craftsuite.feature.wordcraft.WordToolHandler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolSchemaValidationTest {

    private lateinit var registry: ToolRegistry
    private lateinit var wordAdapter: WordEngineAdapterImpl

    @Before
    fun setup() {
        registry = ToolRegistry()
        wordAdapter = WordEngineAdapterImpl()
        WordToolHandler(wordAdapter).registerWith(registry)
    }

    @Test
    fun testAllStandardToolDefinitionsHaveValidSchemas() {
        val tools = StandardToolDefinitions.allDefinitions()
        assertTrue("Expected standard tools to be defined", tools.isNotEmpty())

        for (tool in tools) {
            assertTrue("Tool name must not be blank", tool.name.isNotBlank())
            assertTrue("Tool description must not be blank", tool.description.isNotBlank())

            val schema = tool.parametersSchema
            assertEquals("Root schema type must be 'object'", "object", schema["type"]?.jsonPrimitive?.content)
            assertEquals("additionalProperties must be false", false, schema["additionalProperties"]?.jsonPrimitive?.boolean)
            assertNotNull("Schema must contain 'properties' object", schema["properties"]?.jsonObject)
            assertNotNull("Schema must contain 'required' array", schema["required"]?.jsonArray)
        }
    }

    @Test
    fun testRejectToolCallWithAdditionalProperties() = runBlocking {
        val confirmation = AlwaysApproveConfirmationHandler()
        // word_insert_text requires 'text', but does not permit 'unauthorized_field'
        val invalidArgs = buildJsonObject {
            put("text", "Hello World")
            put("unauthorized_field", "malicious_injection")
        }

        val result = registry.dispatch(
            toolName = "word_insert_text",
            arguments = invalidArgs,
            activeEditor = EditorType.WORD,
            confirmationHandler = confirmation
        )

        assertFalse("Tool dispatch should fail on unexpected property", result.isSuccess)
        assertTrue("Error message should mention unexpected property", result.content.contains("Unexpected property 'unauthorized_field'"))
    }

    @Test
    fun testRejectToolCallWithMissingRequiredField() = runBlocking {
        val confirmation = AlwaysApproveConfirmationHandler()
        // word_insert_text requires 'text'
        val emptyArgs = buildJsonObject {}

        val result = registry.dispatch(
            toolName = "word_insert_text",
            arguments = emptyArgs,
            activeEditor = EditorType.WORD,
            confirmationHandler = confirmation
        )

        assertFalse("Tool dispatch should fail on missing required field", result.isSuccess)
        assertTrue("Error should mention missing parameter", result.content.contains("Missing required parameter 'text'"))
    }

    @Test
    fun testRejectToolForWrongEditor() = runBlocking {
        val confirmation = AlwaysApproveConfirmationHandler()
        val validArgs = buildJsonObject { put("text", "Some text") }

        // Attempting to call word_insert_text when active editor is GRID
        val result = registry.dispatch(
            toolName = "word_insert_text",
            arguments = validArgs,
            activeEditor = EditorType.GRID,
            confirmationHandler = confirmation
        )

        assertFalse("Tool dispatch should fail when tool does not belong to active editor", result.isSuccess)
        assertTrue("Error should mention editor mismatch", result.content.contains("only supported for WordCraft Document"))
    }
}
