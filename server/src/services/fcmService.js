import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import admin from 'firebase-admin';
import { logger } from '../middleware/logger.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class FcmService {
  constructor() {
    this.initialized = false;
    this.fcmTokens = new Map(); // userCode -> string token
    this.initFirebase();
  }

  initFirebase() {
    try {
      const defaultKeyPath = path.resolve(__dirname, '../../serviceAccountKey.json');
      const envKey = process.env.FIREBASE_SERVICE_ACCOUNT;

      let credential = null;
      if (envKey) {
        try {
          const parsed = JSON.parse(envKey);
          credential = admin.credential.cert(parsed);
        } catch {
          logger.warn('fcm_env_parse_failed');
        }
      } else if (fs.existsSync(defaultKeyPath)) {
        credential = admin.credential.cert(defaultKeyPath);
      }

      if (credential) {
        admin.initializeApp({ credential });
        this.initialized = true;
        logger.info('fcm_initialized');
      } else {
        logger.info('fcm_disabled_no_credentials');
      }
    } catch (err) {
      logger.error('fcm_init_error', { error: err.message });
      this.initialized = false;
    }
  }

  /**
   * Registers an FCM token for a specific userCode.
   * @param {string} userCode - 8-character normalized userCode
   * @param {string} token - FCM registration token
   */
  registerToken(userCode, token) {
    if (!userCode || !token || typeof token !== 'string') return;
    this.fcmTokens.set(userCode, token.trim());
    logger.info('fcm_token_registered');
  }

  /**
   * Unregisters an FCM token (e.g. on logout or app reset).
   */
  unregisterToken(userCode) {
    if (userCode) {
      this.fcmTokens.delete(userCode);
    }
  }

  /**
   * Sends a zero-knowledge background wake-up ping to the recipient's device.
   * CRITICAL PRIVACY RULE: Never include message content, sender identity, or private keys.
   *
   * @param {string} targetUserCode
   */
  async sendWakeUpPing(targetUserCode) {
    if (!this.initialized) return false;
    const token = this.fcmTokens.get(targetUserCode);
    if (!token) return false;

    try {
      const payload = {
        token,
        data: {
          type: 'wake_up'
        },
        android: {
          priority: 'high'
        }
      };

      await admin.messaging().send(payload);
      logger.info('fcm_wakeup_sent');
      return true;
    } catch (err) {
      // If token is invalid or expired, prune it
      if (err.code === 'messaging/registration-token-not-registered' ||
          err.code === 'messaging/invalid-registration-token') {
        this.fcmTokens.delete(targetUserCode);
        logger.warn('fcm_token_pruned', { reason: err.code });
      } else {
        logger.warn('fcm_send_failed', { error: err.message });
      }
      return false;
    }
  }
}

export const fcmService = new FcmService();
