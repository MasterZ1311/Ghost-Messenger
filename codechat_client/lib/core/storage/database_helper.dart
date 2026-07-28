import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite_sqlcipher/sqflite.dart';

/// Singleton helper that initializes and manages the encrypted SQLCipher database.
class DatabaseHelper {
  static final DatabaseHelper _instance = DatabaseHelper._internal();
  static Database? _database;

  static const String _encryptionKeyStorageKey = 'master_encryption_key';
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();

  factory DatabaseHelper() => _instance;
  DatabaseHelper._internal();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    Directory documentsDirectory = await getApplicationDocumentsDirectory();
    String path = p.join(documentsDirectory.path, 'codechat_secure.db');

    final encryptionKey = await _getEncryptionKey();

    return await openDatabase(
      path,
      version: 1,
      password: encryptionKey,
      onCreate: _onCreate,
    );
  }

  /// Retrieves the master encryption key from secure storage (iOS Keychain / Android Keystore),
  /// or generates a new high-entropy 256-bit key and stores it if one does not exist.
  Future<String> _getEncryptionKey() async {
    String? key = await _secureStorage.read(key: _encryptionKeyStorageKey);
    if (key == null) {
      key = _generateHighEntropyKey();
      await _secureStorage.write(key: _encryptionKeyStorageKey, value: key);
    }
    return key;
  }

  /// Generates a high-entropy 256-bit (32-byte) key encoded as base64Url.
  String _generateHighEntropyKey() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    return base64Url.encode(bytes);
  }

  /// Public accessor to retrieve the master encryption key stored in secure storage.
  Future<String> getEncryptionKey() => _getEncryptionKey();

  /// Closes the database connection if open.
  Future<void> close() async {
    final db = _database;
    if (db != null && db.isOpen) {
      await db.close();
      _database = null;
    }
  }

  /// Deletes the local SQLCipher database and removes the encryption key from secure storage.
  Future<void> deleteSecureDatabase() async {
    await close();
    Directory documentsDirectory = await getApplicationDocumentsDirectory();
    String path = p.join(documentsDirectory.path, 'codechat_secure.db');
    final file = File(path);
    if (await file.exists()) {
      await file.delete();
    }
    await _secureStorage.delete(key: _encryptionKeyStorageKey);
  }

  Future<void> _onCreate(Database db, int version) async {
    // Identity Keys & Registration ID
    await db.execute('''
      CREATE TABLE identity (
        registration_id INTEGER PRIMARY KEY,
        keypair BLOB NOT NULL
      )
    ''');

    // Trusted remote identities (for TOFU verification)
    await db.execute('''
      CREATE TABLE trusted_identities (
        address_name TEXT PRIMARY KEY,
        identity_key BLOB NOT NULL
      )
    ''');

    // Signal Sessions
    await db.execute('''
      CREATE TABLE sessions (
        address_name TEXT,
        device_id INTEGER,
        record BLOB NOT NULL,
        PRIMARY KEY (address_name, device_id)
      )
    ''');

    // Signal PreKeys
    await db.execute('''
      CREATE TABLE prekeys (
        prekey_id INTEGER PRIMARY KEY,
        record BLOB NOT NULL
      )
    ''');

    // Signal SignedPreKeys
    await db.execute('''
      CREATE TABLE signed_prekeys (
        signed_prekey_id INTEGER PRIMARY KEY,
        record BLOB NOT NULL
      )
    ''');

    // Local Chat History (encrypted at rest by SQLCipher)
    await db.execute('''
      CREATE TABLE messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        remote_code TEXT NOT NULL,
        content TEXT NOT NULL,
        is_me INTEGER NOT NULL,
        timestamp TEXT NOT NULL
      )
    ''');
  }

  /// Check if local identity has been registered in the database.
  Future<bool> hasIdentity() async {
    final db = await database;
    final res = await db.query('identity');
    return res.isNotEmpty;
  }

  /// Retrieve the stored identity record if present.
  Future<Map<String, dynamic>?> getLocalIdentity() async {
    final db = await database;
    final res = await db.query('identity');
    if (res.isNotEmpty) return res.first;
    return null;
  }

  /// Save an encrypted message (either sent or received) to local database.
  Future<int> saveMessage({
    required String remoteCode,
    required String content,
    required bool isMe,
    required DateTime timestamp,
  }) async {
    final db = await database;
    return await db.insert('messages', {
      'remote_code': remoteCode,
      'content': content,
      'is_me': isMe ? 1 : 0,
      'timestamp': timestamp.toIso8601String(),
    });
  }

  /// Load historical messages for a specific peer code, sorted chronologically (oldest to newest).
  Future<List<Map<String, dynamic>>> getMessagesForPeer(String remoteCode) async {
    final db = await database;
    return await db.query(
      'messages',
      where: 'remote_code = ?',
      whereArgs: [remoteCode],
      orderBy: 'timestamp ASC',
    );
  }

  /// Get distinct recent peer contacts with their latest message preview and timestamp.
  Future<List<Map<String, dynamic>>> getRecentChats() async {
    final db = await database;
    return await db.rawQuery('''
      SELECT m1.remote_code, m1.content, m1.is_me, m1.timestamp
      FROM messages m1
      INNER JOIN (
        SELECT remote_code, MAX(id) as max_id
        FROM messages
        GROUP BY remote_code
      ) m2 ON m1.remote_code = m2.remote_code AND m1.id = m2.max_id
      ORDER BY m1.timestamp DESC
    ''');
  }
}
