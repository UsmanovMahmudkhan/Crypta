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

abstract interface class GroupCryptoProvider {
  Future<Uint8List> createGroupCommit({
    required String roomId,
    required List<String> memberDeviceIds,
  });

  Future<Uint8List> encryptGroupMessage({
    required String roomId,
    required Uint8List plaintext,
    required Map<String, Uint8List> associatedData,
  });

  Future<Uint8List> decryptGroupMessage({
    required String roomId,
