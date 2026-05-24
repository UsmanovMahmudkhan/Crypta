import 'dart:typed_data';

abstract interface class SecureStorageProvider {
  Future<Uint8List> wrapKey({
    required String hardwareKeyAlias,
    required Uint8List keyMaterial,
  });

  Future<Uint8List> unwrapKey({
    required String hardwareKeyAlias,
    required Uint8List wrappedKey,
  });
}

abstract interface class DeviceAttestationProvider {
  Future<DeviceAttestationResult> attestDevice({required Uint8List challenge});
}

abstract interface class BiometricUnlockProvider {
  Future<bool> unlockForSensitiveAction({required String localizedReason});
}

abstract interface class SecureScreenProvider {
  Future<void> enableSecureScreen();
  Future<void> disableSecureScreenForAllowedViews();
}

class DeviceAttestationResult {
  const DeviceAttestationResult({
    required this.format,
    required this.attestationObject,
    required this.hardwareBacked,
    required this.strongBoxOrSecureEnclave,
  });

  final String format;
  final Uint8List attestationObject;
  final bool hardwareBacked;
  final bool strongBoxOrSecureEnclave;
}
