import 'package:flutter/material.dart';
import '../../core/crypto/user_code_utils.dart';

/// Modal dialog for starting a new chat with a peer by entering their 8-character UserCode.
class ContactDialog extends StatefulWidget {
  const ContactDialog({super.key});

  static Future<String?> show(BuildContext context) {
    return showDialog<String>(
      context: context,
      builder: (context) => const ContactDialog(),
    );
  }

  @override
  State<ContactDialog> createState() => _ContactDialogState();
}

class _ContactDialogState extends State<ContactDialog> {
  final TextEditingController _codeController = TextEditingController();
  String? _errorText;

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  void _validateAndSubmit() {
    final input = _codeController.text.trim().toUpperCase();
    if (input.isEmpty) {
      setState(() => _errorText = 'Please enter a UserCode');
      return;
    }

    if (!UserCodeUtils.isValidFormat(input)) {
      setState(() => _errorText = 'Invalid format. Use 8 characters (e.g. 5J9L-2P4X)');
      return;
    }

    final formatted = input.contains('-')
        ? input
        : '${input.substring(0, 4)}-${input.substring(4)}';

    Navigator.of(context).pop(formatted);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF1E1E1E),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Colors.white12),
      ),
      title: const Row(
        children: [
          Icon(Icons.person_add_alt_1_rounded, color: Color(0xFF00FF41)),
          SizedBox(width: 10),
          Text(
            'New P2P Session',
            style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
          ),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Enter the 8-character UserCode of the peer you want to message:',
            style: TextStyle(color: Colors.white70, fontSize: 13),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _codeController,
            autofocus: true,
            textCapitalization: TextCapitalization.characters,
            style: const TextStyle(
              color: Colors.white,
              fontFamily: 'monospace',
              fontSize: 18,
              letterSpacing: 2,
            ),
            decoration: InputDecoration(
              hintText: 'e.g. 5J9L-2P4X',
              hintStyle: const TextStyle(color: Colors.white30, letterSpacing: 2),
              errorText: _errorText,
              filled: true,
              fillColor: Colors.black26,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: Colors.white24),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: Color(0xFF00FF41)),
              ),
            ),
            onSubmitted: (_) => _validateAndSubmit(),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel', style: TextStyle(color: Colors.white54)),
        ),
        ElevatedButton(
          onPressed: _validateAndSubmit,
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF00FF41),
            foregroundColor: Colors.black,
          ),
          child: const Text('Connect', style: TextStyle(fontWeight: FontWeight.bold)),
        ),
      ],
    );
  }
}
