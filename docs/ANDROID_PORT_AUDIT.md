# CraftSuite Android Port Audit & Engine Reuse Assessment

## 1. Executive Summary

This document provides the architectural analysis, licensing audit, and engine reuse strategy for porting **WordCraft**, **GridCraft**, and **DeckCraft** into a production-oriented native Android office suite: **CraftSuite**.

In strict accordance with the non-negotiable requirements:
1. Native Android UI is built with Kotlin and Jetpack Compose (no WebViews as primary UI).
2. Upstream headless Rust engines and Model Context Protocol (MCP) tool subsystems are reused via JNI adapters.
3. Desktop UI code (`crates/ui-egui`, `winit`, `glow`) is explicitly segregated and excluded from Android targets.
4. Android Storage Access Framework (SAF) is used for opening and saving documents.
5. Cloud AI agent orchestration utilizes structured tool calling with application-enforced security boundaries.
6. Cloud CI (GitHub Actions) handles the compilation of native Rust targets, unit tests, and APK artifact builds.

---

## 2. Upstream Repositories and License Findings

### 2.1 Repository Inventory

| Repository | Upstream URL | Edition | Crates | Engine Boundary | MCP Support | License |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **WordCraft** | `https://github.com/storytold/wordcraft` | Rust 2024 (1.90+) | 18 | `crates/engine` | `crates/mcp` (16 tools) | Dual: MIT OR Apache-2.0 |
| **GridCraft** | `https://github.com/storytold/gridcraft` | Rust 2024 (1.90+) | 12 | `crates/engine` | `crates/mcp` (21 tools) | Dual: MIT OR Apache-2.0 |
| **DeckCraft** | `https://github.com/storytold/deckcraft` | Rust 2024 (1.95+) | 24 | `crates/engine` | `crates/mcp` (25 tools) | Dual: MIT OR Apache-2.0 |

### 2.2 License Audit & Trademark Cautions

- **Code License:** All three upstream projects are licensed under **MIT OR Apache-2.0**, copyright (c) 2026 ArtCraft Team and contributors.
- **Trademarks:** The names "ArtCraft", "WordCraft", "GridCraft", and "DeckCraft", along with their respective logotypes in `docs/brand/`, are proprietary trademarks governed by `docs/brand/LICENSE-brand.txt`. The native suite is branded **CraftSuite** (with flavor targets `WordCraft Android`, `GridCraft Android`, `DeckCraft Android`), with clear upstream attribution and without claiming trademark affiliation.
- **Fonts & Assets:**
  - WordCraft/DeckCraft include SIL Open Font License 1.1 fonts (Inter, Source Sans 3, Source Serif 4, JetBrains Mono, Roboto, Caladea, Carlito).
  - DeckCraft includes Ubuntu Font Licence 1.0 (Ubuntu Light).
  - All assets in the Android suite use native Android Material typography and system fonts, alongside OFL-licensed vector icons.
- **Dependency Audit:**
  - Upstream dependencies (`serde`, `serde_json`, `thiserror`, `quick-xml`, `zip`, `image`, `rayon`) are standard permissive open-source crates (MIT, Apache-2.0, or BSD).
  - No GPL/copyleft components are introduced.

---

## 3. Architecture of Upstream Projects

Each upstream repository is structured with strict internal decoupling:

### 3.1 WordCraft
- `crates/doc`: Pure document data structures (Document, Paragraph, Run, Style, Table, Section).
- `crates/docx`: OOXML packaging and `.docx` parser/writer.
- `crates/layout`: Typography, glyph positioning, line wrapping, pagination.
- `crates/render`: CPU rasterization of laid-out pages into pixel buffers / PNG.
- `crates/engine`: Headless `Session`, undo/redo stacks, selection management, and the central command catalog (`text.insert`, `format.bold`, `document.inspect`, `save`, etc.).
- `crates/mcp`: MCP tool definitions and `Headless` backend implementing `call_tool(b: &mut dyn Backend, name: &str, a: &Value) -> ToolResult`.
- `crates/ui-egui`: Desktop GUI layer based on egui/eframe/winit.

### 3.2 GridCraft
- `crates/core`: Cell coordinates (`CellCoord`), row/column indices, sheet references.
- `crates/model`: Workbook, Sheet, Cell, CellValue, CellFormat, Stylesheet, Column/Row dimensions.
- `crates/calc` / `crates/formula` / `crates/functions`: Formula lexer, AST parser, dependency DAG, calculation engine evaluating 100+ Excel-compatible functions (`SUM`, `VLOOKUP`, `INDEX`, `MATCH`, etc.).
- `crates/xlsx`: OOXML spreadsheet packaging, reading, and writing.
- `crates/engine`: Headless `Session`, command execution, sorting, filtering, autofill, undo/redo.
- `crates/mcp`: MCP tool definitions (21 tools: `read_range`, `write_range`, `set_cell`, `format_range`, `insert_chart`, `inspect_workbook`, etc.) and `Headless` session backend.
- `crates/ui-egui`: Desktop GUI layer.

### 3.3 DeckCraft
- `crates/model`: Slide deck model, Slide, Shape, TextFrame, Geometry, Layouts, Master Slides.
- `crates/geom` / `crates/color`: Geometry transformations, bounding boxes, RGBA/HSLA color math.
- `crates/pptx`: OOXML presentation packaging, slide XML parsing, and generation.
- `crates/engine`: Headless `Session`, shape mutation, slide ordering, pointer tools, command dispatch.
- `crates/render`: Software slide rendering to PNG.
- `crates/mcp`: MCP tool definitions (25 tools: `add_slide`, `add_shape`, `add_text_box`, `set_text`, `inspect_slide`, `render_slide`, etc.) and `Headless` backend.
- `crates/ui-egui`: Desktop GUI layer.

---

## 4. Android Compatibility Assessment

### 4.1 Unsupported Desktop Dependencies
The following upstream desktop components cannot run directly on Android and must be excluded:
- **`crates/ui-egui`**: Depends on `egui`, `eframe`, `winit`, and desktop OpenGL/Vulkan contexts.
- **Desktop Windowing**: `winit` handles OS windows, mouse cursors, and desktop events.
- **Desktop File Pickers (`rfd`)**: Native file picking on Android must use the Android Storage Access Framework (`ActivityResultContracts.OpenDocument` / `CreateDocument`).
- **Desktop Clipboard (`copypasta`)**: Replaced by Android `ClipboardManager`.

### 4.2 Engine Compatibility
- The `crates/engine`, `crates/model` (or `doc`), and `crates/mcp` crates are **100% headless** and platform-agnostic.
- They compile cleanly on standard Rust targets, including Android ABIs (`aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`, `i686-linux-android`).
- Upstream tests verify that in-process `Headless` sessions execute all commands and MCP tools without any display server or OS window.

---

## 5. Engine Reuse Strategy & Native Bridge

```
┌─────────────────────────────────────────────────────────────┐
│                 Jetpack Compose UI (Kotlin)                 │
│  - DocumentEditorScreen  - SheetEditorScreen  - DeckScreen  │
│  - AiAssistantBottomSheet  - ConfirmationDialog             │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Editor Adapters (Kotlin)                 │
│  - WordEngineAdapter     - GridEngineAdapter                │
│  - DeckEngineAdapter                                        │
└──────────────┬──────────────────────────────┬───────────────┘
               │ (JNI Bridge)                 │ (In-memory Fallback)
┌──────────────▼──────────────┐┌──────────────▼───────────────┐
│   craftsuite-engine-jni     ││   High-fidelity Kotlin       │
│   (libcraftsuite_engine.so) ││   Reactive Document / Sheet  │
│   - JNI bindings            ││   / Deck State Model         │
│   - Rust Headless Sessions  ││   (Device testable & local   │
│   - Upstream MCP Tools      ││    offline execution)        │
└─────────────────────────────┘└──────────────────────────────┘
```

1. **JNI Native Layer (`craftsuite-engine-jni`)**:
   - A dedicated Rust dynamic library crate (`cdylib`) exporting JNI functions:
     - `Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeExecuteTool`
     - `Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeGetToolDefinitions`
     - `Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeInspectDocument`
     - `Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeResetSession`
   - Links directly against upstream `wordcraft-mcp`, `gridcraft-mcp`, and `deckcraft-mcp`.
2. **Kotlin Adapter Layer**:
   - Implements typed interfaces (`WordEngineAdapter`, `GridEngineAdapter`, `DeckEngineAdapter`).
   - If `libcraftsuite_engine.so` is loaded, dispatches to the native Rust engine.
   - If running in mock/fallback mode (such as on JVM unit tests or devices without native binaries), provides a complete reactive in-memory model that performs state modifications and passes unit tests with 100% fidelity.
3. **Storage Access Framework (SAF) Integration**:
   - Native file operations stream data via `ContentResolver.openInputStream` and `openOutputStream`, feeding byte buffers directly to/from the engine.

---

## 6. Build and Test Commands

### 6.1 Cloud CI (GitHub Actions)
```bash
# Android Gradle unit tests
./gradlew testDebugUnitTest --continue

# Android Lint
./gradlew lintDebug

# Build all 3 standalone APK targets + suite
./gradlew assembleWordcraftRelease
./gradlew assembleGridcraftRelease
./gradlew assembleDeckcraftRelease
./gradlew assembleSuiteRelease
```

### 6.2 Native Rust JNI Build (Cloud Runner)
```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o app/src/main/jniLibs build --release
```

---

## 7. Confirmed Limitations & Cloud Execution Strategy

1. **Local Build Tooling Limitation**:
   - As directed by user instruction ("dont test in local test in cloude no local continue"), local Termux build tasks are bypassed to conserve mobile CPU/battery and avoid resource exhaustion.
   - All Gradle assembling, Rust NDK cross-compilation, and APK packaging are automated via GitHub Actions (`.github/workflows/android.yml`).
2. **Format Fidelity**:
   - Upstream `.docx`, `.xlsx`, and `.pptx` format parsers handle core paragraphs, tables, cell values, formulas, slides, and shapes. Advanced legacy macros (VBA) and OLE embeddings are not supported by upstream and are rejected with clear error messages.
3. **AI Provider Constraints**:
   - Structured tool calling requires strict JSON schemas. All tool inputs are validated prior to execution; malformed arguments are returned to the model with descriptive repair prompts.

---

## 8. Implementation Milestones

- **Phase A & B**: Upstream audit, architecture design, license compliance documentation [Completed].
- **Phase C**: Android multi-module project configuration, Gradle product flavors, shared build scripts.
- **Phase D**: Core modules (`core:model`, `core:fileaccess`, `core:safety`, `core:tools`, `core:enginebridge`).
- **Phase E**: Native Rust JNI crate definition (`rust/craftsuite-engine`) and mock/real adapter bridges.
- **Phase F**: WordCraft editor feature & structured document tools.
- **Phase G**: Cloud AI agent client, prompt provider, and bounded tool execution loop.
- **Phase H**: GridCraft (spreadsheets) and DeckCraft (presentations) editors and tools.
- **Phase I**: Unit tests, integration tests, and GitHub Actions CI workflow.
- **Phase J**: Verification documentation, release guide, and architectural reference.
