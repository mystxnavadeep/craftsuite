package com.storytold.craftsuite

import com.storytold.craftsuite.core.model.CellValueType
import com.storytold.craftsuite.core.model.ShapeType
import com.storytold.craftsuite.feature.deckcraft.DeckEngineAdapterImpl
import com.storytold.craftsuite.feature.gridcraft.GridEngineAdapterImpl
import com.storytold.craftsuite.feature.wordcraft.WordEngineAdapterImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EditorAdaptersTest {

    @Test
    fun testWordEngineAdapterEditingAndUndoRedo() = runBlocking {
        val adapter = WordEngineAdapterImpl("Test Document")
        assertEquals("Test Document", adapter.stateFlow.value.documentTitle)

        // Insert text
        adapter.insertText("First paragraph.")
        adapter.insertText("Second line.\nThird line.")

        val state = adapter.stateFlow.value
        assertTrue("Document must have multiple paragraphs", state.paragraphs.size >= 3)
        assertTrue("State must be marked modified", state.isModified)
        assertTrue("Must be able to undo", state.canUndo)

        // Test Undo
        adapter.undo()
        val afterUndo = adapter.stateFlow.value
        assertTrue("Must be able to redo", afterUndo.canRedo)

        // Test Redo
        adapter.redo()
        val afterRedo = adapter.stateFlow.value
        assertTrue("Redo restored paragraphs", afterRedo.paragraphs.size >= 3)

        // Test Save and Reopen Flow
        val savedBytes = adapter.saveToBytes().getOrThrow()
        assertTrue("Serialized bytes must not be empty", savedBytes.isNotEmpty())

        val reopenedAdapter = WordEngineAdapterImpl()
        reopenedAdapter.loadFromBytes(savedBytes).getOrThrow()
        val reopenedState = reopenedAdapter.stateFlow.value

        assertEquals(state.documentTitle, reopenedState.documentTitle)
        assertEquals(state.paragraphs.size, reopenedState.paragraphs.size)
        assertFalse("Reopened document should not be marked modified", reopenedState.isModified)
    }

    @Test
    fun testGridEngineAdapterCalculationAndRangeOperations() = runBlocking {
        val adapter = GridEngineAdapterImpl("Budget")
        val sheetId = "sheet-1"

        // Set cells
        adapter.setCell(sheetId, "A1", "100", CellValueType.NUMBER)
        adapter.setCell(sheetId, "B1", "250", CellValueType.NUMBER)
        adapter.setCell(sheetId, "C1", "=SUM(A1,B1)", CellValueType.FORMULA)

        val state = adapter.stateFlow.value
        val sheet = state.activeSheet
        assertNotNull(sheet)

        val c1 = sheet!!.cells["C1"]
        assertNotNull("Cell C1 should be populated", c1)
        assertEquals("350", c1!!.displayValue)

        // Read range
        val rangeData = adapter.readRange(sheetId, "A1:C1").getOrThrow()
        assertEquals("100", rangeData["A1"])
        assertEquals("250", rangeData["B1"])
        assertEquals("350", rangeData["C1"])

        // Add sheet
        val newSheetId = adapter.createSheet("Q2 Projections").getOrThrow()
        assertTrue(adapter.stateFlow.value.sheets.any { it.id == newSheetId })

        // Save and reload
        val bytes = adapter.saveToBytes().getOrThrow()
        val reloadAdapter = GridEngineAdapterImpl()
        reloadAdapter.loadFromBytes(bytes).getOrThrow()
        assertEquals(2, reloadAdapter.stateFlow.value.sheets.size)
    }

    @Test
    fun testDeckEngineAdapterSlideManagement() = runBlocking {
        val adapter = DeckEngineAdapterImpl("Keynote")
        assertEquals(1, adapter.stateFlow.value.slides.size)

        // Add slide
        val slide2 = adapter.createSlide("Market Trends").getOrThrow()
        assertEquals(2, adapter.stateFlow.value.slides.size)
        assertEquals("Market Trends", slide2.title)

        // Add shape to slide 2
        val shape = adapter.addShape(
            slideIndex = 1,
            type = ShapeType.TEXT_BOX,
            text = "Revenue grew 40% YoY",
            x = 50f,
            y = 50f,
            width = 300f,
            height = 50f
        ).getOrThrow()

        val currentSlide = adapter.stateFlow.value.slides[1]
        assertTrue("Slide 2 must contain added shape", currentSlide.shapes.any { it.id == shape.id })

        // Reorder slides
        adapter.reorderSlides(0, 1)
        assertEquals("Market Trends", adapter.stateFlow.value.slides[0].title)

        // Save and reload
        val bytes = adapter.saveToBytes().getOrThrow()
        val reloadAdapter = DeckEngineAdapterImpl()
        reloadAdapter.loadFromBytes(bytes).getOrThrow()
        assertEquals(2, reloadAdapter.stateFlow.value.slides.size)
        assertEquals("Market Trends", reloadAdapter.stateFlow.value.slides[0].title)
    }
}
