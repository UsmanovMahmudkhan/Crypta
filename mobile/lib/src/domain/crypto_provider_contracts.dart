import 'dart:typed_data';

abstract interface class DirectCryptoProvider {
  Future<Uint8List> encryptDirectMessage({
    required String recipientDeviceId,
    required Uint8List plaintext,
    required Map<String, Uint8List> associatedData,
  });

  Future<Uint8List> decryptDirectMessage({
    required String senderDeviceId,
    required Uint8List ciphertextEnvelope,
    required Map<String, Uint8List> associatedData,
  });
}
