package com.storytold.craftsuite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storytold.craftsuite.core.ai.*
import com.storytold.craftsuite.core.model.EditorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantBottomSheet(
    orchestrator: AgentOrchestrator,
    aiConfig: AiConfig,
    currentEditorProvider: () -> EditorState,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val executionState by orchestrator.executionState.collectAsState()
    val scope = rememberCoroutineScope()
    var currentJob by remember { mutableStateOf<Job?>(null) }

    var promptInput by remember { mutableStateOf("") }
    val isRunning = executionState !is AgentExecutionState.Idle &&
            executionState !is AgentExecutionState.Success &&
            executionState !is AgentExecutionState.Error

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "CraftSuite AI Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }

            // Warning if API Key is empty
            if (aiConfig.apiKey.isBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "No API key configured",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Add your API key in settings to enable structured tool calling.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onOpenSettings) {
                            Text("Configure")
                        }
                    }
                }
            }

            // Status Indicator Banner
            when (val state = executionState) {
                is AgentExecutionState.Planning -> {
                    StatusBanner("Analyzing editor context and user request...", Icons.Default.Psychology, true)
                }
                is AgentExecutionState.CallingModel -> {
                    StatusBanner("Requesting structured tool calls (step ${state.iteration})...", Icons.Default.CloudSync, true)
                }
                is AgentExecutionState.ExecutingTool -> {
                    StatusBanner("Executing tool: ${state.toolName}...", Icons.Default.Build, true)
                }
                is AgentExecutionState.WaitingForUserConfirmation -> {
                    StatusBanner("Awaiting user confirmation: ${state.actionTitle}", Icons.Default.Security, false)
                }
                is AgentExecutionState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Execution Complete", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(state.finalReport, style = MaterialTheme.typography.bodyMedium)
                            if (state.executedTools.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tools applied: ${state.executedTools.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is AgentExecutionState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Operation Notice", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            Text(state.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else -> {}
            }

            Spacer(Modifier.height(8.dp))

            // Natural language input
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                label = { Text("Describe what to create or edit...") },
                placeholder = { Text("e.g. 'Add a summary paragraph and format the heading in bold'") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning,
                trailingIcon = {
                    if (isRunning) {
                        IconButton(onClick = {
                            currentJob?.cancel()
                            orchestrator.resetState()
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (promptInput.isNotBlank()) {
                                    val prompt = promptInput
                                    promptInput = ""
                                    currentJob = scope.launch {
                                        orchestrator.executeTask(
                                            userPrompt = prompt,
                                            currentEditorStateProvider = currentEditorProvider,
                                            aiConfig = aiConfig
                                        )
                                    }
                                }
                            },
                            enabled = promptInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatusBanner(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, showProgress: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (showProgress) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}
