# CraftSuite Build, Signing, and Release Guide

## 1. Overview

CraftSuite supports four distinct build configurations using Gradle product flavors:

| Target Name | Flavor | Application ID | Output APK Path |
| :--- | :--- | :--- | :--- |
| **WordCraft Android** | `wordcraft` | `com.storytold.craftsuite.wordcraft` | `app/build/outputs/apk/wordcraft/release/app-wordcraft-release.apk` |
| **GridCraft Android** | `gridcraft` | `com.storytold.craftsuite.gridcraft` | `app/build/outputs/apk/gridcraft/release/app-gridcraft-release.apk` |
| **DeckCraft Android** | `deckcraft` | `com.storytold.craftsuite.deckcraft` | `app/build/outputs/apk/deckcraft/release/app-deckcraft-release.apk` |
| **CraftSuite Combined** | `suite` | `com.storytold.craftsuite` | `app/build/outputs/apk/suite/release/app-suite-release.apk` |

---

## 2. APK Signing Architecture & Installation Failure Root Cause

### Root Cause of "App not installed as package appears to be invalid"
In Android 7.0+ through Android 14/15, the Android Package Manager (`PackageInstaller`) requires all installed APK packages to be signed with valid certificate signatures (specifically APK Signature Scheme v2/v3).
If an APK is generated without an explicit `signingConfig` attached to `buildTypes.release`, the Android Gradle Plugin outputs an unsigned archive (`*-release-unsigned.apk`). When a user attempts to install an unsigned APK on device, Android aborts with `INSTALL_PARSE_FAILED_NO_CERTIFICATES` and surfaces the user-facing error:
> *"App not installed as package appears to be invalid"*

### Solution in CraftSuite v0.8.1+
1. **Gradle `signingConfigs`**:
   The `app/build.gradle.kts` defines a `release` signing configuration that reads from `$KEYSTORE_FILE` if configured, or automatically inherits the debug signing configuration (`initWith(getByName("debug"))`).
2. **Dual-Mode Cloud Signing in GitHub Actions**:
   - **Production Mode**: If repository administrators configure GitHub Actions secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), CI automatically decodes the keystore into `$RUNNER_TEMP` (ephemeral runner storage) and signs the APKs with the official production certificate.
   - **Test-Signing Mode**: If secrets are absent, CI automatically generates a secure, compliant RSA-2048 test keystore on the runner, zip-aligns the APKs, and signs them with full **v1, v2, v3, and v4 signing schemes**.
3. **Rigorous CI Validation**:
   CI runs Android SDK `aapt dump badging` to verify manifest parsing, package IDs, minSdk, and targetSdk, followed by `apksigner verify --verbose --print-certs` to assert signature integrity before any release asset is published.

---

## 3. GitHub Actions Secrets Setup (for Production Releases)

To enable production-signed APKs in GitHub Actions:
1. Generate your production keystore locally:
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias craftsuite -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Base64-encode the keystore file:
   ```bash
   base64 -w 0 release.jks > release.jks.base64
   ```
3. In GitHub, navigate to **Settings > Secrets and variables > Actions** and add:
   - `RELEASE_KEYSTORE_BASE64`: The full base64 string from `release.jks.base64`.
   - `RELEASE_KEYSTORE_PASSWORD`: The keystore password.
   - `RELEASE_KEY_ALIAS`: The key alias (e.g. `craftsuite`).
   - `RELEASE_KEY_PASSWORD`: The private key password.

> [!CAUTION]
> Never commit `.jks` or `.keystore` files or passwords to the Git repository.

---

## 4. Device Installation Instructions

For users installing APKs directly onto Android devices:
1. Download the desired APK (e.g. `app-suite-release.apk` or `CraftSuite-suite-v0.8.1-test-signed.apk`).
2. If installing via Chrome or a file manager, ensure **"Install unknown apps"** is toggled ON for that application:
   - Go to **Settings > Apps > Special app access > Install unknown apps**.
   - Select your browser or file manager and toggle **"Allow from this source"**.
3. Tap the downloaded APK to install.
4. If Google Play Protect shows a dialog (*"Unrecognized app / Unsafe app blocked"*):
   - Tap **"More details"**.
   - Tap **"Install anyway"**.
5. Once installation finishes, open **CraftSuite** from your home screen or app drawer.

---

## 5. Local Build & Signing Commands

```bash
# 1. Compile native Rust JNI libraries
cd rust/craftsuite-engine
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../../app/src/main/jniLibs build --release
cd ../..

# 2. Assemble release APKs
./gradlew assembleWordcraftRelease \
          assembleGridcraftRelease \
          assembleDeckcraftRelease \
          assembleSuiteRelease

# 3. Verify signature of generated APK
apksigner verify --verbose --print-certs app/build/outputs/apk/suite/release/app-suite-release.apk
```
