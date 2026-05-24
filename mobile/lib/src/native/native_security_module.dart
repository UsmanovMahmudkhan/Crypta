import 'dart:typed_data';

abstract interface class SecureStorageProvider {
  Future<Uint8List> wrapKey({
    required String hardwareKeyAlias,
    required Uint8List keyMaterial,
  });

  Future<Uint8List> unwrapKey({
    required String hardwareKeyAlias,
