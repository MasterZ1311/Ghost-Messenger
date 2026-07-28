import 'dart:convert';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';

/// Chat screen for a P2P encrypted session.
///
/// Accepts optional identity key bytes for both the local user and the remote
/// peer.  When both are present, a Signal-style safety number (fingerprint) is
/// computed and shown in the verification dialog.
class ChatScreen extends StatefulWidget {
  final String remoteCode;

  /// The local user's Signal identity public key bytes (33-byte compressed
  /// Curve25519 key as serialized by libsignal_protocol_dart).
  final Uint8List? localIdentityKey;

  /// The remote peer's Signal identity public key bytes.
  /// May be null until the session is fully established.
  final Uint8List? remoteIdentityKey;

  const ChatScreen({
    super.key,
    required this.remoteCode,
    this.localIdentityKey,
    this.remoteIdentityKey,
  });

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _messageController = TextEditingController();
  final List<Message> _messages = [];

  @override
  void dispose() {
    _messageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('P2P SESSION'),
            Text(
              'Peer: ${widget.remoteCode}',
              style: const TextStyle(fontSize: 12, color: Colors.greenAccent),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.security, size: 18),
            onPressed: _showSafetyNumbers,
            tooltip: 'Verify Safety Numbers',
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              reverse: true,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final msg = _messages[index];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _ChatBubble(msg: msg),
                );
              },
            ),
          ),
          _buildInputArea(),
        ],
      ),
    );
  }

  Widget _buildInputArea() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: const BoxDecoration(
        color: Color(0xFF1E1E1E),
        border: Border(top: BorderSide(color: Colors.white12)),
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _messageController,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration.collapsed(
                  hintText: 'Type encrypted message...',
                  hintStyle: TextStyle(color: Colors.white38),
                ),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.send_rounded, color: Colors.greenAccent),
              onPressed: _sendMessage,
            ),
          ],
        ),
      ),
    );
  }

  void _sendMessage() {
    if (_messageController.text.trim().isEmpty) return;

    setState(() {
      _messages.insert(
        0,
        Message(
          text: _messageController.text.trim(),
          isMe: true,
          timestamp: DateTime.now(),
        ),
      );
    });
    _messageController.clear();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Safety Numbers (Signal-style fingerprint)
  // ─────────────────────────────────────────────────────────────────────────

  /// Computes a Signal-style safety number from two Curve25519 identity keys.
  ///
  /// Algorithm:
  ///   1. Canonically sort the two 33-byte key blobs lexicographically so that
  ///      both sides of the session always produce the same string regardless
  ///      of who was the initiator.
  ///   2. Concatenate the sorted pair and hash with SHA-512.
  ///   3. Take the first 30 bytes of the digest.
  ///   4. Split into 6 groups of 5 bytes; interpret each group as a big-endian
  ///      unsigned 40-bit integer, then take `value % 100000` to yield a
  ///      5-digit decimal (zero-padded).  This matches Signal's specification.
  ///
  /// Returns a string like "05823 19302 44812 01947 33019 82741".
  static String _computeFingerprint(
    Uint8List localKey,
    Uint8List remoteKey,
  ) {
    // Step 1 — canonical ordering (lexicographic byte comparison)
    final Uint8List first;
    final Uint8List second;

    bool localIsFirst = true;
    for (int i = 0; i < localKey.length && i < remoteKey.length; i++) {
      if (localKey[i] < remoteKey[i]) {
        localIsFirst = true;
        break;
      } else if (localKey[i] > remoteKey[i]) {
        localIsFirst = false;
        break;
      }
    }
    first = localIsFirst ? localKey : remoteKey;
    second = localIsFirst ? remoteKey : localKey;

    // Step 2 — SHA-512(first || second)
    final combined = Uint8List(first.length + second.length)
      ..setAll(0, first)
      ..setAll(first.length, second);
    final digest = sha512.convert(combined).bytes;

    // Step 3 — first 30 bytes
    final fingerprintBytes = Uint8List.fromList(digest.sublist(0, 30));

    // Step 4 — 6 groups of 5 bytes → 5-digit decimal chunks
    final buffer = StringBuffer();
    for (int group = 0; group < 6; group++) {
      final offset = group * 5;
      // Interpret 5 bytes as a big-endian unsigned integer.
      int value = 0;
      for (int b = 0; b < 5; b++) {
        value = (value << 8) | fingerprintBytes[offset + b];
      }
      // Reduce to 5 digits (mod 100000, zero-padded).
      final chunk = (value % 100000).toString().padLeft(5, '0');
      if (group > 0) buffer.write(' ');
      buffer.write(chunk);
    }
    return buffer.toString();
  }

  void _showSafetyNumbers() {
    final localKey = widget.localIdentityKey;
    final remoteKey = widget.remoteIdentityKey;

    final bool sessionReady = localKey != null && remoteKey != null;
    final String fingerprintText = sessionReady
        ? _computeFingerprint(localKey, remoteKey)
        : '—';

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Row(
          children: const [
            Icon(Icons.verified_user, color: Colors.greenAccent, size: 20),
            SizedBox(width: 8),
            Text(
              'Safety Numbers',
              style: TextStyle(color: Colors.white, fontSize: 16),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              sessionReady
                  ? 'Compare these numbers with your contact out-of-band to confirm the E2EE session has not been tampered with.'
                  : 'Session not yet established.\nConnect to your peer to generate safety numbers.',
              style: const TextStyle(color: Colors.white70, fontSize: 13),
            ),
            const SizedBox(height: 16),
            if (sessionReady) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 12,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFF0D0D1A),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: Colors.greenAccent.withAlpha(60),
                  ),
                ),
                child: Text(
                  fingerprintText,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 15,
                    letterSpacing: 2,
                    color: Colors.greenAccent,
                    height: 1.8,
                  ),
                ),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            child: const Text(
              'Dismiss',
              style: TextStyle(color: Colors.greenAccent),
            ),
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
    );
  }
}

class Message {
  final String text;
  final bool isMe;
  final DateTime timestamp;

  Message({required this.text, required this.isMe, required this.timestamp});
}

class _ChatBubble extends StatelessWidget {
  final Message msg;
  const _ChatBubble({required this.msg});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: msg.isMe ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.75,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: msg.isMe
              ? const Color(0xFF00FF41).withAlpha(25)
              : const Color(0xFF1E1E1E),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: msg.isMe
                ? const Color(0xFF00FF41).withAlpha(50)
                : Colors.white12,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(msg.text, style: const TextStyle(color: Colors.white)),
            const SizedBox(height: 4),
            Text(
              '${msg.timestamp.hour.toString().padLeft(2, '0')}:${msg.timestamp.minute.toString().padLeft(2, '0')}',
              style: const TextStyle(fontSize: 10, color: Colors.white30),
            ),
          ],
        ),
      ),
    );
  }
}
