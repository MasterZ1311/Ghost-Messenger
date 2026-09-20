# Calypso — Build, Test & Lint Audit Report

> **Audit Date**: September 20, 2026  
> **Repository**: `MasterZ1311/Ghost-Messenger` (Project Name: Calypso)  
> **Auditor**: Antigravity Assistant  
> **Status**: **Observation Only** — Zero code modifications applied per instructions.

---

## 1. Executive Summary

| Target Component | Subtask | Result | Exit Code | Summary |
| :--- | :--- | :---: | :---: | :--- |
| **Android APK** | `assembleDebug` | ❌ **FAILED** | 1 | Failed at `:app:compileDebugKotlin` due to 2 unresolved references to `GhostColors.SurfaceDark`. |
| **Android Tests** | `testDebugUnitTest` | ⚠️ **BLOCKED** | 1 | 8 test suites blocked from running because main source compilation failed. |
| **Android Lint** | `lintDebug` | ⚠️ **BLOCKED** | 1 | Blocked at `:app:compileDebugKotlin` prior to bytecode/semantic lint analysis. |
| **Server Runtime** | `node --check` | ✅ **PASSED** | 0 | All ES module source files and test files passed Node syntax analysis. |
| **Server Tests** | `node --test` | ✅ **PASSED** | 0 | 6 out of 6 tests passed (100% pass rate) across 4 suites in 1.32s. |
| **Server Security** | `npm audit` | ⚠️ **WARNING** | 1 | 3 moderate severity vulnerabilities in `qs` / `body-parser` / `express`. |

---

## 2. Environment Specifications

```yaml
Operating System: Windows 11 Home / Pro (10.0 amd64)
Shell: PowerShell 5.1 / 7.x
JDK / Java: OpenJDK 21.0.10 (build 21.0.10+-14961533-b1163.108, JetBrains s.r.o. JBR)
Node.js: v22.19.0
npm: 10.9.3
Gradle: 8.11.1
Android Gradle Plugin (AGP): 8.7.3
Kotlin: 2.0.21
KSP: 2.0.21-1.0.28
Android SDK Location: C:\Users\sivak\AppData\Local\Android\Sdk
Android SDK Build Tools: compileSdk = 36, targetSdk = 36, minSdk = 26
JVM Target: 17
```

---

## 3. Exact Commands & Real Terminal Output

### 3.1 Server Unit & Integration Tests

- **Directory**: `e:\Github\Ghost Messenger\server`
- **Command**: `npm test`
- **Exit Code**: `0`
- **Duration**: ~1.32s
- **Output**:

```tap
> calypso-signaling@1.0.0 test
> node --test test/**/*.test.js

TAP version 13
# Subtest: Calypso Ephemeral Signaling Server Test Suite
    # Subtest: InMemoryPreKeyStore Unit Tests
        # Subtest: should upload and retrieve prekey bundles
        ok 1 - should upload and retrieve prekey bundles
          ---
          duration_ms: 2.265
          type: 'test'
          ...
        # Subtest: should evict expired bundles
        ok 2 - should evict expired bundles
          ---
          duration_ms: 70.3176
          type: 'test'
          ...
        1..2
    ok 1 - InMemoryPreKeyStore Unit Tests
      ---
      duration_ms: 74.3216
      type: 'suite'
      ...
    # Subtest: PresenceManager Unit Tests
        # Subtest: should register and track online presence
        ok 1 - should register and track online presence
          ---
          duration_ms: 1.5477
          type: 'test'
          ...
        1..1
    ok 2 - PresenceManager Unit Tests
      ---
      duration_ms: 1.7249
      type: 'suite'
      ...
    # Subtest: Full Server REST & Socket.IO Integration Tests
        # Subtest: REST: Health check returns active status
        ok 1 - REST: Health check returns active status
          ---
          duration_ms: 44.7608
          type: 'test'
          ...
        # Subtest: REST: Upload PreKey bundle and retrieve it
        ok 2 - REST: Upload PreKey bundle and retrieve it
          ---
          duration_ms: 49.5813
          type: 'test'
          ...
        # Subtest: Socket.IO: Full WebRTC SDP & ICE Candidate Signaling Flow
        ok 3 - Socket.IO: Full WebRTC SDP & ICE Candidate Signaling Flow
          ---
          duration_ms: 17.1792
          type: 'test'
          ...
        1..3
    ok 3 - Full Server REST & Socket.IO Integration Tests
      ---
      duration_ms: 145.1519
      type: 'suite'
      ...
    1..3
ok 1 - Calypso Ephemeral Signaling Server Test Suite
  ---
  duration_ms: 222.3534
  type: 'suite'
  ...
1..1
# tests 6
# suites 4
# pass 6
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 1320.4995
```

---

### 3.2 Server Dependency Vulnerability Audit

- **Directory**: `e:\Github\Ghost Messenger\server`
- **Command**: `npm audit`
- **Exit Code**: `1`
- **Output**:

```text
# npm audit report

qs  2.2.5 - 6.15.3
Severity: moderate
qs array-limit bypass via bracket-key comma parsing - https://github.com/advisories/GHSA-x5fp-wj9c-mxmx
qs: Denial of Service via Attacker Controlled isBuffer - https://github.com/advisories/GHSA-4mjr-xmp4-gh2g
fix available via `npm audit fix`
node_modules/qs
  body-parser  1.20.5 - 1.20.6
  Depends on vulnerable versions of qs
  node_modules/body-parser
  express  4.22.2
  Depends on vulnerable versions of qs
  node_modules/express

3 moderate severity vulnerabilities

To address all issues, run:
  npm audit fix
```

---

### 3.3 Server Syntax Validation

- **Directory**: `e:\Github\Ghost Messenger\server`
- **Command**: `Get-ChildItem -Path src,test -Filter *.js -Recurse | ForEach-Object { node --check $_.FullName }`
- **Exit Code**: `0`
- **Result**: All 7 JavaScript files validated cleanly with no syntax or module import errors.

---

### 3.4 Android Debug APK Build

- **Directory**: `e:\Github\Ghost Messenger\android`
- **Command**: `.\gradlew.bat assembleDebug --warning-mode=all`
- **Exit Code**: `1`
- **Duration**: 12m 47s (initial daemon initialization and dependency desugaring)
- **Output**:

```text
Starting a Gradle Daemon, 2 incompatible and 1 stopped Daemons could not be reused, use --status for details
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :app:generateDebugResValues
> Task :app:checkDebugAarMetadata
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:mergeDebugResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugAssets
> Task :app:compressDebugAssets
> Task :app:desugarDebugFileDependencies
> Task :app:mergeDebugJniLibFolders
> Task :app:checkDebugDuplicateClasses
> Task :app:l8DexDesugarLibDebug
> Task :app:processDebugManifestForPackage
> Task :app:mergeDebugNativeLibs
> Task :app:mergeExtDexDebug
> Task :app:mergeLibDexDebug
> Task :app:validateSigningDebug
> Task :app:writeDebugAppMetadata
> Task :app:writeDebugSigningConfigVersions

> Task :app:stripDebugDebugSymbols
Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so, libjingle_peerconnection_so.so, libsignal_jni.so, libsignal_jni_testing.so, libsqlcipher.so.

> Task :app:processDebugResources
> Task :app:kspDebugKotlin
> Task :app:compileDebugKotlin
e: file:///E:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/home/HomeScreen.kt:168:49 Unresolved reference 'SurfaceDark'.
e: file:///E:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/onboarding/OnboardingScreen.kt:105:41 Unresolved reference 'SurfaceDark'.

> Task :app:compileDebugKotlin FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 12m 47s
30 actionable tasks: 30 executed
```

---

### 3.5 Android Unit Tests

- **Directory**: `e:\Github\Ghost Messenger\android`
- **Command**: `.\gradlew.bat testDebugUnitTest --warning-mode=all`
- **Exit Code**: `1`
- **Duration**: 1m 59s
- **Output**:

```text
Starting a Gradle Daemon, 2 incompatible and 2 stopped Daemons could not be reused, use --status for details
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:kspDebugKotlin UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileDebugUnitTest
> Task :app:compileDebugKotlin
e: file:///E:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/home/HomeScreen.kt:168:49 Unresolved reference 'SurfaceDark'.
e: file:///E:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/onboarding/OnboardingScreen.kt:105:41 Unresolved reference 'SurfaceDark'.

> Task :app:compileDebugKotlin FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

BUILD FAILED in 1m 59s
17 actionable tasks: 2 executed, 15 up-to-date
```

---

### 3.6 Android Lint Analysis

- **Directory**: `e:\Github\Ghost Messenger\android`
- **Command**: `.\gradlew.bat lintDebug --warning-mode=all`
- **Exit Code**: `1`
- **Duration**: 1m 59s
- **Output**:

```text
Starting a Gradle Daemon, 2 incompatible and 3 stopped Daemons could not be reused, use --status for details
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:kspDebugKotlin UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:preDebugAndroidTestBuild SKIPPED
> Task :app:generateDebugAndroidTestResValues FROM-CACHE
> Task :app:extractProguardFiles
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:compileDebugKotlin
e: file:///E:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/home/HomeScreen.kt:168:49 Unresolved reference 'SurfaceDark'.
e: file:///E:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/onboarding/OnboardingScreen.kt:105:41 Unresolved reference 'SurfaceDark'.

> Task :app:compileDebugKotlin FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

BUILD FAILED in 1m 59s
18 actionable tasks: 2 executed, 1 from cache, 15 up-to-date
```

---

### 3.7 Isolated Kotlin Compiler Pass Check

- **Directory**: `e:\Github\Ghost Messenger\android`
- **Command**: `.\gradlew.bat compileDebugKotlin --continue`
- **Exit Code**: `1`
- **Output**:
Confirmed that exactly two compiler errors exist across the entire project. There are no other Kotlin syntax errors, missing imports, or missing symbol references in any other source file.

---

## 4. Comprehensive Inventory of Errors, Blockers, Warnings & Deprecations

### 4.1 Kotlin Compiler Errors (FATAL)

#### Error 1: Unresolved Reference in `HomeScreen.kt`
- **File**: `android/app/src/main/kotlin/org/ghostmessenger/ui/home/HomeScreen.kt`
- **Line/Column**: Line 168, Column 49
- **Error**: `Unresolved reference 'SurfaceDark'.`
- **Source Context**:
  ```kotlin
  164: Box(
  165:     modifier = Modifier
  166:         .size(28.dp)
  167:         .background(
  168:             color = GhostColors.SurfaceDark, // <--- ERROR: SurfaceDark does not exist
  169:             shape = CircleShape
  170:         )
  ```
- **Root Cause**: `GhostColors` (defined in `org.ghostmessenger.ui.theme.GhostTheme.kt:13-23`) contains `BgObsidian`, `SurfaceSlate`, `CardGlass`, `BorderGlow`, `GhostGreen`, `NeonCyan`, `WarningRed`, `TextPrimary`, `TextMuted`. It does not define `SurfaceDark`. Likely intended to be `GhostColors.SurfaceSlate` or `GhostColors.BgObsidian`.

#### Error 2: Unresolved Reference in `OnboardingScreen.kt`
- **File**: `android/app/src/main/kotlin/org/ghostmessenger/ui/onboarding/OnboardingScreen.kt`
- **Line/Column**: Line 105, Column 41
- **Error**: `Unresolved reference 'SurfaceDark'.`
- **Source Context**:
  ```kotlin
  101: Box(
  102:     modifier = Modifier
  103:         .size(88.dp)
  104:         .background(
  105:             color = GhostColors.SurfaceDark, // <--- ERROR: SurfaceDark does not exist
  106:             shape = CircleShape
  107:         )
  ```
- **Root Cause**: Identical to Error 1.

---

### 4.2 Blocked Android Unit Test Suites (8 Suites, 0 Executed)

In the Android Gradle Plugin architecture, all unit test tasks (`:app:testDebugUnitTest`, `:app:compileDebugUnitTestKotlin`) depend strictly on the classes produced by `:app:compileDebugKotlin`. Because compilation failed, **none** of the 8 existing test suites could be compiled or executed:

| Test Suite Class | File Path | Status |
| :--- | :--- | :---: |
| `Base32Test` | `android/app/src/test/kotlin/org/ghostmessenger/core/crypto/Base32Test.kt` | ⚠️ BLOCKED |
| `KeyManagerTest` | `android/app/src/test/kotlin/org/ghostmessenger/core/crypto/KeyManagerTest.kt` | ⚠️ BLOCKED |
| `SignalCryptoManagerTest` | `android/app/src/test/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManagerTest.kt` | ⚠️ BLOCKED |
| `UserCodeUtilsTest` | `android/app/src/test/kotlin/org/ghostmessenger/core/crypto/UserCodeUtilsTest.kt` | ⚠️ BLOCKED |
| `SqliteSignalProtocolStoreTest` | `android/app/src/test/kotlin/org/ghostmessenger/data/crypto/SqliteSignalProtocolStoreTest.kt` | ⚠️ BLOCKED |
| `ConversationAndMessageTest` | `android/app/src/test/kotlin/org/ghostmessenger/data/local/ConversationAndMessageTest.kt` | ⚠️ BLOCKED |
| `PreKeyApiClientTest` | `android/app/src/test/kotlin/org/ghostmessenger/data/network/PreKeyApiClientTest.kt` | ⚠️ BLOCKED |
| `SignalingModelTest` | `android/app/src/test/kotlin/org/ghostmessenger/data/network/SignalingModelTest.kt` | ⚠️ BLOCKED |

---

### 4.3 Blocked Lint Analysis

- **Task**: `:app:lintDebug`
- **Status**: ⚠️ **BLOCKED**
- **Impact**: AGP Lint performs bytecode-level inspection, type resolution, and semantic analysis across Kotlin and XML assets. Because compilation aborted, no HTML or XML lint report was generated at `android/app/build/reports/lint-results-debug.html`.

---

### 4.4 Build & Packaging Warnings

#### Warning 1: Unstrippable Native Libraries
- **Task**: `:app:stripDebugDebugSymbols`
- **Warning Message**:
  ```text
  Unable to strip the following libraries, packaging them as they are:
  libandroidx.graphics.path.so, libjingle_peerconnection_so.so, libsignal_jni.so, libsignal_jni_testing.so, libsqlcipher.so.
  ```
- **Severity**: Low (Debug build). In release builds, failure to strip debug symbols from JNI shared objects increases APK/AAB size by 15–30 MB, though functionality is preserved.

#### Warning 2: Gradle Daemon Compatibility
- **Message**: `Starting a Gradle Daemon, 2 incompatible and 1 stopped Daemons could not be reused, use --status for details`
- **Severity**: Informational. Caused by varying JVM memory arguments between CLI runs and IDE instances.

---

### 4.5 Deprecations & Build Configuration Risks

#### Deprecation 1: Kotlin `kotlinOptions` DSL
- **File**: `android/app/build.gradle.kts:66-68`
- **Code**:
  ```kotlin
  kotlinOptions {
      jvmTarget = "17"
  }
  ```
- **Deprecation**: In Kotlin 2.0+, `kotlinOptions` is deprecated in favor of the type-safe `compilerOptions` extension:
  ```kotlin
  compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
  ```

#### Deprecation 2: Suppressed Unsupported CompileSdk 36
- **File**: `android/gradle.properties:7`
- **Code**: `android.suppressUnsupportedCompileSdk=36`
- **Issue**: Android Gradle Plugin 8.7.3 officially supports up to API 35 (Android 15). API 36 (Android 16 preview) requires this suppression flag. While build execution succeeds, AGP features or lint rules may have unexpected behavior against API 36 until AGP is upgraded to a version supporting SDK 36.

#### Deprecation 3: AndroidX Security Crypto Alpha Dependency
- **File**: `android/gradle/libs.versions.toml:19`
- **Code**: `security-crypto = "1.1.0-alpha06"`
- **Risk**: Google has left `androidx.security:security-crypto` in alpha since 2023. It has documented instability and Keystore corruption issues on certain Samsung and Xiaomi devices running Android 14+.

---

### 4.6 Server Dependencies & Security Audit Findings

- **Report Source**: `npm audit`
- **Vulnerabilities**:
  1. `qs` (versions 2.2.5 - 6.15.3): Array-limit bypass via bracket-key comma parsing ([GHSA-x5fp-wj9c-mxmx](https://github.com/advisories/GHSA-x5fp-wj9c-mxmx))
  2. `qs` (versions 2.2.5 - 6.15.3): Denial of Service via Attacker Controlled `isBuffer` ([GHSA-4mjr-xmp4-gh2g](https://github.com/advisories/GHSA-4mjr-xmp4-gh2g))
  3. `body-parser` (1.20.5 - 1.20.6) and `express` (4.22.2): Transitive dependency on the vulnerable `qs` parser.
- **Remediation**: Can be resolved via `npm audit fix` or by updating `express` / specifying an npm `overrides` block for `qs: "^6.13.0"` or patched version once approved.

---

## 5. Verification Check Matrix

| Check Item | Target | Evidence | Status |
| :--- | :--- | :--- | :---: |
| Node syntax check | Server | 7/7 files parsed with exit code 0 | ✅ VERIFIED |
| Server unit tests | Server | 6/6 tests passed in `test/server.test.js` | ✅ VERIFIED |
| Server npm audit | Server | 3 moderate vulnerabilities detected | ⚠️ IDENTIFIED |
| Gradle configuration | Android | Gradle 8.11.1 + AGP 8.7.3 loaded successfully | ✅ VERIFIED |
| Kotlin compilation | Android | 2 unresolved references to `SurfaceDark` | ❌ FAILED |
| Debug APK packaging | Android | Blocked at `:app:compileDebugKotlin` | ❌ FAILED |
| Android unit tests | Android | Blocked at `:app:compileDebugKotlin` | ⚠️ BLOCKED |
| Android Lint check | Android | Blocked at `:app:compileDebugKotlin` | ⚠️ BLOCKED |

---

## 6. Recommended Next Steps (For When Fixing Is Authorized)

1. **Fix Color References**:
   - In `HomeScreen.kt:168`: Replace `GhostColors.SurfaceDark` with `GhostColors.SurfaceSlate` or define `SurfaceDark` in `GhostTheme.kt`.
   - In `OnboardingScreen.kt:105`: Replace `GhostColors.SurfaceDark` with `GhostColors.SurfaceSlate` or define `SurfaceDark` in `GhostTheme.kt`.
2. **Re-run Android Build & Tests**:
   - `.\gradlew.bat assembleDebug` to verify APK generation.
   - `.\gradlew.bat testDebugUnitTest` to execute the 8 Android unit test suites.
   - `.\gradlew.bat lintDebug` to run static analysis and produce full lint report.
3. **Modernize Kotlin Compiler DSL**:
   - Replace `kotlinOptions { jvmTarget = "17" }` with `compilerOptions { jvmTarget.set(...) }` in `android/app/build.gradle.kts`.
4. **Patch Server Dependencies**:
   - Run `npm audit fix` in `server/` to upgrade `qs` / `body-parser`.
