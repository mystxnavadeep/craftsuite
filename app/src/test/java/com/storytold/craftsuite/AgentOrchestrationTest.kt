package com.storytold.craftsuite

import com.storytold.craftsuite.core.ai.*
import com.storytold.craftsuite.core.safety.AlwaysApproveConfirmationHandler
import com.storytold.craftsuite.core.safety.DeferredConfirmationHandler
import com.storytold.craftsuite.core.tools.ToolRegistry
import com.storytold.craftsuite.feature.wordcraft.WordEngineAdapterImpl
import com.storytold.craftsuite.feature.wordcraft.WordToolHandler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class AgentOrchestrationTest {

    @Test
    fun testAiAgentExecutesStructuredToolCallAndModifiesDocument() = runBlocking {
        val wordAdapter = WordEngineAdapterImpl("Project Report")
        val registry = ToolRegistry()
        WordToolHandler(wordAdapter).registerWith(registry)

        var modelStep = 0
        val mockAiClient = MockCloudAiClient { messages, tools ->
            modelStep++
            if (modelStep == 1) {
                // First turn: model requests tool call word_insert_text
                AiResponse.ToolCalls(
                    calls = listOf(
                        AiToolCall(
                            callId = "call_abc123",
                            name = "word_insert_text",
                            arguments = buildJsonObject {
                                put("text", "Automated section from AI Agent.")
                            }
                        )
                    ),
                    thoughtProcess = "User wants to add content, calling word_insert_text."
                )
            } else {
                // Second turn: model provides final answer after observing tool result
                AiResponse.TextMessage("I have successfully added the requested automated section to your document.")
            }
        }

        val orchestrator = AgentOrchestrator(
            cloudAiClient = mockAiClient,
            toolRegistry = registry,
            confirmationHandler = AlwaysApproveConfirmationHandler()
        )

        val report = orchestrator.executeTask(
            userPrompt = "Please add an automated section to the document.",
            currentEditorStateProvider = { wordAdapter.stateFlow.value },
            aiConfig = AiConfig(apiKey = "mock-key")
        )

        // Verify Orchestrator Report
        assertTrue("Execution should be successful", report.isSuccess)
        assertTrue("word_insert_text must be in executed tools", report.executedToolNames.contains("word_insert_text"))
        assertEquals(
            "I have successfully added the requested automated section to your document.",
            report.summary
        )

        // Verify Document State was physically updated
        val docText = wordAdapter.getContent().getOrThrow()
        assertTrue("Document must contain the AI-inserted text", docText.contains("Automated section from AI Agent."))
        assertTrue("Document must be marked modified", wordAdapter.stateFlow.value.isModified)
    }

    @Test
    fun testUserDenialAbortsDestructiveToolExecution() = runBlocking {
        val wordAdapter = WordEngineAdapterImpl("Important File")
        val registry = ToolRegistry()
        WordToolHandler(wordAdapter).registerWith(registry)

        val confirmationHandler = DeferredConfirmationHandler()
        confirmationHandler.onConfirmationRequested = {
            // User denies confirmation
            confirmationHandler.respond(false)
        }

        var modelStep = 0
        val mockAiClient = MockCloudAiClient { _, _ ->
            modelStep++
            if (modelStep == 1) {
                AiResponse.ToolCalls(
                    calls = listOf(
                        AiToolCall(
                            callId = "call_destructive",
                            name = "word_replace_selection",
                            arguments = buildJsonObject {
                                put("replacement_text", "All new data")
                                put("replace_entire_document", true)
                            }
                        )
                    )
                )
            } else {
                AiResponse.TextMessage("The operation was cancelled by the user.")
            }
        }

        val orchestrator = AgentOrchestrator(
            cloudAiClient = mockAiClient,
            toolRegistry = registry,
            confirmationHandler = confirmationHandler
        )

        val report = orchestrator.executeTask(
            userPrompt = "Replace the entire document with new text.",
            currentEditorStateProvider = { wordAdapter.stateFlow.value },
            aiConfig = AiConfig(apiKey = "mock-key")
        )

        // Document should NOT have been replaced because user cancelled
        assertFalse("Document must not contain replacement text", wordAdapter.getContent().getOrThrow().contains("All new data"))
        assertTrue("Report must record partial failure / cancellation", report.partialFailures.isNotEmpty())
    }
}
