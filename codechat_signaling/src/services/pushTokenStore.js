'use strict';

/**
 * In-memory storage for user push notification tokens (FCM/APNs).
 */
class PushTokenStore {
  constructor() {
    /** @type {Map<string, { token: string, platform: string, updatedAt: number }>} */
    this.tokens = new Map();
  }

  /**
   * Register or update push token for a userCode.
   * @param {string} userCode 
   * @param {string} token 
   * @param {'android' | 'ios' | string} [platform='android'] 
   */
  registerToken(userCode, token, platform = 'android') {
    if (!userCode || !token) return false;
    const code = userCode.trim();
    this.tokens.set(code, {
      token: token.trim(),
      platform: (platform || 'android').toLowerCase(),
      updatedAt: Date.now(),
    });
    return true;
  }

  /**
   * Get push token record for a userCode.
   * @param {string} userCode 
   * @returns {{ token: string, platform: string, updatedAt: number } | null}
   */
  getToken(userCode) {
    if (!userCode) return null;
    return this.tokens.get(userCode.trim()) || null;
  }

  /**
   * Remove token record for a userCode.
   * @param {string} userCode 
   */
  removeToken(userCode) {
    if (!userCode) return false;
    return this.tokens.delete(userCode.trim());
  }

  /**
   * Clear all stored push tokens.
   */
  clear() {
    this.tokens.clear();
  }
}

module.exports = new PushTokenStore();
