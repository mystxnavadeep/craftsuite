package com.storytold.craftsuite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storytold.craftsuite.core.model.CellValueType
import com.storytold.craftsuite.feature.gridcraft.GridEngineAdapterImpl
import kotlinx.coroutines.launch

@Composable
fun GridEditorScreen(
    adapter: GridEngineAdapterImpl,
    onOpen: () -> Unit,
    onSave: () -> Unit
) {
    val state by adapter.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedCellRef by remember { mutableStateOf("A1") }
    var formulaBarText by remember { mutableStateOf("") }

    val activeSheet = state.activeSheet

    // Keep formula bar in sync with selected cell
    LaunchedEffect(selectedCellRef, activeSheet) {
        val cell = activeSheet?.cells?.get(selectedCellRef)
        formulaBarText = cell?.rawValue ?: ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Workbook metadata bar
        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = state.documentTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${state.sheets.size} sheets • ${activeSheet?.cells?.size ?: 0} cells ${if (state.isModified) "• (Modified)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { scope.launch { adapter.undo() } }, enabled = state.canUndo) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { scope.launch { adapter.redo() } }, enabled = state.canRedo) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open")
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            }
        }

        // Formula Bar
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = selectedCellRef,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("fx", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))

                OutlinedTextField(
                    value = formulaBarText,
                    onValueChange = { newVal ->
                        formulaBarText = newVal
                        val sheetId = activeSheet?.id ?: "sheet-1"
                        val type = if (newVal.startsWith("=")) CellValueType.FORMULA else CellValueType.TEXT
                        scope.launch {
                            adapter.setCell(sheetId, selectedCellRef, newVal, type)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    singleLine = true,
                    placeholder = { Text("Enter text, number, or formula (e.g. =SUM(A1,B1))", fontSize = 12.sp) }
                )
            }
        }

        Divider()

        // Spreadsheet Grid (Rows 1..15, Cols A..F)
        val cols = listOf("A", "B", "C", "D", "E", "F")
        val rows = (1..15).toList()
        val horizontalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                // Column Header Row
                item {
                    Row {
                        Box(
                            modifier = Modifier
                                .size(40.dp, 32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.5.dp, Color.Gray)
                        )
                        cols.forEach { colName ->
                            Box(
                                modifier = Modifier
                                    .size(90.dp, 32.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(0.5.dp, Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = colName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Data Rows
                items(rows) { rowNum ->
                    Row {
                        // Row header
                        Box(
                            modifier = Modifier
                                .size(40.dp, 36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.5.dp, Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "$rowNum", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        cols.forEach { colName ->
                            val cellRef = "$colName$rowNum"
                            val cellData = activeSheet?.cells?.get(cellRef)
                            val isSelected = cellRef == selectedCellRef

                            Box(
                                modifier = Modifier
                                    .size(90.dp, 36.dp)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                    )
                                    .clickable { selectedCellRef = cellRef }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cellData?.displayValue ?: "",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontWeight = if (cellData?.isBold == true) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sheet tabs along the bottom
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.sheets) { sheet ->
                        val isSelected = sheet.id == state.activeSheetId
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                // Select sheet
                            },
                            label = { Text(sheet.title) }
                        )
                    }
                }

                IconButton(onClick = {
                    scope.launch {
                        adapter.createSheet("Sheet ${state.sheets.size + 1}")
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Sheet")
                }
            }
        }
    }
}
