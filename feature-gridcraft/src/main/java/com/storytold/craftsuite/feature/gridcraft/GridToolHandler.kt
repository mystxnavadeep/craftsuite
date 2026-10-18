package com.storytold.craftsuite.feature.gridcraft

import com.storytold.craftsuite.core.enginebridge.GridEngineAdapter
import com.storytold.craftsuite.core.model.CellValueType
import com.storytold.craftsuite.core.safety.SafetyValidator
import com.storytold.craftsuite.core.tools.StandardToolDefinitions
import com.storytold.craftsuite.core.tools.ToolHandler
import com.storytold.craftsuite.core.tools.ToolRegistry
import com.storytold.craftsuite.core.tools.ToolResult
import kotlinx.serialization.json.*

class GridToolHandler(private val adapter: GridEngineAdapter) : ToolHandler {

    override suspend fun execute(toolName: String, arguments: JsonObject): ToolResult {
        return when (toolName) {
            "editor_get_state" -> {
                val state = adapter.stateFlow.value
                val data = buildJsonObject {
                    put("title", state.documentTitle)
                    put("sheet_count", state.sheets.size)
                    put("active_sheet", state.activeSheetId)
                    put("is_modified", state.isModified)
                    put("can_undo", state.canUndo)
                    put("can_redo", state.canRedo)
                }
                ToolResult.success(toolName, state.summary(), data)
            }

            "editor_get_selection" -> {
                val state = adapter.stateFlow.value
                val data = buildJsonObject {
                    put("sheet_id", state.selection.sheetId)
                    put("active_cell", state.selection.activeCell)
                    put("selected_range", state.selection.selectedRange)
                }
                ToolResult.success(toolName, "Selected range: ${state.selection.selectedRange}", data)
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
                val isValid = state.sheets.isNotEmpty()
                ToolResult.success(toolName, if (isValid) "Workbook validated successfully." else "Workbook has no sheets.")
            }

            "grid_create_workbook" -> {
                val title = arguments["title"]?.jsonPrimitive?.content ?: "Untitled Workbook"
                val res = adapter.createWorkbook(title)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Created new workbook: \"$title\"")
                } else {
                    ToolResult.error(toolName, "Failed to create workbook: ${res.exceptionOrNull()?.message}")
                }
            }

            "grid_get_sheets" -> {
                val res = adapter.getSheets()
                if (res.isSuccess) {
                    val sheets = res.getOrNull() ?: emptyList()
                    val data = buildJsonObject {
                        put("sheets", JsonArray(sheets.map { JsonPrimitive(it) }))
                    }
                    ToolResult.success(toolName, "Sheets: ${sheets.joinToString()}", data)
                } else {
                    ToolResult.error(toolName, "Failed to get sheets: ${res.exceptionOrNull()?.message}")
                }
            }

            "grid_read_range" -> {
                val sheetId = arguments["sheet_id"]?.jsonPrimitive?.content ?: "Sheet1"
                val range = arguments["range"]?.jsonPrimitive?.content ?: "A1:A1"
                val rangeVal = SafetyValidator.validateRangeReference(range)
                if (rangeVal is com.storytold.craftsuite.core.safety.ValidationResult.Invalid) {
                    return ToolResult.error(toolName, rangeVal.reason)
                }

                val res = adapter.readRange(sheetId, range)
                if (res.isSuccess) {
                    val map = res.getOrNull() ?: emptyMap()
                    val data = buildJsonObject {
                        for ((k, v) in map) {
                            put(k, v)
                        }
                    }
                    ToolResult.success(toolName, "Read ${map.size} cells in range $range", data)
                } else {
                    ToolResult.error(toolName, "Failed to read range: ${res.exceptionOrNull()?.message}")
                }
            }

            "grid_set_cell" -> {
                val sheetId = arguments["sheet_id"]?.jsonPrimitive?.content ?: "Sheet1"
                val cell = arguments["cell"]?.jsonPrimitive?.content ?: "A1"
                val cellVal = SafetyValidator.validateCellReference(cell)
                if (cellVal is com.storytold.craftsuite.core.safety.ValidationResult.Invalid) {
                    return ToolResult.error(toolName, cellVal.reason)
                }

                var value = arguments["value"]?.jsonPrimitive?.content ?: ""
                val typeStr = arguments["value_type"]?.jsonPrimitive?.content ?: "text"
                if (typeStr == "formula") {
                    value = SafetyValidator.sanitizeFormulaInput(value)
                }

                val valueType = when (typeStr.lowercase()) {
                    "number" -> CellValueType.NUMBER
                    "formula" -> CellValueType.FORMULA
                    else -> CellValueType.TEXT
                }

                val res = adapter.setCell(sheetId, cell, value, valueType)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Set cell $cell on sheet '$sheetId' to \"$value\" ($typeStr).")
                } else {
                    ToolResult.error(toolName, "Failed to set cell: ${res.exceptionOrNull()?.message}")
                }
            }

            "grid_apply_range" -> {
                val sheetId = arguments["sheet_id"]?.jsonPrimitive?.content ?: "Sheet1"
                val cellsJsonStr = arguments["cells_json"]?.jsonPrimitive?.content ?: "{}"
                val cellsMap = try {
                    val parsed = Json.parseToJsonElement(cellsJsonStr).jsonObject
                    parsed.mapValues { it.value.jsonPrimitive.content }
                } catch (e: Exception) {
                    return ToolResult.error(toolName, "Invalid cells_json format: ${e.message}")
                }

                val res = adapter.applyRange(sheetId, cellsMap)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Updated ${cellsMap.size} cells on sheet '$sheetId'.")
                } else {
                    ToolResult.error(toolName, "Failed to apply range: ${res.exceptionOrNull()?.message}")
                }
            }

            "grid_create_sheet" -> {
                val title = arguments["title"]?.jsonPrimitive?.content ?: "New Sheet"
                val res = adapter.createSheet(title)
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Created new sheet '$title' (id: ${res.getOrNull()}).")
                } else {
                    ToolResult.error(toolName, "Failed to create sheet: ${res.exceptionOrNull()?.message}")
                }
            }

            "grid_save_workbook" -> {
                val res = adapter.saveToBytes()
                if (res.isSuccess) {
                    ToolResult.success(toolName, "Workbook saved successfully (${res.getOrNull()?.size ?: 0} bytes).")
                } else {
                    ToolResult.error(toolName, "Failed to save workbook: ${res.exceptionOrNull()?.message}")
                }
            }

            else -> ToolResult.error(toolName, "Unsupported GridCraft operation: $toolName")
        }
    }

    fun registerWith(registry: ToolRegistry) {
        registry.registerTool(StandardToolDefinitions.EDITOR_GET_STATE, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_GET_SELECTION, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_UNDO, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_REDO, this)
        registry.registerTool(StandardToolDefinitions.EDITOR_VALIDATE_CHANGES, this)
        registry.registerTool(StandardToolDefinitions.GRID_CREATE_WORKBOOK, this)
        registry.registerTool(StandardToolDefinitions.GRID_GET_SHEETS, this)
        registry.registerTool(StandardToolDefinitions.GRID_READ_RANGE, this)
        registry.registerTool(StandardToolDefinitions.GRID_SET_CELL, this)
        registry.registerTool(StandardToolDefinitions.GRID_APPLY_RANGE, this)
        registry.registerTool(StandardToolDefinitions.GRID_CREATE_SHEET, this)
        registry.registerTool(StandardToolDefinitions.GRID_SAVE_WORKBOOK, this)
    }
}
