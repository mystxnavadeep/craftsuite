package com.storytold.craftsuite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    flavorTarget: String,
    onOpenWord: () -> Unit,
    onOpenGrid: () -> Unit,
    onOpenDeck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CraftSuite Native Android",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Production Office Suite powered by Cloud AI & Upstream Rust Engines",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (flavorTarget == "suite" || flavorTarget == "wordcraft") {
            SuiteAppCard(
                title = "WordCraft",
                subtitle = "Rich Document Editor (.docx)",
                icon = Icons.Default.Description,
                description = "Compose structured documents with headings, paragraphs, and AI writing tools.",
                onClick = onOpenWord
            )
        }

        if (flavorTarget == "suite" || flavorTarget == "gridcraft") {
            SuiteAppCard(
                title = "GridCraft",
                subtitle = "Dynamic Spreadsheets (.xlsx)",
                icon = Icons.Default.TableChart,
                description = "Perform calculations, evaluate formulas, and analyze data tables.",
                onClick = onOpenGrid
            )
        }

        if (flavorTarget == "suite" || flavorTarget == "deckcraft") {
            SuiteAppCard(
                title = "DeckCraft",
                subtitle = "Presentations (.pptx)",
                icon = Icons.Default.Slideshow,
                description = "Build presentation slides, text frames, shapes, and notes.",
                onClick = onOpenDeck
            )
        }
    }
}

@Composable
fun SuiteAppCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
