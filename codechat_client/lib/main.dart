import 'package:flutter/material.dart';
import 'ui/theme/app_theme.dart';
import 'ui/onboarding/onboarding_screen.dart';
import 'ui/chat/chat_screen.dart';

void main() {
  runApp(const CodeChatApp());
}

class CodeChatApp extends StatelessWidget {
  const CodeChatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CodeChat',
      theme: CodeChatTheme.darkTheme,
      home: const OnboardingScreen(),
      routes: {
        '/home': (context) => const ChatScreen(remoteCode: 'Unknown'),
      },
    );
  }
}