import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:base32/base32.dart';

/// Derives a short human-readable User Code from a public identity key.
class UserCodeUtils {
  /// Generates the short 8-character user code from a public key.
  /// Result example: "5J9L-2P4X"
  static String generateUserCode(Uint8List publicKey) {
    // 1. Hash the public key
    final hash = sha256.convert(publicKey).bytes;

    // 2. Take the first 5 bytes (40 bits)
    final prefix = Uint8List.fromList(hash.sublist(0, 5));

    // 3. Encode in Base32 (RFC4648)
    // base32 of 5 bytes = 8 characters
    String encoded = base32.encode(prefix).toUpperCase();

    // 4. Remove any padding
    encoded = encoded.replaceAll('=', '');

    // 5. Add hyphen for readability
    if (encoded.length >= 8) {
      encoded = encoded.substring(0, 8);
      return '${encoded.substring(0, 4)}-${encoded.substring(4)}';
    }

    return encoded;
  }

  /// Validates a user-entered code for basic format.
  static bool isValidFormat(String code) {
    final clean = code.replaceAll('-', '').trim().toUpperCase();
    return clean.length == 8 && RegExp(r'^[A-Z2-7]+$').hasMatch(clean);
  }
}
