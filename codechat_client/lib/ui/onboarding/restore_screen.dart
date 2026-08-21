import 'package:flutter/material.dart';
import '../../services/app_state.dart';

class RestoreScreen extends StatefulWidget {
  const RestoreScreen({super.key});

  @override
  State<RestoreScreen> createState() => _RestoreScreenState();
}

class _RestoreScreenState extends State<RestoreScreen> {
  final TextEditingController _mnemonicController = TextEditingController();
  bool _isRestoring = false;
  String? _errorMessage;

  @override
  void dispose() {
    _mnemonicController.dispose();
    super.dispose();
  }

  Future<void> _handleRestore() async {
    final mnemonic = _mnemonicController.text.trim();
    if (mnemonic.isEmpty) {
      setState(() => _errorMessage = 'Please enter your 12-word seed phrase');
      return;
    }
    setState(() { _isRestoring = true; _errorMessage = null; });
    try {
      final success = await AppState.instance.restoreIdentityFromMnemonic(mnemonic);
      if (!mounted) return;
      setState(() => _isRestoring = false);
      if (success) {
        Navigator.pushNamedAndRemoveUntil(context, '/home', (route) => false);
      } else {
        setState(() => _errorMessage = 'Invalid 12-word mnemonic phrase. Please check and try again.');
      }
    } catch (e) {
      if (!mounted) return;
      setState(() { _isRestoring = false; _errorMessage = 'Restore failed: $e'; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('RESTORE IDENTITY'),
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(28.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Image.asset(
                  'assets/images/logo.png',
                  width: 80,
                  height: 80,
                  fit: BoxFit.contain,
                ),
                const SizedBox(height: 24),
                const Text(
                  'Restore from Mnemonic',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Enter your 12-word BIP39 seed phrase below to restore your cryptographic identity and keys.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white60, fontSize: 13),
                ),
                const SizedBox(height: 32),
                TextField(
                  controller: _mnemonicController,
                  maxLines: 3,
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 14,
                    color: Colors.white,
                  ),
                  decoration: InputDecoration(
                    hintText: 'e.g. abandon amount quality trigger word ...',
                    errorText: _errorMessage,
                  ),
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: _isRestoring ? null : _handleRestore,
                  child: _isRestoring
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.black,
                          ),
                        )
                      : const Text('RESTORE IDENTITY'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
