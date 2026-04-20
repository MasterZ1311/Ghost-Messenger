import 'dart:typed_data';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';
import 'package:sqflite_sqlcipher/sqflite.dart';

/// Implements Signal Protocol storage interfaces using SQLCipher.
///
/// This class provides persistent, encrypted storage for all Signal
/// Protocol state: sessions, pre-keys, signed pre-keys, and identity keys.
class SQLiteSignalStore
    implements SessionStore, PreKeyStore, SignedPreKeyStore, IdentityKeyStore {
  final Database db;

  // Cached identity for quick access
  IdentityKeyPair? _identityKeyPair;
  int? _localRegistrationId;

  SQLiteSignalStore(this.db);

  // ──────────────────────────────────────────────
  //  SessionStore
  // ──────────────────────────────────────────────

  @override
  Future<SessionRecord> loadSession(SignalProtocolAddress address) async {
    final maps = await db.query(
      'sessions',
      where: 'address_name = ? AND device_id = ?',
      whereArgs: [address.getName(), address.getDeviceId()],
    );
    if (maps.isEmpty) return SessionRecord();
    return SessionRecord.fromSerialized(
      Uint8List.fromList(maps.first['record'] as List<int>),
    );
  }

  @override
  Future<List<int>> getSubDeviceSessions(String name) async {
    final maps = await db.query(
      'sessions',
      columns: ['device_id'],
      where: 'address_name = ?',
      whereArgs: [name],
    );
    return maps.map((m) => m['device_id'] as int).toList();
  }

  @override
  Future<void> storeSession(
    SignalProtocolAddress address,
    SessionRecord record,
  ) async {
    await db.insert(
      'sessions',
      {
        'address_name': address.getName(),
        'device_id': address.getDeviceId(),
        'record': record.serialize(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  @override
  Future<bool> containsSession(SignalProtocolAddress address) async {
    final maps = await db.query(
      'sessions',
      where: 'address_name = ? AND device_id = ?',
      whereArgs: [address.getName(), address.getDeviceId()],
    );
    return maps.isNotEmpty;
  }

  @override
  Future<void> deleteSession(SignalProtocolAddress address) async {
    await db.delete(
      'sessions',
      where: 'address_name = ? AND device_id = ?',
      whereArgs: [address.getName(), address.getDeviceId()],
    );
  }

  @override
  Future<void> deleteAllSessions(String name) async {
    await db.delete(
      'sessions',
      where: 'address_name = ?',
      whereArgs: [name],
    );
  }

  // ──────────────────────────────────────────────
  //  PreKeyStore
  // ──────────────────────────────────────────────

  @override
  Future<PreKeyRecord> loadPreKey(int preKeyId) async {
    final maps = await db.query(
      'prekeys',
      where: 'prekey_id = ?',
      whereArgs: [preKeyId],
    );
    if (maps.isEmpty) {
      throw InvalidKeyIdException('No pre key found for id: $preKeyId');
    }
    return PreKeyRecord.fromBuffer(
      Uint8List.fromList(maps.first['record'] as List<int>),
    );
  }

  @override
  Future<void> storePreKey(int preKeyId, PreKeyRecord record) async {
    await db.insert(
      'prekeys',
      {
        'prekey_id': preKeyId,
        'record': record.serialize(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  @override
  Future<bool> containsPreKey(int preKeyId) async {
    final maps = await db.query(
      'prekeys',
      where: 'prekey_id = ?',
      whereArgs: [preKeyId],
    );
    return maps.isNotEmpty;
  }

  @override
  Future<void> removePreKey(int preKeyId) async {
    await db.delete(
      'prekeys',
      where: 'prekey_id = ?',
      whereArgs: [preKeyId],
    );
  }

  // ──────────────────────────────────────────────
  //  SignedPreKeyStore
  // ──────────────────────────────────────────────

  @override
  Future<List<SignedPreKeyRecord>> loadSignedPreKeys() async {
    final maps = await db.query('signed_prekeys');
    return maps.map((m) {
      return SignedPreKeyRecord.fromSerialized(
        Uint8List.fromList(m['record'] as List<int>),
      );
    }).toList();
  }

  @override
  Future<SignedPreKeyRecord> loadSignedPreKey(int signedPreKeyId) async {
    final maps = await db.query(
      'signed_prekeys',
      where: 'signed_prekey_id = ?',
      whereArgs: [signedPreKeyId],
    );
    if (maps.isEmpty) {
      throw InvalidKeyIdException(
        'No signed pre key found for id: $signedPreKeyId',
      );
    }
    return SignedPreKeyRecord.fromSerialized(
      Uint8List.fromList(maps.first['record'] as List<int>),
    );
  }

  @override
  Future<void> storeSignedPreKey(
    int signedPreKeyId,
    SignedPreKeyRecord record,
  ) async {
    await db.insert(
      'signed_prekeys',
      {
        'signed_prekey_id': signedPreKeyId,
        'record': record.serialize(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  @override
  Future<bool> containsSignedPreKey(int signedPreKeyId) async {
    final maps = await db.query(
      'signed_prekeys',
      where: 'signed_prekey_id = ?',
      whereArgs: [signedPreKeyId],
    );
    return maps.isNotEmpty;
  }

  @override
  Future<void> removeSignedPreKey(int signedPreKeyId) async {
    await db.delete(
      'signed_prekeys',
      where: 'signed_prekey_id = ?',
      whereArgs: [signedPreKeyId],
    );
  }

  // ──────────────────────────────────────────────
  //  IdentityKeyStore
  // ──────────────────────────────────────────────

  @override
  Future<IdentityKeyPair> getIdentityKeyPair() async {
    if (_identityKeyPair != null) return _identityKeyPair!;
    final maps = await db.query('identity');
    if (maps.isEmpty) throw Exception('Identity not initialized');
    _identityKeyPair = IdentityKeyPair.fromSerialized(
      Uint8List.fromList(maps.first['keypair'] as List<int>),
    );
    return _identityKeyPair!;
  }

  @override
  Future<int> getLocalRegistrationId() async {
    if (_localRegistrationId != null) return _localRegistrationId!;
    final maps = await db.query('identity');
    if (maps.isEmpty) throw Exception('Identity not initialized');
    _localRegistrationId = maps.first['registration_id'] as int;
    return _localRegistrationId!;
  }

  @override
  Future<bool> saveIdentity(
    SignalProtocolAddress address,
    IdentityKey? identityKey,
  ) async {
    // Returns true if an existing identity was replaced (trust change)
    if (identityKey == null) return false;

    final existing = await db.query(
      'trusted_identities',
      where: 'address_name = ?',
      whereArgs: [address.getName()],
    );

    await db.insert(
      'trusted_identities',
      {
        'address_name': address.getName(),
        'identity_key': identityKey.serialize(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );

    return existing.isNotEmpty;
  }

  @override
  Future<bool> isTrustedIdentity(
    SignalProtocolAddress address,
    IdentityKey? identityKey,
    Direction direction,
  ) async {
    // Trust on first use (TOFU)
    if (identityKey == null) return false;

    final maps = await db.query(
      'trusted_identities',
      where: 'address_name = ?',
      whereArgs: [address.getName()],
    );

    if (maps.isEmpty) return true; // First contact — trust it

    final storedKey = IdentityKey.fromBytes(
      Uint8List.fromList(maps.first['identity_key'] as List<int>),
      0,
    );
    return identityKey == storedKey;
  }

  @override
  Future<IdentityKey?> getIdentity(SignalProtocolAddress address) async {
    final maps = await db.query(
      'trusted_identities',
      where: 'address_name = ?',
      whereArgs: [address.getName()],
    );
    if (maps.isEmpty) return null;
    return IdentityKey.fromBytes(
      Uint8List.fromList(maps.first['identity_key'] as List<int>),
      0,
    );
  }

  // ──────────────────────────────────────────────
  //  Local identity bootstrap
  // ──────────────────────────────────────────────

  /// Saves the local device's identity keypair and registration ID.
  Future<void> saveLocalIdentity(
    int registrationId,
    IdentityKeyPair keyPair,
  ) async {
    _identityKeyPair = keyPair;
    _localRegistrationId = registrationId;
    await db.insert(
      'identity',
      {
        'registration_id': registrationId,
        'keypair': keyPair.serialize(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }
}
