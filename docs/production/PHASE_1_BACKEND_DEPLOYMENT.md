# Phase 1: Backend Infrastructure & Signaling Deployment

This guide walks you through deploying the **Ghost Messenger Signaling & Prekey Server** into production with full HTTPS/WSS encryption and setting up a **TURN relay server** for reliable mobile peer-to-peer connectivity.

---

## 📌 Overview

Ghost Messenger uses a zero-knowledge, metadata-minimal signaling server (`server/`) that performs two tasks:
1. **WebRTC Brokering**: Routes SDP offers, answers, and ICE candidates between peers over WebSocket connections.
2. **Ephemeral PreKey Distribution**: Stores one-time public cryptographic keys in memory so contacts can initiate asynchronous X3DH Signal handshakes.

The server has **no database**, stores **no logs**, and **never sees message payloads**.

---

## 🛠️ Step 1: Deploy Signaling Server to Cloud Hosting

Because the repository already contains a production [`Dockerfile`](../../Dockerfile) and [`railway.json`](../../railway.json), **Railway.app** is the easiest and fastest deployment target. Render and VPS options are also detailed below.

### Option A: Railway.app (Recommended — Takes ~3 Minutes)

Railway provides automatic HTTPS, container auto-healing, and native GitHub integration.

1. **Sign in to Railway**:
   - Navigate to **[railway.com](https://railway.com)**.
   - Click **Login** and choose **Continue with GitHub**.

2. **Create a New Project**:
   - Click **+ New Project** on your dashboard.
   - Select **Deploy from GitHub repo**.
   - Select your repository: `MasterZ1311/Ghost-Messenger`.

3. **Verify Build Settings**:
   - Railway automatically detects `railway.json` and [`Dockerfile`](../../Dockerfile).
   - It will build the Docker container:
     ```dockerfile
     FROM node:20-alpine
     WORKDIR /app
     COPY server/package*.json ./
     RUN npm ci --only=production
     COPY server/src/ ./src/
     EXPOSE 3000
     CMD ["npm", "start"]
     ```

4. **Generate a Public HTTPS Domain**:
   - Click on your newly created service tile.
   - Go to the **Settings** tab.
   - Scroll down to the **Networking** section.
   - Under **Public Networking**, click **Generate Domain**.
   - Railway assigns a public URL such as:
     ```
     https://ghost-messenger-production-xxxx.up.railway.app
     ```
   - **Copy this URL** — you will need it for Phase 2.

5. **Verify the Deployment**:
   - Open your terminal or browser and test the health endpoint:
     ```bash
     curl https://your-railway-domain.up.railway.app/api/prekeys/health/status
     ```
   - Expected JSON response:
     ```json
     {
       "status": "ok",
       "uptimeSeconds": 15,
       "activeUsers": 0,
       "storedBundles": 0
     }
     ```

---

### Option B: Render.com (100% Free Tier Alternative)

1. Sign in to **[render.com](https://render.com)** using GitHub.
2. Click **New +** → **Web Service**.
3. Select the `Ghost-Messenger` repository.
4. Fill in the service configuration:
   - **Name**: `ghost-messenger-signaling`
   - **Region**: Closest to you (e.g., Oregon, Frankfurt, Singapore)
   - **Branch**: `MZ-Main`
   - **Root Directory**: `.` (leave as root)
   - **Runtime**: `Docker`
   - **Dockerfile Path**: `./Dockerfile`
   - **Instance Type**: `Free`
5. Click **Create Web Service**.
6. Render builds the Docker image and provides a live HTTPS URL:
   ```
   https://ghost-messenger-signaling.onrender.com
   ```
   *(Note: Free tier instances spin down after 15 minutes of inactivity; initial wakeups take ~30 seconds).*

---

### Option C: Self-Hosted VPS (Oracle Cloud Free Tier / Ubuntu)

If you prefer full control on an Ubuntu VM:

1. **SSH into your server**:
   ```bash
   ssh ubuntu@your-server-ip
   ```

2. **Install Docker and Docker Compose**:
   ```bash
   curl -fsSL https://get.docker.com | sh
   sudo usermod -aG docker ubuntu
   newgrp docker
   ```

3. **Clone your repository**:
   ```bash
   git clone https://github.com/MasterZ1311/Ghost-Messenger.git
   cd Ghost-Messenger
   ```

4. **Build and run the container**:
   ```bash
   docker build -t ghost-signaling -f Dockerfile .
   docker run -d --name ghost-signaling --restart unless-stopped -p 3000:3000 ghost-signaling
   ```

5. **Set up Caddy for automatic free SSL**:
   Install Caddy:
   ```bash
   sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
   curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
   curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
   sudo apt update && sudo apt install caddy
   ```
   Edit `/etc/caddy/Caddyfile`:
   ```caddy
   ghost.yourdomain.com {
       reverse_proxy localhost:3000
   }
   ```
   Restart Caddy:
   ```bash
   sudo systemctl restart caddy
   ```

---

## 🌐 Step 2: Acquire TURN Server Credentials (WebRTC NAT Traversal)

### Why is a TURN Server Mandatory for Production?
- WebRTC attempts direct P2P connections using **STUN** (`stun:stun.l.google.com:19302`).
- STUN works across residential routers, but **fails on 4G/5G mobile data, carrier-grade NAT (CGNAT), and restricted Wi-Fi**.
- A **TURN server** acts as an encrypted relay fallback when direct UDP connection fails.
- Because messages are encrypted via the **Signal Protocol (Double Ratchet)**, the TURN server can only see encrypted packets and cannot read message contents.

### Acquiring Free TURN Credentials from Metered.ca (50 GB Free/Month)

1. Go to **[metered.ca/tools/openrelay](https://www.metered.ca/tools/openrelay/)** or **[metered.ca](https://www.metered.ca)**.
2. Click **Create Free Account** (no credit card required).
3. In your Metered dashboard:
   - Navigate to **TURN Server Credentials**.
   - Locate your unique credentials:
     - **TURN Server URL**: `turn:standard.metered.ca:80` (UDP) and `turn:standard.metered.ca:443?transport=tcp` (TCP fallback)
     - **Username**: e.g., `a1b2c3d4e5f6...`
     - **Credential / Password**: e.g., `xYz123456789...`

4. **Add TURN Credentials to the Android App**:
   Open [`WebRtcManager.kt`](../../android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt) and update the `iceServers` list:

   ```kotlin
   private val iceServers = listOf(
       // Primary STUN (free Google public STUN)
       PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
       PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),

       // TURN Relay Fallback (Metered.ca)
       PeerConnection.IceServer.builder("turn:standard.metered.ca:80")
           .setUsername("YOUR_METERED_USERNAME")
           .setPassword("YOUR_METERED_PASSWORD")
           .createIceServer(),

       PeerConnection.IceServer.builder("turn:standard.metered.ca:443?transport=tcp")
           .setUsername("YOUR_METERED_USERNAME")
           .setPassword("YOUR_METERED_PASSWORD")
           .createIceServer()
   )
   ```

---

## 🔍 Step 3: Verify Your Deployment

Run this automated checklist against your live server:

| Test | Command / Action | Expected Result |
|---|---|---|
| **HTTPS Certificate** | Open `https://your-domain.com` in browser | Valid SSL lock icon, JSON root greeting |
| **Health Check Endpoint** | `curl https://your-domain.com/api/prekeys/health/status` | HTTP 200 with `status: "ok"` |
| **PreKey Upload API** | `curl -X POST https://your-domain.com/api/prekeys/upload -H "Content-Type: application/json" -d "{}"` | HTTP 400 with `error: "Missing required fields..."` |
| **WebSocket Connection** | Connect via Socket.IO client or test script | HTTP 101 Switching Protocols to WSS |

---

## 🏁 Phase 1 Completed!

You now have:
- [x] A live, secure HTTPS/WSS signaling server deployed on the cloud.
- [x] Verified REST endpoints for PreKey distribution.
- [x] High-reliability TURN credentials configured for WebRTC peer-to-peer fallback.

👉 **Proceed to [Phase 2: Android Client Configuration & Release Build](./PHASE_2_ANDROID_RELEASE_BUILD.md).**
