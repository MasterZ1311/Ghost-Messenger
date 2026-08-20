# Google Play Store Listing Metadata

## 1. Core Listing Details

### Application Title
**Ghost Messenger** (15 / 30 characters)

### Short Description
**Private peer-to-peer encrypted messaging without phone numbers or emails.** (76 / 80 characters)

### Full Description
Ghost Messenger is a privacy-first, zero-knowledge peer-to-peer (P2P) messaging application engineered for private, verifiable communications.

COMMUNICATION WITHOUT COMPROMISE
Unlike conventional messaging platforms that require phone numbers, email addresses, or central account registrations, Ghost Messenger operates without personal identifiers. Your identity is derived solely from a 12-word cryptographic seed phrase that you control.

CORE CAPABILITIES

1. Self-Sovereign Identity
Create a cryptographic identity locally on your device. Your public identity generates an 8-character alphanumeric User Code. Share only this code to establish a connection.

2. True Peer-to-Peer Architecture
Chat messages flow directly between devices over WebRTC DataChannels. There are no central message storage databases or intermediate application relays processing message payloads.

3. Signal Protocol End-to-End Encryption
Every conversation is protected by the Double Ratchet Algorithm and Extended Triple Diffie-Hellman (X3DH) key agreement. This architecture ensures Forward Secrecy and Post-Compromise Security.

4. Encrypted Local Storage
All messages, encryption keys, and session records stored on your device are encrypted at rest using SQLCipher (256-bit AES encryption) with master keys backed by hardware-backed keystores.

5. Cryptographic Safety Number Verification
Verify the authenticity of your communication channel with visual and numeric 60-digit fingerprints to ensure protection against adversary-in-the-middle attacks.

6. Zero Telemetry and Zero Logs
Ghost Messenger does not collect device analytics, tracking identifiers, contact lists, or usage telemetry.

PERMISSIONS AND SYSTEM USAGE
- Network Access: Required to establish signaling connections and peer-to-peer data transport.
- Camera and Audio (Optional): Utilized solely for media streaming over WebRTC channels.
- Notifications: Used to alert you to incoming connection requests.

TECHNICAL SPECIFICATIONS
- Protocol: Signal Protocol (libsignal-protocol-dart)
- Transport: WebRTC (RFC 8831 DataChannels)
- Database: SQLCipher AES-256
- Identity Derivation: BIP39 / RFC 5869 HKDF-SHA-256

---

## 2. Categorization and Tags

- **Application Category**: Communication
- **Secondary Category**: Productivity / Security
- **Tags / Keywords**:
  - Encrypted Messaging
  - Peer to Peer
  - Signal Protocol
  - Privacy
  - WebRTC
  - Secure Chat
  - Zero Knowledge
  - End to End Encryption

---

## 3. Contact and Support Information

- **Support Email**: support@ghostmessenger.org (or your designated support address)
- **Website**: https://ghostmessenger.org
- **Privacy Policy URL**: https://ghostmessenger.org/privacy-policy

---

## 4. Graphic Asset Requirements

### App Icon
- Format: 32-bit PNG (with alpha)
- Dimensions: 512 px by 512 px
- Maximum File Size: 1024 KB

### Feature Graphic
- Format: JPEG or 24-bit PNG (no alpha)
- Dimensions: 1024 px width by 500 px height
- Recommendation: Dark visual theme displaying the Ghost Messenger shield emblem and the title "Ghost Messenger - Zero-Knowledge P2P Messaging".

### Phone Screenshots
- Quantity: Minimum 4 screenshots, Maximum 8 screenshots
- Format: JPEG or 24-bit PNG (no alpha)
- Minimum Dimension: 1080 px
- Recommended Resolutions: 1080 x 2400 px or 1440 x 3120 px (16:9 or 20:9 aspect ratio)
- Recommended Screens:
  1. Onboarding Screen: "Generate Cryptographic Identity - No Phone or Email"
  2. Backup Screen: "12-Word BIP39 Passphrase Recovery"
  3. Home Hub: "Permanent UserCode and Active Sessions"
  4. Encrypted Chat: "Direct P2P DataChannel Communication"
  5. Security Verification: "60-Digit Cryptographic Safety Numbers"
