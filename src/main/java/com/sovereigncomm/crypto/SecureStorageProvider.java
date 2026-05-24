package com.sovereigncomm.crypto;

public interface SecureStorageProvider {
    byte[] wrapKey(String hardwareKeyAlias, byte[] keyMaterial);
    byte[] unwrapKey(String hardwareKeyAlias, byte[] wrappedKey);
}
