import 'dart:async';

import '../api/crypta_api_client.dart';
import '../demo/demo_crypto.dart';

enum DemoConnectionState { loading, offlineReady, connected, failed }

enum SendState { idle, sending, sent, failed }

class MissionRoom {
  const MissionRoom({
    required this.id,
    required this.name,
    required this.classification,
    required this.status,
    required this.hasUnread,
  });

  final String id;
  final String name;
  final String classification;
  final String status;
  final bool hasUnread;
}

class MissionMessage {
  const MissionMessage({
    required this.id,
    required this.sender,
    required this.body,
    required this.mine,
    required this.status,
  });

  final String id;
  final String sender;
  final String body;
  final bool mine;
  final String status;
}

class MissionRoomController {
  MissionRoomController({
    CryptaApiClient? apiClient,
    DeterministicDemoCrypto? crypto,
    this.organizationId = '',
    this.userId = '',
    this.deviceId = '',
    this.recipientUserId = '',
    this.recipientDeviceId = 'demo-recipient-device',
    this.attachmentRoomId = '',
  })  : apiClient = apiClient,
        crypto = crypto ?? DeterministicDemoCrypto();

  factory MissionRoomController.fromEnvironment() {
    const backendUrl = String.fromEnvironment('CRYPTA_BACKEND_URL');
    const bootstrapToken = String.fromEnvironment('CRYPTA_BOOTSTRAP_TOKEN');
    const orgId = String.fromEnvironment('CRYPTA_ORGANIZATION_ID');
    const userId = String.fromEnvironment('CRYPTA_USER_ID');
    const deviceId = String.fromEnvironment('CRYPTA_DEVICE_ID');
    const recipientUserId = String.fromEnvironment('CRYPTA_RECIPIENT_USER_ID');
    const recipientDeviceId = String.fromEnvironment('CRYPTA_RECIPIENT_DEVICE_ID');
    const roomId = String.fromEnvironment('CRYPTA_ROOM_ID');

    final configured = backendUrl.isNotEmpty && bootstrapToken.isNotEmpty;
    return MissionRoomController(
      apiClient: configured ? CryptaApiClient(baseUrl: backendUrl, bootstrapToken: bootstrapToken) : null,
      organizationId: orgId,
      userId: userId,
      deviceId: deviceId,
      recipientUserId: recipientUserId,
      recipientDeviceId: recipientDeviceId.isEmpty ? 'demo-recipient-device' : recipientDeviceId,
      attachmentRoomId: roomId,
    );
  }

  final CryptaApiClient? apiClient;
  final DeterministicDemoCrypto crypto;
  final String organizationId;
  final String userId;
  final String deviceId;
  final String recipientUserId;
  final String recipientDeviceId;
  final String attachmentRoomId;

  final rooms = <MissionRoom>[
    const MissionRoom(
      id: 'demo-room-executive-ops',
      name: 'Executive Ops',
      classification: 'VERIFIED',
      status: '3 unread',
      hasUnread: true,
    ),
    const MissionRoom(
      id: 'demo-room-board',
      name: 'Board Channel',
      classification: 'LOCKDOWN READY',
      status: 'No unread',
      hasUnread: false,
    ),
    const MissionRoom(
      id: 'demo-room-incident',
      name: 'Incident Cell',
      classification: 'E2EE',
      status: '1 unread',
      hasUnread: true,
    ),
  ];

  final messages = <MissionMessage>[
    const MissionMessage(
      id: 'demo-message-1',
      sender: 'Amina - verified iPhone',
      body: 'Encrypted envelope received. Local decrypt is simulated for this internal MVP.',
      mine: false,
      status: 'ciphertext stored',
    ),
    const MissionMessage(
      id: 'demo-message-2',
      sender: 'You - hardware-backed key',
      body: 'Deterministic demo crypto is active. Real audited crypto remains intentionally deferred.',
      mine: true,
      status: 'demo envelope',
    ),
  ];

  int selectedRoom = 0;
  DemoConnectionState connectionState = DemoConnectionState.loading;
  SendState sendState = SendState.idle;
  bool deviceVerified = false;
  bool lockdownActive = false;
  String statusText = 'Starting internal demo';
  String? errorText;

  MissionRoom get room => rooms[selectedRoom];

  bool get backendConfigured {
    return apiClient != null &&
        organizationId.isNotEmpty &&
        userId.isNotEmpty &&
        deviceId.isNotEmpty &&
        recipientUserId.isNotEmpty &&
        recipientDeviceId.isNotEmpty;
  }

  Future<void> initialize() async {
    connectionState = DemoConnectionState.loading;
    errorText = null;
    if (!backendConfigured) {
      connectionState = DemoConnectionState.offlineReady;
      statusText = 'Offline demo ready - configure --dart-define values for backend mode';
      return;
    }
    try {
      await apiClient!.issueBootstrapSession(userId: userId, deviceId: deviceId);
      connectionState = DemoConnectionState.connected;
      statusText = 'Connected to Crypta backend demo session';
    } on Object catch (error) {
      connectionState = DemoConnectionState.failed;
      errorText = _friendlyError(error);
      statusText = 'Backend unavailable - offline demo remains usable';
    }
  }

  void selectRoom(int index) {
    selectedRoom = index;
    errorText = null;
  }

  String verifyDevice() {
    deviceVerified = true;
    statusText = 'Device marked verified for the internal demo';
    return statusText;
  }

  String startPasskeyDemo() {
    statusText = 'Passkey ceremony is challenge/replay protected, but authenticator verification is demo-only';
    return statusText;
  }

  String toggleLockdown() {
    lockdownActive = !lockdownActive;
    statusText = lockdownActive ? 'Demo lockdown active - sending is blocked' : 'Demo lockdown ended';
    return statusText;
  }

  Future<String> attachEncryptedFile() async {
    final label = 'demo-briefing-${DateTime.now().millisecondsSinceEpoch}.bin';
    final hash = crypto.hashAttachmentLabel(label);
    if (backendConfigured && apiClient!.bearerToken != null) {
      try {
        await apiClient!.createAttachmentMetadata(
          roomId: attachmentRoomId.isEmpty ? room.id : attachmentRoomId,
          objectKey: 'demo/$label',
          ciphertextSha256: hash,
          ciphertextBytes: 128,
          cryptoMetadata: const {
            'algorithm': 'DEMO-MOCK-ATTACHMENT-v1',
            'demoOnly': true,
          },
        );
      } on Object catch (error) {
        errorText = _friendlyError(error);
        statusText = 'Attachment metadata failed - $errorText';
        return statusText;
      }
    }
    messages.add(MissionMessage(
      id: 'attachment-$hash',
      sender: 'You - encrypted attachment',
      body: 'Attachment metadata prepared for $label. Plaintext file bytes were not uploaded by the demo.',
      mine: true,
      status: backendConfigured ? 'metadata submitted' : 'offline metadata',
    ));
    statusText = 'Encrypted attachment metadata prepared';
    return statusText;
  }

  Future<String> sendMessage(String plaintext) async {
    final trimmed = plaintext.trim();
    if (trimmed.isEmpty) {
      sendState = SendState.failed;
      errorText = 'Message cannot be empty';
      statusText = errorText!;
      return statusText;
    }
    if (lockdownActive) {
      sendState = SendState.failed;
      errorText = 'Demo lockdown is active';
      statusText = 'Sending blocked by lockdown';
      return statusText;
    }

    sendState = SendState.sending;
    errorText = null;
    final envelope = crypto.encryptDirectMessage(
      plaintext: trimmed,
      roomId: room.id,
      recipientDeviceId: recipientDeviceId,
    );
    try {
      String messageId = 'offline-${envelope.ciphertextSha256.substring(0, 12)}';
      if (backendConfigured && apiClient!.bearerToken != null) {
        messageId = await apiClient!.submitDirectMessage(
          organizationId: organizationId,
          senderUserId: userId,
          senderDeviceId: deviceId,
          recipientUserId: recipientUserId,
          recipientDeviceId: recipientDeviceId,
          ciphertextBase64: envelope.ciphertextBase64,
          ciphertextSha256: envelope.ciphertextSha256,
          cryptoMetadata: envelope.metadata,
        );
      }
      messages.add(MissionMessage(
        id: messageId,
        sender: 'You - deterministic demo crypto',
        body: 'Sent as deterministic demo ciphertext (${envelope.ciphertextSha256.substring(0, 12)}...).',
        mine: true,
        status: backendConfigured ? 'backend accepted' : 'offline queued',
      ));
      sendState = SendState.sent;
      statusText = backendConfigured ? 'Ciphertext accepted by backend' : 'Sent as deterministic demo ciphertext';
      return statusText;
    } on Object catch (error) {
      sendState = SendState.failed;
      errorText = _friendlyError(error);
      statusText = 'Send failed - $errorText';
      return statusText;
    }
  }

  String _friendlyError(Object error) {
    if (error is CryptaApiException) {
      return error.toString();
    }
    if (error is TimeoutException) {
      return 'Request timed out';
    }
    return error.toString();
  }
}
