import 'package:flutter_test/flutter_test.dart';
import 'package:codechat_client/main.dart';

void main() {
  testWidgets('App smoke test loads CodeChatApp', (WidgetTester tester) async {
    await tester.pumpWidget(const CodeChatApp());
    expect(find.byType(CodeChatApp), findsOneWidget);
  });
}
