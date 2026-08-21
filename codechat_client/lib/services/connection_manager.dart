import 'dart:async';
import 'dart:convert';
import 'package:socket_io_client/socket_io_client.dart' as io;
import 'package:flutter_webrtc/flutter_webrtc.dart';
import '../core/storage/database_helper.dart';
import 'p2p_service.dart';
import 'push_service.dart';
import 'signal_service.dart';

/// Orchestrates Signaling, WebRTC, and Signal Protocol encryption flows.
class ConnectionManager {
  // Socket is nullable — only non-null after connect() is called.
  io.Socket? _socket;
  final P2PService _p2p = P2PService();
  final SignalService _signal;
  final PushService? _pushService;
  final String _localUserCode;
  final Map<String, String> _peerStatus = {};
  String? _lastServerUrl;
  bool _isSignalingConnected = false;

  // External callbacks
  Function(String message, String fromCode)? onSecureMessageReceived;
  Function(String fromCode)? onIncomingConnection;
  Function(String peerCode, String status)? onPeerStatusChanged;
  Function(bool isConnected)? onSignalingStatusChanged;

  ConnectionManager(this._signal, this._localUserCode, {PushService? pushService})
      : _pushService = pushService {
    _subscribeToPushWakeup();
  }

  bool get isSignalingConnected => _isSignalingConnected;
  String get localUserCode => _localUserCode;
  SignalService get signalService => _signal;
  P2PService get p2pService => _p2p;

  void _subscribeToPushWakeup() {
    _pushService?.onBackgroundWakeup.listen((event) {
      // ignore: avoid_print
      print('Background push wakeup received from ${event.fromCode}');
      if (_lastServerUrl != null) {
        // Reconnect to signaling server to flush pending offers queued while in background
        connect(_lastServerUrl!);
      }
    });
  }

  /// Connects to the signaling server and registers the local user.
  void connect(String serverUrl) {
    _lastServerUrl = serverUrl;

    // Cleanly tear down any existing socket before reconnecting.
    try {
      _socket?.disconnect();
      _socket?.dispose();
    } catch (_) {}
    _socket = null;
    _isSignalingConnected = false;

    final socket = io.io(serverUrl, <String, dynamic>{
      'transports': ['websocket'],
      'autoConnect': false,
    });
    _socket = socket;

    socket.connect();

    socket.onConnect((_) async {
      // ignore: avoid_print
      print('Connected to Signaling Server');
      _isSignalingConnected = true;
      onSignalingStatusChanged?.call(true);
      socket.emit('join', _localUserCode);
      if (_pushService != null) {
        _pushService!.registerPushTokenWithSocket(socket);
      }

      // Publish local PreKey bundle to signaling server
      await publishPreKeyBundle();
    });

    socket.onDisconnect((_) {
      // ignore: avoid_print
      print('Disconnected from Signaling Server');
      _isSignalingConnected = false;
      onSignalingStatusChanged?.call(false);
    });

    socket.on('joined', (_) {
      // ignore: avoid_print
      print('Successfully registered on signaling network as $_localUserCode');
    });

    // Handle incoming signaling data from the relay
    socket.on('signal', (data) async {
      try {
        final fromCode = data['fromCode'] as String;
        final signalData = data['signalData'] as Map<String, dynamic>;
        final type = signalData['type'] as String;

        if (type == 'offer') {
          await _handleOffer(fromCode, signalData);
        } else if (type == 'answer') {
          await _handleAnswer(signalData);
        } else if (type == 'candidate') {
          await _handleCandidate(signalData);
        }
      } catch (e) {
        // ignore: avoid_print
        print('Signal handler error: $e');
      }
    });

    socket.on('error_message', (data) {
      // ignore: avoid_print
      print('Signaling error: $data');
    });
  }

  /// Uploads local PreKey bundle to signaling server for X3DH distribution.
  Future<void> publishPreKeyBundle() async {
    final socket = _socket;
    if (socket == null || !socket.connected) return;
    try {
      final bundle = await _signal.exportPreKeyBundle(_localUserCode);
      socket.emitWithAck('publish_prekey', bundle, ack: (response) {
        // ignore: avoid_print
        print('PreKey bundle publication status: $response');
      });
    } catch (e) {
      // ignore: avoid_print
      print('Failed to export/publish PreKey bundle: $e');
    }
  }

  /// Fetches a remote peer's PreKey bundle from the signaling server.
  Future<bool> fetchAndProcessPreKeyBundle(String targetCode) async {
    final socket = _socket;
    if (socket == null || !socket.connected) return false;

    final completer = Completer<bool>();
    socket.emitWithAck('get_prekey', targetCode, ack: (response) async {
      if (response != null && response['ok'] == true && response['bundle'] != null) {
        try {
          final bundleMap = Map<String, dynamic>.from(response['bundle'] as Map);
          await _signal.processPreKeyBundleMap(targetCode, bundleMap);
          completer.complete(true);
        } catch (e) {
          // ignore: avoid_print
          print('Error processing remote prekey bundle: $e');
          completer.complete(false);
        }
      } else {
        completer.complete(false);
      }
    });

    return completer.future.timeout(
      const Duration(seconds: 5),
      onTimeout: () => false,
    );
  }

  /// Initiates a secure P2P connection to the target peer.
  Future<void> initiateSecureConnection(String targetCode) async {
    final socket = _socket;
    if (socket == null || !socket.connected) {
      // ignore: avoid_print
      print('Cannot initiate P2P: signaling socket not connected');
      return;
    }

    _setPeerStatus(targetCode, 'connecting');

    // Ensure Signal session exists before connecting
    final hasSession = await _signal.hasSession(targetCode);
    if (!hasSession) {
      await fetchAndProcessPreKeyBundle(targetCode);
    }

    await _p2p.initializePeerConnection();
    _setupP2PCallbacks(targetCode);

    // Create WebRTC Offer
    final RTCSessionDescription offer = await _p2p.createOffer();

    socket.emit('signal', {
      'toCode': targetCode,
      'fromCode': _localUserCode,
      'signalData': {
        'type': 'offer',
        'sdp': offer.sdp,
      },
    });
  }

  Future<void> _handleOffer(
    String fromCode,
    Map<String, dynamic> signal,
  ) async {
    final socket = _socket;
    if (socket == null) return;

    _setPeerStatus(fromCode, 'connecting');
    onIncomingConnection?.call(fromCode);
    await _p2p.initializePeerConnection();
    _setupP2PCallbacks(fromCode);

    final RTCSessionDescription offer = RTCSessionDescription(
      signal['sdp'] as String?,
      'offer',
    );
    final RTCSessionDescription answer = await _p2p.createAnswer(offer);

    socket.emit('signal', {
      'toCode': fromCode,
      'fromCode': _localUserCode,
      'signalData': {
        'type': 'answer',
        'sdp': answer.sdp,
      },
    });
  }

  Future<void> _handleAnswer(Map<String, dynamic> signal) async {
    final answer = RTCSessionDescription(
      signal['sdp'] as String?,
      'answer',
    );
    await _p2p.setRemoteDescription(answer);
  }

  Future<void> _handleCandidate(Map<String, dynamic> signal) async {
    final candidate = RTCIceCandidate(
      signal['candidate'] as String?,
      signal['sdpMid'] as String?,
      signal['sdpMLineIndex'] as int?,
    );
    await _p2p.addIceCandidate(candidate);
  }

  void _setupP2PCallbacks(String remoteCode) {
    // Relay ICE candidates through signaling
    _p2p.onIceCandidate = (candidate) {
      _socket?.emit('signal', {
        'toCode': remoteCode,
        'fromCode': _localUserCode,
        'signalData': {
          'type': 'candidate',
          'candidate': candidate.candidate,
          'sdpMid': candidate.sdpMid,
          'sdpMLineIndex': candidate.sdpMLineIndex,
        },
      });
    };

    // Track peer connection states
    _p2p.onConnectionStateChange = (state) {
      if (state == RTCPeerConnectionState.RTCPeerConnectionStateConnected) {
        _setPeerStatus(remoteCode, 'online');
      } else if (state == RTCPeerConnectionState.RTCPeerConnectionStateConnecting) {
        _setPeerStatus(remoteCode, 'connecting');
      } else if (state == RTCPeerConnectionState.RTCPeerConnectionStateDisconnected ||
                 state == RTCPeerConnectionState.RTCPeerConnectionStateFailed ||
                 state == RTCPeerConnectionState.RTCPeerConnectionStateClosed) {
        _setPeerStatus(remoteCode, 'offline');
      }
    };

    _p2p.onDataChannelStateChange = (state) {
      if (state == RTCDataChannelState.RTCDataChannelOpen) {
        _setPeerStatus(remoteCode, 'online');
      } else if (state == RTCDataChannelState.RTCDataChannelClosed) {
        _setPeerStatus(remoteCode, 'offline');
      }
    };

    // Handle incoming encrypted messages
    _p2p.onMessageReceived = (rawPayload) async {
      try {
        final ciphertext = base64Decode(rawPayload);
        final plaintext = await _signal.decryptMessage(
          remoteCode,
          ciphertext.toList(),
        );

        // Save incoming decrypted message to local SQLCipher database
        await DatabaseHelper().saveMessage(
          remoteCode: remoteCode,
          content: plaintext,
          isMe: false,
          timestamp: DateTime.now(),
        );

        onSecureMessageReceived?.call(plaintext, remoteCode);
      } catch (e) {
        // ignore: avoid_print
        print('Decryption error: $e');
      }
    };
  }

  void _setPeerStatus(String peerCode, String status) {
    _peerStatus[peerCode] = status;
    onPeerStatusChanged?.call(peerCode, status);
  }

  String getPeerStatus(String peerCode) {
    return _peerStatus[peerCode] ?? 'offline';
  }

  /// Sends a Signal-encrypted message over the P2P DataChannel and persists to local DB.
  Future<void> sendSecureMessage(
    String targetCode,
    String plaintext,
  ) async {
    // Ensure session exists
    final hasSession = await _signal.hasSession(targetCode);
    if (!hasSession) {
      await fetchAndProcessPreKeyBundle(targetCode);
    }

    final ciphertext = await _signal.encryptMessage(targetCode, plaintext);
    final payload = base64Encode(ciphertext);
    await _p2p.sendRawData(payload);

    // Save sent message to local SQLCipher database
    await DatabaseHelper().saveMessage(
      remoteCode: targetCode,
      content: plaintext,
      isMe: true,
      timestamp: DateTime.now(),
    );
  }

  /// Queries the signaling server if a user is currently online.
  Future<bool> checkPeerPresence(String targetCode) async {
    final socket = _socket;
    if (socket == null || !socket.connected) return false;

    final completer = Completer<bool>();
    socket.emitWithAck('check_presence', targetCode, ack: (response) {
      if (response != null && response['online'] != null) {
        completer.complete(response['online'] == true);
      } else {
        completer.complete(false);
      }
    });
    return completer.future.timeout(
      const Duration(seconds: 3),
      onTimeout: () => false,
    );
  }

  /// Disconnects from the signaling server and releases P2P resources.
  Future<void> disconnect() async {
    try {
      _socket?.disconnect();
      _socket?.dispose();
    } catch (_) {}
    _socket = null;
    _isSignalingConnected = false;
    await _p2p.dispose();
  }
}
