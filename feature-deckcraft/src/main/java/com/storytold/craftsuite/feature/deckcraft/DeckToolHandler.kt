package com.storytold.craftsuite.feature.deckcraft

import com.storytold.craftsuite.core.enginebridge.DeckEngineAdapter
import com.storytold.craftsuite.core.tools.StandardToolDefinitions
import com.storytold.craftsuite.core.tools.ToolHandler
import com.storytold.craftsuite.core.tools.ToolRegistry
import com.storytold.craftsuite.core.tools.ToolResult
import kotlinx.serialization.json.*

class DeckToolHandler(private val adapter: DeckEngineAdapter) : ToolHandler {

    override suspend fun execute(toolName: String, arguments: JsonObject): ToolResult {
        return when (toolName) {
            "editor_get_state" -> {
                val state = adapter.stateFlow.value
                val data = buildJsonObject {
                    put("title", state.documentTitle)
                    put("slide_count", state.slides.size)
                    put("active_slide", state.selection.currentSlideIndex)
                    put("is_modified", state.isModified)
                    put("can_undo", state.canUndo)
                    put("can_redo", state.canRedo)
                }
                ToolResult.success(toolName, state.summary(), data)
            }

            "editor_get_selection" -> {
                val state = adapter.stateFlow.value
                val data = buildJsonObject {
                    put("slide_index", state.selection.currentSlideIndex)
                    put("shape_id", state.selection.selectedShapeId)
                }
                ToolResult.success(toolName, "Active slide: ${state.selection.currentSlideIndex + 1}", data)
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
                val isValid = state.slides.isNotEmpty()
                ToolResult.success(toolName, if (isValid) "Presentation slides verified valid." else "Presentation has no slides.")
            }

            "deck_create_presentation" -> {
                val title = arguments["title"]?.jsonPrimitive?.content ?: "Untitled Presentation"
                val res = adapter.createPresentation(title)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Created new presentation: \"$title\"")
                } else {
                    ToolResult.error(toolName, "Failed to create presentation: ${res.exceptionOrNull()?.message}")
                }
            }

            "deck_get_slides" -> {
                val res = adapter.getSlides()
                if (res.isSuccess) {
                    val slides = res.getOrNull() ?: emptyList()
                    val data = buildJsonObject {
                        put("slides", JsonArray(slides.map { s ->
                            buildJsonObject {
                                put("index", s.index)
                                put("title", s.title)
                                put("shape_count", s.shapes.size)
                            }
                        }))
                    }
                    ToolResult.success(toolName, "Presentation has ${slides.size} slides.", data)
                } else {
                    ToolResult.error(toolName, "Failed to get slides: ${res.exceptionOrNull()?.message}")
                }
            }

            "deck_create_slide" -> {
                val title = arguments["title"]?.jsonPrimitive?.content ?: "New Slide"
                val idx = arguments["index"]?.jsonPrimitive?.intOrNull
                val res = adapter.createSlide(title, idx)
                if (res.isSuccess) {
                    val slide = res.getOrNull()
                    ToolResult.success(toolName, "Created slide '${slide?.title}' at index ${slide?.index}.")
                } else {
                    ToolResult.error(toolName, "Failed to create slide: ${res.exceptionOrNull()?.message}")
                }
            }

            "deck_update_slide" -> {
                val idx = arguments["slide_index"]?.jsonPrimitive?.int ?: 0
                val title = arguments["title"]?.jsonPrimitive?.contentOrNull
                val notes = arguments["notes"]?.jsonPrimitive?.contentOrNull
                val res = adapter.updateSlide(idx, title, notes)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Updated slide $idx.")
                } else {
                    ToolResult.error(toolName, "Failed to update slide: ${res.exceptionOrNull()?.message}")
                }
            }

            "deck_reorder_slides" -> {
                val from = arguments["from_index"]?.jsonPrimitive?.int ?: 0
                val to = arguments["to_index"]?.jsonPrimitive?.int ?: 0
                val res = adapter.reorderSlides(from, to)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Moved slide from $from to $to.")
                } else {
                    ToolResult.error(toolName, "Failed to reorder slides: ${res.exceptionOrNull()?.message}")
                }
            }

            "deck_save_presentation" -> {
                val res = adapter.saveToBytes()
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Presentation saved successfully (${res.getOrNull()?.size ?: 0} bytes).")
                } else {
                    ToolResult.error(toolName, "Failed to save presentation: ${res.exceptionOrNull()?.message}")
                }
            }

            else -> ToolResult.error(toolName, "Unsupported DeckCraft operation: $toolName")
        }
    }

    fun registerWith(registry: ToolRegistry) {
        registry.registerTool(StandardToolDefinitions.EDITOR_GET_STATE, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_GET_SELECTION, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_UNDO, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_REDO, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_VALIDATE_CHANGES, this)
        registry.registerTool(StandardToolDefinitions.DECK_CREATE_PRESENTATION, this)
        registry.registerTool(StandardToolDefinitions.DECK_GET_SLIDES, this)
        registry.registerTool(StandardToolDefinitions.DECK_CREATE_SLIDE, this)
        registry.registerTool(StandardToolDefinitions.DECK_UPDATE_SLIDE, this)
        registry.registerTool(StandardToolDefinitions.DECK_REORDER_SLIDES, this)
        registry.registerTool(StandardToolDefinitions.DECK_SAVE_PRESENTATION, this)
    }
}
