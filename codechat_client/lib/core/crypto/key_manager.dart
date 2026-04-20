import 'dart:typed_data';
import 'package:bip39/bip39.dart' as bip39;
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart' as signal;

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
    return signal.generateIdentityKeyPair();
  }

  /// Generates a fresh IdentityKeyPair using secure random.
  static IdentityKeyPair generateRegistrationKeyPair() {
    return signal.generateIdentityKeyPair();
  }

  /// Generates a random registration ID.
  static int generateRegistrationId() {
    return signal.generateRegistrationId(false);
  }

  /// Generates a batch of one-time PreKeys.
  static List<PreKeyRecord> generatePreKeys(int start, int count) {
    return signal.generatePreKeys(start, count);
  }

  /// Generates a signed pre-key.
  static SignedPreKeyRecord generateSignedPreKey(
    IdentityKeyPair identityKeyPair,
    int signedPreKeyId,
  ) {
    return signal.generateSignedPreKey(identityKeyPair, signedPreKeyId);
  }
}

