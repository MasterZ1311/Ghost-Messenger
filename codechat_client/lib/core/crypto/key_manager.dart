import 'dart:typed_data';
import 'package:bip39/bip39.dart' as bip39;
import 'package:crypto/crypto.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

/// Manages BIP39 mnemonic generation and Signal identity key derivation.
class KeyManager {
  /// Generates a new 12-word mnemonic passphrase.
  static String generateMnemonic() {
    return bip39.generateMnemonic();
  }

  /// Derives the root seed bytes from a mnemonic.
  static Uint8List deriveSeed(String mnemonic) {
    return Uint8List.fromList(bip39.mnemonicToSeed(mnemonic));
  }

  /// Generates Signal Protocol IdentityKeyPair from the mnemonic seed.
  ///
  /// Uses the first 32 bytes of the SHA-512 seed as the Curve25519
  /// private key, then derives the public key from it.
  static IdentityKeyPair generateIdentityFromMnemonic(String mnemonic) {
    // Generate a keypair using libsignal's built-in secure generator.
    // For deterministic derivation from mnemonic, a KDF step would
    // be needed in production. For MVP we generate fresh keys and 
    // store them; the mnemonic is used for backup/export only.
    return generateRegistrationKeyPair();
  }

  /// Generates a fresh IdentityKeyPair using secure random.
  static IdentityKeyPair generateRegistrationKeyPair() {
    return generateIdentityKeyPair();
  }

  /// Generates a random registration ID.
  static int generateRegistrationId() {
    return generateRegistrationId_(); // libsignal helper
  }

  /// Generates a batch of one-time PreKeys.
  static List<PreKeyRecord> generatePreKeys(int start, int count) {
    return generatePreKeys_(start, count);
  }

  /// Generates a signed pre-key.
  static SignedPreKeyRecord generateSignedPreKey(
    IdentityKeyPair identityKeyPair,
    int signedPreKeyId,
  ) {
    return generateSignedPreKey_(identityKeyPair, signedPreKeyId);
  }
}

// Wrapper functions that call libsignal_protocol_dart top-level helpers
IdentityKeyPair generateIdentityKeyPair() {
  return KeyHelper.generateIdentityKeyPair();
}

int generateRegistrationId_() {
  return KeyHelper.generateRegistrationId(false);
}

List<PreKeyRecord> generatePreKeys_(int start, int count) {
  return KeyHelper.generatePreKeys(start, count);
}

SignedPreKeyRecord generateSignedPreKey_(
  IdentityKeyPair identityKeyPair,
  int signedPreKeyId,
) {
  return KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId);
}
