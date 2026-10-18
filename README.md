# CraftSuite: Native Android Office Suite + Cloud AI Agent

[![Android CI](https://github.com/storytold/craftsuite/actions/workflows/android.yml/badge.svg)](.github/workflows/android.yml)
[![License](https://img.shields.io/badge/License-MIT%20OR%20Apache--2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)

**CraftSuite** is a production-oriented native Android office suite with a built-in Cloud AI Agent, built upon the headless Rust engines of:
- **WordCraft** (`docx` document editor)
- **GridCraft** (`xlsx` spreadsheet calculator)
- **DeckCraft** (`pptx` slide presentation builder)

---

## Architecture Highlights

1. **100% Native Jetpack Compose UI**: Fast, responsive Android interfaces built with Kotlin and Jetpack Compose (no WebViews as primary application UI).
2. **Upstream Headless Rust Engine Reuse**: Integrates upstream engines via JNI bridge (`rust/craftsuite-engine`), linking against headless sessions and tool definitions.
3. **Three Separately Installable APKs + Suite**:
   - `WordCraft Android` (`com.storytold.craftsuite.wordcraft`)
   - `GridCraft Android` (`com.storytold.craftsuite.gridcraft`)
   - `DeckCraft Android` (`com.storytold.craftsuite.deckcraft`)
   - `CraftSuite Combined Suite` (`com.storytold.craftsuite`)
4. **Structured AI Tool Calling**: The cloud AI agent operates exclusively through application-registered tools with strict JSON schemas (`additionalProperties: false`).
5. **Android Storage Access Framework (SAF)**: Full integration with system document providers (`ACTION_OPEN_DOCUMENT` and `ACTION_CREATE_DOCUMENT`), avoiding unnecessary broad storage permissions.
6. **Application Safety Gates**: Destructive operations (whole-document overwrite, bulk range replacement, slide deletion) require explicit user approval via native confirmation dialogs.

---

## Project Structure

```
craftsuite/
├── .github/workflows/
│   └── android.yml            # Cloud CI: builds all 3 APKs + suite, runs tests
├── app/                       # Android app shell, Jetpack Compose UI & product flavors
├── core/
│   ├── model/                 # Shared domain data models (Word, Grid, Deck)
│   ├── fileaccess/            # Storage Access Framework (SAF) & format validation
│   ├── safety/                # Security validators, limits & confirmation handlers
│   ├── enginebridge/          # Kotlin adapter interfaces & native JNI declarations
│   ├── tools/                 # Tool registry, strict JSON schemas & tool dispatcher
│   └── ai/                    # Cloud AI client, prompt provider & 10-step orchestrator
├── feature-wordcraft/         # WordCraft document engine adapter & tools
├── feature-gridcraft/         # GridCraft spreadsheet engine adapter & tools
├── feature-deckcraft/         # DeckCraft presentation engine adapter & tools
├── rust/
│   └── craftsuite-engine/     # Native JNI cdylib crate connecting upstream engines
└── docs/
    ├── ANDROID_PORT_AUDIT.md  # Upstream audit, license findings & port strategy
    ├── AI_AGENT_ARCHITECTURE.md # 10-step AI lifecycle, tool schemas & security
    └── BUILD_AND_RELEASE.md   # Cloud CI, local build instructions & signing
```

---

## Getting Started

### Building in the Cloud (Recommended)
All APK builds and tests are configured in [`.github/workflows/android.yml`](.github/workflows/android.yml). Push to your repository or trigger workflow dispatch to download release APKs from Actions artifacts.

### Building Locally
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build all 3 standalone APK targets + combined suite
./gradlew assembleWordcraftRelease assembleGridcraftRelease assembleDeckcraftRelease assembleSuiteRelease
```

---

## Upstream Attribution & Trademarks

- **WordCraft, GridCraft, DeckCraft**: Copyright (c) 2026 ArtCraft Team and contributors. Dual-licensed under MIT and Apache 2.0.
- The ArtCraft name and logos are trademarks of the ArtCraft Team and are used under fair descriptive reference without claiming trademark ownership.
- Bundled typography uses SIL Open Font License 1.1 fonts (Inter, Source Sans 3, Source Serif 4, JetBrains Mono, Roboto).
