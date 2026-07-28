import 'dart:async';
import 'dart:io' show Platform;
import 'package:socket_io_client/socket_io_client.dart' as IO;

/// Represents a push wakeup event received when device is backgrounded.
class PushWakeupEvent {
  final String fromCode;
  final String signalType;
  final DateTime timestamp;

  PushWakeupEvent({
    required this.fromCode,
    required this.signalType,
    required this.timestamp,
  });
}

/// Service for managing FCM / APNs Push Tokens and background wakeup triggers.
class PushService {
  String? _pushToken;
  final _wakeupStreamController = StreamController<PushWakeupEvent>.broadcast();

  Stream<PushWakeupEvent> get onBackgroundWakeup => _wakeupStreamController.stream;

  PushService({String? initialToken}) : _pushToken = initialToken;

  /// Gets current registered FCM/APNs push token.
  String? get pushToken => _pushToken;

  /// Sets or updates the device push token.
  void setPushToken(String token) {
    _pushToken = token;
  }

  /// Detects the underlying device platform name ('android', 'ios', or 'desktop').
  String get platformName {
    try {
      if (Platform.isAndroid) return 'android';
      if (Platform.isIOS) return 'ios';
      return 'desktop';
    } catch (_) {
      return 'unknown';
    }
  }

  /// Registers the push token with the active signaling Socket.IO connection.
  void registerPushTokenWithSocket(IO.Socket socket) {
    if (_pushToken == null || _pushToken!.isEmpty) return;
    if (socket.connected) {
      socket.emit('register_push_token', {
        'token': _pushToken,
        'platform': platformName,
      });
    }
  }

  /// Processes incoming FCM / APNs background data payloads.
  /// Wakes up the app connection pipeline if an incoming P2P connection request is detected.
  void handleIncomingPushPayload(Map<String, dynamic> data) {
    final type = data['type'] as String?;
    final fromCode = data['fromCode'] as String?;

    if (type == 'connection_request' && fromCode != null && fromCode.isNotEmpty) {
      final signalType = (data['signalType'] as String?) ?? 'offer';
      final event = PushWakeupEvent(
        fromCode: fromCode,
        signalType: signalType,
        timestamp: DateTime.now(),
      );
      _wakeupStreamController.add(event);
    }
  }

  /// Closes stream resources.
  void dispose() {
    _wakeupStreamController.close();
  }
}
