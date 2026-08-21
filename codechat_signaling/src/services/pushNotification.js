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
        fromCode: fromCode,           // kept in data payload for app routing
        signalType: signalData.type || 'offer',
        timestamp: Date.now().toString(),
      },
      notification: {
        title: 'Incoming Encrypted Connection',
        // Do NOT include fromCode in the notification body — it would leak
        // sender metadata to the lock screen and notification tray.
        body: 'You have an incoming encrypted connection request',
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
      // Cap dispatch history to avoid unbounded memory growth in long-running servers.
      const MAX_DISPATCH_HISTORY = 500;
      this.sentDispatches.push(dispatch);
      if (this.sentDispatches.length > MAX_DISPATCH_HISTORY) {
        this.sentDispatches.shift();
      }
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
