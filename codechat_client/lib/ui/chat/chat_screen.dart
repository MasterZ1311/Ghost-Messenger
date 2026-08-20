import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../../core/storage/database_helper.dart';
import '../../services/connection_manager.dart';

/// Chat screen for a live P2P encrypted session.
class ChatScreen extends StatefulWidget {
  final String remoteCode;
  final ConnectionManager connectionManager;
  final Uint8List? localIdentityKey;
  final Uint8List? remoteIdentityKey;

  const ChatScreen({
    super.key,
    required this.remoteCode,
    required this.connectionManager,
    this.localIdentityKey,
    this.remoteIdentityKey,
  });

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _messageController = TextEditingController();
  final List<Message> _messages = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadHistory();
    _setupListeners();
  }

  Future<void> _loadHistory() async {
    final rows = await DatabaseHelper().getMessagesForPeer(widget.remoteCode);
    if (mounted) {
      setState(() {
        _messages.clear();
        for (final row in rows.reversed) {
          _messages.add(
            Message(
              text: row['content'] as String,
              isMe: (row['is_me'] as int) == 1,
              timestamp: DateTime.parse(row['timestamp'] as String),
            ),
          );
        }
        _isLoading = false;
      });
    }
  }

  void _setupListeners() {
    widget.connectionManager.onSecureMessageReceived = (plaintext, fromCode) {
      if (fromCode == widget.remoteCode && mounted) {
        setState(() {
          _messages.insert(
            0,
            Message(
              text: plaintext,
              isMe: false,
              timestamp: DateTime.now(),
            ),
          );
        });
      }
    };

    widget.connectionManager.onPeerStatusChanged = (peerCode, status) {
      if (peerCode == widget.remoteCode && mounted) {
        setState(() {});
      }
    };

    // Auto-initiate WebRTC P2P connection to peer
    widget.connectionManager.initiateSecureConnection(widget.remoteCode);
  }

  @override
  void dispose() {
    _messageController.dispose();
    super.dispose();
  }

  Color _getStatusColor(String status) {
    switch (status) {
      case 'online':
        return const Color(0xFF00FF41);
      case 'connecting':
        return Colors.amberAccent;
      default:
        return Colors.white38;
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = widget.connectionManager.getPeerStatus(widget.remoteCode);

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.remoteCode,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                letterSpacing: 2,
                fontSize: 16,
              ),
            ),
            Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: _getStatusColor(status),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  status.toUpperCase(),
                  style: TextStyle(
                    fontSize: 10,
                    color: _getStatusColor(status),
                    fontWeight: FontWeight.bold,
                    letterSpacing: 1,
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.verified_user_rounded, size: 20),
            color: const Color(0xFF00FF41),
            onPressed: _showSafetyNumbers,
            tooltip: 'Verify Safety Numbers',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(
                valueColor: AlwaysStoppedAnimation(Color(0xFF00FF41)),
              ),
            )
          : Column(
              children: [
                Expanded(
                  child: _messages.isEmpty
                      ? Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(
                                Icons.lock_outline_rounded,
                                size: 48,
                                color: Colors.white24,
                              ),
                              const SizedBox(height: 12),
                              Text(
                                'End-to-End Encrypted Session\nMessages with ${widget.remoteCode} are secure.',
                                textAlign: TextAlign.center,
                                style: const TextStyle(color: Colors.white38, fontSize: 13),
                              ),
                            ],
                          ),
                        )
                      : ListView.builder(
                          reverse: true,
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
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
                onSubmitted: (_) => _sendMessage(),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.send_rounded, color: Color(0xFF00FF41)),
              onPressed: _sendMessage,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _sendMessage() async {
    final text = _messageController.text.trim();
    if (text.isEmpty) return;

    _messageController.clear();
    setState(() {
      _messages.insert(
        0,
        Message(
          text: text,
          isMe: true,
          timestamp: DateTime.now(),
        ),
      );
    });

    try {
      await widget.connectionManager.sendSecureMessage(widget.remoteCode, text);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Send error: $e')),
        );
      }
    }
  }

  Future<void> _showSafetyNumbers() async {
    final fingerprint = await widget.connectionManager.signalService
        .computeSafetyNumbers(widget.remoteCode);

    if (!mounted) return;

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: Colors.white12),
        ),
        title: const Row(
          children: [
            Icon(Icons.verified_user_rounded, color: Color(0xFF00FF41), size: 22),
            SizedBox(width: 10),
            Text(
              'Safety Numbers',
              style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Compare these numbers with your contact out-of-band to confirm your end-to-end encryption is untampered:',
              style: TextStyle(color: Colors.white70, fontSize: 13),
            ),
            const SizedBox(height: 16),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
              decoration: BoxDecoration(
                color: Colors.black38,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFF00FF41).withAlpha(80)),
              ),
              child: Text(
                fingerprint,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 16,
                  letterSpacing: 3,
                  color: Color(0xFF00FF41),
                  height: 1.6,
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            child: const Text('Dismiss', style: TextStyle(color: Color(0xFF00FF41))),
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
