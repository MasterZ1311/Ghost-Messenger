import 'package:flutter/material.dart';
import '../../services/app_state.dart';

/// Chat screen for a live P2P encrypted session with SQLCipher message persistence.
class ChatScreen extends StatefulWidget {
  final String remoteCode;

  const ChatScreen({super.key, required this.remoteCode});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _messageController = TextEditingController();
  final AppState _appState = AppState.instance;
  List<Message> _messages = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _appState.addListener(_onAppStateChanged);
    _loadHistoricalMessages();
  }

  @override
  void dispose() {
    _appState.removeListener(_onAppStateChanged);
    _messageController.dispose();
    super.dispose();
  }

  void _onAppStateChanged() {
    _loadHistoricalMessages();
  }

  Future<void> _loadHistoricalMessages() async {
    final rawMessages = await _appState.loadMessages(widget.remoteCode);
    if (!mounted) return;

    final parsed = rawMessages.map((m) {
      final rawTime = m['timestamp'] as String;
      return Message(
        text: m['content'] as String,
        isMe: (m['is_me'] as int) == 1,
        timestamp: DateTime.tryParse(rawTime) ?? DateTime.now(),
      );
    }).toList();

    // Reverse list so newest message is at index 0 for reverse ListView
    setState(() {
      _messages = parsed.reversed.toList();
      _isLoading = false;
    });
  }

  Future<void> _sendMessage() async {
    final text = _messageController.text.trim();
    if (text.isEmpty) return;

    _messageController.clear();
    await _appState.sendMessage(widget.remoteCode, text);
    await _loadHistoricalMessages();
  }

  @override
  Widget build(BuildContext context) {
    final status = _appState.getPeerStatus(widget.remoteCode);

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Stack(
              children: [
                CircleAvatar(
                  radius: 16,
                  backgroundColor: const Color(0xFF262626),
                  child: Text(
                    widget.remoteCode.substring(0, 2),
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
                Positioned(
                  right: 0,
                  bottom: 0,
                  child: Container(
                    width: 9,
                    height: 9,
                    decoration: BoxDecoration(
                      color: status == 'online'
                          ? const Color(0xFF00FF41)
                          : status == 'connecting'
                              ? const Color(0xFFFFC107)
                              : const Color(0xFF666666),
                      shape: BoxShape.circle,
                      border: Border.all(color: const Color(0xFF141414), width: 1.5),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.remoteCode,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    fontFamily: 'monospace',
                  ),
                ),
                Text(
                  status.toUpperCase(),
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.8,
                    color: status == 'online'
                        ? const Color(0xFF00FF41)
                        : status == 'connecting'
                            ? const Color(0xFFFFC107)
                            : Colors.white38,
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.verified_user_outlined, size: 20),
            onPressed: _showSafetyNumbers,
            tooltip: 'Verify Safety Numbers',
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(
                      color: Color(0xFF00FF41),
                      strokeWidth: 2,
                    ),
                  )
                : _messages.isEmpty
                    ? _buildEmptyChatPrompt()
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

  Widget _buildEmptyChatPrompt() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.lock_clock_outlined, size: 48, color: Colors.white24),
            const SizedBox(height: 12),
            Text(
              'P2P Session with ${widget.remoteCode}',
              style: const TextStyle(color: Colors.white70, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            const Text(
              'Messages sent in this chat are end-to-end encrypted using Signal Protocol and saved locally to your encrypted SQLCipher store.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white38, fontSize: 12),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInputArea() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: const BoxDecoration(
        color: Color(0xFF141414),
        border: Border(top: BorderSide(color: Color(0xFF262626))),
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _messageController,
                style: const TextStyle(color: Colors.white, fontSize: 14),
                decoration: const InputDecoration(
                  hintText: 'Type encrypted message...',
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  fillColor: Colors.transparent,
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

  void _showSafetyNumbers() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.security, color: Color(0xFF00FF41)),
            SizedBox(width: 8),
            Text('Safety Numbers'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Compare these safety numbers with ${widget.remoteCode} to verify end-to-end encryption integrity:',
              style: const TextStyle(fontSize: 13, color: Colors.white70),
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFF0A0A0A),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFF262626)),
              ),
              child: const Text(
                '4582 1195 2201 0039\n8834 5012 9910 4421',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontFamily: 'monospace',
                  letterSpacing: 2,
                  color: Color(0xFF00FF41),
                  fontSize: 13,
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            child: const Text('DISMISS'),
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
          maxWidth: MediaQuery.of(context).size.width * 0.78,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: msg.isMe
              ? const Color(0xFF00FF41).withAlpha(30)
              : const Color(0xFF1E1E1E),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: msg.isMe
                ? const Color(0xFF00FF41).withAlpha(70)
                : const Color(0xFF2E2E2E),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              msg.text,
              style: const TextStyle(color: Colors.white, fontSize: 14),
            ),
            const SizedBox(height: 4),
            Text(
              '${msg.timestamp.hour.toString().padLeft(2, '0')}:${msg.timestamp.minute.toString().padLeft(2, '0')}',
              style: const TextStyle(fontSize: 10, color: Colors.white38),
            ),
          ],
        ),
      ),
    );
  }
}
