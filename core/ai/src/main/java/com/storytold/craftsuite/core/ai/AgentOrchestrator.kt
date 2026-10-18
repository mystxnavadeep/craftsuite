package com.storytold.craftsuite.core.ai

import com.storytold.craftsuite.core.model.EditorState
import com.storytold.craftsuite.core.safety.SafetyLimits
import com.storytold.craftsuite.core.safety.UserConfirmationHandler
import com.storytold.craftsuite.core.tools.ToolRegistry
import com.storytold.craftsuite.core.tools.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

sealed class AgentExecutionState {
    data object Idle : AgentExecutionState()
    data class Planning(val userGoal: String) : AgentExecutionState()
    data class CallingModel(val iteration: Int) : AgentExecutionState()
    data class ExecutingTool(val toolName: String, val iteration: Int) : AgentExecutionState()
    data class WaitingForUserConfirmation(val actionTitle: String) : AgentExecutionState()
    data class Success(val finalReport: String, val executedTools: List<String>) : AgentExecutionState()
    data class Error(val message: String, val executedTools: List<String>) : AgentExecutionState()
}

data class AgentExecutionReport(
    val isSuccess: Boolean,
    val summary: String,
    val executedToolNames: List<String>,
    val partialFailures: List<String> = emptyList()
)

class AgentOrchestrator(
    private val cloudAiClient: CloudAiClient,
    private val toolRegistry: ToolRegistry,
    private val confirmationHandler: UserConfirmationHandler,
    private val limits: SafetyLimits = SafetyLimits()
) {
    private val _executionState = MutableStateFlow<AgentExecutionState>(AgentExecutionState.Idle)
    val executionState: StateFlow<AgentExecutionState> = _executionState.asStateFlow()

    suspend fun executeTask(
        userPrompt: String,
        currentEditorStateProvider: () -> EditorState,
        aiConfig: AiConfig
    ): AgentExecutionReport {
        if (userPrompt.isBlank()) {
            return AgentExecutionReport(false, "User prompt cannot be empty.", emptyList())
        }

        _executionState.value = AgentExecutionState.Planning(userPrompt)

        val executedTools = mutableListOf<String>()
        val partialFailures = mutableListOf<String>()

        // 1 & 2. Identify active editor and state
        val initialState = currentEditorStateProvider()
        val activeEditor = initialState.editorType

        // 3. Obtain necessary editor state and build system context
        val availableTools = toolRegistry.getAvailableTools(activeEditor)
        val conversation = mutableListOf<AgentMessage>()

        conversation.add(AgentMessage("system", AgentPromptProvider.SYSTEM_PROMPT))
        conversation.add(AgentMessage("system", AgentPromptProvider.buildContextPrompt(initialState)))
        conversation.add(AgentMessage("user", userPrompt))

        var loopCount = 0
        var finalSummary = ""

        // 4. Bounded tool-call loop
        while (loopCount < limits.maxToolCallsPerRequest) {
            if (!coroutineContext.isActive) {
                _executionState.value = AgentExecutionState.Error("Task cancelled by user", executedTools)
                return AgentExecutionReport(false, "Task was cancelled.", executedTools, partialFailures)
            }

            loopCount++
            _executionState.value = AgentExecutionState.CallingModel(loopCount)

            // 5. Request structured tool calls from configured cloud model
            val response = cloudAiClient.sendChat(conversation, availableTools, aiConfig)

            when (response) {
                is AiResponse.Error -> {
                    val errMsg = "Cloud AI Error (${response.code}): ${response.message}"
                    _executionState.value = AgentExecutionState.Error(errMsg, executedTools)
                    return AgentExecutionReport(false, errMsg, executedTools, partialFailures)
                }

                is AiResponse.TextMessage -> {
                    // Final response received from model
                    finalSummary = response.text
                    break
                }

                is AiResponse.ToolCalls -> {
                    // Append assistant message with tool calls
                    conversation.add(
                        AgentMessage(
                            role = "assistant",
                            content = response.thoughtProcess ?: "",
                            toolCalls = response.calls
                        )
                    )

                    // 6, 7 & 8. Validate and execute each tool call
                    for (toolCall in response.calls) {
                        _executionState.value = AgentExecutionState.ExecutingTool(toolCall.name, loopCount)

                        val toolResult = toolRegistry.dispatch(
                            toolName = toolCall.name,
                            arguments = toolCall.arguments,
                            activeEditor = activeEditor,
                            confirmationHandler = confirmationHandler,
                            limits = limits
                        )

                        executedTools.add(toolCall.name)

                        if (!toolResult.isSuccess) {
                            partialFailures.add("${toolCall.name}: ${toolResult.content}")
                        }

                        // Feed tool execution result back into conversation
                        conversation.add(
                            AgentMessage(
                                role = "tool",
                                content = toolResult.content,
                                toolCallId = toolCall.callId
                            )
                        )
                    }

                    // Update system context with refreshed editor state
                    val refreshedState = currentEditorStateProvider()
                    conversation.add(
                        AgentMessage("system", "Current editor state: ${refreshedState.summary()}")
                    )
                }
            }
        }

        // 9. Verify final editor state
        val finalEditorState = currentEditorStateProvider()

        // 10. Formulate final report
        val resultSummary = if (finalSummary.isNotBlank()) {
            finalSummary
        } else {
            "Task executed. Modified state: ${finalEditorState.summary()}. Tools executed: ${executedTools.joinToString()}."
        }

        _executionState.value = AgentExecutionState.Success(resultSummary, executedTools)
        return AgentExecutionReport(
            isSuccess = partialFailures.isEmpty() || executedTools.isNotEmpty(),
            summary = resultSummary,
            executedToolNames = executedTools,
            partialFailures = partialFailures
        )
    }

    fun resetState() {
        _executionState.value = AgentExecutionState.Idle
    }
}
