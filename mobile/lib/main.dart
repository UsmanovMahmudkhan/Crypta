import 'package:flutter/material.dart';

import 'src/app/mission_room_controller.dart';

void main() {
  runApp(SovereignCommApp(controller: MissionRoomController.fromEnvironment()));
}

class SovereignCommApp extends StatelessWidget {
  const SovereignCommApp({required this.controller, super.key});

  final MissionRoomController controller;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sovereign Comm',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF245B57),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: MissionRoomShell(controller: controller),
    );
  }
}

class MissionRoomShell extends StatefulWidget {
  const MissionRoomShell({required this.controller, super.key});

  final MissionRoomController controller;

  @override
  State<MissionRoomShell> createState() => _MissionRoomShellState();
}

class _MissionRoomShellState extends State<MissionRoomShell> {
  final composer = TextEditingController();

  MissionRoomController get controller => widget.controller;

  @override
  void initState() {
    super.initState();
    controller.initialize().whenComplete(() {
      if (mounted) {
        setState(() {});
      }
    });
  }

  @override
  void dispose() {
    composer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final room = controller.room;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Sovereign Comm'),
        actions: [
          IconButton(
            key: const Key('verify-device-button'),
            tooltip: 'Verify device',
            onPressed: () => _showStatus(controller.verifyDevice()),
            icon: Icon(controller.deviceVerified ? Icons.verified : Icons.verified_user_outlined),
          ),
          IconButton(
            key: const Key('lockdown-button'),
            tooltip: controller.lockdownActive ? 'End lockdown' : 'Start lockdown',
            onPressed: () => _showStatus(controller.toggleLockdown()),
            icon: Icon(controller.lockdownActive ? Icons.lock : Icons.lock_open_outlined),
          ),
        ],
      ),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isWide = constraints.maxWidth >= 760;
            final roomList = RoomList(
              rooms: controller.rooms,
              selectedRoom: controller.selectedRoom,
              onSelected: (index) => setState(() => controller.selectRoom(index)),
            );
            final timeline = MissionTimeline(
              room: room,
              messages: controller.messages,
              connectionState: controller.connectionState,
              statusText: controller.statusText,
              errorText: controller.errorText,
              lockdownActive: controller.lockdownActive,
              deviceVerified: controller.deviceVerified,
              sendState: controller.sendState,
              composer: composer,
              onPasskey: () => _showStatus(controller.startPasskeyDemo()),
              onAttach: () => _runAction(controller.attachEncryptedFile),
              onSend: _sendMessage,
            );
            if (isWide) {
              return Row(
                children: [
                  SizedBox(width: 320, child: roomList),
                  const VerticalDivider(width: 1),
                  Expanded(child: timeline),
                ],
              );
            }
            return Column(
              children: [
                SizedBox(height: 164, child: roomList),
                const Divider(height: 1),
                Expanded(child: timeline),
              ],
            );
          },
        ),
      ),
    );
  }

  Future<void> _sendMessage() async {
    final message = composer.text;
    setState(() {});
    final status = await controller.sendMessage(message);
    if (controller.sendState == SendState.sent) {
      composer.clear();
    }
    _showStatus(status);
    setState(() {});
  }

  Future<void> _runAction(Future<String> Function() action) async {
    setState(() {});
    final status = await action();
    _showStatus(status);
    setState(() {});
  }

  void _showStatus(String status) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(status)));
    setState(() {});
  }
}

class RoomList extends StatelessWidget {
  const RoomList({
    required this.rooms,
    required this.selectedRoom,
    required this.onSelected,
    super.key,
  });

  final List<MissionRoom> rooms;
  final int selectedRoom;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.all(12),
      itemCount: rooms.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final room = rooms[index];
        final selected = index == selectedRoom;
        return ListTile(
          key: Key('room-${room.id}'),
          selected: selected,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          tileColor: selected ? Theme.of(context).colorScheme.secondaryContainer : null,
          leading: Icon(room.hasUnread ? Icons.mark_chat_unread_outlined : Icons.forum_outlined),
          title: Text(room.name, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text('${room.classification} - ${room.status}', maxLines: 1, overflow: TextOverflow.ellipsis),
          onTap: () => onSelected(index),
        );
      },
    );
  }
}

class MissionTimeline extends StatelessWidget {
  const MissionTimeline({
    required this.room,
    required this.messages,
    required this.connectionState,
    required this.statusText,
    required this.errorText,
    required this.lockdownActive,
    required this.deviceVerified,
    required this.sendState,
    required this.composer,
    required this.onPasskey,
    required this.onAttach,
    required this.onSend,
    super.key,
  });

  final MissionRoom room;
  final List<MissionMessage> messages;
  final DemoConnectionState connectionState;
  final String statusText;
  final String? errorText;
  final bool lockdownActive;
  final bool deviceVerified;
  final SendState sendState;
  final TextEditingController composer;
  final VoidCallback onPasskey;
  final VoidCallback onAttach;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    final sending = sendState == SendState.sending;
    return Column(
      children: [
        ListTile(
          title: Text(room.name),
          subtitle: Text(_subtitle, maxLines: 2, overflow: TextOverflow.ellipsis),
          trailing: FilledButton.icon(
            key: const Key('passkey-button'),
            onPressed: onPasskey,
            icon: const Icon(Icons.fingerprint),
            label: const Text('Passkey'),
          ),
        ),
        StatusBand(
          connectionState: connectionState,
          statusText: statusText,
          errorText: errorText,
          lockdownActive: lockdownActive,
        ),
        Expanded(
          child: messages.isEmpty
              ? const Center(child: Text('No ciphertext envelopes yet'))
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: messages.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, index) => MessageBubble(message: messages[index]),
                ),
        ),
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              IconButton(
                key: const Key('attach-button'),
                tooltip: 'Attach encrypted file',
                onPressed: sending ? null : onAttach,
                icon: const Icon(Icons.attach_file),
              ),
              Expanded(
                child: TextField(
                  key: const Key('message-composer'),
                  controller: composer,
                  enabled: !sending && !lockdownActive,
                  minLines: 1,
                  maxLines: 4,
                  decoration: InputDecoration(
                    hintText: lockdownActive ? 'Lockdown blocks sending' : 'Compose locally encrypted message',
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                key: const Key('send-button'),
                tooltip: 'Encrypt and send',
                onPressed: sending || lockdownActive ? null : onSend,
                icon: sending
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send),
              ),
            ],
          ),
        ),
      ],
    );
  }

  String get _subtitle {
    final trust = deviceVerified ? 'device verified' : 'device verification pending';
    return 'Ciphertext delivery only - $trust';
  }
}

class StatusBand extends StatelessWidget {
  const StatusBand({
    required this.connectionState,
    required this.statusText,
    required this.errorText,
    required this.lockdownActive,
    super.key,
  });

  final DemoConnectionState connectionState;
  final String statusText;
  final String? errorText;
  final bool lockdownActive;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final color = switch (connectionState) {
      DemoConnectionState.loading => colorScheme.secondaryContainer,
      DemoConnectionState.offlineReady => colorScheme.tertiaryContainer,
      DemoConnectionState.connected => colorScheme.primaryContainer,
      DemoConnectionState.failed => colorScheme.errorContainer,
    };
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      color: lockdownActive ? colorScheme.errorContainer : color,
      child: Row(
        children: [
          Icon(_icon, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              errorText == null ? statusText : '$statusText. $errorText',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  IconData get _icon {
    if (lockdownActive) {
      return Icons.lock;
    }
    return switch (connectionState) {
      DemoConnectionState.loading => Icons.sync,
      DemoConnectionState.offlineReady => Icons.cloud_off_outlined,
      DemoConnectionState.connected => Icons.cloud_done_outlined,
      DemoConnectionState.failed => Icons.warning_amber_outlined,
    };
  }
}

class MessageBubble extends StatelessWidget {
  const MessageBubble({required this.message, super.key});

  final MissionMessage message;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: message.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Card(
          elevation: 0,
          color: message.mine
              ? Theme.of(context).colorScheme.primaryContainer
              : Theme.of(context).colorScheme.surfaceContainerHighest,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(message.sender, style: Theme.of(context).textTheme.labelMedium),
                const SizedBox(height: 6),
                Text(message.body),
                const SizedBox(height: 8),
                Text(message.status, style: Theme.of(context).textTheme.labelSmall),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
