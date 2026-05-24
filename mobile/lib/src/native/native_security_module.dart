import 'dart:typed_data';

abstract interface class SecureStorageProvider {
  Future<Uint8List> wrapKey({
    required String hardwareKeyAlias,
