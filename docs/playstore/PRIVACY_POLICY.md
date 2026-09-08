# Privacy Policy for Calypso

**Effective Date**: August 20, 2026  
**Last Updated**: September 7, 2026

## 1. Introduction

Calypso ("we," "our," or "us") is dedicated to protecting user privacy through cryptographic design. Calypso is engineered as a zero-knowledge, peer-to-peer (P2P) messaging application. This Privacy Policy details our technical architecture, data handling practices, and our commitment to ensuring that user communication remains private and confidential.

By downloading, installing, or utilizing Calypso, you acknowledge the terms outlined in this Privacy Policy.

---

## 2. Fundamental Architectural Principles

Calypso operates on three foundational privacy tenets:

1. **Zero Personally Identifiable Information (PII)**: We do not require, collect, or store phone numbers, email addresses, usernames, device contacts, or government-issued identifiers.
2. **End-to-End Encryption (E2EE)**: All communications are encrypted end-to-end using the Signal Protocol. We do not possess cryptographic decryption keys and cannot access message contents under any circumstance.
3. **Decentralized Data Transport**: Message exchange occurs directly between client devices over WebRTC DataChannels, bypassing central message storage systems.

---

## 3. Information We Do Not Collect

We strictly abstain from collecting the following categories of data:
- User identity records (names, phone numbers, email addresses, physical addresses).
- Device address books or contact lists.
- Chat message contents, attachments, or media transmissions.
- Geolocation data or GPS tracking information.
- Advertising identifiers, tracking cookies, or behavioral telemetry.
- Crash reports containing user identifiers.

---

## 4. Information Handled During Operation

To broker connections between devices, the following technical data is processed transiently:

### A. Cryptographic Identity
- **Public Identity Key**: Generated locally on your device via deterministic BIP39 seed derivation.
- **Derived User Code**: An 8-character public representation (e.g., `5J9L-2P4X`) derived from your public identity key, used solely to identify your device on the signaling network.
- **PreKey Bundles**: Public cryptographic keys published to our signaling server to allow other users to establish encrypted sessions via the Extended Triple Diffie-Hellman (X3DH) protocol.

### B. Ephemeral Signaling Data
- **Session Description Protocol (SDP)**: Metadata necessary to establish direct WebRTC peer connections.
- **Interactive Connectivity Establishment (ICE) Candidates**: IP addresses and port numbers exchanged between connecting peers solely for the duration required to negotiate network traversal.
- **Signal Buffering**: In the event that a recipient is momentarily disconnected, signaling handshake envelopes are temporarily retained in volatile server memory for a maximum duration of thirty (30) seconds before automatic purging.

### C. Push Notification Tokens (Optional)
- If push notifications are enabled, a randomized platform token (Firebase Cloud Messaging / Apple Push Notification service) is associated transiently with your User Code solely to wake your device for incoming peer connection requests. These tokens contain no personal identifiers.

---

## 5. Local Storage and Data at Rest

- **SQLCipher Encryption**: All message history, cryptographic keys, trusted identity mappings, and session state stored locally on your device are encrypted using SQLCipher (256-bit AES encryption in CBC mode).
- **Master Key Security**: The master encryption key is generated on-device and secured within your operating system's hardware-backed credential storage (Android Keystore / iOS Keychain).
- **Data Deletion**: Uninstalling the application or selecting the in-app account reset function permanently deletes the local database and cryptographic keys. Because no cloud backups exist, deleted data cannot be recovered.

---

## 6. Third-Party Services and Infrastructure

- **STUN/TURN Infrastructure**: Standard STUN servers (e.g., Google STUN) and self-hosted TURN relays are utilized solely for NAT traversal and packet routing when direct peer-to-peer connection is obstructed by network firewalls. Relays process encrypted packets and cannot inspect the payload.
- **Third-Party Analytics**: Calypso incorporates zero commercial advertising networks, third-party analytics SDKs, or behavioral trackers.

---

## 7. Compliance with Global Regulations

### A. General Data Protection Regulation (GDPR - EEA / UK)
Under the GDPR, we process minimal technical connection data under the legal basis of contractual necessity to facilitate peer-to-peer communication. Because we do not associate technical identifiers with natural persons, our system operates on anonymized cryptographic tokens.

### B. California Consumer Privacy Act (CCPA / CPRA)
We do not sell, share, or monetize personal information as defined under the CCPA/CPRA.

### C. Children's Online Privacy Protection Act (COPPA)
Calypso does not knowingly collect or solicit personal information from children under the age of 13.

---

## 8. Changes to This Privacy Policy

We reserve the right to revise this Privacy Policy to reflect changes in legal requirements or technical enhancements. Updated versions will be published on our official repository and website with a revised effective date.

---

## 9. Contact and Inquiries

For technical inquiries or questions regarding our cryptographic implementation and privacy practices, please contact:

- **Entity**: Calypso Project
- **Email**: privacy@calypso.chat
- **Repository**: https://github.com/MasterZ1311/Ghost-Messenger
