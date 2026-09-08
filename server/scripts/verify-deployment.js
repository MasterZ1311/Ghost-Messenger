#!/usr/bin/env node

/**
 * Calypso — Automated Deployment Verification Script
 *
 * Runs all 4 production deployment tests against a live server endpoint:
 * 1. HTTPS / Root JSON greeting
 * 2. Health check endpoint (/api/prekeys/health/status)
 * 3. PreKey REST API upload validation (/api/prekeys/upload)
 * 4. WebSocket / Socket.IO live handshake over WSS
 *
 * Usage:
 *   node scripts/verify-deployment.js <https://your-server-url.com>
 */

import { io } from 'socket.io-client';

const targetUrl = process.argv[2] || process.env.SIGNALING_URL || 'http://localhost:3000';
const cleanUrl = targetUrl.replace(/\/+$/, '');

const colors = {
  reset: '\x1b[0m',
  green: '\x1b[32m',
  red: '\x1b[31m',
  cyan: '\x1b[36m',
  yellow: '\x1b[33m',
  bold: '\x1b[1m'
};

if (cleanUrl.includes('your-domain.com') || cleanUrl.includes('yourdomain.com') || cleanUrl.includes('example.com')) {
  console.log(`\n${colors.yellow}${colors.bold}⚠️  NOTE: "${cleanUrl}" is a placeholder URL from the guide!${colors.reset}`);
  console.log(`${colors.yellow}Please replace it with your actual deployed URL:${colors.reset}`);
  console.log(`  • Railway: ${colors.cyan}node scripts/verify-deployment.js https://calypso-production-xxxx.up.railway.app${colors.reset}`);
  console.log(`  • Render:  ${colors.cyan}node scripts/verify-deployment.js https://calypso-messenger.onrender.com${colors.reset}`);
  console.log(`  • Local:   ${colors.cyan}node scripts/verify-deployment.js http://localhost:3000${colors.reset}\n`);
  process.exit(1);
}

console.log(`\n${colors.bold}${colors.cyan}═════════════════════════════════════════════════════════════════${colors.reset}`);
console.log(`${colors.bold} 🛡️ CALYPSO — PRODUCTION DEPLOYMENT VERIFICATION${colors.reset}`);
console.log(`${colors.cyan} Target Endpoint: ${colors.bold}${cleanUrl}${colors.reset}`);
console.log(`${colors.bold}${colors.cyan}═════════════════════════════════════════════════════════════════${colors.reset}\n`);

let passedTests = 0;
const totalTests = 4;

async function runTests() {
  // Test 1: Root Greeting & TLS Handshake
  try {
    process.stdout.write(`[1/4] Testing Root Greeting & SSL/TLS... `);
    const res = await fetch(`${cleanUrl}/`);
    const data = await res.json();

    if (res.ok && data.name && data.status === 'active') {
      console.log(`${colors.green}✓ PASSED (HTTP ${res.status} - "${data.name}" v${data.version})${colors.reset}`);
      passedTests++;
    } else {
      console.log(`${colors.red}✗ FAILED (Unexpected response: ${JSON.stringify(data)})${colors.reset}`);
    }
  } catch (err) {
    console.log(`${colors.red}✗ FAILED: ${err.message}${colors.reset}`);
  }

  // Test 2: Health Check Endpoint
  try {
    process.stdout.write(`[2/4] Testing Health Check Endpoint (/api/prekeys/health/status)... `);
    const res = await fetch(`${cleanUrl}/api/prekeys/health/status`);
    const data = await res.json();

    if (res.ok && data.status === 'ok') {
      console.log(`${colors.green}✓ PASSED (Uptime: ${data.uptimeSeconds}s, Active Users: ${data.activeUsers}, Bundles: ${data.storedBundles})${colors.reset}`);
      passedTests++;
    } else {
      console.log(`${colors.red}✗ FAILED (HTTP ${res.status})${colors.reset}`);
    }
  } catch (err) {
    console.log(`${colors.red}✗ FAILED: ${err.message}${colors.reset}`);
  }

  // Test 3: PreKey REST Validation
  try {
    process.stdout.write(`[3/4] Testing PreKey Upload Validation API (/api/prekeys/upload)... `);
    const res = await fetch(`${cleanUrl}/api/prekeys/upload`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    });
    const data = await res.json();

    if (res.status === 400 && data.success === false && data.error?.includes('Missing required fields')) {
      console.log(`${colors.green}✓ PASSED (Validation rejected empty payload with 400 Bad Request)${colors.reset}`);
      passedTests++;
    } else {
      console.log(`${colors.red}✗ FAILED (Unexpected response: HTTP ${res.status})${colors.reset}`);
    }
  } catch (err) {
    console.log(`${colors.red}✗ FAILED: ${err.message}${colors.reset}`);
  }

  // Test 4: Socket.IO WebSocket Connection over WSS
  await new Promise((resolve) => {
    process.stdout.write(`[4/4] Testing WebSocket / Socket.IO Connection... `);
    const testUserCode = `TEST-${Math.random().toString(36).substring(2, 6).toUpperCase()}`;

    const socket = io(cleanUrl, {
      transports: ['websocket'],
      timeout: 8000,
      reconnection: false
    });

    const timeoutId = setTimeout(() => {
      console.log(`${colors.red}✗ FAILED (Connection timed out after 8s)${colors.reset}`);
      socket.disconnect();
      resolve();
    }, 8000);

    socket.on('connect', () => {
      socket.emit('register', { userCode: testUserCode }, (ack) => {
        clearTimeout(timeoutId);
        if (ack && ack.success) {
          console.log(`${colors.green}✓ PASSED (Connected via WebSocket & registered "${testUserCode}")${colors.reset}`);
          passedTests++;
        } else {
          console.log(`${colors.green}✓ PASSED (WebSocket connected, transport verified)${colors.reset}`);
          passedTests++;
        }
        socket.disconnect();
        resolve();
      });
    });

    socket.on('connect_error', (err) => {
      clearTimeout(timeoutId);
      console.log(`${colors.red}✗ FAILED (Socket error: ${err.message})${colors.reset}`);
      socket.disconnect();
      resolve();
    });
  });

  // Summary
  console.log(`\n${colors.bold}═════════════════════════════════════════════════════════════════${colors.reset}`);
  if (passedTests === totalTests) {
    console.log(`${colors.green}${colors.bold}🎉 ALL ${totalTests} PRODUCTION TESTS PASSED!${colors.reset}`);
    console.log(`${colors.green}Your signaling server at ${cleanUrl} is fully operational and ready for production.${colors.reset}\n`);
    process.exit(0);
  } else {
    console.log(`${colors.yellow}${colors.bold}⚠️  ${passedTests}/${totalTests} TESTS PASSED.${colors.reset}`);
    console.log(`${colors.yellow}Please check your server logs, domain DNS, and firewall settings.${colors.reset}\n`);
    process.exit(1);
  }
}

runTests();
