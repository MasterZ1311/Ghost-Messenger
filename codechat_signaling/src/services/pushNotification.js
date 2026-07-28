'use strict';

const pushTokenStore = require('./pushTokenStore');
const logger = require('../utils/logger');

/**
 * Service for managing FCM / APNs Push Notifications for device wakeup.
 */
class PushNotificationService {
  constructor() {
    /** @type {Array<{ toCode: string, token: string, payload: object, sentAt: number }>} */
    this.sentDispatches = [];
    /** @type {((dispatch: object) => Promise<boolean> | boolean) | null} */
    this.customDispatcher = null;
  }

  /**
   * Set a custom provider dispatcher (e.g. Firebase Admin SDK or APNs client)
   * @param {(dispatch: { toCode: string, token: string, payload: object, platform: string }) => Promise<boolean> | boolean} dispatcher 
   */
  setDispatcher(dispatcher) {
    this.customDispatcher = dispatcher;
  }

  /**
   * Constructs a high-priority push notification payload for background wakeup.
   * @param {string} fromCode 
   * @param {object} signalData 
   * @returns {object}
   */
  buildWakeupPayload(fromCode, signalData = {}) {
    return {
      priority: 'high',
      contentAvailable: true,
      data: {
        type: 'connection_request',
        fromCode: fromCode,
        signalType: signalData.type || 'offer',
        timestamp: Date.now().toString(),
      },
      notification: {
        title: 'Incoming Encrypted Connection',
        body: `${fromCode} is attempting to establish a P2P session`,
      },
    };
  }

  /**
   * Dispatches a background wakeup push notification to recipient device.
   * @param {string} toCode 
   * @param {string} fromCode 
   * @param {object} [signalData={}] 
   * @returns {Promise<{ sent: boolean, reason?: string, dispatch?: object }>}
   */
  async sendWakeupNotification(toCode, fromCode, signalData = {}) {
    const record = pushTokenStore.getToken(toCode);
    if (!record || !record.token) {
      logger.debug({ toCode }, 'push wakeup skipped: no push token registered');
      return { sent: false, reason: 'no_token' };
    }

    const payload = this.buildWakeupPayload(fromCode, signalData);
    const dispatch = {
      toCode: toCode.trim(),
      token: record.token,
      platform: record.platform,
      payload,
      sentAt: Date.now(),
    };

    let sent = true;
    if (this.customDispatcher) {
      try {
        const result = await this.customDispatcher(dispatch);
        sent = result !== false;
      } catch (err) {
        logger.error({ err, toCode }, 'error executing custom push dispatcher');
        sent = false;
      }
    }

    if (sent) {
      this.sentDispatches.push(dispatch);
      logger.info({ toCode, fromCode, platform: record.platform }, 'push wakeup notification dispatched');
    }

    return { sent, dispatch };
  }

  /**
   * Helper for tests to clear dispatch history.
   */
  clearHistory() {
    this.sentDispatches = [];
  }
}

module.exports = new PushNotificationService();
