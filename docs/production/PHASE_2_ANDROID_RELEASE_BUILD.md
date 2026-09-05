# Phase 2: Android Client Configuration & Release Build

This guide walks you through configuring the Android native client with your production server endpoint, generating an official release signing keystore, configuring Gradle release signing, and compiling production-ready **Android App Bundles (`.aab`)** and **Release APKs (`.apk`)**.

---

## 📌 Prerequisites

Ensure the following tools are installed on your Windows development machine:
- **Android Studio** (Koala / Ladybug or newer) or Android SDK Command-Line Tools.
- **Java Development Kit (JDK)**: JDK 17 (bundled with Android Studio or OpenJDK 17).
- **Git**: Configured for command line.

---

## ⚙️ Step 1: Update the Production Signaling URL

By default, the client is configured to connect to the Android Emulator loopback (`http://10.0.2.2:3000`). For a production release, update this to your live domain from Phase 1.

Open [`SecurePreferences.kt`](../../android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt) and update line 132:

```kotlin
companion object {
    private const val PREFS_FILE_NAME = "ghost_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_hex"
    private const val KEY_MNEMONIC = "user_mnemonic"
    private const val KEY_USER_CODE = "user_code"
    private const val KEY_REGISTRATION_ID = "registration_id"
    private const val KEY_SIGNALING_URL = "signaling_url"

    // REPLACE WITH YOUR LIVE PRODUCTION URL FROM PHASE 1:
    const val DEFAULT_SIGNALING_URL = "https://your-production-server.up.railway.app"
}
```

> [!NOTE]
> Users can still change or override this endpoint manually at any time inside the app's **Settings → Signaling Broker Endpoint** screen.

---

## 🔑 Step 2: Generate an Android Release Keystore (`.jks`)

Google Play requires that all production apps be digitally signed with a cryptographic private key.

### Generating on Windows PowerShell:

1. Open PowerShell and navigate to your `android/` directory:
   ```powershell
   cd "e:\Github\Ghost Messenger\android"
   ```

2. Run `keytool` (which is included with the JDK):
   ```powershell
   keytool -genkeypair -v `
     -keystore upload-keystore.jks `
     -alias ghost_upload `
     -keyalg RSA `
     -keysize 2048 `
     -validity 10000 `
     -storetype JKS
   ```

3. `keytool` will prompt you for:
   - **Keystore password**: Enter a strong password (e.g., 20+ random characters).
   - **First and last name**: `Ghost Messenger`
   - **Organizational unit**: `Mobile`
   - **Organization**: `GhostMessenger`
   - **City / Locality**: Your city
   - **State / Province**: Your state
   - **Country code**: Your 2-letter country code (e.g., `US`, `GB`, `IN`, `DE`)
   - Type `yes` to confirm the details.

This creates `upload-keystore.jks` in your `android/` folder.

> [!CAUTION]
> **CRITICAL SECURITY WARNING**:
> - Never commit `upload-keystore.jks` to a public GitHub repository.
> - Back up `upload-keystore.jks` and your passwords in a secure password manager (e.g. 1Password, Bitwarden, KeePass).
> - If you lose this keystore file, Google Play will **not** allow you to publish updates to your existing app without undergoing a lengthy developer identity verification process.

---

## 📝 Step 3: Create `key.properties`

Create a file named `key.properties` inside `e:\Github\Ghost Messenger\android\`:

```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEYSTORE_PASSWORD
keyAlias=ghost_upload
storeFile=upload-keystore.jks
```

Verify that `key.properties` and `*.jks` are in your `.gitignore` so secrets are never pushed:
```
# android/.gitignore
*.jks
*.keystore
key.properties
```

---

## 🛠️ Step 4: Configure Release Signing in Gradle

Open [`android/app/build.gradle.kts`](../../android/app/build.gradle.kts) and ensure Gradle loads `key.properties`:

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "org.ghostmessenger"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.ghostmessenger"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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
    // ... rest of config
}
```

---

## 📦 Step 5: Compile Production Artifacts

Open PowerShell in the `android/` directory:

### 1. For Google Play Store: Android App Bundle (`.aab`)
Google Play requires `.aab` bundles for all new app releases:
```powershell
.\gradlew :app:bundleRelease
```
- **Output Path**:
  `android\app\build\outputs\bundle\release\app-release.aab`
- **Purpose**: Uploaded to Google Play Console. Google Play automatically generates device-optimized APK splits for users.

### 2. For Direct Download / Sideloading: Signed Release APK (`.apk`)
For users downloading directly from your GitHub Releases or website:
```powershell
.\gradlew :app:assembleRelease
```
- **Output Path**:
  `android\app\build\outputs\apk\release\app-release.apk`
- **Purpose**: Standalone, signed APK file ready to install directly on any Android 8.0+ (API 26+) device.

---

## 🔍 Step 6: Verify Binary Signing & Shrinking

### 1. Verify Signature Integrity
Use `apksigner` (located in your Android SDK `build-tools/<version>/` directory):
```powershell
apksigner verify --verbose "app\build\outputs\apk\release\app-release.apk"
```
Expected output:
```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

### 2. Verify ProGuard / R8 Obfuscation
Check `android\app\build\outputs\mapping\release\mapping.txt` to verify that unused code has been stripped and symbols are obfuscated while keeping cryptographic primitives intact.

---

## 📱 Step 7: Test the Release APK on a Physical Device

Before submitting to Google Play, always verify the release build on a physical smartphone:

1. Connect your Android phone via USB and enable **USB Debugging**.
2. Install the signed release APK:
   ```powershell
   adb install -r "app\build\outputs\apk\release\app-release.apk"
   ```
3. Test the core flow on the phone:
   - **Identity Creation**: Tap **Create Identity**, verify 12-word mnemonic generation.
   - **UserCode**: Verify that your 8-character UserCode displays.
   - **Signaling Connection**: Verify that the top bar / status indicator connects to your live server.
   - **Send/Receive Message**: Exchange messages with a second phone or emulator to confirm WebRTC DataChannel connection over cellular/Wi-Fi.
   - **Vault Purge**: Navigate to **Settings → Danger Zone → Purge Local Vault & Reset** to verify clean deletion.

---

## 🏁 Phase 2 Completed!

You now have:
- [x] Production signaling URL baked into the application.
- [x] Official release keystore generated and securely stored.
- [x] Validated `app-release.aab` ready for the Google Play Console.
- [x] Validated `app-release.apk` ready for direct distribution.

👉 **Proceed to [Phase 3: Legal, Privacy & Store Policy Compliance](./PHASE_3_LEGAL_AND_COMPLIANCE.md).**
