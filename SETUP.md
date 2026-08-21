# Ghost Messenger — Setup Guide

This document covers every credential and configuration value needed to deploy
Ghost Messenger. Items marked ✅ are already generated. Items marked 🔧 need
your action.

---

## ✅ Already Done For You

| Item | Value / Location |
|---|---|
| Android keystore | `codechat_client/android/upload-keystore.jks` (file exists) |
| Keystore passwords | `android/key.properties` — `GhostMessenger2026` |
| TURN secret | `codechat_signaling/.env` → `TURN_SECRET` (pre-generated 32-byte hex) |

---

## 🔧 Step 1 — Get a Domain Name (Free options)

You need a domain to run HTTPS and the TURN server. Two free options:

### Option A: Freenom (free .tk / .ml domains)
1. Go to https://www.freenom.com
2. Search for a name → choose a `.tk` or `.ml` extension → register free for 12 months
3. Note your domain (e.g. `ghostmsg.tk`)

### Option B: DuckDNS (free subdomain, easiest)
1. Go to https://www.duckdns.org
2. Sign in with Google/GitHub
3. Create a subdomain like `ghostmessenger.duckdns.org`
4. Point it to your server IP in the DuckDNS dashboard
5. No DNS propagation wait needed — updates in ~1 minute

Once you have your domain, fill in these three files:

**`codechat_signaling/.env`** — replace both `yourdomain.com` occurrences:
```
CORS_ORIGINS=yourdomain.com
TURN_REALM=yourdomain.com
TURN_URLS=turn:yourdomain.com:3478   ← uncomment this line too
```

**`codechat_signaling/infra/caddy/Caddyfile`** — replace `yourdomain.com` on line 8.

**`codechat_client/lib/services/app_state.dart`** — line 12:
```dart
const String _kDefaultSignalingUrl = 'https://yourdomain.com';
```

---

## 🔧 Step 2 — Get a Free Server (to host the signaling + TURN server)

### Option A: Oracle Cloud Free Tier (recommended — always free)
1. Sign up at https://cloud.oracle.com/free
2. Create an **Always Free** VM (AMD shape, 1 OCPU / 1 GB RAM is enough)
3. Use Ubuntu 22.04
4. Open ports **80, 443, 3000, 3478** in the Security List
5. Install Docker:
   ```bash
   curl -fsSL https://get.docker.com | sh
   sudo usermod -aG docker ubuntu
   ```
6. Clone your repo and run:
   ```bash
   cd codechat_signaling
   docker compose up -d
   ```

### Option B: Fly.io (free tier, 3 VMs)
1. Sign up at https://fly.io
2. Install flyctl: https://fly.io/docs/hands-on/install-flyctl/
3. Run `fly launch` in `codechat_signaling/`

### Option C: Render.com (free web service)
1. Sign up at https://render.com
2. New → Web Service → connect your GitHub repo
3. Set root directory to `codechat_signaling`
4. Add all env vars from `.env` in the Render dashboard

---

## 🔧 Step 3 — Push Notifications (Optional — for background call wakeup)

Without push notifications the app still works fully when open. Push is only
needed to wake the app when it's in the background.

### Android — Firebase Cloud Messaging (FCM) — Free

1. Go to https://console.firebase.google.com
2. Click **Add project** → name it (e.g. `GhostMessenger`) → Continue
3. Skip Google Analytics if you want → **Create project**
4. Click the **Android** icon to add an Android app
5. Enter package name: `org.ghostmessenger.app`
6. Download `google-services.json`
7. Place it at: `codechat_client/android/app/google-services.json`
8. In Firebase Console → Project Settings → **Service accounts** tab
9. Click **Generate new private key** → download the JSON file
10. On your server, set the env var:
    ```
    GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
    ```
11. In `codechat_signaling/src/app.js` (or `server.js`), wire up the dispatcher:
    ```js
    const admin = require('firebase-admin');
    admin.initializeApp({ credential: admin.credential.applicationDefault() });
    pushNotification.setDispatcher(async ({ token, payload }) => {
      await admin.messaging().send({ token, data: payload });
      return true;
    });
    ```
12. Add `firebase-admin` to the server: `npm install firebase-admin@^12.0.0`

### iOS — APNs — Free (requires Apple Developer account — $99/year)

If you need iOS push notifications, you need a paid Apple Developer account.
Steps:
1. Sign in to https://developer.apple.com
2. Certificates, IDs & Profiles → Keys → **+** → enable **Apple Push Notifications**
3. Download the `.p8` key file — **you can only download it once**
4. Note your **Key ID** (10-char string) and **Team ID** (top-right of dev portal)
5. Place the `.p8` file on your server
6. Add to `.env`:
    ```
    APNS_KEY_PATH=/run/secrets/apns-key.p8
    APNS_KEY_ID=XXXXXXXXXX
    APNS_TEAM_ID=XXXXXXXXXX
    APNS_BUNDLE_ID=org.ghostmessenger.app
    ```

---

## 🔧 Step 4 — Android Play Store Signing (only needed to publish)

The keystore (`upload-keystore.jks`) and `key.properties` are already set up.
You only need this if publishing to the Play Store:

1. Go to https://play.google.com/console → create a developer account ($25 one-time fee)
2. Create a new app
3. Under **Setup → App signing**, upload your keystore or let Google manage signing
4. Build your release APK/AAB:
   ```bash
   cd codechat_client
   flutter build appbundle --release
   ```
   Output: `build/app/outputs/bundle/release/app-release.aab`

---

## Quick Checklist

- [ ] Domain name obtained and DNS pointed to server IP
- [ ] Replace `yourdomain.com` in `.env`, `Caddyfile`, `app_state.dart` ✅ Done — using `ghostmessenger.duckdns.org`
- [ ] Uncomment `TURN_URLS` in `.env` ✅ Done
- [ ] Server provisioned with Docker installed
- [ ] `docker compose up -d` running on server
- [ ] (Optional) Firebase project created, `google-services.json` placed, dispatcher wired
- [ ] (Optional) APNs key downloaded and env vars set

---

## Pre-Generated Values (keep these secret)

```
TURN_SECRET=23a3f3c5ea132c21b82da869c9561cd3bf642b366ae849e3bc2bce7ae84dadb4
```

This secret is already written to `codechat_signaling/.env`. It is also used
as coturn's `--static-auth-secret` in docker-compose. Do not change it unless
you regenerate it in both places simultaneously.
