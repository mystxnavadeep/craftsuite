package com.storytold.craftsuite.feature.wordcraft

import com.storytold.craftsuite.core.enginebridge.NativeEngineBridge
import com.storytold.craftsuite.core.enginebridge.WordEngineAdapter
import com.storytold.craftsuite.core.model.DocumentParagraph
import com.storytold.craftsuite.core.model.DocumentSelection
import com.storytold.craftsuite.core.model.TextFormatting
import com.storytold.craftsuite.core.model.WordDocumentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Stack

class WordEngineAdapterImpl(initialTitle: String = "Untitled Document") : WordEngineAdapter {

    private val undoStack = Stack<WordDocumentState>()
    private val redoStack = Stack<WordDocumentState>()

    private val _stateFlow = MutableStateFlow(
        WordDocumentState(
            documentTitle = initialTitle,
            paragraphs = listOf(DocumentParagraph(id = "p-1", text = "Welcome to WordCraft."))
        )
    )
    override val stateFlow: StateFlow<WordDocumentState> = _stateFlow.asStateFlow()

    private fun pushUndoState(current: WordDocumentState) {
        undoStack.push(current)
        redoStack.clear()
    }

    override suspend fun createDocument(title: String): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        if (NativeEngineBridge.isNativeLoaded) {
            try {
                NativeEngineBridge.nativeResetSession("word")
            } catch (e: Exception) {
                // Log and continue with state sync
            }
        }

        _stateFlow.value = WordDocumentState(
            documentTitle = title,
            isModified = false,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false,
            paragraphs = listOf(DocumentParagraph(id = "p-1", text = ""))
        )
        return Result.success(Unit)
    }

    override suspend fun insertText(text: String, paragraphIndex: Int?): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        if (NativeEngineBridge.isNativeLoaded) {
            try {
                val args = "{\"text\":\"${text.replace("\"", "\\\"")}\"}"
                NativeEngineBridge.nativeExecuteTool("word", "word_insert_text", args)
            } catch (e: Exception) {
                // Fallback handles state update
            }
        }

        val updatedParagraphs = current.paragraphs.toMutableList()
        val targetIdx = paragraphIndex?.coerceIn(0, updatedParagraphs.size) ?: updatedParagraphs.size

        if (text.contains("\n")) {
            val lines = text.split("\n")
            val newParagraphs = lines.mapIndexed { idx, line ->
                DocumentParagraph(id = "p-${System.currentTimeMillis()}-$idx", text = line)
            }
            if (targetIdx >= updatedParagraphs.size) {
                updatedParagraphs.addAll(newParagraphs)
            } else {
                updatedParagraphs.addAll(targetIdx, newParagraphs)
            }
        } else {
            if (targetIdx < updatedParagraphs.size) {
                val existing = updatedParagraphs[targetIdx]
                updatedParagraphs[targetIdx] = existing.copy(text = existing.text + text)
            } else {
                updatedParagraphs.add(DocumentParagraph(id = "p-${System.currentTimeMillis()}", text = text))
            }
        }

        _stateFlow.value = current.copy(
            paragraphs = updatedParagraphs,
            isModified = true,
            canUndo = true,
            canRedo = false
        )
        return Result.success(Unit)
    }

    override suspend fun replaceSelection(replacementText: String): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        // For simple replacement, updates the active or last paragraph
        val updated = current.paragraphs.toMutableList()
        if (updated.isEmpty()) {
            updated.add(DocumentParagraph(id = "p-1", text = replacementText))
        } else {
            val idx = current.selection.paragraphIndex.coerceIn(0, updated.lastIndex)
            updated[idx] = updated[idx].copy(text = replacementText)
        }

        _stateFlow.value = current.copy(
            paragraphs = updated,
            isModified = true,
            canUndo = true,
            canRedo = false,
            selection = DocumentSelection(
                paragraphIndex = current.selection.paragraphIndex,
                selectedText = replacementText
            )
        )
        return Result.success(Unit)
    }

    override suspend fun applyFormat(
        bold: Boolean?,
        italic: Boolean?,
        underline: Boolean?,
        fontSizeSp: Float?,
        colorHex: String?
    ): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        val updated = current.paragraphs.toMutableList()
        val idx = current.selection.paragraphIndex.coerceIn(0, (updated.size - 1).coerceAtLeast(0))

        if (updated.isNotEmpty()) {
            val existingFormat = updated[idx].formatting
            val newFormat = existingFormat.copy(
                bold = bold ?: existingFormat.bold,
                italic = italic ?: existingFormat.italic,
                underline = underline ?: existingFormat.underline,
                fontSizeSp = fontSizeSp ?: existingFormat.fontSizeSp,
                colorHex = colorHex ?: existingFormat.colorHex
            )
            updated[idx] = updated[idx].copy(formatting = newFormat)
        }

        _stateFlow.value = current.copy(
            paragraphs = updated,
            isModified = true,
            canUndo = true,
            canRedo = false
        )
        return Result.success(Unit)
    }

    override suspend fun getContent(): Result<String> {
        return Result.success(_stateFlow.value.fullText)
    }

    override suspend fun undo(): Result<Unit> {
        if (undoStack.isEmpty()) return Result.failure(IllegalStateException("No undo actions available"))
        redoStack.push(_stateFlow.value)
        val previous = undoStack.pop()
        _stateFlow.value = previous.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = true
        )
        return Result.success(Unit)
    }

    override suspend fun redo(): Result<Unit> {
        if (redoStack.isEmpty()) return Result.failure(IllegalStateException("No redo actions available"))
        undoStack.push(_stateFlow.value)
        val next = redoStack.pop()
        _stateFlow.value = next.copy(
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
        return Result.success(Unit)
    }

    override suspend fun loadFromBytes(bytes: ByteArray): Result<Unit> {
        return try {
            val jsonString = String(bytes, Charsets.UTF_8)
            val loadedState = Json.decodeFromString<WordDocumentState>(jsonString)
            undoStack.clear()
            redoStack.clear()
            _stateFlow.value = loadedState.copy(isModified = false, canUndo = false, canRedo = false)
            Result.success(Unit)
        } catch (e: Exception) {
            // If not JSON, treat as plain text document
            val plainText = String(bytes, Charsets.UTF_8)
            val paragraphs = plainText.split("\n").mapIndexed { i, p ->
                DocumentParagraph(id = "p-$i", text = p)
            }
            undoStack.clear()
            redoStack.clear()
            _stateFlow.value = WordDocumentState(
                documentTitle = "Imported Document",
                paragraphs = paragraphs,
                isModified = false,
                canUndo = false,
                canRedo = false
            )
            Result.success(Unit)
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
