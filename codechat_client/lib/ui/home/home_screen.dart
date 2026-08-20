import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/storage/database_helper.dart';
import '../../services/app_state.dart';
import '../../services/connection_manager.dart';
import '../chat/chat_screen.dart';
import '../chat/contact_dialog.dart';

/// Main hub displaying active conversations, user's UserCode, and connection status.
class HomeScreen extends StatefulWidget {
  final ConnectionManager? connectionManager;

  const HomeScreen({
    super.key,
    this.connectionManager,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  List<Map<String, dynamic>> _recentChats = [];
  bool _isLoading = true;

  ConnectionManager? get _cm => widget.connectionManager ?? AppState.instance.connectionManager;

  @override
  void initState() {
    super.initState();
    _loadRecentChats();
    _setupConnectionListeners();
  }

  Future<void> _loadRecentChats() async {
    final chats = await DatabaseHelper().getRecentChats();
    if (mounted) {
      setState(() {
        _recentChats = chats;
        _isLoading = false;
      });
    }
  }

  void _setupConnectionListeners() {
    final cm = _cm;
    if (cm == null) return;

    cm.onSignalingStatusChanged = (connected) {
      if (mounted) setState(() {});
    };

    cm.onSecureMessageReceived = (msg, fromCode) {
      _loadRecentChats();
    };

    cm.onIncomingConnection = (fromCode) {
      _loadRecentChats();
    };
  }

  void _openChat(String remoteCode) async {
    final cm = _cm;
    if (cm == null) return;
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => ChatScreen(
          remoteCode: remoteCode,
          connectionManager: cm,
        ),
      ),
    );
    _loadRecentChats();
  }

  void _startNewChat() async {
    final remoteCode = await ContactDialog.show(context);
    if (remoteCode != null && remoteCode.isNotEmpty) {
      _openChat(remoteCode);
    }
  }

  void _copyUserCode() {
    final code = _cm?.localUserCode ?? AppState.instance.localUserCode ?? '';
    if (code.isNotEmpty) {
      Clipboard.setData(ClipboardData(text: code));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Your UserCode copied to clipboard'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isConnected = _cm?.isSignalingConnected ?? false;
    final userCode = _cm?.localUserCode ?? AppState.instance.localUserCode ?? 'UNKNOWN';

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Image.asset(
              'assets/images/logo.png',
              width: 28,
              height: 28,
              fit: BoxFit.contain,
            ),
            const SizedBox(width: 10),
            const Text(
              'Ghost Messenger',
              style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline_rounded),
            tooltip: 'About Ghost Messenger',
            onPressed: () => _showAboutDialog(context),
          ),
        ],
      ),
      body: Column(
        children: [
          _buildIdentityHeader(userCode, isConnected),
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(
                      valueColor: AlwaysStoppedAnimation(Color(0xFF00FF41)),
                    ),
                  )
                : _recentChats.isEmpty
                    ? _buildEmptyState()
                    : _buildChatsList(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _startNewChat,
        backgroundColor: const Color(0xFF00FF41),
        foregroundColor: Colors.black,
        icon: const Icon(Icons.message_rounded),
        label: const Text('NEW CHAT', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
    );
  }

  Widget _buildIdentityHeader(String userCode, bool isConnected) {
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1E1E1E),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'YOUR USER CODE',
                style: TextStyle(
                  fontSize: 11,
                  letterSpacing: 1.5,
                  fontWeight: FontWeight.bold,
                  color: Colors.white54,
                ),
              ),
              Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: isConnected ? const Color(0xFF00FF41) : Colors.amber,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    isConnected ? 'SIGNALING READY' : 'CONNECTING...',
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1,
                      color: isConnected ? const Color(0xFF00FF41) : Colors.amber,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                userCode,
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 3,
                  color: Color(0xFF00FF41),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.copy_rounded, color: Colors.white70),
                tooltip: 'Copy UserCode',
                onPressed: _copyUserCode,
              ),
            ],
          ),
          const SizedBox(height: 4),
          const Text(
            'Share this code with friends to start an end-to-end encrypted session.',
            style: TextStyle(fontSize: 12, color: Colors.white38),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.chat_bubble_outline_rounded,
              size: 64,
              color: Colors.white.withAlpha(40),
            ),
            const SizedBox(height: 16),
            const Text(
              'No Active Conversations',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              'Tap "NEW CHAT" below and enter a friend\'s 8-character UserCode to start messaging.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChatsList() {
    return ListView.separated(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      itemCount: _recentChats.length,
      separatorBuilder: (_, __) => const Divider(color: Colors.white10, height: 1),
      itemBuilder: (context, index) {
        final chat = _recentChats[index];
        final remoteCode = chat['remote_code'] as String;
        final lastMsg = chat['content'] as String;
        final isMe = (chat['is_me'] as int) == 1;
        final timestamp = DateTime.parse(chat['timestamp'] as String);

        return ListTile(
          contentPadding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
          leading: CircleAvatar(
            backgroundColor: const Color(0xFF00FF41).withAlpha(30),
            child: const Icon(Icons.lock_rounded, color: Color(0xFF00FF41), size: 20),
          ),
          title: Text(
            remoteCode,
            style: const TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1),
          ),
          subtitle: Text(
            '${isMe ? 'You: ' : ''}$lastMsg',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: Colors.white54, fontSize: 13),
          ),
          trailing: Text(
            '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}',
            style: const TextStyle(fontSize: 11, color: Colors.white30),
          ),
          onTap: () => _openChat(remoteCode),
        );
      },
    );
  }

  void _showAboutDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        title: Row(
          children: [
            Image.asset(
              'assets/images/logo.png',
              width: 32,
              height: 32,
              fit: BoxFit.contain,
            ),
            const SizedBox(width: 10),
            const Text('Ghost Messenger'),
          ],
        ),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Zero-knowledge, peer-to-peer end-to-end encrypted messaging powered by Signal Protocol and WebRTC.',
              style: TextStyle(color: Colors.white70, fontSize: 13),
            ),
            SizedBox(height: 12),
            Text(
              '• No phone number or email required\n• Self-sovereign BIP39 cryptographic identity\n• Direct P2P DataChannels\n• Encrypted local SQLCipher storage',
              style: TextStyle(color: Colors.white54, fontSize: 12, height: 1.5),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close', style: TextStyle(color: Color(0xFF00FF41))),
          ),
        ],
      ),
    );
  }
}
