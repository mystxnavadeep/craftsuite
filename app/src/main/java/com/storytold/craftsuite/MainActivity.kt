package com.storytold.craftsuite

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.storytold.craftsuite.core.ai.*
import com.storytold.craftsuite.core.fileaccess.AndroidSafDocumentManager
import com.storytold.craftsuite.core.fileaccess.FileAccessResult
import com.storytold.craftsuite.core.model.EditorType
import com.storytold.craftsuite.core.safety.DeferredConfirmationHandler
import com.storytold.craftsuite.core.safety.DestructiveAction
import com.storytold.craftsuite.core.tools.ToolRegistry
import com.storytold.craftsuite.feature.deckcraft.DeckEngineAdapterImpl
import com.storytold.craftsuite.feature.deckcraft.DeckToolHandler
import com.storytold.craftsuite.feature.gridcraft.GridEngineAdapterImpl
import com.storytold.craftsuite.feature.gridcraft.GridToolHandler
import com.storytold.craftsuite.feature.wordcraft.WordEngineAdapterImpl
import com.storytold.craftsuite.feature.wordcraft.WordToolHandler
import com.storytold.craftsuite.ui.screens.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val wordAdapter by lazy { WordEngineAdapterImpl() }
    private val gridAdapter by lazy { GridEngineAdapterImpl() }
    private val deckAdapter by lazy { DeckEngineAdapterImpl() }

    private val safManager by lazy { AndroidSafDocumentManager(contentResolver) }
    private val confirmationHandler by lazy { DeferredConfirmationHandler() }
    private val toolRegistry by lazy { ToolRegistry() }

    private val aiClient: CloudAiClient by lazy { StandardCloudAiClient() }
    private val orchestrator by lazy {
        AgentOrchestrator(aiClient, toolRegistry, confirmationHandler)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register tools for all editors
        WordToolHandler(wordAdapter).registerWith(toolRegistry)
        GridToolHandler(gridAdapter).registerWith(toolRegistry)
        DeckToolHandler(deckAdapter).registerWith(toolRegistry)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CraftSuiteApp(
                        flavorTarget = BuildConfig.FLAVOR_TARGET,
                        wordAdapter = wordAdapter,
                        gridAdapter = gridAdapter,
                        deckAdapter = deckAdapter,
                        orchestrator = orchestrator,
                        confirmationHandler = confirmationHandler,
                        onOpenDocument = { type, onLoaded ->
                            openFileViaSaf(type, onLoaded)
                        },
                        onSaveDocument = { type, data, onSaved ->
                            saveFileViaSaf(type, data, onSaved)
                        }
                    )
                }
            }
        }
    }

    private var pendingLoadCallback: ((ByteArray) -> Unit)? = null
    private var pendingSaveData: ByteArray? = null
    private var pendingSaveCallback: (() -> Unit)? = null

    private val openDocLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                when (val res = safManager.readBytes(it)) {
                    is FileAccessResult.Success -> {
                        pendingLoadCallback?.invoke(res.data)
                        Toast.makeText(this@MainActivity, "File loaded successfully", Toast.LENGTH_SHORT).show()
                    }
                    is FileAccessResult.Error -> {
                        Toast.makeText(this@MainActivity, "Error: ${res.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val createDocLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        uri?.let {
            val data = pendingSaveData ?: return@let
            lifecycleScope.launch {
                when (val res = safManager.writeBytes(it, data)) {
                    is FileAccessResult.Success -> {
                        pendingSaveCallback?.invoke()
                        Toast.makeText(this@MainActivity, "Saved successfully", Toast.LENGTH_SHORT).show()
                    }
                    is FileAccessResult.Error -> {
                        Toast.makeText(this@MainActivity, "Save failed: ${res.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openFileViaSaf(type: EditorType, onLoaded: (ByteArray) -> Unit) {
        pendingLoadCallback = onLoaded
        openDocLauncher.launch(arrayOf(type.mimeType, "*/*"))
    }

    private fun saveFileViaSaf(type: EditorType, data: ByteArray, onSaved: () -> Unit) {
        pendingSaveData = data
        pendingSaveCallback = onSaved
        val defaultName = "Document.${type.extension}"
        createDocLauncher.launch(defaultName)
    }
}
