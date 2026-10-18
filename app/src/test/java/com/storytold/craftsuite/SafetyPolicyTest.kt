package com.storytold.craftsuite

import com.storytold.craftsuite.core.safety.SafetyLimits
import com.storytold.craftsuite.core.safety.SafetyValidator
import com.storytold.craftsuite.core.safety.ValidationResult
import org.junit.Assert.*
import org.junit.Test

class SafetyPolicyTest {

    @Test
    fun testValidCellReferences() {
        val validCells = listOf("A1", "B10", "Z99", "AA100", "XFD1048576")
        for (ref in validCells) {
            val res = SafetyValidator.validateCellReference(ref)
            assertTrue("Expected cell '$ref' to be valid", res is ValidationResult.Valid)
        }
    }

    @Test
    fun testInvalidCellReferences() {
        val invalidCells = listOf("1A", "A", "1", "A0", "!A1", "A-1", "", "A 1")
        for (ref in invalidCells) {
            val res = SafetyValidator.validateCellReference(ref)
            assertTrue("Expected cell '$ref' to be invalid", res is ValidationResult.Invalid)
        }
    }

    @Test
    fun testCellReferenceLimits() {
        val limits = SafetyLimits(maxRowsCount = 100, maxColsCount = 10) // col 10 is 'J'
        val withinRes = SafetyValidator.validateCellReference("J50", limits)
        assertTrue("J50 should be within limit", withinRes is ValidationResult.Valid)

        val outOfCol = SafetyValidator.validateCellReference("K50", limits)
        assertTrue("K50 should exceed column limit", outOfCol is ValidationResult.Invalid)

        val outOfRow = SafetyValidator.validateCellReference("A101", limits)
        assertTrue("A101 should exceed row limit", outOfRow is ValidationResult.Invalid)
    }

    @Test
    fun testRangeValidation() {
        assertTrue(SafetyValidator.validateRangeReference("A1:C10") is ValidationResult.Valid)
        assertTrue(SafetyValidator.validateRangeReference("A1-C10") is ValidationResult.Invalid)
        assertTrue(SafetyValidator.validateRangeReference("INVALID") is ValidationResult.Invalid)
    }

    @Test
    fun testFormulaSanitizationAgainstCommandInjection() {
        val safeFormula = "=SUM(A1,B1)"
        assertEquals(safeFormula, SafetyValidator.sanitizeFormulaInput(safeFormula))

        val maliciousDde = "=cmd|' /C calc.exe'!A0"
        val sanitized = SafetyValidator.sanitizeFormulaInput(maliciousDde)
        assertTrue("Malicious DDE formula must be blocked", sanitized.startsWith("ERROR:"))
    }

    @Test
    fun testDestructiveActionConfirmationRequired() {
        val wholeDocArgs = mapOf(
            "replacement_text" to "Entire new content",
            "replace_entire_document" to true
        )
        val res = SafetyValidator.assessToolCallSafety("word_replace_selection", wholeDocArgs)
        assertTrue("Replacing entire document must require user confirmation", res is ValidationResult.RequiresConfirmation)

        val partialArgs = mapOf(
            "replacement_text" to "Some phrase",
            "replace_entire_document" to false
        )
        val resPartial = SafetyValidator.assessToolCallSafety("word_replace_selection", partialArgs)
        assertTrue("Partial replacement should be valid without confirmation", resPartial is ValidationResult.Valid)
    }
}
