package com.sovereigncomm.crypto;

import java.util.Map;

public interface DirectCryptoProvider {
    byte[] encryptDirectMessage(String recipientDeviceId, byte[] plaintext, Map<String, byte[]> associatedData);
    byte[] decryptDirectMessage(String senderDeviceId, byte[] ciphertextEnvelope, Map<String, byte[]> associatedData);
}
