package com.storytold.craftsuite.core.model

import kotlinx.serialization.Serializable

enum class EditorType(val displayName: String, val extension: String, val mimeType: String) {
    WORD("WordCraft Document", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    GRID("GridCraft Spreadsheet", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DECK("DeckCraft Presentation", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    companion object {
        fun fromExtension(ext: String): EditorType? = entries.firstOrNull { it.extension.equals(ext, ignoreCase = true) }
    }
}

interface EditorState {
    val editorType: EditorType
    val documentTitle: String
    val isModified: Boolean
    val canUndo: Boolean
    val canRedo: Boolean
    fun summary(): String
}

@Serializable
data class TextFormatting(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSizeSp: Float = 16f,
    val colorHex: String = "#000000"
)

@Serializable
data class DocumentParagraph(
    val id: String,
    val text: String,
    val formatting: TextFormatting = TextFormatting(),
    val style: String = "Normal"
)

@Serializable
data class DocumentSelection(
    val paragraphIndex: Int = 0,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val selectedText: String = ""
)

@Serializable
data class WordDocumentState(
    override val documentTitle: String = "Untitled Document",
    override val isModified: Boolean = false,
    override val canUndo: Boolean = false,
    override val canRedo: Boolean = false,
    val paragraphs: List<DocumentParagraph> = listOf(DocumentParagraph(id = "p-1", text = "")),
    val selection: DocumentSelection = DocumentSelection()
) : EditorState {
    override val editorType: EditorType = EditorType.WORD

    val fullText: String
        get() = paragraphs.joinToString("\n") { it.text }

    val wordCount: Int
        get() = fullText.split(Regex("\\s+")).filter { it.isNotBlank() }.size

    override fun summary(): String {
        return "WordCraft Document: \"$documentTitle\", ${paragraphs.size} paragraphs, $wordCount words"
    }
}

@Serializable
enum class CellValueType {
    TEXT, NUMBER, FORMULA, BOOLEAN, EMPTY
}

@Serializable
data class CellData(
    val reference: String,
    val rawValue: String,
    val displayValue: String,
    val valueType: CellValueType = CellValueType.TEXT,
    val formula: String? = null,
    val isBold: Boolean = false,
    val backgroundColorHex: String? = null
)

@Serializable
data class WorksheetData(
    val id: String,
    val title: String,
    val cells: Map<String, CellData> = emptyMap(),
    val maxRows: Int = 100,
    val maxCols: Int = 26
)

@Serializable
data class GridSelection(
    val sheetId: String = "Sheet1",
    val activeCell: String = "A1",
    val selectedRange: String = "A1:A1"
)

@Serializable
data class WorkbookState(
    override val documentTitle: String = "Untitled Workbook",
    override val isModified: Boolean = false,
    override val canUndo: Boolean = false,
    override val canRedo: Boolean = false,
    val sheets: List<WorksheetData> = listOf(WorksheetData(id = "sheet-1", title = "Sheet1")),
    val activeSheetId: String = "sheet-1",
    val selection: GridSelection = GridSelection()
) : EditorState {
    override val editorType: EditorType = EditorType.GRID

    val activeSheet: WorksheetData?
        get() = sheets.firstOrNull { it.id == activeSheetId } ?: sheets.firstOrNull()

    override fun summary(): String {
        val sheetCount = sheets.size
        val cellCount = sheets.sumOf { it.cells.size }
        return "GridCraft Workbook: \"$documentTitle\", $sheetCount sheets, $cellCount populated cells"
    }
}

@Serializable
enum class ShapeType {
    TEXT_BOX, RECTANGLE, ROUNDED_RECT, CIRCLE, LINE, IMAGE
}

@Serializable
data class SlideShape(
    val id: String,
    val type: ShapeType,
    val text: String = "",
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 200f,
    val height: Float = 100f,
    val fillHex: String = "#FFFFFF",
    val strokeHex: String = "#000000",
    val fontSizeSp: Float = 18f
)

@Serializable
data class SlideData(
    val id: String,
    val index: Int,
    val title: String = "Slide ${index + 1}",
    val shapes: List<SlideShape> = emptyList(),
    val notes: String = ""
)

@Serializable
data class DeckSelection(
    val currentSlideIndex: Int = 0,
    val selectedShapeId: String? = null
)

@Serializable
data class PresentationState(
    override val documentTitle: String = "Untitled Presentation",
    override val isModified: Boolean = false,
    override val canUndo: Boolean = false,
    override val canRedo: Boolean = false,
    val slides: List<SlideData> = listOf(
        SlideData(
            id = "slide-1",
            index = 0,
            title = "Title Slide",
            shapes = listOf(
                SlideShape(
                    id = "shape-title",
                    type = ShapeType.TEXT_BOX,
                    text = "Welcome to DeckCraft",
                    x = 80f,
                    y = 120f,
                    width = 480f,
                    height = 80f,
                    fontSizeSp = 28f
                )
            )
        )
    ),
    val selection: DeckSelection = DeckSelection()
) : EditorState {
    override val editorType: EditorType = EditorType.DECK

    val currentSlide: SlideData?
        get() = slides.getOrNull(selection.currentSlideIndex) ?: slides.firstOrNull()

    override fun summary(): String {
        return "DeckCraft Presentation: \"$documentTitle\", ${slides.size} slides"
    }
}
