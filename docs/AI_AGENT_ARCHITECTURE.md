# CraftSuite Cloud AI Agent Architecture

## 1. Overview

CraftSuite integrates an in-app Cloud AI Agent that enables users to create and edit documents, spreadsheets, and presentations using natural language. 

The AI agent does **not** rely on arbitrary screen tapping or untrusted shell execution. Instead, all modifications are executed through **structured application tools** registered with the application and mediated by typed engine adapters.

```
┌────────────────────────────────────────────────────────┐
│                   User Natural Prompt                  │
│    "Add a Q3 financial summary and bold the total"     │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                    AgentOrchestrator                   │
│  - Captures active editor state & selection            │
│  - Builds system prompt & tool schema catalog          │
│  - Manages bounded reasoning loop (max 15 iterations)  │
└───────────────────────────┬────────────────────────────┘
                            │ (HTTPS REST API / JSON)
┌───────────────────────────▼────────────────────────────┐
│               Cloud Model (Gemini / OpenAI)            │
│  - Evaluates context & chooses structured tool calls   │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                    ToolDispatcher                      │
│  1. Check editor authorization (Word vs Grid vs Deck)  │
│  2. Strict Schema Validation (additionalProperties=false)
│  3. Safety & confirmation assessment                   │
└───────────────────────────┬────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
   [Requires Confirmation]        [Safe Operation]
              │                           │
┌─────────────▼─────────────┐             │
│    Native Confirm Modal   │             │
│   (User approves/denies)  │             │
└─────────────┬─────────────┘             │
              └─────────────┬─────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│         Engine Adapters (Word / Grid / Deck)           │
│  - JNI Native Bridge to Rust Sessions                  │
│  - Reactive Kotlin StateFlow (UI updates immediately)  │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                 Verification & Feedback                │
│  - Inspect updated document state                      │
│  - Feed tool result back to model                      │
│  - Present final completion summary to user            │
└────────────────────────────────────────────────────────┘
```

---

## 2. The 10-Step Agent Lifecycle

The `AgentOrchestrator` implements the following lifecycle for every user task:

1. **Receive Request**: The user enters a natural-language prompt in the AI Assistant panel.
2. **Identify Active Editor**: Determines whether WordCraft, GridCraft, or DeckCraft is currently active.
3. **Capture State Snapshot**: Obtains document title, paragraph count, cell count, or slide count, as well as current text selection or active cell.
4. **Context Injection**: Formulates the prompt with the non-negotiable System Prompt (defining role boundaries and security constraints) and the current state snapshot.
5. **Request Tool Calls**: Dispatches the conversation and available tool schemas to the configured cloud model.
6. **Validate Tool Calls**: 
   - Checks if the requested tool belongs to the active editor.
   - Validates arguments against strict JSON Schema parameters.
   - Verifies cell coordinates (e.g. `A1`, `B12`) against spreadsheet boundary limits.
7. **Security & Confirmation Check**: 
   - Destructive operations (replacing whole documents, bulk modifying >200 cells, deleting slides, overwriting existing files) pause execution and trigger a native Material 3 confirmation dialog.
   - Execution resumes only if the user explicitly approves.
8. **Apply Operation**: Invokes the corresponding adapter method (`insertText`, `setCell`, `addSlide`, etc.), which updates the native Rust session and emits new state on Kotlin's `StateFlow`.
9. **Return Tool Results**: Sends the execution result (`ToolResult.success` or `ToolResult.error`) back to the cloud model for iterative reasoning.
10. **State Verification & Report**: Validates the final editor state and reports a human-readable summary of applied changes and any partial failures.

---

## 3. Strict Tool Schemas

Every tool definition strictly enforces:
- `additionalProperties: false` (rejecting any unrecognized arguments injected by hallucination or prompt injection).
- Typed properties (`string`, `integer`, `boolean`, `enum`).
- Bounded input lengths (`maxLength`, `minimum`, `maximum`).

### Example: `grid_set_cell` Schema
```json
{
  "name": "grid_set_cell",
  "description": "Set a value or formula in a validated worksheet cell.",
  "parameters": {
    "type": "object",
    "properties": {
      "sheet_id": { "type": "string", "maxLength": 128 },
      "cell": { "type": "string", "maxLength": 16 },
      "value": { "type": "string", "maxLength": 4000 },
      "value_type": { "type": "string", "enum": ["text", "number", "formula"] }
    },
    "required": ["sheet_id", "cell", "value", "value_type"],
    "additionalProperties": false
  }
}
```

---

## 4. Security & Privacy Safeguards

1. **No Embedded Production Secrets**:
   - The codebase and generated APKs contain zero embedded API secrets.
   - Users provide their own API key (BYOK) stored locally in app-private storage.
2. **Formula Injection Sanitization**:
   - Dynamic Data Exchange (DDE) formulas starting with `=cmd|`, `@cmd|`, `+cmd|`, `-cmd|` are rejected immediately.
3. **Prompt Injection Isolation**:
   - Untrusted text within opened documents is treated purely as content data, never as system instructions.
4. **Data Transmission Disclosure**:
   - The application does not send entire document contents to the cloud model unless required for the specific user instruction.
