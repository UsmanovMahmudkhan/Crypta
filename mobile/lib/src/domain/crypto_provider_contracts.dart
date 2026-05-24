import 'dart:typed_data';

abstract interface class DirectCryptoProvider {
  Future<Uint8List> encryptDirectMessage({
    required String recipientDeviceId,
