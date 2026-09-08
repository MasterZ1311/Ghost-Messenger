# Google Play Console — Data Safety Declaration Guide

This document provides exact responses for completing the mandatory **Data Safety** questionnaire in the Google Play Console for **Calypso**.

---

## 1. Data Collection and Sharing Overview

### Question: Does your app collect or share any of the required user data types?
- **Response**: **No**  
*(Calypso does not collect or share personal user data with servers or third parties. All message payloads are encrypted end-to-end and routed directly between devices via peer-to-peer WebRTC DataChannels).*

### Question: Is all of the user data collected by your app encrypted in transit?
- **Response**: **Yes**  
*(All communication uses TLS 1.3 for signaling negotiation and end-to-end encryption via the Signal Protocol Double Ratchet for peer messaging).*

### Question: Do you provide a way for users to request that their data be deleted?
- **Response**: **Yes**  
*(Users can permanently purge all cryptographic keys, sessions, and message history locally at any time via the in-app reset function or by uninstalling the application. No remote copies exist).*

---

## 2. Detailed Data Category Breakdown

| Data Category | Specific Data Type | Collected? | Shared? | Processing / Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Location** | Approximate or Precise Location | **No** | **No** | Not processed. |
| **Personal Info** | Name, Email, Phone Number, User IDs | **No** | **No** | No account creation or personal identifiers required. |
| **Financial Info** | Payment info, purchase history, credit score | **No** | **No** | No monetary transactions or in-app billing. |
| **Health & Fitness**| Health/fitness info | **No** | **No** | Not processed. |
| **Messages** | Emails, SMS, in-app messages | **No** | **No** | Message content is encrypted on-device via Signal Protocol. The server never has access to plaintext or ciphertext messages. |
| **Photos & Videos** | Photos or videos | **No** | **No** | Not collected. |
| **Audio Files** | Voice or sound recordings | **No** | **No** | Not collected. |
| **Files & Docs** | Files or documents | **No** | **No** | Not collected. |
| **Calendar** | Calendar events | **No** | **No** | Not collected. |
| **Contacts** | Contact lists / address book | **No** | **No** | Calypso does not access or upload device contacts. |
| **App Activity** | App interactions, in-app search, installed apps | **No** | **No** | Zero telemetry or behavioral tracking. |
| **Web Browsing** | Web browsing history | **No** | **No** | Not collected. |
| **App Info & Performance** | Crash logs, diagnostics, performance metrics | **No** | **No** | No third-party crash reporting or analytics SDKs. |
| **Device or Other IDs** | Device ID, advertising ID, MAC address | **No** | **No** | No device tracking identifiers are collected. |

---

## 3. Security Practices

### Cryptographic Implementation
- **In-Transit Encryption**: All signaling connections operate over secure WebSockets (WSS/TLS). Peer messaging transport utilizes WebSockets/WebRTC DataChannels with DTLS-SRTP and Signal Protocol Double Ratchet payload encryption.
- **At-Rest Encryption**: All locally persisted state (database entries, pre-keys, session records) is secured using SQLCipher (256-bit AES encryption) with keys stored in the Android Keystore (`flutter_secure_storage`).

### Account Deletion Mechanism
- Because Calypso does not maintain user accounts or cloud storage, selecting the in-app reset option immediately destroys the local cryptographic identity and all encrypted message tables, leaving zero residual data.

---

## 4. Google Play Policy Declarations

### Target Audience and Content
- **Target Age Group**: 18 and older (or 13+ depending on regional distribution preferences).
- **Appeal to Children**: No.

### News and Government Status
- **Is this a news app?**: No.
- **Is this a COVID-19 tracing app?**: No.
- **Is this a government app?**: No.

### Financial and Loan Services
- **Does this app provide financial features?**: No.

### Advertising ID Declaration
- **Does your app use Advertising ID?**: **No**  
*(Calypso contains no advertising libraries or Google Mobile Ads SDKs).*
