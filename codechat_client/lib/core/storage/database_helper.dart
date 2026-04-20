import 'dart:io';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite_sqlcipher/sqflite.dart';

/// Singleton helper that initializes and manages the encrypted SQLCipher database.
class DatabaseHelper {
  static final DatabaseHelper _instance = DatabaseHelper._internal();
  static Database? _database;

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

    // TODO: In production, derive this password from the user's root key 
    // and store it in flutter_secure_storage.
    return await openDatabase(
      path,
      version: 1,
      password: 'TemporarySecurePassword!123',
      onCreate: _onCreate,
    );
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
}
