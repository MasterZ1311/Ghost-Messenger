# Google Play Store Release and Signing Guide

This technical guide details the end-to-end process for generating release signing keys, configuring the Android build system, compiling an Android App Bundle (AAB), and publishing **Ghost Messenger** to the Google Play Console.

---

## 1. Prerequisites

Ensure the following tools are installed and available in your system path:
- **Flutter SDK** (v3.0.0 or higher)
- **Java Development Kit (JDK)** (Version 17 or higher)
- **Android SDK Build-Tools** (API level 34 recommended)

---

## 2. Generate an Android Release Keystore

Execute the following command in a secure terminal to generate a production signing key. Replace `YOUR_KEYSTORE_PASSWORD` and `YOUR_KEY_PASSWORD` with secure credentials:

```bash
keytool -genkey -v -keystore e:/Github/Ghost\ Messenger/codechat_client/android/upload-keystore.jks ^
  -storetype JKS ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -alias upload ^
  -storepass YOUR_KEYSTORE_PASSWORD ^
  -keypass YOUR_KEY_PASSWORD
```

**Important Security Notice**: Store the resulting `upload-keystore.jks` file and passwords in a secure password manager. If you lose this key, you will not be able to publish updates to your existing Google Play listing unless using Play App Signing key reset protocols.

---

## 3. Configure Release Signing in Gradle

### Step A: Create `key.properties`
Create a file at `codechat_client/android/key.properties` containing the following configuration (do not commit this file to public version control):

```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=upload
storeFile=../upload-keystore.jks
```

### Step B: Update `codechat_client/android/app/build.gradle.kts`
Verify that `build.gradle.kts` is configured to load `key.properties` for release builds:

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    ...
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
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}
```

---

## 4. Set Application ID and Versioning

In `codechat_client/pubspec.yaml`, adjust the version string prior to each release:

```yaml
version: 1.0.0+1
```
- `1.0.0`: Version Name (displayed to users)
- `1`: Version Code (integer incremented on each Play Store update)

In `codechat_client/android/app/build.gradle.kts`, ensure your `applicationId` matches your verified package identifier:

```kotlin
defaultConfig {
    applicationId = "org.ghostmessenger.app" // Adjust to your preferred domain identifier
    minSdk = flutter.minSdkVersion
    targetSdk = flutter.targetSdkVersion
    versionCode = flutter.versionCode
    versionName = flutter.versionName
}
```

---

## 5. Build the Production Android App Bundle (AAB)

Run the release build command from the `codechat_client` directory:

```bash
cd codechat_client
flutter clean
flutter pub get
flutter build appbundle --release
```

The output file will be generated at:
`codechat_client/build/app/outputs/bundle/release/app-release.aab`

---

## 6. Google Play Console Submission Steps

### Step 1: Create Application
1. Log in to [Google Play Console](https://play.google.com/console).
2. Click **Create app**.
3. Enter App Name: `Ghost Messenger`.
4. Default Language: `English (United States)`.
5. App or Game: `App`.
6. Free or Paid: `Free`.
7. Accept Developer Program Policies and US Export Laws.

### Step 2: Set Up Store Listing
1. Navigate to **Grow** > **Store presence** > **Main store listing**.
2. Copy the metadata from [`STORE_LISTING.md`](./STORE_LISTING.md):
   - App title
   - Short description
   - Full description
3. Upload Graphics:
   - App icon (512x512 PNG)
   - Feature graphic (1024x500 PNG/JPEG)
   - Phone screenshots (minimum 4).

### Step 3: Complete App Content Declarations
Navigate to **Policy and programs** > **App content** and complete the required sections:

1. **Privacy Policy**: Provide the hosted URL of [`PRIVACY_POLICY.md`](./PRIVACY_POLICY.md).
2. **Data Safety**: Complete the questionnaire using the exact answers from [`DATA_SAFETY_DECLARATION.md`](./DATA_SAFETY_DECLARATION.md).
3. **Ads**: Select "No, my app does not contain ads".
4. **App Access**: Select "All functionality is available without special access restrictions".
5. **Content Rating (IARC)**: Complete the questionnaire:
   - Category: Utility, Productivity, Communication
   - Violence, Sexual Content, Offensive Language: No
   - Miscellaneous: Select "Users can interact or exchange content (messaging)".
6. **Target Audience**: Select "18 and over" (or 13+).
7. **Financial Features / Government App Declarations**: Select "None".

### Step 4: Create and Roll Out Release
1. Navigate to **Release** > **Testing** > **Internal testing** (recommended first step).
2. Click **Create new release**.
3. Upload `app-release.aab`.
4. Enter Release Name (e.g., `1.0.0 (1)`).
5. Enter Release Notes:
   ```
   Initial release of Ghost Messenger.
   - Zero-knowledge peer-to-peer end-to-end encrypted messaging.
   - Signal Protocol (Double Ratchet + X3DH).
   - BIP39 cryptographic identity generation and recovery.
   - Encrypted local storage with SQLCipher.
   ```
6. Click **Save** > **Review release** > **Start rollout to Internal testing**.
7. Once verified on test devices, promote the release to **Production**.
