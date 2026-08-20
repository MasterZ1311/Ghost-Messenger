import 'dart:convert';
import 'dart:typed_data';
import 'package:bip39/bip39.dart' as bip39;
import 'package:crypto/crypto.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart' as signal;

/// Manages BIP39 mnemonic generation and Signal identity key derivation.
class KeyManager {
  // HKDF application context string bound to this app and key type.
  // Changing this value is a breaking change — all existing sessions are invalidated.
  static const String _hkdfInfo = 'GhostMessenger/IdentityKey/v1';

  /// Generates a new 12-word mnemonic passphrase.
  static String generateMnemonic() {
    return bip39.generateMnemonic();
  }

  /// Validates whether a mnemonic passphrase is a valid BIP39 mnemonic.
  static bool validateMnemonic(String mnemonic) {
    return bip39.validateMnemonic(mnemonic.trim().toLowerCase());
  }

  /// Derives the 64-byte root seed from a BIP39 mnemonic.
  static Uint8List deriveSeed(String mnemonic) {
    return Uint8List.fromList(bip39.mnemonicToSeed(mnemonic));
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  HKDF-SHA-256 helpers
  //  RFC 5869 — implemented using the `crypto` package (dart:typed_data).
  //
  //  We do not use a random salt because the BIP39 seed (64 bytes of
  //  PBKDF2-derived entropy) already provides the full security budget.
  //  A fixed, all-zeros salt is the RFC-recommended default for deterministic
  //  derivation from high-entropy input material.
  // ─────────────────────────────────────────────────────────────────────────

  /// HKDF-Extract: PRK = HMAC-SHA256(salt, ikm)
  static Uint8List _hkdfExtract(Uint8List salt, Uint8List ikm) {
    final hmac = Hmac(sha256, salt);
    return Uint8List.fromList(hmac.convert(ikm).bytes);
  }

  /// HKDF-Expand: produces [length] bytes of output keying material.
  /// [length] must be ≤ 32 * 255 bytes (one hash-length block is sufficient here).
  static Uint8List _hkdfExpand(Uint8List prk, Uint8List info, int length) {
    final hmac = Hmac(sha256, prk);
    // T(1) = HMAC-SHA256(PRK, info || 0x01)  (first and only block for ≤32 bytes)
    final input = Uint8List(info.length + 1);
    input.setAll(0, info);
    input[info.length] = 0x01;
    final t1 = Uint8List.fromList(hmac.convert(input).bytes);
    return t1.sublist(0, length);
  }

  /// Derives a deterministic 32-byte Curve25519 private key scalar from
  /// the BIP39 seed using HKDF-SHA-256.
  static Uint8List _derivePrivateKeyBytes(Uint8List seed) {
    // Salt: 32 zero bytes (RFC 5869 §2.2 — "not provided" default for high-entropy IKM)
    final salt = Uint8List(32);
    final info = Uint8List.fromList(utf8.encode(_hkdfInfo));

    final prk = _hkdfExtract(salt, seed);
    return _hkdfExpand(prk, info, 32);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Signal IdentityKeyPair derivation
  // ─────────────────────────────────────────────────────────────────────────

  /// Derives a deterministic Signal [IdentityKeyPair] from a BIP39 mnemonic.
  ///
  /// The derivation path is:
  ///   BIP39 seed (64 B)
  ///     → HKDF-SHA256(salt=0x00*32, info="GhostMessenger/IdentityKey/v1")
  ///     → 32-byte Curve25519 private scalar
  ///     → Curve25519 public key (via libsignal Curve API)
  ///     → IdentityKeyPair
  ///
  /// The same mnemonic always produces the same key pair, enabling recovery
  /// from a seed phrase backup.
  static IdentityKeyPair generateIdentityFromMnemonic(String mnemonic) {
    final seed = deriveSeed(mnemonic);
    return _identityKeyPairFromSeedBytes(seed);
  }

  /// Internal: builds an [IdentityKeyPair] from 64 bytes of seed material by
  /// applying HKDF and constructing the Curve25519 key pair.
  static IdentityKeyPair _identityKeyPairFromSeedBytes(Uint8List seed) {
    final privBytes = _derivePrivateKeyBytes(seed);
    final ecKeyPair = Curve.generateKeyPairFromPrivate(privBytes);
    final identityKey = IdentityKey(ecKeyPair.publicKey);
    return IdentityKeyPair(identityKey, ecKeyPair.privateKey);
  }

  /// Generates a fresh random [IdentityKeyPair] (used when no mnemonic is
  /// available, e.g. during first-boot before the user writes down their phrase).
  static IdentityKeyPair generateRegistrationKeyPair() {
    return signal.generateIdentityKeyPair();
  }

  /// Generates a random registration ID.
  static int generateRegistrationId() {
    return signal.generateRegistrationId(false);
  }

  /// Derives a deterministic registration ID from the same seed material.
  ///
  /// Signal registration IDs are 14-bit values (1–16380).  We take two bytes
  /// from a second HKDF expansion (different info string) and mask to 14 bits,
  /// guaranteeing the result is always in range and reproducible.
  static int deriveRegistrationIdFromMnemonic(String mnemonic) {
    final seed = deriveSeed(mnemonic);
    final salt = Uint8List(32);
    final info = Uint8List.fromList(
      utf8.encode('GhostMessenger/RegistrationId/v1'),
    );
    final prk = _hkdfExtract(salt, seed);
    final idBytes = _hkdfExpand(prk, info, 2);
    // Mask to 14 bits, then clamp to [1, 16380].
    final raw = ((idBytes[0] & 0x3F) << 8) | idBytes[1];
    return (raw % 16380) + 1;
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
