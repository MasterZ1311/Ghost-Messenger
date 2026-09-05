# 👻 Ghost Messenger — Production Deployment & Launch Runbook

Welcome to the production deployment guide for **Ghost Messenger**. This runbook contains everything required to take your application from a local development environment into a fully operational, live production service.

---

## 🗺️ System Architecture in Production

```
+-----------------------------------------------------------------------------------+
|                                 PRODUCTION SETUP                                  |
|                                                                                   |
|   +--------------------------+                     +--------------------------+   |
|   |     Client A (Android)   |                     |     Client B (Android)   |   |
|   |  - Signal Double Ratchet |                     |  - Signal Double Ratchet |   |
|   |  - Encrypted SQLCipher   |                     |  - Encrypted SQLCipher   |   |
|   |  - Hardware Keystore     |                     |  - Hardware Keystore     |   |
|   +------------+-------------+                     +-------------+------------+   |
|                |                                                 |                |
|                | 1. Connect (WSS / HTTPS)                        | 1. Connect     |
|                v                                                 v                |
|        +-----------------------------------------------------------------+        |
|        |           Signaling & Ephemeral Prekey Server                   |        |
|        |       (Hosted on Railway / Render / Oracle Cloud)               |        |
|        |  - Brokering WebSockets (SDP Offers / Answers / ICE)            |        |
|        |  - In-Memory Ephemeral PreKey Store (Zero Logs / Zero DB)       |        |
|        +-----------------------------------------------------------------+        |
|                                         |                                         |
|                 2. Direct WebRTC P2P DataChannel Connection                       |
|         <===============================================================>         |
|                                         |                                         |
|                  (If Symmetric NAT / Mobile 4G/5G blocks direct P2P)              |
|                                         |                                         |
|                                         v                                         |
|        +-----------------------------------------------------------------+        |
|        |                   TURN Server Relay (Metered.ca)                |        |
|        |               Encrypted Packet Fallback Forwarding              |        |
|        +-----------------------------------------------------------------+        |
+-----------------------------------------------------------------------------------+
```

---

## ⏱️ Estimated Time & Cost Breakdown

| Component | Recommended Provider | Cost | Setup Time |
|---|---|---|---|
| **Signaling Server** | [Railway.app](https://railway.com) or [Render.com](https://render.com) | **Free** ($5 free credit / free tier) | ~5 minutes |
| **NAT Relay (TURN)** | [Metered.ca](https://www.metered.ca) | **Free** (50 GB free / month) | ~3 minutes |
| **Privacy Policy Hosting** | **GitHub Pages** (Built-in) | **Free** | ~2 minutes |
| **Android Release Signing** | Local Java `keytool` (Windows) | **Free** | ~2 minutes |
| **Google Play Developer Account** | [Google Play Console](https://play.google.com/console) | **$25 USD** (One-time registration) | ~15 mins (+ identity verification) |
| **Direct APK Distribution** | **GitHub Releases** / Direct download | **Free** | Instant |

---

## 📑 The 4 Production Phases

Follow these four guides in chronological order:

### 🚀 [Phase 1: Backend Infrastructure & Signaling Deployment](./PHASE_1_BACKEND_DEPLOYMENT.md)
> **Goal**: Deploy your Node.js signaling server with HTTPS/WSS and set up a free TURN server for mobile peer-to-peer relay.
> - Deploying via Railway.app (1-click using repository Dockerfile) or Render.com.
> - Setting up Metered.ca TURN credentials for 4G/5G mobile NAT traversal.
> - Health checking and live endpoint verification.

### 📱 [Phase 2: Android Client Configuration & Release Build](./PHASE_2_ANDROID_RELEASE_BUILD.md)
> **Goal**: Configure your Android app with the live server URL, generate a production keystore, configure Gradle signing, and compile release binaries.
> - Setting production signaling URL in `SecurePreferences.kt`.
> - Generating an Android release keystore (`upload-keystore.jks`) on Windows.
> - Configuring `key.properties` and Gradle `signingConfigs`.
> - Building the Google Play Android App Bundle (`app-release.aab`) and standalone release APK (`app-release.apk`).

### ⚖️ [Phase 3: Legal, Privacy & Store Policy Compliance](./PHASE_3_LEGAL_AND_COMPLIANCE.md)
> **Goal**: Host your Privacy Policy and Terms of Service online for free and prepare your Google Play compliance declarations.
> - Hosting [`PRIVACY_POLICY.md`](../playstore/PRIVACY_POLICY.md) on GitHub Pages with HTTPS.
> - Google Play Data Safety Form answers (Ghost Messenger collects **0 personal data**).
> - IARC Content Rating, Target Audience (18+), and App Access disclosures.

### 🛒 [Phase 4: Google Play Console Submission & Launch Operations](./PHASE_4_PLAY_STORE_SUBMISSION.md)
> **Goal**: Set up your Google Play Console, satisfy the 20-tester / 14-day closed testing rule, upload assets, and launch to production.
> - Creating and verifying a Google Play Developer account.
> - Navigating Google's mandatory 20-tester closed testing period for personal accounts.
> - Uploading store assets (icon, feature banner, screenshots from `docs/playstore/assets/`).
> - Releasing `app-release.aab` and executing a parallel direct APK release on GitHub Releases.

---

## ✅ Pre-Flight Checklist

Before marking the app live, ensure every checkbox below is checked:

- [ ] **Phase 1**: Signaling server is running on public HTTPS (e.g. `https://your-domain.up.railway.app/api/prekeys/health/status` returns `"status": "ok"`).
- [ ] **Phase 1**: TURN credentials from Metered.ca are configured in `WebRtcManager.kt`.
- [ ] **Phase 2**: Production server URL is configured in Android code.
- [ ] **Phase 2**: `upload-keystore.jks` is safely generated and backed up.
- [ ] **Phase 2**: `app-release.aab` and `app-release.apk` compile cleanly with R8 shrinking enabled.
- [ ] **Phase 2**: Sideloaded release APK has been tested on a physical Android phone.
- [ ] **Phase 3**: Privacy Policy URL is live and accessible on the public web.
- [ ] **Phase 3**: Data Safety questionnaire responses are reviewed and ready to submit.
- [ ] **Phase 4**: Google Play Console account is paid ($25) and identity-verified.
- [ ] **Phase 4**: Store listing graphics and text descriptions are uploaded.
- [ ] **Phase 4**: Internal / Closed testing tracks launched and verified.

---

👉 **Ready to begin? Start with [Phase 1: Backend Deployment](./PHASE_1_BACKEND_DEPLOYMENT.md).**
