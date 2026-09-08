import { describe, it, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import http from 'http';
import { Server } from 'socket.io';
import { io as Client } from 'socket.io-client';
import { createApp } from '../src/app.js';
import { setupSignalingHandlers } from '../src/sockets/signalingHandler.js';
import { InMemoryPreKeyStore } from '../src/store/InMemoryPreKeyStore.js';
import { PresenceManager } from '../src/store/PresenceManager.js';

describe('Calypso Ephemeral Signaling Server Test Suite', () => {

  describe('InMemoryPreKeyStore Unit Tests', () => {
    let store;

    beforeEach(() => {
      store = new InMemoryPreKeyStore();
    });

    it('should upload and retrieve prekey bundles', () => {
      const mockBundle = {
        identityKey: 'base64IdentityKey',
        registrationId: 1337,
        signedPreKey: { keyId: 1, publicKey: 'base64SignedKey', signature: 'base64Sig' },
        preKeys: [
          { keyId: 1, publicKey: 'preKey1' },
          { keyId: 2, publicKey: 'preKey2' }
        ]
      };

      const result = store.upload('5jkl-2p4x', mockBundle);
      assert.equal(result.userCode, '5JKL-2P4X');
      assert.equal(result.preKeyCount, 2);

      // First fetch consumes prekey 1
      const bundle1 = store.fetchAndConsume('5JKL-2P4X');
      assert.ok(bundle1);
      assert.equal(bundle1.identityKey, 'base64IdentityKey');
      assert.equal(bundle1.preKey.keyId, 1);
      assert.equal(bundle1.remainingPreKeys, 1);

      // Second fetch consumes prekey 2
      const bundle2 = store.fetchAndConsume('5JKL-2P4X');
      assert.equal(bundle2.preKey.keyId, 2);
      assert.equal(bundle2.remainingPreKeys, 0);

      // Third fetch has no one-time prekey remaining (falls back to signedPreKey only)
      const bundle3 = store.fetchAndConsume('5JKL-2P4X');
      assert.equal(bundle3.preKey, null);
      assert.equal(bundle3.remainingPreKeys, 0);
    });

    it('should evict expired bundles', () => {
      const storeWithShortTtl = new InMemoryPreKeyStore(50); // 50ms TTL
      storeWithShortTtl.upload('5JKL-2P4X', {
        identityKey: 'key',
        signedPreKey: { keyId: 1 },
        preKeys: []
      });

      assert.equal(storeWithShortTtl.size(), 1);

      // Wait 60ms
      return new Promise((resolve) => {
        setTimeout(() => {
          const evicted = storeWithShortTtl.evictExpired(50);
          assert.equal(evicted, 1);
          assert.equal(storeWithShortTtl.size(), 0);
          resolve();
        }, 60);
      });
    });
  });

  describe('PresenceManager Unit Tests', () => {
    let presence;

    beforeEach(() => {
      presence = new PresenceManager();
    });

    it('should register and track online presence', () => {
      presence.register('5jkl-2p4x', 'socket_1');
      assert.equal(presence.isOnline('5JKL-2P4X'), true);
      assert.equal(presence.getUserCode('socket_1'), '5JKL-2P4X');
      assert.deepEqual(presence.getSockets('5JKL-2P4X'), ['socket_1']);

      presence.unregister('socket_1');
      assert.equal(presence.isOnline('5JKL-2P4X'), false);
      assert.equal(presence.getUserCode('socket_1'), null);
    });
  });

  describe('Full Server REST & Socket.IO Integration Tests', () => {
    let server;
    let io;
    let preKeyStore;
    let presenceManager;
    let serverUrl;
    let port;

    before(() => {
      return new Promise((resolve) => {
        const appSetup = createApp();
        preKeyStore = appSetup.preKeyStore;
        presenceManager = appSetup.presenceManager;

        server = http.createServer(appSetup.app);
        io = new Server(server, { cors: { origin: '*' } });
        setupSignalingHandlers(io, presenceManager);

        server.listen(0, '127.0.0.1', () => {
          port = server.address().port;
          serverUrl = `http://127.0.0.1:${port}`;
          resolve();
        });
      });
    });

    after(() => {
      return new Promise((resolve) => {
        io.close();
        server.close(resolve);
      });
    });

    it('REST: Health check returns active status', async () => {
      const res = await fetch(`${serverUrl}/api/prekeys/health/status`);
      assert.equal(res.status, 200);
      const data = await res.json();
      assert.equal(data.status, 'ok');
      assert.equal(typeof data.uptimeSeconds, 'number');
    });

    it('REST: Upload PreKey bundle and retrieve it', async () => {
      const uploadPayload = {
        userCode: '5JKL-2P4X',
        bundle: {
          identityKey: 'ALICE_IDENTITY_KEY_BASE64',
          registrationId: 4321,
          signedPreKey: {
            keyId: 1,
            publicKey: 'ALICE_SIGNED_PREKEY_BASE64',
            signature: 'ALICE_SIGNATURE_BASE64'
          },
          preKeys: [
            { keyId: 1, publicKey: 'ALICE_ONE_TIME_1' },
            { keyId: 2, publicKey: 'ALICE_ONE_TIME_2' }
          ]
        }
      };

      const uploadRes = await fetch(`${serverUrl}/api/prekeys/upload`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(uploadPayload)
      });

      assert.equal(uploadRes.status, 200);
      const uploadData = await uploadRes.json();
      assert.equal(uploadData.success, true);
      assert.equal(uploadData.preKeyCount, 2);

      // Fetch bundle
      const fetchRes = await fetch(`${serverUrl}/api/prekeys/5JKL-2P4X`);
      assert.equal(fetchRes.status, 200);
      const fetchData = await fetchRes.json();
      assert.equal(fetchData.success, true);
      assert.equal(fetchData.bundle.identityKey, 'ALICE_IDENTITY_KEY_BASE64');
      assert.equal(fetchData.bundle.preKey.keyId, 1);
    });

    it('Socket.IO: Full WebRTC SDP & ICE Candidate Signaling Flow', (done) => {
      const aliceClient = Client(serverUrl, { reconnection: false });
      const bobClient = Client(serverUrl, { reconnection: false });

      let aliceRegistered = false;
      let bobRegistered = false;

      const checkBothReady = () => {
        if (!aliceRegistered || !bobRegistered) return;

        // 1. Alice checks Bob presence
        aliceClient.emit('check-presence', { targetUserCode: 'WXYZ-2345' }, (presenceRes) => {
          assert.equal(presenceRes.online, true);

          // 2. Alice sends WebRTC Offer to Bob
          const mockOffer = { type: 'offer', sdp: 'v=0...alice_offer_sdp...' };
          aliceClient.emit('webrtc-offer', {
            targetUserCode: 'WXYZ-2345',
            offer: mockOffer
          });
        });
      };

      aliceClient.on('connect', () => {
        aliceClient.emit('register', { userCode: '5JKL-2P4X' }, (res) => {
          assert.equal(res.success, true);
          aliceRegistered = true;
          checkBothReady();
        });
      });

      bobClient.on('connect', () => {
        bobClient.emit('register', { userCode: 'WXYZ-2345' }, (res) => {
          assert.equal(res.success, true);
          bobRegistered = true;
          checkBothReady();
        });
      });

      // Bob listens for Offer from Alice
      bobClient.on('webrtc-offer', (data) => {
        assert.equal(data.fromUserCode, '5JKL-2P4X');
        assert.equal(data.offer.type, 'offer');

        // Bob replies with SDP Answer
        const mockAnswer = { type: 'answer', sdp: 'v=0...bob_answer_sdp...' };
        bobClient.emit('webrtc-answer', {
          targetUserCode: '5JKL-2P4X',
          answer: mockAnswer
        });

        // Bob sends ICE Candidate
        bobClient.emit('ice-candidate', {
          targetUserCode: '5JKL-2P4X',
          candidate: { candidate: 'candidate:1 1 UDP...', sdpMid: '0', sdpMLineIndex: 0 }
        });
      });

      // Alice listens for Answer from Bob
      aliceClient.on('webrtc-answer', (data) => {
        assert.equal(data.fromUserCode, 'WXYZ-2345');
        assert.equal(data.answer.type, 'answer');
      });

      // Alice listens for ICE Candidate from Bob
      aliceClient.on('ice-candidate', (data) => {
        assert.equal(data.fromUserCode, 'WXYZ-2345');
        assert.equal(data.candidate.sdpMid, '0');

        // 3. Ephemeral Relay fallback test
        aliceClient.emit('encrypted-envelope', {
          targetUserCode: 'WXYZ-2345',
          envelope: 'CIPHERTEXT_BASE64_PAYLOAD'
        }, (ack) => {
          assert.equal(ack.delivered, true);
        });
      });

      // Bob receives ephemeral relay message
      bobClient.on('encrypted-envelope', (data) => {
        assert.equal(data.fromUserCode, '5JKL-2P4X');
        assert.equal(data.envelope, 'CIPHERTEXT_BASE64_PAYLOAD');

        // Cleanup
        aliceClient.disconnect();
        bobClient.disconnect();
        done();
      });
    });
  });
});
