package com.storytold.craftsuite.feature.deckcraft

import com.storytold.craftsuite.core.enginebridge.DeckEngineAdapter
import com.storytold.craftsuite.core.enginebridge.NativeEngineBridge
import com.storytold.craftsuite.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Stack

class DeckEngineAdapterImpl(initialTitle: String = "Untitled Presentation") : DeckEngineAdapter {

    private val undoStack = Stack<PresentationState>()
    private val redoStack = Stack<PresentationState>()

    private val _stateFlow = MutableStateFlow(
        PresentationState(
            documentTitle = initialTitle,
            slides = listOf(
                SlideData(
                    id = "slide-1",
                    index = 0,
                    title = "Introduction",
                    shapes = listOf(
                        SlideShape(
                            id = "shape-1",
                            type = ShapeType.TEXT_BOX,
                            text = "DeckCraft Android",
                            x = 50f,
                            y = 80f,
                            width = 400f,
                            height = 60f,
                            fontSizeSp = 24f
                        )
                    )
                )
            )
        )
    )
    override val stateFlow: StateFlow<PresentationState> = _stateFlow.asStateFlow()

    private fun pushUndoState(current: PresentationState) {
        undoStack.push(current)
        redoStack.clear()
    }

    override suspend fun createPresentation(title: String): Result<Unit> {
        pushUndoState(_stateFlow.value)
        if (NativeEngineBridge.isNativeLoaded) {
            try {
                NativeEngineBridge.nativeResetSession("deck")
            } catch (e: Exception) {
                // native fallback
            }
        }
        _stateFlow.value = PresentationState(
            documentTitle = title,
            isModified = false,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false,
            slides = listOf(SlideData(id = "slide-1", index = 0, title = "Title Slide"))
        )
        return Result.success(Unit)
    }

    override suspend fun getSlides(): Result<List<SlideData>> {
        return Result.success(_stateFlow.value.slides)
    }

    override suspend fun createSlide(title: String, index: Int?): Result<SlideData> {
        val current = _stateFlow.value
        pushUndoState(current)

        val updatedSlides = current.slides.toMutableList()
        val targetIdx = index?.coerceIn(0, updatedSlides.size) ?: updatedSlides.size
        val newSlide = SlideData(
            id = "slide-${System.currentTimeMillis()}",
            index = targetIdx,
            title = title
        )
        updatedSlides.add(targetIdx, newSlide)

        // Re-index slides
        val reindexed = updatedSlides.mapIndexed { idx, s -> s.copy(index = idx) }

        _stateFlow.value = current.copy(
            slides = reindexed,
            isModified = true,
            canUndo = true,
            canRedo = false,
            selection = DeckSelection(currentSlideIndex = targetIdx)
        )
        return Result.success(newSlide)
    }

    override suspend fun updateSlide(slideIndex: Int, title: String?, notes: String?): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        if (slideIndex !in current.slides.indices) {
            return Result.failure(IndexOutOfBoundsException("Slide index $slideIndex out of bounds (total slides: ${current.slides.size})"))
        }

        val updatedSlides = current.slides.toMutableList()
        val existing = updatedSlides[slideIndex]
        updatedSlides[slideIndex] = existing.copy(
            title = title ?: existing.title,
            notes = notes ?: existing.notes
        )

        _stateFlow.value = current.copy(
            slides = updatedSlides,
            isModified = true,
            canUndo = true,
            canRedo = false
        )
        return Result.success(Unit)
    }

    override suspend fun addShape(
        slideIndex: Int,
        type: ShapeType,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Result<SlideShape> {
        val current = _stateFlow.value
        pushUndoState(current)

        if (slideIndex !in current.slides.indices) {
            return Result.failure(IndexOutOfBoundsException("Slide index $slideIndex out of bounds"))
        }

        val shape = SlideShape(
            id = "shape-${System.currentTimeMillis()}",
            type = type,
            text = text,
            x = x,
            y = y,
            width = width,
            height = height
        )

        val updatedSlides = current.slides.toMutableList()
        val existingSlide = updatedSlides[slideIndex]
        updatedSlides[slideIndex] = existingSlide.copy(shapes = existingSlide.shapes + shape)

        _stateFlow.value = current.copy(
            slides = updatedSlides,
            isModified = true,
            canUndo = true,
            canRedo = false
        )
        return Result.success(shape)
    }

    override suspend fun reorderSlides(fromIndex: Int, toIndex: Int): Result<Unit> {
        val current = _stateFlow.value
        pushUndoState(current)

        if (fromIndex !in current.slides.indices || toIndex !in current.slides.indices) {
            return Result.failure(IndexOutOfBoundsException("Indices ($fromIndex, $toIndex) out of bounds"))
        }

        val updated = current.slides.toMutableList()
        val moved = updated.removeAt(fromIndex)
        updated.add(toIndex, moved)
        val reindexed = updated.mapIndexed { idx, s -> s.copy(index = idx) }

        _stateFlow.value = current.copy(
            slides = reindexed,
            isModified = true,
            canUndo = true,
            canRedo = false,
            selection = DeckSelection(currentSlideIndex = toIndex)
        )
        return Result.success(Unit)
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
            val loadedState = Json.decodeFromString<PresentationState>(jsonString)
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
