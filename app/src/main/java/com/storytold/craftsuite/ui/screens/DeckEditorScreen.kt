package com.storytold.craftsuite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storytold.craftsuite.core.model.ShapeType
import com.storytold.craftsuite.feature.deckcraft.DeckEngineAdapterImpl
import kotlinx.coroutines.launch

@Composable
fun DeckEditorScreen(
    adapter: DeckEngineAdapterImpl,
    onOpen: () -> Unit,
    onSave: () -> Unit
) {
    val state by adapter.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var activeSlideIndex by remember { mutableStateOf(0) }
    val currentSlide = state.slides.getOrNull(activeSlideIndex) ?: state.slides.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        // Metadata Bar
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
                        text = "Slide ${activeSlideIndex + 1} of ${state.slides.size} ${if (state.isModified) "• (Modified)" else ""}",
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

        // Shape insertion toolbar
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            adapter.addShape(
                                slideIndex = activeSlideIndex,
                                type = ShapeType.TEXT_BOX,
                                text = "New Text Box",
                                x = 40f,
                                y = 40f,
                                width = 300f,
                                height = 60f
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Text", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            adapter.addShape(
                                slideIndex = activeSlideIndex,
                                type = ShapeType.RECTANGLE,
                                text = "Rectangle",
                                x = 60f,
                                y = 100f,
                                width = 200f,
                                height = 100f
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Shape", fontSize = 12.sp)
                }
            }
        }

        Divider()

        // Presentation Slide Canvas (16:9 Aspect Ratio)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Slide Title
                        Text(
                            text = currentSlide?.title ?: "Untitled Slide",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Render shapes in slide
                        currentSlide?.shapes?.forEach { shape ->
                            Card(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (shape.type == ShapeType.TEXT_BOX) Icons.Default.TextFields else Icons.Default.Category,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = shape.text.ifEmpty { "[${shape.type.name}]" },
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Slide Thumbnails Strip
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.slides) { idx, slide ->
                        val isSelected = idx == activeSlideIndex
                        Card(
                            modifier = Modifier
                                .width(90.dp)
                                .height(60.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable { activeSlideIndex = idx },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${idx + 1}. ${slide.title}",
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = {
                    scope.launch {
                        adapter.createSlide("Slide ${state.slides.size + 1}")
                        activeSlideIndex = state.slides.size
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Slide")
                }
            }
        }
    }
}
