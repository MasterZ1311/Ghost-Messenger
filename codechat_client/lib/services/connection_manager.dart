import 'dart:convert';
import 'package:socket_io_client/socket_io_client.dart' as IO;
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'p2p_service.dart';
import 'signal_service.dart';

/// Orchestrates Signaling, WebRTC, and Signal Protocol encryption flows.
class ConnectionManager {
  late IO.Socket _socket;
  final P2PService _p2p = P2PService();
  final SignalService _signal;
  final String _localUserCode;

  // External callbacks
  Function(String message, String fromCode)? onSecureMessageReceived;
  Function(String fromCode)? onIncomingConnection;

  ConnectionManager(this._signal, this._localUserCode);

  /// Connects to the signaling server and registers the local user.
  void connect(String serverUrl) {
    _socket = IO.io(serverUrl, <String, dynamic>{
      'transports': ['websocket'],
      'autoConnect': false,
    });

    _socket.connect();

    _socket.onConnect((_) {
      // ignore: avoid_print
      print('Connected to Signaling Server');
      _socket.emit('join', _localUserCode);
    });

    _socket.onDisconnect((_) {
      // ignore: avoid_print
      print('Disconnected from Signaling Server');
    });

    // Handle incoming signaling data from the relay
    _socket.on('signal', (data) async {
      final fromCode = data['fromCode'] as String;
      final signal = data['signalData'] as Map<String, dynamic>;
      final type = signal['type'] as String;

      if (type == 'offer') {
        await _handleOffer(fromCode, signal);
      } else if (type == 'answer') {
        await _handleAnswer(signal);
      } else if (type == 'candidate') {
        await _handleCandidate(signal);
      }
    });

    _socket.on('error_message', (data) {
      // ignore: avoid_print
      print('Signaling error: $data');
    });
  }

  /// Initiates a secure P2P connection to the target peer.
  Future<void> initiateSecureConnection(String targetCode) async {
    await _p2p.initializePeerConnection();
    _setupP2PCallbacks(targetCode);

    // Create WebRTC Offer
    RTCSessionDescription offer = await _p2p.createOffer();

    _socket.emit('signal', {
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
    onIncomingConnection?.call(fromCode);
    await _p2p.initializePeerConnection();
    _setupP2PCallbacks(fromCode);

    RTCSessionDescription offer = RTCSessionDescription(
      signal['sdp'] as String?,
      'offer',
    );
    RTCSessionDescription answer = await _p2p.createAnswer(offer);

    _socket.emit('signal', {
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
      _socket.emit('signal', {
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

    // Handle incoming encrypted messages
    _p2p.onMessageReceived = (rawPayload) async {
      try {
        final ciphertext = base64Decode(rawPayload);
        final plaintext = await _signal.decryptMessage(
          remoteCode,
          ciphertext.toList(),
        );
        onSecureMessageReceived?.call(plaintext, remoteCode);
      } catch (e) {
        // ignore: avoid_print
        print('Decryption error: $e');
      }
    };
  }

  /// Sends a Signal-encrypted message over the P2P DataChannel.
  Future<void> sendSecureMessage(
    String targetCode,
    String plaintext,
  ) async {
    final ciphertext = await _signal.encryptMessage(targetCode, plaintext);
    final payload = base64Encode(ciphertext);
    await _p2p.sendRawData(payload);
  }

  /// Disconnects from the signaling server and releases P2P resources.
  Future<void> disconnect() async {
    _socket.disconnect();
    await _p2p.dispose();
  }
}
