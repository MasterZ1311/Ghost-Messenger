'use strict';

const { test, before, after } = require('node:test');
const assert = require('node:assert');
const http = require('http');
const { io: ioClient } = require('socket.io-client');

const { createApp } = require('../src/app');
const { createSocketServer } = require('../src/socket');
const messageQueue = require('../src/services/messageQueue');

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

test('HTTP /health returns ok', async () => {
  const res = await fetch(`${baseUrl}/health`);
  assert.strictEqual(res.status, 200);
  const body = await res.json();
  assert.strictEqual(body.status, 'ok');
});

test('GET /api/ice-servers returns STUN servers', async () => {
  const res = await fetch(`${baseUrl}/api/ice-servers`);
  assert.strictEqual(res.status, 200);
  const body = await res.json();
  assert.ok(Array.isArray(body.iceServers));
  assert.ok(body.iceServers.length >= 1);
  assert.ok(body.iceServers.some((s) => String(s.urls).includes('stun:')));
});

test('GET /api/ice-servers rejects invalid userCode', async () => {
  const res = await fetch(`${baseUrl}/api/ice-servers?userCode=bad%20code%21`);
  assert.strictEqual(res.status, 400);
});

test('join then relay signal between two peers', async () => {
  const alice = connectClient();
  const bob = connectClient();

  try {
    await waitFor(alice, 'connect');
    await waitFor(bob, 'connect');

    alice.emit('join', 'ALICE1');
    bob.emit('join', 'BOB2');
    await waitFor(alice, 'joined');
    await waitFor(bob, 'joined');

    const received = waitFor(bob, 'signal');
    alice.emit('signal', {
      toCode: 'BOB2',
      fromCode: 'ALICE1',
      signalData: { type: 'offer', sdp: 'fake-sdp' },
    });

    const envelope = await received;
    assert.strictEqual(envelope.fromCode, 'ALICE1');
    assert.strictEqual(envelope.signalData.type, 'offer');
    assert.strictEqual(envelope.signalData.sdp, 'fake-sdp');
  } finally {
    alice.close();
    bob.close();
  }
});

test('signal with spoofed fromCode is rejected', async () => {
  const mallory = connectClient();
  try {
    await waitFor(mallory, 'connect');
    mallory.emit('join', 'MALLORY');
    await waitFor(mallory, 'joined');

    const err = waitFor(mallory, 'error_message');
    mallory.emit('signal', {
      toCode: 'BOB2',
      fromCode: 'SOMEONE_ELSE',
      signalData: { type: 'offer', sdp: 'x' },
    });
    const msg = await err;
    assert.strictEqual(msg.code, 403);
  } finally {
    mallory.close();
  }
});

test('signaling before join is rejected', async () => {
  const ghost = connectClient();
  try {
    await waitFor(ghost, 'connect');
    const err = waitFor(ghost, 'error_message');
    ghost.emit('signal', {
      toCode: 'BOB2',
      fromCode: 'GHOST',
      signalData: { type: 'offer', sdp: 'x' },
    });
    const msg = await err;
    assert.strictEqual(msg.code, 401);
  } finally {
    ghost.close();
  }
});

test('invalid UserCode on join is rejected', async () => {
  const client = connectClient();
  try {
    await waitFor(client, 'connect');
    const err = waitFor(client, 'error_message');
    client.emit('join', 'a'); // too short
    const msg = await err;
    assert.strictEqual(msg.code, 400);
  } finally {
    client.close();
  }
});

test('offline recipient gets queued signal on later join', async () => {
  const sender = connectClient();
  try {
    await waitFor(sender, 'connect');
    sender.emit('join', 'SENDER1');
    await waitFor(sender, 'joined');

    // Recipient is offline; expect a "queued" status back to sender.
    const status = waitFor(sender, 'peer_status');
    sender.emit('signal', {
      toCode: 'LATECOMER',
      fromCode: 'SENDER1',
      signalData: { type: 'offer', sdp: 'queued-sdp' },
    });
    const statusMsg = await status;
    assert.strictEqual(statusMsg.status, 'queued');

    // Now the recipient connects and should receive the buffered signal.
    const latecomer = connectClient();
    try {
      await waitFor(latecomer, 'connect');
      const signalPromise = waitFor(latecomer, 'signal');
      latecomer.emit('join', 'LATECOMER');
      const envelope = await signalPromise;
      assert.strictEqual(envelope.fromCode, 'SENDER1');
      assert.strictEqual(envelope.signalData.sdp, 'queued-sdp');
    } finally {
      latecomer.close();
    }
  } finally {
    sender.close();
  }
});

test('check_presence acknowledges online status', async () => {
  const a = connectClient();
  const b = connectClient();
  try {
    await waitFor(a, 'connect');
    await waitFor(b, 'connect');
    a.emit('join', 'ONLINE1');
    await waitFor(a, 'joined');

    const result = await new Promise((resolve) => {
      b.emit('check_presence', 'ONLINE1', resolve);
    });
    assert.strictEqual(result.online, true);

    const result2 = await new Promise((resolve) => {
      b.emit('check_presence', 'NOBODY', resolve);
    });
    assert.strictEqual(result2.online, false);
  } finally {
    a.close();
    b.close();
  }
});
