package com.storytold.craftsuite.core.safety

import kotlinx.coroutines.CompletableDeferred

data class SafetyLimits(
    val maxTextInsertLength: Int = 100_000,
    val maxRangeCells: Int = 5_000,
    val maxToolCallsPerRequest: Int = 15,
    val maxPromptLengthChars: Int = 32_000,
    val maxSlidesCount: Int = 500,
    val maxRowsCount: Int = 100_000,
    val maxColsCount: Int = 1_000
)

data class DestructiveAction(
    val title: String,
    val description: String,
    val impactLevel: ImpactLevel = ImpactLevel.MEDIUM,
    val actionType: String
)

enum class ImpactLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

interface UserConfirmationHandler {
    suspend fun confirmAction(action: DestructiveAction): Boolean
}

class AlwaysApproveConfirmationHandler : UserConfirmationHandler {
    override suspend fun confirmAction(action: DestructiveAction): Boolean = true
}

class DeferredConfirmationHandler : UserConfirmationHandler {
    private var pendingDeferred: CompletableDeferred<Boolean>? = null
    var onConfirmationRequested: ((DestructiveAction) -> Unit)? = null

    override suspend fun confirmAction(action: DestructiveAction): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingDeferred = deferred
        onConfirmationRequested?.invoke(action)
        return deferred.await()
    }

    fun respond(approved: Boolean) {
        pendingDeferred?.complete(approved)
        pendingDeferred = null
    }
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
    data class RequiresConfirmation(val action: DestructiveAction) : ValidationResult()
}

object SafetyValidator {
    private val CELL_REF_REGEX = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}$")
    private val RANGE_REF_REGEX = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}:[A-Z]{1,3}[1-9][0-9]{0,6}$")

    fun validateCellReference(cell: String, limits: SafetyLimits = SafetyLimits()): ValidationResult {
        val upper = cell.trim().uppercase()
        if (!CELL_REF_REGEX.matches(upper)) {
            return ValidationResult.Invalid("Invalid cell coordinate: '$cell'. Expected format like 'A1', 'B12'.")
        }

        val colPart = upper.takeWhile { it.isLetter() }
        val rowPart = upper.dropWhile { it.isLetter() }.toIntOrNull() ?: return ValidationResult.Invalid("Invalid row number in $cell")

        var colNum = 0
        for (ch in colPart) {
            colNum = colNum * 26 + (ch - 'A' + 1)
        }

        if (colNum > limits.maxColsCount) {
            return ValidationResult.Invalid("Column index ($colNum) exceeds maximum supported columns (${limits.maxColsCount})")
        }
        if (rowPart > limits.maxRowsCount) {
            return ValidationResult.Invalid("Row index ($rowPart) exceeds maximum supported rows (${limits.maxRowsCount})")
        }

        return ValidationResult.Valid
    }

    fun validateRangeReference(range: String, limits: SafetyLimits = SafetyLimits()): ValidationResult {
        val upper = range.trim().uppercase()
        if (!RANGE_REF_REGEX.matches(upper)) {
            return ValidationResult.Invalid("Invalid range reference: '$range'. Expected format like 'A1:C10'.")
        }
        val parts = upper.split(":")
        val startVal = validateCellReference(parts[0], limits)
        if (startVal !is ValidationResult.Valid) return startVal
        val endVal = validateCellReference(parts[1], limits)
        if (endVal !is ValidationResult.Valid) return endVal

        return ValidationResult.Valid
    }

    fun sanitizeFormulaInput(formula: String): String {
        val trimmed = formula.trim()
        // Prevent DDE injection attacks (=cmd|' /C ...'!, etc.)
        if (trimmed.startsWith("=cmd|", ignoreCase = true) ||
            trimmed.startsWith("@cmd|", ignoreCase = true) ||
            trimmed.startsWith("+cmd|", ignoreCase = true) ||
            trimmed.startsWith("-cmd|", ignoreCase = true)
        ) {
            return "ERROR: DDE / External shell formulas are blocked for security"
        }
        return trimmed
    }

    fun assessToolCallSafety(toolName: String, args: Map<String, Any?>, limits: SafetyLimits = SafetyLimits()): ValidationResult {
        when (toolName) {
            "word_replace_selection" -> {
                val newText = args["replacement_text"] as? String ?: ""
                if (newText.length > limits.maxTextInsertLength) {
                    return ValidationResult.Invalid("Text length (${newText.length}) exceeds safety limit (${limits.maxTextInsertLength})")
                }
                val isReplacingAll = args["replace_entire_document"] as? Boolean ?: false
                if (isReplacingAll) {
                    return ValidationResult.RequiresConfirmation(
                        DestructiveAction(
                            title = "Replace Entire Document",
                            description = "The AI agent is requesting to overwrite the entire document content.",
                            impactLevel = ImpactLevel.HIGH,
                            actionType = toolName
                        )
                    )
                }
            }

            "word_insert_text" -> {
                val text = args["text"] as? String ?: ""
                if (text.length > limits.maxTextInsertLength) {
                    return ValidationResult.Invalid("Text length exceeds safety limit (${limits.maxTextInsertLength})")
                }
            }

            "grid_apply_range" -> {
                val cellCount = (args["values"] as? List<*>)?.size ?: 0
                if (cellCount > limits.maxRangeCells) {
                    return ValidationResult.Invalid("Range modification of $cellCount cells exceeds safety limit (${limits.maxRangeCells})")
                }
                if (cellCount > 200) {
                    return ValidationResult.RequiresConfirmation(
                        DestructiveAction(
                            title = "Bulk Range Modification",
                            description = "The agent is updating $cellCount cells in the active spreadsheet.",
                            impactLevel = ImpactLevel.MEDIUM,
                            actionType = toolName
                        )
                    )
                }
            }

            "grid_create_workbook", "word_create_document", "deck_create_presentation" -> {
                return ValidationResult.RequiresConfirmation(
                    DestructiveAction(
                        title = "Create New Document",
                        description = "Creating a new file will reset the active editor session. Any unsaved changes could be lost.",
                        impactLevel = ImpactLevel.HIGH,
                        actionType = toolName
                    )
                )
            }

            "deck_reorder_slides" -> {
                return ValidationResult.RequiresConfirmation(
                    DestructiveAction(
                        title = "Reorder Slides",
                        description = "The agent is reordering presentation slides.",
                        impactLevel = ImpactLevel.LOW,
                        actionType = toolName
                    )
                )
            }
        }

        return ValidationResult.Valid
    }
}
