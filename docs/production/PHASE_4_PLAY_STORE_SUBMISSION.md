# Phase 4: Google Play Console Submission & Launch Operations

This guide walks you through setting up your **Google Play Developer Console** account, completing the **Main Store Listing**, satisfying the **mandatory 20-tester / 14-day closed testing policy**, submitting your release for review, and executing a **parallel direct APK release**.

---

## 🏢 Step 1: Set Up Your Google Play Developer Account

1. Go to **[play.google.com/console/signup](https://play.google.com/console/signup)**.
2. Sign in with your primary Google Account.
3. Choose your account type:
   - **Personal Account**: Suitable for individuals. *(Note: Subject to the 20-tester / 14-day testing rule before production access is granted).*
   - **Organization Account**: Requires an official business name, website, and a **D-U-N-S Number** from Dun & Bradstreet. *(Exempt from the 20-tester rule).*
4. Pay the **$25 USD one-time registration fee**.
5. Complete **Developer Identity Verification**:
   - Google requires uploading a government-issued photo ID (passport, national identity card, or driver's license).
   - Approval usually takes **24 to 48 hours**.

---

## 🎨 Step 2: Set Up Main Store Listing

In the Play Console, navigate to **Grow** → **Store presence** → **Main store listing**.

### 1. App Details
- **App name**: `Ghost Messenger`
- **Short description** *(80 characters max)*:
  ```
  Zero-knowledge peer-to-peer messaging. E2E encrypted, no registration required.
  ```
- **Full description** *(Copy from [`docs/playstore/STORE_LISTING.md`](../playstore/STORE_LISTING.md))*:
  ```markdown
  Ghost Messenger is a privacy-first, peer-to-peer encrypted messaging app engineered for true digital sovereignty.

  🔒 ZERO REGISTRATION
  No phone numbers. No email addresses. No usernames. Your cryptographic identity is derived entirely on-device from a 12-word BIP-39 mnemonic phrase.

  🛡️ SIGNAL PROTOCOL ENCRYPTION
  Every message is encrypted end-to-end using the industry-standard Signal Protocol (Double Ratchet + X3DH). Your conversations enjoy forward secrecy and post-compromise security.

  ⚡ TRUE PEER-TO-PEER
  Messages travel directly between devices over encrypted WebRTC DataChannels. The signaling server is ephemeral and metadata-minimal—it only brokers connection handshakes and never sees message content.

  🗄️ HARDWARE-BACKED LOCAL SECURITY
  All local message history is encrypted at rest using SQLCipher (AES-256). Passphrases and mnemonic secrets are anchored in Android's hardware-backed Keystore.

  💥 DANGER ZONE VAULT PURGE
  Destroy your identity and wipe all encrypted chat history locally at any time with a single tap. Zero server residue.
  ```

### 2. Graphical Assets
All graphics are pre-rendered and ready to upload in [`docs/playstore/assets/`](../playstore/assets/):

| Asset | File Location | Specifications |
|---|---|---|
| **App Icon** | [`docs/playstore/assets/app_icon_512.png`](../playstore/assets/app_icon_512.png) | 512 × 512 PNG (32-bit color, no alpha/transparency on Play Store background) |
| **Feature Graphic** | [`docs/playstore/assets/feature_graphic_1024x500.png`](../playstore/assets/feature_graphic_1024x500.png) | 1024 × 500 PNG |
| **Phone Screenshots** | [`docs/playstore/assets/screenshots/`](../playstore/assets/screenshots/) | 4 themed screenshots (Onboarding, Home, Chat, Safety Verification) |

---

## 👥 Step 3: Satisfy the 20-Tester Closed Testing Policy

> [!IMPORTANT]
> **Google Policy for Personal Accounts (Created after November 13, 2023)**:
> Before Google permits you to release to **Production**, you must run a **Closed Test** with at least **20 testers opted in for 14 continuous days**.

### Step-by-Step Closed Testing Setup:

1. In Play Console, go to **Release** → **Testing** → **Closed testing**.
2. Click **Create track** → Name it `Closed Testing`.
3. In the **Testers** tab:
   - Choose **Email lists** or **Google Groups**.
   - Create an email list containing at least **20–25 email addresses** (friends, colleagues, or community testers).
4. Click **Create new release** inside the Closed Testing track:
   - Upload: `app-release.aab` (from [`Phase 2`](./PHASE_2_ANDROID_RELEASE_BUILD.md)).
   - **Release name**: `1.0.0 (1)`
   - **Release notes**:
     ```
     Initial release of Ghost Messenger.
     - Peer-to-peer end-to-end encrypted messaging via WebRTC DataChannels.
     - Signal Protocol (Double Ratchet + X3DH).
     - BIP-39 12-word cryptographic identity.
     - SQLCipher local database encryption.
     ```
5. Click **Next** → **Review release** → **Start rollout to Closed testing**.
6. Wait for Google's automated review (typically 12–48 hours).
7. Once approved, copy the **Opt-in link**:
   ```
   https://play.google.com/apps/testing/org.ghostmessenger
   ```
8. Send this link to your 20 testers. Each tester must:
   - Open the link on their Android device.
   - Click **Become a Tester**.
   - Download the app from the Google Play link.
9. **Keep all 20 testers enrolled for 14 consecutive days**.
10. On Day 15, the **Apply for production** button will activate in your Play Console dashboard. Click it and complete the brief feedback questionnaire. Production access is typically approved within 3–7 business days.

---

## 🚀 Step 4: Promote to Production

Once your production application is approved:

1. Navigate to **Release** → **Production**.
2. Click **Create new release** (or promote directly from your approved Closed Testing track).
3. Confirm the release details and rollout percentage:
   - **Staged Rollout** *(Optional, recommended)*: Start at 20% or 50% for 24 hours to monitor stability, then increase to 100%.
   - **Full Rollout**: Select 100%.
4. Click **Review release** → **Start rollout to Production**.
5. Your app is officially **LIVE on the Google Play Store** worldwide! 🎉

---

## ⚡ Parallel Launch: Direct APK Distribution (Zero Waiting Period)

While completing the 14-day Google Play closed testing period, you can release the production application **immediately** to users via GitHub Releases or your website.

### Creating a GitHub Release in 60 Seconds:

1. Navigate to your repository: `https://github.com/MasterZ1311/Ghost-Messenger/releases`.
2. Click **Draft a new release**.
3. Fill in the release metadata:
   - **Tag version**: `v1.0.0`
   - **Release title**: `Ghost Messenger v1.0.0 — Production Release`
   - **Description**:
     ```markdown
     ### Ghost Messenger v1.0.0 (Android)
     Zero-knowledge, peer-to-peer encrypted messaging for Android.

     #### Features:
     - 🔑 **Zero Accounts**: Identity derived from a 12-word BIP-39 mnemonic.
     - 🛡️ **Signal Protocol**: X3DH and Double Ratchet end-to-end encryption.
     - ⚡ **WebRTC P2P**: Direct device-to-device DataChannels.
     - 🗄️ **SQLCipher**: Hardware-backed AES-256 database encryption.

     #### Download:
     Download `GhostMessenger-v1.0.0.apk` below and install directly on Android 8.0+.
     ```
4. Drag and drop your compiled release APK:
   `android/app/build/outputs/apk/release/app-release.apk`
   *(Rename it to `GhostMessenger-v1.0.0.apk` for clarity).*
5. Click **Publish release**.
6. Anyone can now download and use Ghost Messenger immediately!

---

## 🏁 Phase 4 Completed!

You have completed the entire production launch lifecycle:
- [x] Live cloud signaling server.
- [x] TURN relay for reliable 4G/5G mobile connectivity.
- [x] Signed production binaries (AAB & APK).
- [x] Publicly hosted Privacy Policy and verified Data Safety declaration.
- [x] Google Play Store submission & direct GitHub release.

🎉 **Congratulations on taking Ghost Messenger live into production!**
