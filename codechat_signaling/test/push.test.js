'use strict';

const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert');
const http = require('http');
const { io: ioClient } = require('socket.io-client');

const { createApp } = require('../src/app');
const { createSocketServer } = require('../src/socket');
const messageQueue = require('../src/services/messageQueue');
const pushTokenStore = require('../src/services/pushTokenStore');
const pushNotification = require('../src/services/pushNotification');

let httpServer;
let io;
let baseUrl;

function connectClient() {
  return ioClient(baseUrl, {
    transports: ['websocket'],
    forceNew: true,
    reconnection: false,
  });
}

function waitFor(socket, event, timeoutMs = 2000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`timeout waiting for "${event}"`)),
      timeoutMs
    );
    socket.once(event, (data) => {
      clearTimeout(timer);
      resolve(data);
    });
  });
}

before(async () => {
  const app = createApp();
  httpServer = http.createServer(app);
  io = createSocketServer(httpServer);
  messageQueue.start();
  await new Promise((resolve) => httpServer.listen(0, '127.0.0.1', resolve));
  const { port } = httpServer.address();
  baseUrl = `http://127.0.0.1:${port}`;
});

after(async () => {
  messageQueue.stop();
  await new Promise((resolve) => io.close(resolve));
  await new Promise((resolve) => httpServer.close(resolve));
});

beforeEach(() => {
  pushTokenStore.clear();
  pushNotification.clearHistory();
});

test('PushTokenStore registers and retrieves tokens', () => {
  pushTokenStore.registerToken('USER_A1', 'fcm_token_123', 'android');
  const record = pushTokenStore.getToken('USER_A1');
  assert.ok(record);
  assert.strictEqual(record.token, 'fcm_token_123');
  assert.strictEqual(record.platform, 'android');

  pushTokenStore.removeToken('USER_A1');
  assert.strictEqual(pushTokenStore.getToken('USER_A1'), null);
});

test('POST /api/push-token and DELETE /api/push-token endpoints', async () => {
  // Register token via HTTP
  const res = await fetch(`${baseUrl}/api/push-token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userCode: 'PUSHUSER1',
      token: 'apns_token_xyz',
      platform: 'ios',
    }),
  });
  assert.strictEqual(res.status, 200);
  const body = await res.json();
  assert.strictEqual(body.status, 'ok');

  const stored = pushTokenStore.getToken('PUSHUSER1');
  assert.ok(stored);
  assert.strictEqual(stored.token, 'apns_token_xyz');
  assert.strictEqual(stored.platform, 'ios');

  // Delete token via HTTP
  const delRes = await fetch(`${baseUrl}/api/push-token`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userCode: 'PUSHUSER1' }),
  });
  assert.strictEqual(delRes.status, 200);
  assert.strictEqual(pushTokenStore.getToken('PUSHUSER1'), null);
});

test('POST /api/push-token rejects missing parameters', async () => {
  const res = await fetch(`${baseUrl}/api/push-token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userCode: 'PUSHUSER1' }),
  });
  assert.strictEqual(res.status, 400);
});

test('Socket event register_push_token saves push token', async () => {
  const client = connectClient();
  try {
    await waitFor(client, 'connect');
    client.emit('join', 'PUSH_CLIENT1');
    await waitFor(client, 'joined');

    const promise = waitFor(client, 'push_token_registered');
    client.emit('register_push_token', { token: 'socket_fcm_token', platform: 'android' });

    const result = await promise;
    assert.strictEqual(result.status, 'ok');

    const stored = pushTokenStore.getToken('PUSH_CLIENT1');
    assert.ok(stored);
    assert.strictEqual(stored.token, 'socket_fcm_token');
  } finally {
    client.close();
  }
});

test('Dispatches FCM/APNs wakeup notification when sending offer to offline peer', async () => {
  // Pre-register token for offline target user
  pushTokenStore.registerToken('TARGET_OFFLINE', 'target_fcm_token_999', 'android');

  const sender = connectClient();
  try {
    await waitFor(sender, 'connect');
    sender.emit('join', 'CALLER_1');
    await waitFor(sender, 'joined');

    // Caller sends WebRTC offer to TARGET_OFFLINE
    sender.emit('signal', {
      toCode: 'TARGET_OFFLINE',
      fromCode: 'CALLER_1',
      signalData: { type: 'offer', sdp: 'v=0\r\no=- 12345...' },
    });

    await waitFor(sender, 'peer_status');

    // Give a brief tick for async push promise
    await new Promise((r) => setTimeout(r, 50));

    assert.strictEqual(pushNotification.sentDispatches.length, 1);
    const dispatch = pushNotification.sentDispatches[0];
    assert.strictEqual(dispatch.toCode, 'TARGET_OFFLINE');
    assert.strictEqual(dispatch.token, 'target_fcm_token_999');
    assert.strictEqual(dispatch.payload.data.type, 'connection_request');
    assert.strictEqual(dispatch.payload.data.fromCode, 'CALLER_1');
  } finally {
    sender.close();
  }
});
