package com.sovereigncomm.crypto;

public interface SecureStorageProvider {
    byte[] wrapKey(String hardwareKeyAlias, byte[] keyMaterial);
