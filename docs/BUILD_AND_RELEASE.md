# CraftSuite Build and Release Guide

## 1. Overview

CraftSuite supports four distinct build configurations using Gradle product flavors:

| Target Name | Flavor | Application ID | Output APK Path |
| :--- | :--- | :--- | :--- |
| **WordCraft Android** | `wordcraft` | `com.storytold.craftsuite.wordcraft` | `app/build/outputs/apk/wordcraft/release/app-wordcraft-release-unsigned.apk` |
| **GridCraft Android** | `gridcraft` | `com.storytold.craftsuite.gridcraft` | `app/build/outputs/apk/gridcraft/release/app-gridcraft-release-unsigned.apk` |
| **DeckCraft Android** | `deckcraft` | `com.storytold.craftsuite.deckcraft` | `app/build/outputs/apk/deckcraft/release/app-deckcraft-release-unsigned.apk` |
| **CraftSuite Combined** | `suite` | `com.storytold.craftsuite` | `app/build/outputs/apk/suite/release/app-suite-release-unsigned.apk` |

---

## 2. Cloud CI Build (GitHub Actions)

All production compilation, testing, and APK generation are automated via GitHub Actions in `.github/workflows/android.yml`.

### Workflow Execution
1. Push to `main` branch or create a pull request.
2. Or trigger manually via **Actions > CraftSuite Android CI & APK Build > Run workflow**.
3. The workflow runs unit tests, compiles the native Rust JNI bridge with `cargo-ndk`, packages all 4 APK targets, and uploads them as downloadable GitHub Actions artifacts.

---

## 3. Local Development Build

### Prerequisites
- JDK 17 (Eclipse Temurin or OpenJDK 17)
- Android SDK (API 34, Build-Tools 34.0.0)
- Rust 1.80+ (with `cargo-ndk`)

### Step 1: Compile Native Rust JNI Libraries
```bash
# Add Android targets to Rust
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# Install cargo-ndk
cargo install cargo-ndk

# Build native libraries into Android jniLibs
mkdir -p app/src/main/jniLibs
cd rust/craftsuite-engine
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../../app/src/main/jniLibs build --release
cd ../..
```

### Step 2: Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Step 3: Build Standalone APK Targets
```bash
# Build WordCraft APK
./gradlew assembleWordcraftRelease

# Build GridCraft APK
./gradlew assembleGridcraftRelease

# Build DeckCraft APK
./gradlew assembleDeckcraftRelease

# Build Combined Suite APK
./gradlew assembleSuiteRelease
```

---

## 4. Release Signing Configuration

For official production releases, sign the APKs using Android `apksigner` or configure Gradle signing configs via environment variables:

```bash
apksigner sign --ks release-keystore.jks \
               --ks-key-alias craftsuite \
               --out app-wordcraft-release-signed.apk \
               app/build/outputs/apk/wordcraft/release/app-wordcraft-release-unsigned.apk
```

> [!CAUTION]
> Never commit keystores, signing credentials, or API tokens into source control. All signing materials in CI must be passed securely via GitHub Actions Secrets.
