import 'dart:convert';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';
import '../core/signal/sqlite_signal_store.dart';
import '../core/crypto/key_manager.dart';

/// Orchestrates Signal Protocol encryption and decryption over the store.
class SignalService {
  final SQLiteSignalStore _store;

  SignalService(this._store);

  SQLiteSignalStore get store => _store;

  /// Checks if an active Signal session exists with the remote peer.
  Future<bool> hasSession(String remoteCode) async {
    final address = SignalProtocolAddress(remoteCode, 1);
    return await _store.containsSession(address);
  }

  /// Exports the local user's PreKey bundle formatted for server upload / distribution.
  Future<Map<String, dynamic>> exportPreKeyBundle(String userCode) async {
    final identityKeyPair = await _store.getIdentityKeyPair();
    final registrationId = await _store.getLocalRegistrationId();

    // Ensure a signed pre-key exists
    final signedPreKeys = await _store.loadSignedPreKeys();
    SignedPreKeyRecord signedPreKey;
    if (signedPreKeys.isNotEmpty) {
      signedPreKey = signedPreKeys.first;
    } else {
      signedPreKey = KeyManager.generateSignedPreKey(identityKeyPair, 1);
      await _store.storeSignedPreKey(signedPreKey.id, signedPreKey);
    }

    // Ensure at least one pre-key exists
    PreKeyRecord? preKey;
    try {
      preKey = await _store.loadPreKey(1);
    } catch (_) {
      final preKeys = KeyManager.generatePreKeys(1, 10);
      for (final pk in preKeys) {
        await _store.storePreKey(pk.id, pk);
      }
      preKey = preKeys.first;
    }

    final identityKeyBytes = identityKeyPair.getPublicKey().serialize();
    final signedPreKeyBytes = signedPreKey.getKeyPair().publicKey.serialize();
    final preKeyBytes = preKey.getKeyPair().publicKey.serialize();

    return {
      'userCode': userCode,
      'registrationId': registrationId,
      'identityKey': base64.encode(identityKeyBytes),
      'signedPreKey': {
        'keyId': signedPreKey.id,
        'publicKey': base64.encode(signedPreKeyBytes),
        'signature': base64.encode(signedPreKey.signature),
      },
      'preKey': {
        'keyId': preKey.id,
        'publicKey': base64.encode(preKeyBytes),
      },
    };
  }

  /// Processes an incoming PreKey bundle map received from the signaling server.
  Future<void> processPreKeyBundleMap(
    String remoteCode,
    Map<String, dynamic> bundleMap,
  ) async {
    final registrationId = bundleMap['registrationId'] as int;
    final identityKeyBytes = base64.decode(bundleMap['identityKey'] as String);
    final identityKey = IdentityKey.fromBytes(Uint8List.fromList(identityKeyBytes), 0);

    final signedPreKeyMap = bundleMap['signedPreKey'] as Map<String, dynamic>;
    final signedPreKeyId = signedPreKeyMap['keyId'] as int;
    final signedPreKeyBytes = base64.decode(signedPreKeyMap['publicKey'] as String);
    final signedPreKeySignature = base64.decode(signedPreKeyMap['signature'] as String);
    final signedPreKeyPoint = Curve.decodePoint(Uint8List.fromList(signedPreKeyBytes), 0);

    int? preKeyId;
    ECPublicKey? preKeyPoint;
    if (bundleMap['preKey'] != null) {
      final preKeyMap = bundleMap['preKey'] as Map<String, dynamic>;
      preKeyId = preKeyMap['keyId'] as int;
      final preKeyBytes = base64.decode(preKeyMap['publicKey'] as String);
      preKeyPoint = Curve.decodePoint(Uint8List.fromList(preKeyBytes), 0);
    }

    final bundle = PreKeyBundle(
      registrationId,
      1, // Device ID
      preKeyId,
      preKeyPoint,
      signedPreKeyId,
      signedPreKeyPoint,
      Uint8List.fromList(signedPreKeySignature),
      identityKey,
    );

    await processPreKeyBundle(remoteCode, bundle);
  }

  /// Processes an incoming PreKey bundle to establish a new Signal session.
  Future<void> processPreKeyBundle(
    String remoteCode,
    PreKeyBundle bundle,
  ) async {
    final address = SignalProtocolAddress(remoteCode, 1);
    final sessionBuilder = SessionBuilder(_store, _store, _store, _store, address);
    await sessionBuilder.processPreKeyBundle(bundle);
  }

  /// Encrypts a plaintext message for the recipient.
  /// Returns serialized ciphertext bytes.
  Future<List<int>> encryptMessage(
    String recipientCode,
    String plaintext,
  ) async {
    final address = SignalProtocolAddress(recipientCode, 1);
    final sessionCipher = SessionCipher(_store, _store, _store, _store, address);

    final ciphertext = await sessionCipher.encrypt(
      Uint8List.fromList(utf8.encode(plaintext)),
    );

    return ciphertext.serialize();
  }

  /// Decrypts a ciphertext message from the sender.
  Future<String> decryptMessage(
    String senderCode,
    List<int> serializedCiphertext,
  ) async {
    final address = SignalProtocolAddress(senderCode, 1);
    final sessionCipher = SessionCipher(_store, _store, _store, _store, address);

    final ciphertextBytes = Uint8List.fromList(serializedCiphertext);
    Uint8List decryptedBytes;

    // The first message in a session is a PreKeySignalMessage.
    // Subsequent messages are regular SignalMessages.
    try {
      final preKeyMessage = PreKeySignalMessage(ciphertextBytes);
      decryptedBytes = await sessionCipher.decrypt(preKeyMessage);
    } catch (_) {
      final signalMessage = SignalMessage.fromSerialized(ciphertextBytes);
      decryptedBytes = await sessionCipher.decryptFromSignal(signalMessage);
    }

    return utf8.decode(decryptedBytes);
  }

  /// Computes a formatted Safety Number (numeric fingerprint) between local user and remote peer.
  Future<String> computeSafetyNumbers(String remoteCode) async {
    final localIdentity = await _store.getIdentityKeyPair();
    final localKeyBytes = localIdentity.getPublicKey().serialize();

    final address = SignalProtocolAddress(remoteCode, 1);
    final remoteIdentity = await _store.getIdentity(address);

    Uint8List remoteKeyBytes;
    if (remoteIdentity != null) {
      remoteKeyBytes = remoteIdentity.serialize();
    } else {
      // If remote identity is not yet established in store, fallback to remoteCode representation
      remoteKeyBytes = Uint8List.fromList(utf8.encode(remoteCode));
    }

    // Sort key byte arrays lexicographically for symmetry between Alice and Bob
    final List<int> combined = [];
    final compare = _compareBytes(localKeyBytes, remoteKeyBytes);
    if (compare <= 0) {
      combined.addAll(localKeyBytes);
      combined.addAll(remoteKeyBytes);
    } else {
      combined.addAll(remoteKeyBytes);
      combined.addAll(localKeyBytes);
    }

    // Hash with SHA-512 to generate a 60-digit numeric fingerprint (12 chunks of 5 digits)
    final digest = sha512.convert(combined).bytes;
    final buffer = StringBuffer();
    for (int i = 0; i < 12; i++) {
      final val = (digest[i * 4] << 24) |
          (digest[i * 4 + 1] << 16) |
          (digest[i * 4 + 2] << 8) |
          digest[i * 4 + 3];
      final chunk = (val.abs() % 100000).toString().padLeft(5, '0');
      buffer.write(chunk);
      if (i < 11) {
        buffer.write(i % 3 == 2 ? '\n' : ' ');
      }
    }

    return buffer.toString();
  }

  int _compareBytes(List<int> a, List<int> b) {
    final minLen = a.length < b.length ? a.length : b.length;
    for (int i = 0; i < minLen; i++) {
      if (a[i] != b[i]) return a[i].compareTo(b[i]);
    }
    return a.length.compareTo(b.length);
  }
}
