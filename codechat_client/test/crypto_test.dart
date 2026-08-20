import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:codechat_client/core/crypto/key_manager.dart';
import 'package:codechat_client/core/crypto/user_code_utils.dart';

void main() {
  group('UserCodeUtils Tests', () {
    test('generateUserCode generates valid 8-char hyphenated code', () {
      final fakePublicKey = Uint8List.fromList(List.generate(33, (i) => i * 7 % 256));
      final code = UserCodeUtils.generateUserCode(fakePublicKey);

      expect(code.length, equals(9)); // 8 chars + 1 hyphen
      expect(code.contains('-'), isTrue);
      expect(UserCodeUtils.isValidFormat(code), isTrue);
    });

    test('isValidFormat validates correct base32 formats', () {
      final fakePublicKey = Uint8List.fromList(List.generate(33, (i) => i * 7 % 256));
      final generatedCode = UserCodeUtils.generateUserCode(fakePublicKey);

      expect(UserCodeUtils.isValidFormat(generatedCode), isTrue);
      expect(UserCodeUtils.isValidFormat(generatedCode.replaceAll('-', '')), isTrue);
      expect(UserCodeUtils.isValidFormat('INVALID1!'), isFalse);
      expect(UserCodeUtils.isValidFormat('SHORT'), isFalse);
    });
  });

  group('KeyManager Deterministic Derivation Tests', () {
    test('generateMnemonic generates valid 12 words', () {
      final mnemonic = KeyManager.generateMnemonic();
      final words = mnemonic.split(' ');
      expect(words.length, equals(12));
      expect(KeyManager.validateMnemonic(mnemonic), isTrue);
    });

    test('deterministic derivation yields identical identity keys from same mnemonic', () {
      const mnemonic = 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
      final keyPair1 = KeyManager.generateIdentityFromMnemonic(mnemonic);
      final keyPair2 = KeyManager.generateIdentityFromMnemonic(mnemonic);

      final pub1 = keyPair1.getPublicKey().serialize();
      final pub2 = keyPair2.getPublicKey().serialize();

      expect(pub1, equals(pub2));

      final userCode1 = UserCodeUtils.generateUserCode(pub1);
      final userCode2 = UserCodeUtils.generateUserCode(pub2);
      expect(userCode1, equals(userCode2));
    });

    test('deterministic registration ID derivation', () {
      const mnemonic = 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
      final regId1 = KeyManager.deriveRegistrationIdFromMnemonic(mnemonic);
      final regId2 = KeyManager.deriveRegistrationIdFromMnemonic(mnemonic);

      expect(regId1, equals(regId2));
      expect(regId1, greaterThan(0));
      expect(regId1, lessThanOrEqualTo(16380));
    });
  });
}
