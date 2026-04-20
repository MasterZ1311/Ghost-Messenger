import 'dart:typed_data';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';
import '../core/signal/sqlite_signal_store.dart';

/// Orchestrates Signal Protocol encryption and decryption over the store.
class SignalService {
  final SQLiteSignalStore _store;

  SignalService(this._store);

  /// Encrypts a plaintext message for the recipient.
  /// Returns serialized ciphertext bytes.
  Future<List<int>> encryptMessage(
    String recipientCode,
    String plaintext,
  ) async {
    final address = SignalProtocolAddress(recipientCode, 1);
    final sessionCipher = SessionCipher(_store, _store, _store, _store, address);

    final ciphertext = await sessionCipher.encrypt(
      Uint8List.fromList(plaintext.codeUnits),
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

    return String.fromCharCodes(decryptedBytes);
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
}
