import 'dart:convert';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:http/http.dart' as http;

/// Callback types for P2P events
typedef OnIceCandidateCallback = void Function(RTCIceCandidate candidate);
typedef OnMessageCallback = void Function(String message);
typedef OnConnectionStateCallback = void Function(RTCPeerConnectionState state);
typedef OnDataChannelStateCallback = void Function(RTCDataChannelState state);

/// Manages WebRTC PeerConnection and DataChannel for P2P messaging.
class P2PService {
  RTCPeerConnection? _peerConnection;
  RTCDataChannel? _dataChannel;

  // External callbacks
  OnMessageCallback? onMessageReceived;
  OnIceCandidateCallback? onIceCandidate;
  OnConnectionStateCallback? onConnectionStateChange;
  OnDataChannelStateCallback? onDataChannelStateChange;

  /// Base URL of the signaling server (http/https), set via [setSignalingUrl].
  String? _signalingBaseUrl;

  /// Fallback ICE config used when the server endpoint is unreachable.
  static const Map<String, dynamic> _fallbackIceConfig = {
    'iceServers': [
      {'urls': 'stun:stun.l.google.com:19302'},
      {'urls': 'stun:stun1.l.google.com:19302'},
    ]
  };

  /// Stores the signaling server base URL for ICE config fetching.
  /// Accepts ws:// or wss:// URLs and converts them to http:// / https://.
  void setSignalingUrl(String url) {
    var base = url.trimRight();
    if (base.endsWith('/')) base = base.substring(0, base.length - 1);
    base = base.replaceFirst(RegExp(r'^wss://'), 'https://');
    base = base.replaceFirst(RegExp(r'^ws://'), 'http://');
    _signalingBaseUrl = base;
  }

  /// Fetches the ICE server configuration from the signaling server.
  /// Falls back to Google STUN servers on any failure.
  Future<Map<String, dynamic>> _fetchIceConfig() async {
    final baseUrl = _signalingBaseUrl;
    if (baseUrl == null) return _fallbackIceConfig;

    try {
      final response = await http
          .get(Uri.parse('$baseUrl/api/ice-servers'))
          .timeout(const Duration(seconds: 5));

      if (response.statusCode == 200) {
        final decoded = jsonDecode(response.body);
        if (decoded is Map<String, dynamic> && decoded.containsKey('iceServers')) {
          return {'iceServers': decoded['iceServers']};
        }
        // Some servers return a bare list
        if (decoded is List) {
          return {'iceServers': decoded};
        }
      }
    } catch (e) {
      // ignore: avoid_print
      print('Failed to fetch ICE config from server, using fallback: $e');
    }

    return _fallbackIceConfig;
  }

  /// Initializes a new PeerConnection.
  Future<void> initializePeerConnection() async {
    // Dispose previous connection if any
    await dispose();

    final config = await _fetchIceConfig();
    _peerConnection = await createPeerConnection(config);

    // Relay ICE candidates to signaling server
    _peerConnection!.onIceCandidate = (candidate) {
      onIceCandidate?.call(candidate);
    };

    // Handle incoming data channel (answerer side)
    _peerConnection!.onDataChannel = (channel) {
      _dataChannel = channel;
      _setupDataChannelListeners();
    };

    // Track connection state changes
    _peerConnection!.onConnectionState = (state) {
      onConnectionStateChange?.call(state);
    };
  }

  /// Creates an SDP offer (offerer / initiator side).
  Future<RTCSessionDescription> createOffer() async {
    RTCDataChannelInit dataChannelDict = RTCDataChannelInit()
      ..ordered = true; // Required for Signal Protocol message ordering

    _dataChannel = await _peerConnection!.createDataChannel(
      'chat',
      dataChannelDict,
    );
    _setupDataChannelListeners();

    RTCSessionDescription offer = await _peerConnection!.createOffer();
    await _peerConnection!.setLocalDescription(offer);
    return offer;
  }

  /// Creates an SDP answer (answerer side).
  Future<RTCSessionDescription> createAnswer(
    RTCSessionDescription offer,
  ) async {
    await _peerConnection!.setRemoteDescription(offer);
    RTCSessionDescription answer = await _peerConnection!.createAnswer();
    await _peerConnection!.setLocalDescription(answer);
    return answer;
  }

  /// Sets the remote SDP description (offerer receives answer).
  Future<void> setRemoteDescription(RTCSessionDescription description) async {
    await _peerConnection?.setRemoteDescription(description);
  }

  /// Adds a remote ICE candidate.
  Future<void> addIceCandidate(RTCIceCandidate candidate) async {
    await _peerConnection?.addCandidate(candidate);
  }

  /// Sends raw string data over the data channel.
  Future<void> sendRawData(String data) async {
    if (_dataChannel != null &&
        _dataChannel!.state == RTCDataChannelState.RTCDataChannelOpen) {
      _dataChannel!.send(RTCDataChannelMessage(data));
    }
  }

  /// Cleans up connection resources.
  Future<void> dispose() async {
    await _dataChannel?.close();
    _dataChannel = null;
    await _peerConnection?.close();
    _peerConnection = null;
  }

  void _setupDataChannelListeners() {
    _dataChannel!.onMessage = (data) {
      onMessageReceived?.call(data.text);
    };

    _dataChannel!.onDataChannelState = (state) {
      // ignore: avoid_print
      print('DataChannel state: $state');
      onDataChannelStateChange?.call(state);
    };
  }
}
