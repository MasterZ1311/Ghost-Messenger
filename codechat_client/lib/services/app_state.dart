import 'package:flutter/material.dart';
import '../core/storage/database_helper.dart';
import '../core/signal/sqlite_signal_store.dart';
import '../core/crypto/key_manager.dart';
import '../core/crypto/user_code_utils.dart';
import 'signal_service.dart';
import 'connection_manager.dart';

/// Default signaling server URL.
/// Override with a real server address for production deployments.
/// The value can be changed at runtime via [AppState.signalingServerUrl].
const String _kDefaultSignalingUrl = 'https://ghostmessenger.duckdns.org';
// For physical devices, change to your actual server URL, e.g.:
// const String _kDefaultSignalingUrl = 'https://signal.yourdomain.com';

class AppState extends ChangeNotifier {
  static final AppState instance = AppState._internal();
  factory AppState() => instance;
  AppState._internal();

  final DatabaseHelper _dbHelper = DatabaseHelper();
  SQLiteSignalStore? _store;
  SignalService? _signalService;
  ConnectionManager? _connectionManager;

  String? _localUserCode;
  String? _mnemonic;
  bool _isInitialized = false;
  bool _isLoading = true;
  String? _initError;

  /// Configurable signaling server URL. Change before calling initialize().
  String signalingServerUrl = _kDefaultSignalingUrl;

  List<Map<String, dynamic>> _recentChats = [];
  final Map<String, String> _peerStatusMap = {};

  String? get localUserCode => _localUserCode;
  String? get mnemonic => _mnemonic;
  bool get isInitialized => _isInitialized;
  bool get isLoading => _isLoading;

  /// Non-null when initialization failed — surface this in the UI.
  String? get initError => _initError;

  List<Map<String, dynamic>> get recentChats => _recentChats;
  ConnectionManager? get connectionManager => _connectionManager;

  /// Check for existing identity in database and setup services.
  Future<void> initialize() async {
    _isLoading = true;
    _initError = null;
    notifyListeners();

    try {
      final db = await _dbHelper.database;
      _store = SQLiteSignalStore(db);
      _signalService = SignalService(_store!);

      final hasIdentity = await _dbHelper.hasIdentity();
      if (hasIdentity) {
        final identityKeyPair = await _store!.getIdentityKeyPair();
        final publicKeyBytes = identityKeyPair.getPublicKey().serialize();
        _localUserCode = UserCodeUtils.generateUserCode(publicKeyBytes);

        await _setupConnectionManager();
        await loadRecentChats();
        _isInitialized = true;
      }
    } catch (e, stack) {
      // ignore: avoid_print
      print('AppState init error: $e\n$stack');
      _initError = 'Failed to initialize app: ${e.toString()}';
      _isInitialized = false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Generates a fresh identity.
  ///
  /// The generated mnemonic is the canonical backup phrase. The identity key
  /// is derived deterministically from that mnemonic via HKDF, so the mnemonic
  /// will correctly restore the same identity.
  Future<String> createNewIdentity() async {
    _mnemonic = KeyManager.generateMnemonic();
    // Derive the keypair from the mnemonic so restore works correctly.
    final keyPair = KeyManager.generateIdentityFromMnemonic(_mnemonic!);
    final regId = KeyManager.deriveRegistrationIdFromMnemonic(_mnemonic!);

    final db = await _dbHelper.database;
    _store ??= SQLiteSignalStore(db);
    await _store!.saveLocalIdentity(regId, keyPair);

    final publicKeyBytes = keyPair.getPublicKey().serialize();
    _localUserCode = UserCodeUtils.generateUserCode(publicKeyBytes);

    _signalService = SignalService(_store!);
    await _setupConnectionManager();
    await loadRecentChats();

    _isInitialized = true;
    _initError = null;
    notifyListeners();
    return _mnemonic!;
  }

  /// Restores identity from 12-word mnemonic & saves to SQLCipher.
  Future<bool> restoreIdentityFromMnemonic(String mnemonicInput) async {
    final cleanMnemonic = mnemonicInput.trim().toLowerCase().replaceAll(RegExp(r'\s+'), ' ');
    if (!KeyManager.validateMnemonic(cleanMnemonic)) {
      return false;
    }

    _mnemonic = cleanMnemonic;
    final keyPair = KeyManager.generateIdentityFromMnemonic(cleanMnemonic);
    final regId = KeyManager.deriveRegistrationIdFromMnemonic(cleanMnemonic);

    final db = await _dbHelper.database;
    _store ??= SQLiteSignalStore(db);
    await _store!.saveLocalIdentity(regId, keyPair);

    final publicKeyBytes = keyPair.getPublicKey().serialize();
    _localUserCode = UserCodeUtils.generateUserCode(publicKeyBytes);

    _signalService = SignalService(_store!);
    await _setupConnectionManager();
    await loadRecentChats();

    _isInitialized = true;
    _initError = null;
    notifyListeners();
    return true;
  }

  Future<void> _setupConnectionManager() async {
    if (_localUserCode == null || _signalService == null) return;

    // Safely disconnect the previous manager before replacing it.
    await _connectionManager?.disconnect();
    _connectionManager = ConnectionManager(_signalService!, _localUserCode!);

    // Handle incoming messages — save to DB first, then notify.
    _connectionManager!.onSecureMessageReceived = (message, fromCode) async {
      final timestamp = DateTime.now();
      await _dbHelper.saveMessage(
        remoteCode: fromCode,
        content: message,
        isMe: false,
        timestamp: timestamp,
      );
      await loadRecentChats();
      notifyListeners();
    };

    // Track peer status changes
    _connectionManager!.onPeerStatusChanged = (peerCode, status) {
      _peerStatusMap[peerCode] = status;
      notifyListeners();
    };

    // Connect to the configured signaling server.
    _connectionManager!.connect(signalingServerUrl);
  }

  /// Connect to peer P2P session.
  Future<void> initiatePeerConnection(String targetCode) async {
    if (_connectionManager == null) return;
    await _connectionManager!.initiateSecureConnection(targetCode);
    _peerStatusMap[targetCode] = 'connecting';
    notifyListeners();
  }

  /// Get status of peer: 'online', 'connecting', 'offline'.
  String getPeerStatus(String peerCode) {
    if (_connectionManager != null) {
      return _connectionManager!.getPeerStatus(peerCode);
    }
    return _peerStatusMap[peerCode] ?? 'offline';
  }

  /// Send an encrypted message and save to local database.
  Future<void> sendMessage(String targetCode, String content) async {
    final timestamp = DateTime.now();

    // Try sending over live P2P channel if connected
    try {
      await _connectionManager?.sendSecureMessage(targetCode, content);
    } catch (e) {
      // ignore: avoid_print
      print('Send over P2P failed (saving locally): $e');
    }

    // Always persist outgoing message in local SQLCipher DB
    await _dbHelper.saveMessage(
      remoteCode: targetCode,
      content: content,
      isMe: true,
      timestamp: timestamp,
    );

    await loadRecentChats();
    notifyListeners();
  }

  /// Load historical chat messages for a given peer code.
  Future<List<Map<String, dynamic>>> loadMessages(String remoteCode) async {
    return await _dbHelper.getMessagesForPeer(remoteCode);
  }

  /// Refresh recent chats list.
  Future<void> loadRecentChats() async {
    _recentChats = await _dbHelper.getRecentChats();
    notifyListeners();
  }
}
