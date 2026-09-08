# 🚀 Google Play Store Release Guide — Calypso

This guide outlines the complete procedure for building, verifying, and publishing **Calypso** (`org.calypso.messenger`) to the **Google Play Console**.

---

## 📌 Release Summary & Specifications

| Property | Value | Details |
|---|---|---|
| **App Name** | Calypso | Play Store display title |
| **Package Name / Application ID** | `org.calypso.messenger` | Unique app identifier |
| **Release Version** | **`1.0.2`** (`versionCode: 3`) | Production release version |
| **Minimum SDK** | `26` (Android 8.0 Oreo) | Broad device compatibility |
| **Target SDK** | `36` (Android 16 / Latest) | Exceeds Google Play's required API level |
| **Signing Keystore** | `android/upload-keystore.jks` | Google Play Upload Key |
| **Key Alias** | `ghost_upload` | Alias configured in `key.properties` |
| **Target Production Binary** | `app-release.aab` | Android App Bundle (Required by Google Play) |
| **Testing Binary** | `app-release.apk` | Standalone APK for local sideloading |

> [!IMPORTANT]
> **Why `versionCode` Must Be `3`**:
> Google Play strictly prohibits uploading a bundle with a `versionCode` less than or equal to an existing release. Because version 1.0.1 used `versionCode = 2`, version 1.0.2 **must** use `versionCode = 3`. This has already been updated in [`android/app/build.gradle.kts`](../android/app/build.gradle.kts).

---

## 🛠️ Step 1: Pre-Build Key & Configuration Check

Before running the build, ensure your signing credentials are in place:

1. **Verify Keystore Files**:
   - `android/upload-keystore.jks` must exist in the `android/` directory.
   - `android/key.properties` must exist and contain the matching credentials:
     ```properties
     storePassword=MasterZGeek007PWDGHOST_MESSENGER
     keyPassword=GM007MZ
     keyAlias=ghost_upload
     storeFile=upload-keystore.jks
     ```

2. **Verify Server Endpoint**:
   - [`SecurePreferences.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt) has `DEFAULT_SIGNALING_URL` configured to your live production server:
     ```kotlin
     const val DEFAULT_SIGNALING_URL = "https://ghost-messenger-fp8w.onrender.com"
     ```

---

## 📦 Step 2: Compile the Signed Production Artifacts

Open **PowerShell** in the `android/` directory:

```powershell
cd "e:\Github\Ghost Messenger\android"
```

### 1. Build the Google Play Android App Bundle (AAB):
```powershell
.\gradlew.bat :app:bundleRelease
```
- **Output File**:
  ```
  e:\Github\Ghost Messenger\android\app\build\outputs\bundle\release\app-release.aab
  ```
- **Purpose**: This `.aab` file is the **only file you upload to Google Play Console**. It contains optimized splits for all device architectures and screen densities.

### 2. (Optional) Build Standalone Release APK for Physical Phone Testing:
```powershell
.\gradlew.bat :app:assembleRelease
```
- **Output File**:
  ```
  e:\Github\Ghost Messenger\android\app\build\outputs\apk\release\app-release.apk
  ```
- **Purpose**: Use this APK to install directly on your physical Android test phone before publishing to Google Play.

---

## 📱 Step 3: Quick Smoke Test on a Real Device (Recommended)

Before submitting to Google Play, verify that the release build runs smoothly on a real device:

1. Connect your Android phone via USB (with **USB Debugging** enabled).
2. Install the release APK via ADB:
   ```powershell
   adb install -r "e:\Github\Ghost Messenger\android\app\build\outputs\apk\release\app-release.apk"
   ```
3. **Run Quick Checklist**:
   - [ ] App opens cleanly without crashing (verifies R8/ProGuard rules).
   - [ ] BIP-39 mnemonic generation or wallet restoration functions.
   - [ ] Local encrypted database (SQLCipher) initializes without errors.
   - [ ] Signaling connects to the live server (`ghost-messenger-fp8w.onrender.com`).

---

## 🌐 Step 4: Upload to Google Play Console (Step-by-Step)

Follow these exact steps in your browser:

### 1. Access Your App
1. Go to **[Google Play Console](https://play.google.com/console)**.
2. Sign in with your Google Developer account.
3. On the **All apps** dashboard, click **Calypso**.

### 2. Navigate to Your Target Release Track
Depending on your release setup:

- **If publishing to Production**:
  - In the left sidebar under the **Release** section, click **Production**.
  - At the top-right corner, click the blue **Create new release** button.

- **If running Closed Testing** (e.g. 20-tester requirement):
  - In the left sidebar under **Testing**, click **Closed testing**.
  - Find your active testing track and click **Manage track**.
  - At the top-right, click **Create new release**.

- **If testing via Internal Testing first**:
  - In the left sidebar under **Testing**, click **Internal testing**.
  - Click **Create new release**.

---

### 3. Upload the New App Bundle (`.aab`)
1. Under **App bundles**, click **Upload** (or drag and drop the file).
2. Browse to and select:
   ```
   e:\Github\Ghost Messenger\android\app\build\outputs\bundle\release\app-release.aab
   ```
3. Wait for the upload and validation process to finish.
4. Once completed, Google Play will display:
   - **Version name**: `1.0.2`
   - **Version code**: `3`
   - **Download size**: ~15–20 MB (optimized by Play Feature Delivery)

---

### 4. Configure Release Details

1. **Release Name**:
   - Google Play will automatically populate this as `1.0.2 (3)`. You can keep it as `1.0.2`.

2. **Release Notes**:
   - Click **Add release notes** (or edit the release notes field).
   - Copy and paste the following standard release notes:

```xml
<en-US>
• Calypso initial release
• Zero-knowledge, peer-to-peer end-to-end encrypted messaging
• Signal Protocol Double Ratchet session encryption
• Direct WebRTC DataChannels with zero metadata storage
• BIP39 mnemonic recovery and SQLCipher local vault encryption
</en-US>
```

---

### 5. Review & Rollout

1. Click **Next** at the bottom-right corner of the page.
2. **Inspect Errors & Warnings**:
   - **Errors (Red)**: None should appear. If any error appears, check the Troubleshooting section below.
   - **Warnings (Yellow)**: Informational notices (such as deobfuscation file or API level advice) are normal. ProGuard mapping files are automatically packed inside the `.aab` by the Android Gradle Plugin.
3. Click **Save**.
4. Click **Review release** (or **Go to overview**).
5. Click **Start rollout to Production** (or **Start rollout to Closed testing**).
6. In the confirmation dialog, click **Rollout**.

---

## ⏳ Step 5: What to Expect After Rolling Out

- **Status**: Your release status will change to **In review**.
- **Review Duration**:
  - App updates for already-published apps typically take between **2 hours and 24 hours** (faster than initial submissions).
- **Notification**: You will receive an email from Google Play when the update is approved.
- **Propagation**: Once approved, the update becomes available to users globally within a few hours.

---

## 🔧 Troubleshooting Common Play Console Update Errors

### 1. "Version code 2 has already been used"
- **Cause**: The uploaded `.aab` has the same or lower `versionCode` as a previously uploaded release.
- **Fix**: Verify line 27 of `android/app/build.gradle.kts` has `versionCode = 3` (or higher) and rebuild with `.\gradlew.bat :app:bundleRelease`.

### 2. "Your Android App Bundle is signed with the wrong key"
- **Cause**: The keystore used does not match the Google Play Upload Key registered during initial app creation.
- **Fix**: Ensure `key.properties` points to `upload-keystore.jks`. Do not generate a new keystore, as Google Play only accepts the initial upload key.

### 3. "You must provide a privacy policy URL"
- **Status**: Already satisfied! Your privacy policy is hosted on GitHub Pages:
  ```
  https://masterz1311.github.io/Ghost-Messenger/playstore/PRIVACY_POLICY
  ```

---

## 📁 Repository Reference Documents Retained

The following live documents and store assets are preserved in `docs/playstore/` for ongoing compliance and store presence:

- [`PRIVACY_POLICY.md`](./playstore/PRIVACY_POLICY.md) — Live Privacy Policy.
- [`TERMS_OF_SERVICE.md`](./playstore/TERMS_OF_SERVICE.md) — Live Terms of Service.
- [`DATA_SAFETY_DECLARATION.md`](./playstore/DATA_SAFETY_DECLARATION.md) — Data Safety answers reference.
- [`STORE_LISTING.md`](./playstore/STORE_LISTING.md) — Play Store title, short description, and full copy.
- [`assets/`](./playstore/assets/) — 512x512 app icon, 1024x500 banner, and store screenshots.
