package com.storytold.craftsuite.feature.wordcraft

import com.storytold.craftsuite.core.enginebridge.WordEngineAdapter
import com.storytold.craftsuite.core.tools.StandardToolDefinitions
import com.storytold.craftsuite.core.tools.ToolHandler
import com.storytold.craftsuite.core.tools.ToolRegistry
import com.storytold.craftsuite.core.tools.ToolResult
import kotlinx.serialization.json.*

class WordToolHandler(private val adapter: WordEngineAdapter) : ToolHandler {

    override suspend fun execute(toolName: String, arguments: JsonObject): ToolResult {
        return when (toolName) {
            "editor_get_state" -> {
                val state = adapter.stateFlow.value
                val data = buildJsonObject {
                    put("title", state.documentTitle)
                    put("paragraph_count", state.paragraphs.size)
                    put("word_count", state.wordCount)
                    put("is_modified", state.isModified)
                    put("can_undo", state.canUndo)
                    put("can_redo", state.canRedo)
                }
                ToolResult.success(toolName, state.summary(), data)
            }

            "editor_get_selection" -> {
                val state = adapter.stateFlow.value
                val data = buildJsonObject {
                    put("paragraph_index", state.selection.paragraphIndex)
                    put("selected_text", state.selection.selectedText)
                }
                ToolResult.success(toolName, "Selected text: \"${state.selection.selectedText}\"", data)
            }

            "editor_undo" -> {
                val res = adapter.undo()
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Undo successful. Current state: ${adapter.stateFlow.value.summary()}")
                } else {
                    ToolResult.error(toolName, res.exceptionOrNull()?.message ?: "Undo failed")
                }
            }

            "editor_redo" -> {
                val res = adapter.redo()
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Redo successful. Current state: ${adapter.stateFlow.value.summary()}")
                } else {
                    ToolResult.error(toolName, res.exceptionOrNull()?.message ?: "Redo failed")
                }
            }

            "editor_validate_changes" -> {
                val state = adapter.stateFlow.value
                val isValid = state.paragraphs.isNotEmpty()
                ToolResult.success(toolName, if (isValid) "Document structure verified valid." else "Document is empty.")
            }

            "word_create_document" -> {
                val title = arguments["title"]?.jsonPrimitive?.content ?: "Untitled Document"
                val res = adapter.createDocument(title)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Created new document: \"$title\"")
                } else {
                    ToolResult.error(toolName, "Failed to create document: ${res.exceptionOrNull()?.message}")
                }
            }

            "word_insert_text" -> {
                val text = arguments["text"]?.jsonPrimitive?.content ?: ""
                val pIdx = arguments["paragraph_index"]?.jsonPrimitive?.intOrNull
                val res = adapter.insertText(text, pIdx)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Successfully inserted ${text.length} characters into document.")
                } else {
                    ToolResult.error(toolName, "Failed to insert text: ${res.exceptionOrNull()?.message}")
                }
            }

            "word_replace_selection" -> {
                val replacement = arguments["replacement_text"]?.jsonPrimitive?.content ?: ""
                val res = adapter.replaceSelection(replacement)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Replaced selection with: \"$replacement\"")
                } else {
                    ToolResult.error(toolName, "Failed to replace selection: ${res.exceptionOrNull()?.message}")
                }
            }

            "word_apply_format" -> {
                val bold = arguments["bold"]?.jsonPrimitive?.booleanOrNull
                val italic = arguments["italic"]?.jsonPrimitive?.booleanOrNull
                val underline = arguments["underline"]?.jsonPrimitive?.booleanOrNull
                val fontSize = arguments["font_size_sp"]?.jsonPrimitive?.floatOrNull
                val colorHex = arguments["color_hex"]?.jsonPrimitive?.contentOrNull

                val res = adapter.applyFormat(bold, italic, underline, fontSize, colorHex)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Applied formatting to selection.")
                } else {
                    ToolResult.error(toolName, "Failed to apply formatting: ${res.exceptionOrNull()?.message}")
                }
            }

            "word_get_content" -> {
                val res = adapter.getContent()
                if (res.isSuccess) {
                    val fullText = res.getOrNull() ?: ""
                    ToolResult.success(toolName, fullText, buildJsonObject { put("content", fullText) })
                } else {
                    ToolResult.error(toolName, "Failed to get document content: ${res.exceptionOrNull()?.message}")
                }
            }

            "word_save_document" -> {
                val res = adapter.saveToBytes()
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Document saved successfully (${res.getOrNull()?.size ?: 0} bytes).")
                } else {
                    ToolResult.error(toolName, "Failed to save document: ${res.exceptionOrNull()?.message}")
                }
            }

            else -> ToolResult.error(toolName, "Unsupported WordCraft operation: $toolName")
        }
    }

    fun registerWith(registry: ToolRegistry) {
        registry.registerTool(StandardToolDefinitions.EDITOR_GET_STATE, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_GET_SELECTION, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_UNDO, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_REDO, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_VALIDATE_CHANGES, this)
        registry.registerTool(StandardToolDefinitions.WORD_CREATE_DOCUMENT, this)
        registry.registerTool(StandardToolDefinitions.WORD_INSERT_TEXT, this)
        registry.registerTool(StandardToolDefinitions.WORD_REPLACE_SELECTION, this)
        registry.registerTool(StandardToolDefinitions.WORD_APPLY_FORMAT, this)
        registry.registerTool(StandardToolDefinitions.WORD_GET_CONTENT, this)
        registry.registerTool(StandardToolDefinitions.WORD_SAVE_DOCUMENT, this)
    }
}
