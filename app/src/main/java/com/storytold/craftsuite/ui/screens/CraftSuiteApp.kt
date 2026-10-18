package com.storytold.craftsuite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storytold.craftsuite.core.ai.*
import com.storytold.craftsuite.core.model.EditorType
import com.storytold.craftsuite.core.safety.DeferredConfirmationHandler
import com.storytold.craftsuite.core.safety.DestructiveAction
import com.storytold.craftsuite.feature.deckcraft.DeckEngineAdapterImpl
import com.storytold.craftsuite.feature.gridcraft.GridEngineAdapterImpl
import com.storytold.craftsuite.feature.wordcraft.WordEngineAdapterImpl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CraftSuiteApp(
    flavorTarget: String,
    wordAdapter: WordEngineAdapterImpl,
    gridAdapter: GridEngineAdapterImpl,
    deckAdapter: DeckEngineAdapterImpl,
    orchestrator: AgentOrchestrator,
    confirmationHandler: DeferredConfirmationHandler,
    onOpenDocument: (EditorType, (ByteArray) -> Unit) -> Unit,
    onSaveDocument: (EditorType, ByteArray, () -> Unit) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember {
        mutableStateOf(
            when (flavorTarget) {
                "wordcraft" -> "word"
                "gridcraft" -> "grid"
                "deckcraft" -> "deck"
                else -> "home"
            }
        )
    }

    var showAiSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var pendingConfirmationAction by remember { mutableStateOf<DestructiveAction?>(null) }

    // User-configured API settings
    var aiConfig by remember {
        mutableStateOf(
            AiConfig(
                apiKey = "",
                endpointUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                modelName = "gemini-1.5-flash"
            )
        )
    }

    // Set callback on confirmationHandler
    LaunchedEffect(confirmationHandler) {
        confirmationHandler.onConfirmationRequested = { action ->
            pendingConfirmationAction = action
        }
    }

    val activeEditorType = when (currentScreen) {
        "word" -> EditorType.WORD
        "grid" -> EditorType.GRID
        "deck" -> EditorType.DECK
        else -> EditorType.WORD
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentScreen) {
                            "word" -> "WordCraft Document"
                            "grid" -> "GridCraft Spreadsheet"
                            "deck" -> "DeckCraft Presentation"
                            else -> "CraftSuite Native"
                        }
                    )
                },
                navigationIcon = {
                    if (flavorTarget == "suite" && currentScreen != "home") {
                        IconButton(onClick = { currentScreen = "home" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Home")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "AI Settings")
                    }
                    if (currentScreen != "home") {
                        FilledTonalButton(
                            onClick = { showAiSheet = true },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI Agent")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "home" -> {
                    HomeScreen(
                        flavorTarget = flavorTarget,
                        onOpenWord = { currentScreen = "word" },
                        onOpenGrid = { currentScreen = "grid" },
                        onOpenDeck = { currentScreen = "deck" }
                    )
                }
                "word" -> {
                    WordEditorScreen(
                        adapter = wordAdapter,
                        onOpen = { onOpenDocument(EditorType.WORD) { bytes -> coroutineScope.launch { wordAdapter.loadFromBytes(bytes) } } },
                        onSave = {
                            coroutineScope.launch {
                                wordAdapter.saveToBytes().onSuccess { bytes ->
                                    onSaveDocument(EditorType.WORD, bytes) {}
                                }
                            }
                        }
                    )
                }
                "grid" -> {
                    GridEditorScreen(
                        adapter = gridAdapter,
                        onOpen = { onOpenDocument(EditorType.GRID) { bytes -> coroutineScope.launch { gridAdapter.loadFromBytes(bytes) } } },
                        onSave = {
                            coroutineScope.launch {
                                gridAdapter.saveToBytes().onSuccess { bytes ->
                                    onSaveDocument(EditorType.GRID, bytes) {}
                                }
                            }
                        }
                    )
                }
                "deck" -> {
                    DeckEditorScreen(
                        adapter = deckAdapter,
                        onOpen = { onOpenDocument(EditorType.DECK) { bytes -> coroutineScope.launch { deckAdapter.loadFromBytes(bytes) } } },
                        onSave = {
                            coroutineScope.launch {
                                deckAdapter.saveToBytes().onSuccess { bytes ->
                                    onSaveDocument(EditorType.DECK, bytes) {}
                                }
                            }
                        }
                    )
                }
            }

            if (showAiSheet) {
                AiAssistantBottomSheet(
                    orchestrator = orchestrator,
                    aiConfig = aiConfig,
                    currentEditorProvider = {
                        when (currentScreen) {
                            "word" -> wordAdapter.stateFlow.value
                            "grid" -> gridAdapter.stateFlow.value
                            "deck" -> deckAdapter.stateFlow.value
                            else -> wordAdapter.stateFlow.value
                        }
                    },
                    onDismiss = { showAiSheet = false },
                    onOpenSettings = {
                        showAiSheet = false
                        showSettingsDialog = true
                    }
                )
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    currentConfig = aiConfig,
                    onSave = { updated ->
                        aiConfig = updated
                        showSettingsDialog = false
                    },
                    onDismiss = { showSettingsDialog = false }
                )
            }

            pendingConfirmationAction?.let { action ->
                ConfirmationDialog(
                    action = action,
                    onConfirm = {
                        confirmationHandler.respond(true)
                        pendingConfirmationAction = null
                    },
                    onCancel = {
                        confirmationHandler.respond(false)
                        pendingConfirmationAction = null
                    }
                )
            }
        }
    }
}
