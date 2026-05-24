package com.sovereigncomm.crypto;

import java.util.List;
import java.util.Map;

public interface GroupCryptoProvider {
    byte[] createGroupCommit(String roomId, List<String> memberDeviceIds);
    byte[] encryptGroupMessage(String roomId, byte[] plaintext, Map<String, byte[]> associatedData);
    byte[] decryptGroupMessage(String roomId, byte[] ciphertextEnvelope, Map<String, byte[]> associatedData);
}
