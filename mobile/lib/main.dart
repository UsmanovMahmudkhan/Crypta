import 'package:flutter/material.dart';

void main() {
  runApp(const SovereignCommApp());
}

class SovereignCommApp extends StatelessWidget {
  const SovereignCommApp({super.key});

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
      home: const MissionRoomShell(),
    );
  }
}

class MissionRoomShell extends StatefulWidget {
  const MissionRoomShell({super.key});

  @override
  State<MissionRoomShell> createState() => _MissionRoomShellState();
}

class _MissionRoomShellState extends State<MissionRoomShell> {
  int selectedRoom = 0;

  final rooms = const [
    MissionRoom('Executive Ops', 'VERIFIED', '3 unread', true),
    MissionRoom('Board Channel', 'LOCKDOWN READY', 'No unread', false),
    MissionRoom('Incident Cell', 'E2EE', '1 unread', true),
  ];

  @override
  Widget build(BuildContext context) {
    final room = rooms[selectedRoom];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Sovereign Comm'),
        actions: [
          IconButton(
            tooltip: 'Verify device',
            onPressed: () {},
            icon: const Icon(Icons.verified_user_outlined),
          ),
          IconButton(
            tooltip: 'Lockdown',
            onPressed: () {},
            icon: const Icon(Icons.lock_outline),
          ),
        ],
      ),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isWide = constraints.maxWidth >= 760;
            final roomList = RoomList(
              rooms: rooms,
              selectedRoom: selectedRoom,
              onSelected: (index) => setState(() => selectedRoom = index),
            );
            final timeline = MissionTimeline(room: room);
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
          selected: selected,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          tileColor: selected ? Theme.of(context).colorScheme.secondaryContainer : null,
          leading: Icon(room.hasUnread ? Icons.mark_chat_unread_outlined : Icons.forum_outlined),
          title: Text(room.name, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text('${room.classification} · ${room.status}'),
          onTap: () => onSelected(index),
        );
      },
    );
  }
}

class MissionTimeline extends StatelessWidget {
  const MissionTimeline({required this.room, super.key});

  final MissionRoom room;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ListTile(
          title: Text(room.name),
          subtitle: const Text('Ciphertext delivery only · device verification required'),
          trailing: FilledButton.icon(
            onPressed: () {},
            icon: const Icon(Icons.fingerprint),
            label: const Text('Passkey'),
          ),
        ),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          color: Theme.of(context).colorScheme.tertiaryContainer,
          child: const Text('Secure screen enabled. Plaintext never leaves this device.'),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: const [
              MessageBubble(
                sender: 'Amina · verified iPhone',
                body: 'Encrypted envelope received. Tap to decrypt locally after biometric unlock.',
                mine: false,
              ),
              MessageBubble(
                sender: 'You · hardware-backed key',
                body: 'Acknowledged. Attachment key wrapping is pending external crypto review.',
                mine: true,
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              IconButton(
                tooltip: 'Attach encrypted file',
                onPressed: () {},
                icon: const Icon(Icons.attach_file),
              ),
              Expanded(
                child: TextField(
                  decoration: InputDecoration(
                    hintText: 'Compose locally encrypted message',
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                tooltip: 'Encrypt and send',
                onPressed: () {},
                icon: const Icon(Icons.send),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class MessageBubble extends StatelessWidget {
  const MessageBubble({
    required this.sender,
    required this.body,
    required this.mine,
    super.key,
  });

  final String sender;
  final String body;
  final bool mine;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Card(
          elevation: 0,
          color: mine
              ? Theme.of(context).colorScheme.primaryContainer
              : Theme.of(context).colorScheme.surfaceContainerHighest,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(sender, style: Theme.of(context).textTheme.labelMedium),
                const SizedBox(height: 6),
                Text(body),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class MissionRoom {
  const MissionRoom(this.name, this.classification, this.status, this.hasUnread);

  final String name;
  final String classification;
  final String status;
  final bool hasUnread;
}
