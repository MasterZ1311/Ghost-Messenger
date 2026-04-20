import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/crypto/key_manager.dart';
import '../../core/crypto/user_code_utils.dart';

/// Onboarding screen that generates identity and displays the User Code.
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  String? _mnemonic;
  String? _userCode;
  bool _showMnemonic = false;

  Future<void> _generateIdentity() async {
    final mnemonic = KeyManager.generateMnemonic();
    final identityKeyPair = KeyManager.generateRegistrationKeyPair();

    // Derive user code from the public identity key
    final publicKeyBytes = identityKeyPair.getPublicKey().serialize();
    final userCode = UserCodeUtils.generateUserCode(publicKeyBytes);

    setState(() {
      _mnemonic = mnemonic;
      _userCode = userCode;
      _showMnemonic = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(
                  Icons.security,
                  size: 80,
                  color: Color(0xFF00FF41),
                ),
                const SizedBox(height: 24),
                const Text(
                  'CodeChat',
                  style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                const Text(
                  'No phone number. No email. Just your code.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white70),
                ),
                const SizedBox(height: 48),
                if (!_showMnemonic)
                  ElevatedButton(
                    onPressed: _generateIdentity,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00FF41),
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 32,
                        vertical: 16,
                      ),
                    ),
                    child: const Text('GENERATE IDENTITY'),
                  ),
                if (_showMnemonic) _buildIdentityCard(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildIdentityCard() {
    return Column(
      children: [
        const Text(
          'Your Permanent User Code:',
          style: TextStyle(color: Colors.greenAccent, fontSize: 12),
        ),
        const SizedBox(height: 4),
        Text(
          _userCode!,
          style: const TextStyle(
            fontSize: 36,
            fontWeight: FontWeight.bold,
            letterSpacing: 4,
          ),
        ),
        const SizedBox(height: 24),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white.withAlpha(12),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Colors.white10),
          ),
          child: Column(
            children: [
              const Text(
                'Recovery Phrase (BIP39)',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  color: Colors.orangeAccent,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                _mnemonic!,
                textAlign: TextAlign.center,
                style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
              ),
              const SizedBox(height: 12),
              const Text(
                'Write this down. It is the only way to recover your account.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 10, color: Colors.white30),
              ),
            ],
          ),
        ),
        const SizedBox(height: 32),
        TextButton(
          onPressed: () {
            Clipboard.setData(ClipboardData(text: _mnemonic!));
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Mnemonic copied to clipboard')),
            );
          },
          child: const Text('COPY MNEMONIC'),
        ),
        const SizedBox(height: 16),
        ElevatedButton(
          onPressed: () => Navigator.pushReplacementNamed(context, '/home'),
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF00FF41),
            foregroundColor: Colors.black,
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
          ),
          child: const Text('START CHATTING'),
        ),
      ],
    );
  }
}
