# Phase 3: Legal, Privacy & Store Policy Compliance

Google Play has strict legal, privacy, and user safety requirements. This guide walks you through hosting your **Privacy Policy** and **Terms of Service** online for free, completing the **Data Safety Form**, and answering all mandatory **App Content Declarations**.

---

## 🌐 Step 1: Host Your Privacy Policy Online (Free on GitHub Pages)

Google Play **requires** a publicly accessible, active HTTPS URL for your Privacy Policy. Your repository already contains ready-to-use policy files:
- [`docs/playstore/PRIVACY_POLICY.md`](../playstore/PRIVACY_POLICY.md)
- [`docs/playstore/TERMS_OF_SERVICE.md`](../playstore/TERMS_OF_SERVICE.md)

### Setting Up Free GitHub Pages in 2 Minutes:

1. Open your browser and navigate to your GitHub repository:
   ```
   https://github.com/MasterZ1311/Ghost-Messenger
   ```
2. Click the **Settings** tab in the top navigation bar.
3. In the left-hand sidebar, click **Pages** (under the "Code and automation" section).
4. Under **Build and deployment**:
   - **Source**: Select `Deploy from a branch`.
   - **Branch**: Select `MZ-Main` (or your default branch).
   - **Folder**: Select `/docs` from the dropdown.
5. Click **Save**.
6. Wait approximately 60–90 seconds. GitHub will display a green banner with your live site URL:
   ```
   https://masterz1311.github.io/Ghost-Messenger/
   ```
7. Your official policy URLs are now live:
   - **Privacy Policy**:
     ```
     https://masterz1311.github.io/Ghost-Messenger/playstore/PRIVACY_POLICY
     ```
   - **Terms of Service**:
     ```
     https://masterz1311.github.io/Ghost-Messenger/playstore/TERMS_OF_SERVICE
     ```

> [!TIP]
> **Free Alternative**: You can also copy the markdown text into a public **Notion page**, a public **GitHub Gist**, or a free **GitBook** site.

---

## 📋 Step 2: Complete the Play Console Data Safety Questionnaire

In the Google Play Console, go to **Policy and programs** → **App content** → **Data safety**.

Ghost Messenger is built on **zero telemetry, zero account creation, and zero server persistence**. Here are the exact answers to submit:

### 1. Data Collection & Sharing Overview

| Question | Answer | Explanation / Notes |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **No** | Ghost Messenger does not collect or share personal user data with servers or third parties. All messages are transmitted peer-to-peer via WebRTC DataChannels. |
| **Is all of the user data collected by your app encrypted in transit?** | **Yes** | All signaling runs over TLS 1.3 / WSS, and peer messaging is encrypted via the Signal Protocol (Double Ratchet) with DTLS. |
| **Do you provide a way for users to request that their data be deleted?** | **Yes** | Users can permanently delete their cryptographic identity and all chat history at any time using the in-app "Purge Local Vault" button or by uninstalling the app. |

### 2. Data Categories Checklist

When asked to review individual data categories, select **No** for every category:

- **Location**: No (Not collected or tracked)
- **Personal Info**: No (No name, email, phone number, or user IDs)
- **Financial Info**: No (No credit cards, payments, or purchase history)
- **Health & Fitness**: No
- **Messages**: No (All message payloads are end-to-end encrypted on-device; neither server nor developer has access)
- **Photos & Videos**: No
- **Audio Files**: No
- **Files & Documents**: No
- **Calendar**: No
- **Contacts**: No (No access to device address book)
- **App Activity**: No (Zero analytics SDKs, zero behavioral telemetry)
- **Web Browsing**: No
- **App Info & Performance**: No (No third-party crash analytics SDKs)
- **Device or Other IDs**: No (No advertising ID, IMEI, or hardware fingerprinting)

---

## 🛡️ Step 3: Complete App Content Declarations

In the Google Play Console under **App content**, complete the following sections:

### 1. Privacy Policy
- Paste your live URL:
  `https://masterz1311.github.io/Ghost-Messenger/playstore/PRIVACY_POLICY`

### 2. Ads
- Select: **"No, my app does not contain ads"**.
- Ghost Messenger contains no advertising SDKs.

### 3. App Access
- Select: **"All functionality is available without special access restrictions"**.
- Explanation: The app does not require a login, username, password, or subscription. Reviewers can immediately open the app, generate a 12-word mnemonic, and test the interface.

### 4. Content Rating (IARC)
Click **Start questionnaire**, provide your contact email, and select the category:
- **Category**: `Utility, Productivity, Communication, or Other`
- **Violence**: No
- **Sexuality / Adult Content**: No
- **Profanity**: No
- **Controlled Substances**: No
- **User Interactions**: Select **Yes** to *"Can users interact or exchange content with other people through voice communication, text, or sharing images?"*
  - **Does the app share the user's current physical location?**: No
  - **Does the app allow users to purchase digital goods?**: No
- **Result**: You will receive an **Everyone** / **PEGI 3** / **USK 0** rating.

### 5. Target Audience & Content
- **Target Age Groups**: Select **18 and over** (recommended).
  - *Selecting 18+ avoids Google Play's strict "Designed for Families" regulations and COPPA compliance audits.*
- **Could this app appeal to children?**: Select **No**.

### 6. News App
- Select: **"No, this is not a news app"**.

### 7. COVID-19 Contact Tracing
- Select: **"My app is not a publicly available COVID-19 contact tracing or status app"**.

### 8. Financial Features
- Select: **"None of the above"** (app provides no financial services, loans, or cryptocurrency trading).

### 9. Government Apps
- Select: **"No, this app is not developed by or on behalf of a government entity"**.

---

## 🏁 Phase 3 Completed!

You now have:
- [x] Live, hosted Privacy Policy and Terms of Service URLs.
- [x] Completed Data Safety questionnaire answers.
- [x] Passed all Google Play Content & Policy declarations.

👉 **Proceed to [Phase 4: Google Play Console Submission & Launch Operations](./PHASE_4_PLAY_STORE_SUBMISSION.md).**
