package com.storytold.craftsuite.core.tools

import com.storytold.craftsuite.core.model.EditorType
import kotlinx.serialization.json.*

object StandardToolDefinitions {

    private fun objSchema(properties: Map<String, JsonElement>, required: List<String> = emptyList()): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
            put("additionalProperties", false)
        }
    }

    private fun stringProp(desc: String, maxLength: Int? = null, enumVals: List<String>? = null): JsonObject =
        buildJsonObject {
            put("type", "string")
            put("description", desc)
            maxLength?.let { put("maxLength", it) }
            enumVals?.let { put("enum", JsonArray(it.map { v -> JsonPrimitive(v) })) }
        }

    private fun intProp(desc: String, min: Int? = null, max: Int? = null): JsonObject =
        buildJsonObject {
            put("type", "integer")
            put("description", desc)
            min?.let { put("minimum", it) }
            max?.let { put("maximum", it) }
        }

    private fun boolProp(desc: String): JsonObject =
        buildJsonObject {
            put("type", "boolean")
            put("description", desc)
        }

    // General tools
    val EDITOR_GET_STATE = ToolDefinition(
        name = "editor_get_state",
        description = "Retrieve current editor metadata, modified status, title, and summary.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.WORD, EditorType.GRID, EditorType.DECK)
    )

    val EDITOR_GET_SELECTION = ToolDefinition(
        name = "editor_get_selection",
        description = "Get the active selection or cursor position in the current editor.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.WORD, EditorType.GRID, EditorType.DECK)
    )

    val EDITOR_UNDO = ToolDefinition(
        name = "editor_undo",
        description = "Undo the last editor operation if supported.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.WORD, EditorType.GRID, EditorType.DECK)
    )

    val EDITOR_REDO = ToolDefinition(
        name = "editor_redo",
        description = "Redo the previously undone editor operation if supported.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.WORD, EditorType.GRID, EditorType.DECK)
    )

    val EDITOR_VALIDATE_CHANGES = ToolDefinition(
        name = "editor_validate_changes",
        description = "Verify integrity and structure of active editor state.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.WORD, EditorType.GRID, EditorType.DECK)
    )

    // WordCraft tools
    val WORD_CREATE_DOCUMENT = ToolDefinition(
        name = "word_create_document",
        description = "Create a new blank WordCraft document.",
        parametersSchema = objSchema(
            mapOf("title" to stringProp("Title for the new document", maxLength = 255)),
            required = listOf("title")
        ),
        supportedEditors = listOf(EditorType.WORD)
    )

    val WORD_INSERT_TEXT = ToolDefinition(
        name = "word_insert_text",
        description = "Insert text into the active WordCraft document at current cursor or end.",
        parametersSchema = objSchema(
            mapOf(
                "text" to stringProp("Text to insert", maxLength = 50000),
                "paragraph_index" to intProp("Target paragraph index (0-based)", min = 0)
            ),
            required = listOf("text")
        ),
        supportedEditors = listOf(EditorType.WORD)
    )

    val WORD_REPLACE_SELECTION = ToolDefinition(
        name = "word_replace_selection",
        description = "Replace current text selection or specific text range.",
        parametersSchema = objSchema(
            mapOf(
                "replacement_text" to stringProp("Text to replace selection with", maxLength = 50000),
                "replace_entire_document" to boolProp("Whether to overwrite entire document")
            ),
            required = listOf("replacement_text")
        ),
        supportedEditors = listOf(EditorType.WORD)
    )

    val WORD_APPLY_FORMAT = ToolDefinition(
        name = "word_apply_format",
        description = "Apply formatting attributes (bold, italic, underline, font size, color) to selection.",
        parametersSchema = objSchema(
            mapOf(
                "bold" to boolProp("Toggle bold styling"),
                "italic" to boolProp("Toggle italic styling"),
                "underline" to boolProp("Toggle underline styling"),
                "font_size_sp" to intProp("Font size in SP (8 to 72)", min = 8, max = 72),
                "color_hex" to stringProp("Hex color code, e.g. #FF0000", maxLength = 7)
            )
        ),
        supportedEditors = listOf(EditorType.WORD)
    )

    val WORD_GET_CONTENT = ToolDefinition(
        name = "word_get_content",
        description = "Get full plain text or structured paragraphs of the document.",
        parametersSchema = objSchema(
            mapOf("max_paragraphs" to intProp("Max paragraphs to return", min = 1, max = 500))
        ),
        supportedEditors = listOf(EditorType.WORD)
    )

    val WORD_SAVE_DOCUMENT = ToolDefinition(
        name = "word_save_document",
        description = "Commit and serialize active document state to storage.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.WORD)
    )

    // GridCraft tools
    val GRID_CREATE_WORKBOOK = ToolDefinition(
        name = "grid_create_workbook",
        description = "Create a new blank GridCraft spreadsheet workbook.",
        parametersSchema = objSchema(
            mapOf("title" to stringProp("Workbook title", maxLength = 255)),
            required = listOf("title")
        ),
        supportedEditors = listOf(EditorType.GRID)
    )

    val GRID_GET_SHEETS = ToolDefinition(
        name = "grid_get_sheets",
        description = "List all worksheets in the active workbook.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.GRID)
    )

    val GRID_READ_RANGE = ToolDefinition(
        name = "grid_read_range",
        description = "Read cell coordinates, formulas, and formatted values within a range (e.g. A1:D10).",
        parametersSchema = objSchema(
            mapOf(
                "sheet_id" to stringProp("Target sheet identifier or name", maxLength = 128),
                "range" to stringProp("A1-style range expression, e.g. A1:C5", maxLength = 32)
            ),
            required = listOf("sheet_id", "range")
        ),
        supportedEditors = listOf(EditorType.GRID)
    )

    val GRID_SET_CELL = ToolDefinition(
        name = "grid_set_cell",
        description = "Set a value or formula in a validated worksheet cell.",
        parametersSchema = objSchema(
            mapOf(
                "sheet_id" to stringProp("Target sheet identifier", maxLength = 128),
                "cell" to stringProp("Cell coordinate like A1, B2", maxLength = 16),
                "value" to stringProp("Cell text, number, or formula", maxLength = 4000),
                "value_type" to stringProp("Data type", enumVals = listOf("text", "number", "formula"))
            ),
            required = listOf("sheet_id", "cell", "value", "value_type")
        ),
        supportedEditors = listOf(EditorType.GRID)
    )

    val GRID_APPLY_RANGE = ToolDefinition(
        name = "grid_apply_range",
        description = "Batch apply values across multiple cells in a sheet.",
        parametersSchema = objSchema(
            mapOf(
                "sheet_id" to stringProp("Target sheet identifier", maxLength = 128),
                "cells_json" to stringProp("JSON mapping of cell coordinate to value string", maxLength = 50000)
            ),
            required = listOf("sheet_id", "cells_json")
        ),
        supportedEditors = listOf(EditorType.GRID)
    )

    val GRID_CREATE_SHEET = ToolDefinition(
        name = "grid_create_sheet",
        description = "Add a new worksheet tab to the workbook.",
        parametersSchema = objSchema(
            mapOf("title" to stringProp("Worksheet title", maxLength = 64)),
            required = listOf("title")
        ),
        supportedEditors = listOf(EditorType.GRID)
    )

    val GRID_SAVE_WORKBOOK = ToolDefinition(
        name = "grid_save_workbook",
        description = "Serialize and save active workbook to storage.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.GRID)
    )

    // DeckCraft tools
    val DECK_CREATE_PRESENTATION = ToolDefinition(
        name = "deck_create_presentation",
        description = "Create a new blank DeckCraft presentation.",
        parametersSchema = objSchema(
            mapOf("title" to stringProp("Presentation title", maxLength = 255)),
            required = listOf("title")
        ),
        supportedEditors = listOf(EditorType.DECK)
    )

    val DECK_GET_SLIDES = ToolDefinition(
        name = "deck_get_slides",
        description = "Retrieve list of slides with index, title, and shape counts.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.DECK)
    )

    val DECK_CREATE_SLIDE = ToolDefinition(
        name = "deck_create_slide",
        description = "Add a new slide to the presentation.",
        parametersSchema = objSchema(
            mapOf(
                "title" to stringProp("Slide title", maxLength = 255),
                "index" to intProp("Position to insert slide (defaults to end)", min = 0)
            ),
            required = listOf("title")
        ),
        supportedEditors = listOf(EditorType.DECK)
    )

    val DECK_UPDATE_SLIDE = ToolDefinition(
        name = "deck_update_slide",
        description = "Update title, notes, or content of a specific slide.",
        parametersSchema = objSchema(
            mapOf(
                "slide_index" to intProp("0-based index of slide to update", min = 0),
                "title" to stringProp("Updated slide title", maxLength = 255),
                "notes" to stringProp("Speaker notes", maxLength = 10000)
            ),
            required = listOf("slide_index")
        ),
        supportedEditors = listOf(EditorType.DECK)
    )

    val DECK_REORDER_SLIDES = ToolDefinition(
        name = "deck_reorder_slides",
        description = "Move a slide from one position index to another.",
        parametersSchema = objSchema(
            mapOf(
                "from_index" to intProp("Current slide index", min = 0),
                "to_index" to intProp("New slide index", min = 0)
            ),
            required = listOf("from_index", "to_index")
        ),
        supportedEditors = listOf(EditorType.DECK)
    )

    val DECK_SAVE_PRESENTATION = ToolDefinition(
        name = "deck_save_presentation",
        description = "Serialize and save active presentation to storage.",
        parametersSchema = objSchema(emptyMap()),
        supportedEditors = listOf(EditorType.DECK)
    )

    fun allDefinitions(): List<ToolDefinition> = listOf(
        EDITOR_GET_STATE, EDITOR_GET_SELECTION, EDITOR_UNDO, EDITOR_REDO, EDITOR_VALIDATE_CHANGES,
        WORD_CREATE_DOCUMENT, WORD_INSERT_TEXT, WORD_REPLACE_SELECTION, WORD_APPLY_FORMAT, WORD_GET_CONTENT, WORD_SAVE_DOCUMENT,
        GRID_CREATE_WORKBOOK, GRID_GET_SHEETS, GRID_READ_RANGE, GRID_SET_CELL, GRID_APPLY_RANGE, GRID_CREATE_SHEET, GRID_SAVE_WORKBOOK,
        DECK_CREATE_PRESENTATION, DECK_GET_SLIDES, DECK_CREATE_SLIDE, DECK_UPDATE_SLIDE, DECK_REORDER_SLIDES, DECK_SAVE_PRESENTATION
    )
}
