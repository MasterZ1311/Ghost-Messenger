import 'package:flutter/material.dart';
import 'ui/theme/app_theme.dart';
import 'ui/onboarding/onboarding_screen.dart';
import 'ui/onboarding/restore_screen.dart';
import 'ui/home/home_screen.dart';
import 'services/app_state.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Catch Flutter framework errors (e.g. widget build failures) and show
  // a safe fallback instead of crashing the app.
  FlutterError.onError = (FlutterErrorDetails details) {
    FlutterError.presentError(details);
    // ignore: avoid_print
    print('Flutter error: ${details.exceptionAsString()}');
  };

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

          // Surface initialization errors to the user instead of a frozen screen.
          if (_appState.initError != null) {
            return Scaffold(
              body: SafeArea(
                child: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline_rounded,
                            color: Colors.redAccent, size: 56),
                        const SizedBox(height: 16),
                        const Text(
                          'Initialization Error',
                          style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                              color: Colors.white),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          _appState.initError!,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                              color: Colors.white60, fontSize: 13),
                        ),
                        const SizedBox(height: 24),
                        ElevatedButton(
                          onPressed: () => _appState.initialize(),
                          child: const Text('RETRY'),
                        ),
                      ],
                    ),
                  ),
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
