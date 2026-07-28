import 'package:flutter_test/flutter_test.dart';
import 'package:codechat_client/services/push_service.dart';

void main() {
  group('PushService Unit Tests', () {
    test('Token management', () {
      final pushService = PushService(initialToken: 'initial_token_123');
      expect(pushService.pushToken, equals('initial_token_123'));

      pushService.setPushToken('new_token_456');
      expect(pushService.pushToken, equals('new_token_456'));
    });

    test('Platform detection returns string', () {
      final pushService = PushService();
      expect(pushService.platformName, isNotEmpty);
    });

    test('handleIncomingPushPayload triggers onBackgroundWakeup event', () async {
      final pushService = PushService();

      final wakeupFuture = pushService.onBackgroundWakeup.first;

      pushService.handleIncomingPushPayload({
        'type': 'connection_request',
        'fromCode': 'PEER_X99',
        'signalType': 'offer',
        'timestamp': '1690000000000',
      });

      final event = await wakeupFuture;
      expect(event.fromCode, equals('PEER_X99'));
      expect(event.signalType, equals('offer'));
    });

    test('Ignores irrelevant push payload types', () async {
      final pushService = PushService();
      bool triggered = false;

      pushService.onBackgroundWakeup.listen((_) {
        triggered = true;
      });

      pushService.handleIncomingPushPayload({
        'type': 'random_notification',
        'fromCode': 'PEER_X99',
      });

      await Future.delayed(const Duration(milliseconds: 50));
      expect(triggered, isFalse);
    });
  });
}
