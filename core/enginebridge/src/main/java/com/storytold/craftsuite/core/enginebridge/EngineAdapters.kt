package com.storytold.craftsuite.core.enginebridge

import com.storytold.craftsuite.core.model.*
import kotlinx.coroutines.flow.StateFlow

object NativeEngineBridge {
    private const val TAG = "NativeEngineBridge"
    private const val LIB_NAME = "craftsuite_engine"

    val isNativeLoaded: Boolean by lazy {
        try {
            System.loadLibrary(LIB_NAME)
            android.util.Log.i(TAG, "Native library '$LIB_NAME' successfully loaded.")
            true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.w(TAG, "Native library '$LIB_NAME' not available in runtime. Using high-fidelity Kotlin engine fallback.")
            false
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to load '$LIB_NAME': ${e.message}")
            false
        }
    }

    external fun nativeExecuteTool(engineType: String, toolName: String, jsonArgs: String): String
    external fun nativeGetToolDefinitions(engineType: String): String
    external fun nativeResetSession(engineType: String): String
    external fun nativeInspectDocument(engineType: String): String
}

interface WordEngineAdapter {
    val stateFlow: StateFlow<WordDocumentState>
    suspend fun createDocument(title: String = "Untitled Document"): Result<Unit>
    suspend fun insertText(text: String, paragraphIndex: Int? = null): Result<Unit>
    suspend fun replaceSelection(replacementText: String): Result<Unit>
    suspend fun applyFormat(
        bold: Boolean? = null,
        italic: Boolean? = null,
        underline: Boolean? = null,
        fontSizeSp: Float? = null,
        colorHex: String? = null
    ): Result<Unit>
    suspend fun getContent(): Result<String>
    suspend fun undo(): Result<Unit>
    suspend fun redo(): Result<Unit>
    suspend fun loadFromBytes(bytes: ByteArray): Result<Unit>
    suspend fun saveToBytes(): Result<ByteArray>
}

interface GridEngineAdapter {
    val stateFlow: StateFlow<WorkbookState>
    suspend fun createWorkbook(title: String = "Untitled Workbook"): Result<Unit>
    suspend fun getSheets(): Result<List<String>>
    suspend fun createSheet(title: String): Result<String>
    suspend fun setCell(sheetId: String, cellRef: String, value: String, valueType: CellValueType): Result<Unit>
    suspend fun readRange(sheetId: String, range: String): Result<Map<String, String>>
    suspend fun applyRange(sheetId: String, cells: Map<String, String>): Result<Unit>
    suspend fun undo(): Result<Unit>
    suspend fun redo(): Result<Unit>
    suspend fun loadFromBytes(bytes: ByteArray): Result<Unit>
    suspend fun saveToBytes(): Result<ByteArray>
}

interface DeckEngineAdapter {
    val stateFlow: StateFlow<PresentationState>
    suspend fun createPresentation(title: String = "Untitled Presentation"): Result<Unit>
    suspend fun getSlides(): Result<List<SlideData>>
    suspend fun createSlide(title: String, index: Int? = null): Result<SlideData>
    suspend fun updateSlide(slideIndex: Int, title: String? = null, notes: String? = null): Result<Unit>
    suspend fun addShape(
        slideIndex: Int,
        type: ShapeType,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Result<SlideShape>
    suspend fun reorderSlides(fromIndex: Int, toIndex: Int): Result<Unit>
    suspend fun undo(): Result<Unit>
    suspend fun redo(): Result<Unit>
    suspend fun loadFromBytes(bytes: ByteArray): Result<Unit>
    suspend fun saveToBytes(): Result<ByteArray>
}
