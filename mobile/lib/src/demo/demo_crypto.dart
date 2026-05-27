import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';

class DemoCiphertextEnvelope {
  const DemoCiphertextEnvelope({
    required this.ciphertextBase64,
    required this.ciphertextSha256,
    required this.metadata,
  });

  final String ciphertextBase64;
  final String ciphertextSha256;
  final Map<String, Object> metadata;
}

class DeterministicDemoCrypto {
  DemoCiphertextEnvelope encryptDirectMessage({
    required String plaintext,
    required String roomId,
    required String recipientDeviceId,
  }) {
    final canonical = jsonEncode({
      'demo': true,
      'roomId': roomId,
      'recipientDeviceId': recipientDeviceId,
      'plaintextPreviewLength': plaintext.length,
      'plaintextPreviewHash': sha256.convert(utf8.encode(plaintext)).toString(),
    });
    final envelopeBytes = Uint8List.fromList(utf8.encode(canonical));
    return DemoCiphertextEnvelope(
      ciphertextBase64: base64Encode(envelopeBytes),
      ciphertextSha256: sha256.convert(envelopeBytes).toString(),
      metadata: const {
        'algorithm': 'DEMO-MOCK-ENVELOPE-v1',
        'demoOnly': true,
        'plaintextExcluded': true,
      },
    );
  }

  String hashAttachmentLabel(String label) {
    return sha256.convert(utf8.encode('attachment:$label')).toString();
  }
}
