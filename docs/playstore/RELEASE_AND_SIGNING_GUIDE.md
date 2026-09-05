# Google Play Store Release and Signing Guide

This guide details the process for generating release signing keys, configuring the native Android Gradle build system, compiling an Android App Bundle (AAB), and publishing **Ghost Messenger** to the Google Play Console.

> [!TIP]
> For the complete, end-to-end production launch roadmap including server deployment, free TURN server setup, and policy compliance, refer to the **[Production Deployment Runbook](../production/README.md)**.

---

## 1. Prerequisites

Ensure the following tools are installed and available in your system path:
- **Android Studio** (Koala / Ladybug or newer)
- **Java Development Kit (JDK)**: JDK 17 (recommended)
- **Android SDK Build-Tools** (API level 34 or 36)

---

## 2. Generate an Android Release Keystore

Run the following command in PowerShell from the `android/` directory:

```powershell
keytool -genkeypair -v `
  -keystore upload-keystore.jks `
  -alias ghost_upload `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -storetype JKS
```

Store the resulting `upload-keystore.jks` file and passwords securely. If you lose this key, you will not be able to publish updates to your existing Google Play listing.

---

## 3. Configure Release Signing in Gradle

### Step A: Create `android/key.properties`
Create a file at `android/key.properties` (do not commit this file to public version control; see `android/key.properties.example`):

```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=ghost_upload
storeFile=upload-keystore.jks
```

### Step B: Verify `android/app/build.gradle.kts`
Ensure that `android/app/build.gradle.kts` is configured to load `key.properties`:

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

## 4. Set Application ID and Versioning

In `android/app/build.gradle.kts`, adjust the version information prior to each release:

```kotlin
defaultConfig {
    applicationId = "org.ghostmessenger"
    minSdk = 26
    targetSdk = 36
    versionCode = 2      // Increment for each Play Store update (1, 2, 3...)
    versionName = "1.0.1"// Version name displayed to users
}
```

---

## 5. Build the Production Binaries

Run Gradle commands from the `android/` directory:

### Google Play Android App Bundle (AAB):
```powershell
.\gradlew :app:bundleRelease
```
- **Output**: `android/app/build/outputs/bundle/release/app-release.aab`

### Standalone Signed Release APK:
```powershell
.\gradlew :app:assembleRelease
```
- **Output**: `android/app/build/outputs/apk/release/app-release.apk`

---

## 6. Google Play Console Submission Steps

For the complete, step-by-step submission walkthrough—including asset upload, answering the Data Safety questionnaire, and managing the mandatory 20-tester closed testing track—follow:

👉 **[Phase 4: Google Play Console Submission & Launch Operations](../production/PHASE_4_PLAY_STORE_SUBMISSION.md)**
