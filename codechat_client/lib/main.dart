import 'package:flutter/material.dart';
import 'ui/theme/app_theme.dart';
import 'ui/onboarding/onboarding_screen.dart';
import 'ui/onboarding/restore_screen.dart';
import 'ui/home/home_screen.dart';
import 'services/app_state.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CodeChatApp());
}

class CodeChatApp extends StatefulWidget {
  const CodeChatApp({super.key});

  @override
  State<CodeChatApp> createState() => _CodeChatAppState();
}

class _CodeChatAppState extends State<CodeChatApp> {
  final AppState _appState = AppState.instance;

  @override
  void initState() {
    super.initState();
    _appState.initialize();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ghost Messenger',
      debugShowCheckedModeBanner: false,
      theme: CodeChatTheme.darkTheme,
      home: AnimatedBuilder(
        animation: _appState,
        builder: (context, child) {
          if (_appState.isLoading) {
            return const Scaffold(
              body: Center(
                child: CircularProgressIndicator(
                  color: Color(0xFF00FF41),
                ),
              ),
            );
          }
          if (_appState.isInitialized) {
            return const HomeScreen();
          }
          return const OnboardingScreen();
        },
      ),
      routes: {
        '/onboarding': (context) => const OnboardingScreen(),
        '/restore': (context) => const RestoreScreen(),
        '/home': (context) => const HomeScreen(),
      },
    );
  }
}