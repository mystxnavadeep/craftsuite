package com.storytold.craftsuite.feature.gridcraft

import com.storytold.craftsuite.core.enginebridge.GridEngineAdapter
import com.storytold.craftsuite.core.enginebridge.NativeEngineBridge
import com.storytold.craftsuite.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Stack

class GridEngineAdapterImpl(initialTitle: String = "Untitled Workbook") : GridEngineAdapter {

    private val undoStack = Stack<WorkbookState>()
    private val redoStack = Stack<WorkbookState>()

    private val _stateFlow = MutableStateFlow(
        WorkbookState(
            documentTitle = initialTitle,
            sheets = listOf(
                WorksheetData(
                    id = "sheet-1",
                    title = "Sheet1",
                    cells = mapOf(
                        "A1" to CellData("A1", "Item", "Item", CellValueType.TEXT, isBold = true),
                        "B1" to CellData("B1", "Amount", "Amount", CellValueType.TEXT, isBold = true)
                    )
                )
            ),
            activeSheetId = "sheet-1"
        )
    )
    override val stateFlow: StateFlow<WorkbookState> = _stateFlow.asStateFlow()

    private fun pushUndoState(current: WorkbookState) {
        undoStack.push(current)
        redoStack.clear()
    }

    override suspend fun createWorkbook(title: String): Result<Unit> {
        pushUndoState(_stateFlow.value)
        if (NativeEngineBridge.isNativeLoaded) {
            try {
                NativeEngineBridge.nativeResetSession("grid")
            } catch (e: Exception) {
                // native session fallback
            }
        }
        _stateFlow.value = WorkbookState(
            documentTitle = title,
            isModified = false,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false,
            sheets = listOf(WorksheetData(id = "sheet-1", title = "Sheet1")),
            activeSheetId = "sheet-1"
        )
        return Result.success(Unit)
    }

    override suspend fun getSheets(): Result<List<String>> {
        return Result.success(_stateFlow.value.sheets.map { it.title })
    }

    override suspend fun createSheet(title: String): Result<String> {
        val current = _stateFlow.value
        pushUndoState(current)

        val newId = "sheet-${System.currentTimeMillis()}"
        val newSheet = WorksheetData(id = newId, title = title)
        val updatedSheets = current.sheets + newSheet

        _stateFlow.value = current.copy(
            sheets = updatedSheets,
            activeSheetId = newId,
            isModified = true,
            canUndo = true,
            canRedo = false
        )
        return Result.success(newId)
    }

    override suspend fun setCell(
        sheetId: String,
        cellRef: String,
        value: String,
        valueType: CellValueType
    ): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        val targetCellRef = cellRef.trim().uppercase()
        val sheetIndex = current.sheets.indexOfFirst { it.id == sheetId || it.title.equals(sheetId, ignoreCase = true) }
        if (sheetIndex == -1) {
            return Result.failure(IllegalArgumentException("Sheet '$sheetId' not found"))
        }

        val targetSheet = current.sheets[sheetIndex]
        val updatedCells = targetSheet.cells.toMutableMap()

        // Formula or value evaluation
        val displayVal = if (value.startsWith("=")) {
            evaluateFormula(value, updatedCells)
        } else {
            value
        }

        updatedCells[targetCellRef] = CellData(
            reference = targetCellRef,
            rawValue = value,
            displayValue = displayVal,
            valueType = valueType,
            formula = if (value.startsWith("=")) value else null
        )

        val updatedSheet = targetSheet.copy(cells = updatedCells)
        val updatedSheets = current.sheets.toMutableList()
        updatedSheets[sheetIndex] = updatedSheet

        _stateFlow.value = current.copy(
            sheets = updatedSheets,
            isModified = true,
            canUndo = true,
            canRedo = false
        )
        return Result.success(Unit)
    }

    override suspend fun readRange(sheetId: String, range: String): Result<Map<String, String>> {
        val current = _stateFlow.value
        val sheet = current.sheets.firstOrNull { it.id == sheetId || it.title.equals(sheetId, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("Sheet '$sheetId' not found"))

        val parts = range.uppercase().split(":")
        val startCell = parts.firstOrNull() ?: "A1"
        val endCell = parts.getOrNull(1) ?: startCell

        val startCol = startCell.takeWhile { it.isLetter() }
        val startRow = startCell.dropWhile { it.isLetter() }.toIntOrNull() ?: 1
        val endCol = endCell.takeWhile { it.isLetter() }
        val endRow = endCell.dropWhile { it.isLetter() }.toIntOrNull() ?: startRow

        val result = mutableMapOf<String, String>()
        for (colCh in startCol[0]..endCol[0]) {
            for (r in startRow..endRow) {
                val ref = "$colCh$r"
                val cell = sheet.cells[ref]
                result[ref] = cell?.displayValue ?: ""
            }
        }

        return Result.success(result)
    }

    override suspend fun applyRange(sheetId: String, cells: Map<String, String>): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        val sheetIndex = current.sheets.indexOfFirst { it.id == sheetId || it.title.equals(sheetId, ignoreCase = true) }
        if (sheetIndex == -1) {
            return Result.failure(IllegalArgumentException("Sheet '$sheetId' not found"))
        }

        val targetSheet = current.sheets[sheetIndex]
        val updatedCells = targetSheet.cells.toMutableMap()

        for ((cellRef, value) in cells) {
            val upperRef = cellRef.trim().uppercase()
            val isFormula = value.startsWith("=")
            val display = if (isFormula) evaluateFormula(value, updatedCells) else value
            val type = if (isFormula) CellValueType.FORMULA else if (value.toDoubleOrNull() != null) CellValueType.NUMBER else CellValueType.TEXT

            updatedCells[upperRef] = CellData(
                reference = upperRef,
                rawValue = value,
                displayValue = display,
                valueType = type,
                formula = if (isFormula) value else null
            )
        }

        val updatedSheet = targetSheet.copy(cells = updatedCells)
        val updatedSheets = current.sheets.toMutableList()
        updatedSheets[sheetIndex] = updatedSheet

        _stateFlow.value = current.copy(
            sheets = updatedSheets,
            isModified = true,
            canUndo = true,
            canRedo = false
        )
        return Result.success(Unit)
    }

    private fun evaluateFormula(formula: String, cells: Map<String, CellData>): String {
        val clean = formula.trim().removePrefix("=").trim()
        if (clean.startsWith("SUM(", ignoreCase = true) && clean.endsWith(")")) {
            val inner = clean.substring(4, clean.length - 1).trim()
            val sum = inner.split(",").sumOf { ref ->
                cells[ref.trim().uppercase()]?.displayValue?.toDoubleOrNull() ?: 0.0
            }
            return if (sum % 1.0 == 0.0) sum.toLong().toString() else sum.toString()
        }
        return formula
    }

    override suspend fun undo(): Result<Unit> {
        if (undoStack.isEmpty()) return Result.failure(IllegalStateException("No undo actions available"))
        redoStack.push(_stateFlow.value)
        val prev = undoStack.pop()
        _stateFlow.value = prev.copy(canUndo = undoStack.isNotEmpty(), canRedo = true)
        return Result.success(Unit)
    }

    override suspend fun redo(): Result<Unit> {
        if (redoStack.isEmpty()) return Result.failure(IllegalStateException("No redo actions available"))
        undoStack.push(_stateFlow.value)
        val next = redoStack.pop()
        _stateFlow.value = next.copy(canUndo = true, canRedo = redoStack.isNotEmpty())
        return Result.success(Unit)
    }

    override suspend fun loadFromBytes(bytes: ByteArray): Result<Unit> {
        return try {
            val jsonString = String(bytes, Charsets.UTF_8)
            val loadedState = Json.decodeFromString<WorkbookState>(jsonString)
            undoStack.clear()
            redoStack.clear()
            _stateFlow.value = loadedState.copy(isModified = false, canUndo = false, canRedo = false)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveToBytes(): Result<ByteArray> {
        return try {
            val state = _stateFlow.value
            val serialized = Json.encodeToString(state)
            _stateFlow.value = state.copy(isModified = false)
            Result.success(serialized.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
