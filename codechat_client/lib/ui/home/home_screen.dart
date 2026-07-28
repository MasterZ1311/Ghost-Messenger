import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../services/app_state.dart';
import '../../core/crypto/user_code_utils.dart';
import '../chat/chat_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final AppState _appState = AppState.instance;

  @override
  void initState() {
    super.initState();
    _appState.addListener(_onAppStateChanged);
    _appState.loadRecentChats();
  }

  @override
  void dispose() {
    _appState.removeListener(_onAppStateChanged);
    super.dispose();
  }

  void _onAppStateChanged() {
    if (mounted) setState(() {});
  }

  void _showAddPeerDialog() {
    final controller = TextEditingController();
    String? errorText;

    showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Row(
                children: [
                  Icon(Icons.person_add_outlined, color: Color(0xFF00FF41)),
                  SizedBox(width: 10),
                  Text('New Peer Connection'),
                ],
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    "Enter your peer's 8-character UserCode to start an encrypted P2P session:",
                    style: TextStyle(fontSize: 13, color: Colors.white70),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: controller,
                    textCapitalization: TextCapitalization.characters,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      letterSpacing: 2,
                      fontWeight: FontWeight.bold,
                    ),
                    decoration: InputDecoration(
                      hintText: 'e.g. 5J9L-2P4X',
                      errorText: errorText,
                    ),
                    onChanged: (val) {
                      if (errorText != null) {
                        setDialogState(() => errorText = null);
                      }
                    },
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('CANCEL', style: TextStyle(color: Colors.white54)),
                ),
                ElevatedButton(
                  onPressed: () async {
                    final rawCode = controller.text.trim().toUpperCase();
                    if (!UserCodeUtils.isValidFormat(rawCode)) {
                      setDialogState(() {
                        errorText = 'Invalid 8-character code format';
                      });
                      return;
                    }

                    // Format code as XXXX-XXXX if valid
                    final clean = rawCode.replaceAll('-', '');
                    final formattedCode = '${clean.substring(0, 4)}-${clean.substring(4)}';

                    final currentContext = context;
                    Navigator.pop(currentContext);

                    // Initiate connection & navigate to ChatScreen
                    await _appState.initiatePeerConnection(formattedCode);

                    if (!currentContext.mounted) return;
                    Navigator.push(
                      currentContext,
                      MaterialPageRoute(
                        builder: (_) => ChatScreen(remoteCode: formattedCode),
                      ),
                    );
                  },
                  child: const Text('CONNECT'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final myCode = _appState.localUserCode ?? 'UNKNOWN';
    final recentChats = _appState.recentChats;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('GHOST MESSENGER'),
            Row(
              children: [
                const Text(
                  'My Code: ',
                  style: TextStyle(fontSize: 11, color: Colors.white54),
                ),
                Text(
                  myCode,
                  style: const TextStyle(
                    fontSize: 12,
                    fontFamily: 'monospace',
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF00FF41),
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.copy_rounded, size: 18),
            tooltip: 'Copy My UserCode',
            onPressed: () {
              Clipboard.setData(ClipboardData(text: myCode));
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('UserCode $myCode copied')),
              );
            },
          ),
        ],
      ),
      body: recentChats.isEmpty
          ? _buildEmptyState()
          : ListView.separated(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: recentChats.length,
              separatorBuilder: (_, __) => const Divider(color: Color(0xFF1F1F1F), height: 1),
              itemBuilder: (context, index) {
                final chat = recentChats[index];
                final peerCode = chat['remote_code'] as String;
                final lastMsg = chat['content'] as String;
                final isMe = (chat['is_me'] as int) == 1;
                final rawTime = chat['timestamp'] as String;
                final time = DateTime.tryParse(rawTime) ?? DateTime.now();

                final status = _appState.getPeerStatus(peerCode);

                return ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  leading: Stack(
                    children: [
                      CircleAvatar(
                        backgroundColor: const Color(0xFF1E1E1E),
                        child: Text(
                          peerCode.substring(0, 2),
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontFamily: 'monospace',
                          ),
                        ),
                      ),
                      Positioned(
                        right: 0,
                        bottom: 0,
                        child: _StatusBadge(status: status),
                      ),
                    ],
                  ),
                  title: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        peerCode,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontFamily: 'monospace',
                          fontSize: 15,
                        ),
                      ),
                      Text(
                        '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}',
                        style: const TextStyle(fontSize: 11, color: Colors.white38),
                      ),
                    ],
                  ),
                  subtitle: Text(
                    isMe ? 'You: $lastMsg' : lastMsg,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white54, fontSize: 13),
                  ),
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => ChatScreen(remoteCode: peerCode),
                      ),
                    );
                  },
                );
              },
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showAddPeerDialog,
        backgroundColor: const Color(0xFF00FF41),
        foregroundColor: Colors.black,
        icon: const Icon(Icons.add_comment_rounded),
        label: const Text(
          'NEW SESSION',
          style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.0),
        ),
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
            Icon(Icons.forum_outlined, size: 64, color: Colors.white.withAlpha(40)),
            const SizedBox(height: 16),
            const Text(
              'No Active P2P Sessions',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white70),
            ),
            const SizedBox(height: 8),
            const Text(
              'Tap "NEW SESSION" below to connect to a peer by entering their 8-character UserCode.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white38, fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  final String status;
  const _StatusBadge({required this.status});

  @override
  Widget build(BuildContext context) {
    Color color;
    if (status == 'online') {
      color = const Color(0xFF00FF41); // Live Green
    } else if (status == 'connecting') {
      color = const Color(0xFFFFC107); // Amber
    } else {
      color = const Color(0xFF666666); // Offline Grey
    }

    return Container(
      width: 12,
      height: 12,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(color: const Color(0xFF0A0A0A), width: 2),
      ),
    );
  }
}
