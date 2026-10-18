package com.storytold.craftsuite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storytold.craftsuite.feature.wordcraft.WordEngineAdapterImpl
import kotlinx.coroutines.launch

@Composable
fun WordEditorScreen(
    adapter: WordEngineAdapterImpl,
    onOpen: () -> Unit,
    onSave: () -> Unit
) {
    val state by adapter.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Document metadata bar
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                        text = "${state.paragraphs.size} paragraphs • ${state.wordCount} words ${if (state.isModified) "• (Modified)" else ""}",
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

        // Formatting controls
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconToggleButton(
                    checked = false,
                    onCheckedChange = { scope.launch { adapter.applyFormat(bold = it) } }
                ) {
                    Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                }
                FilledIconToggleButton(
                    checked = false,
                    onCheckedChange = { scope.launch { adapter.applyFormat(italic = it) } }
                ) {
                    Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                }
                FilledIconToggleButton(
                    checked = false,
                    onCheckedChange = { scope.launch { adapter.applyFormat(underline = it) } }
                ) {
                    Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = { scope.launch { adapter.insertText("\n") } },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    Text("Paragraph", fontSize = 12.sp)
                }
            }
        }

        Divider()

        // Document Paragraphs Canvas
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(state.paragraphs) { idx, p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        var textVal by remember(p.text) { mutableStateOf(p.text) }

                        OutlinedTextField(
                            value = textVal,
                            onValueChange = { newVal ->
                                textVal = newVal
                                scope.launch {
                                    adapter.replaceSelection(newVal)
                                }
                            },
                            label = { Text("Paragraph ${idx + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(
                                fontWeight = if (p.formatting.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (p.formatting.italic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (p.formatting.underline) TextDecoration.Underline else TextDecoration.None
                            )
                        )
                    }
                }
            }
        }
    }
}
