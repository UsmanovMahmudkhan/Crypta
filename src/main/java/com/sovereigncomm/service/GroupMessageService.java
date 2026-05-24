package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.EncryptedMessageSubmitRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;

public interface GroupMessageService {
    void acceptCiphertextEnvelope(String encryptedGroupMessageEnvelope);
    IdResponse submit(EncryptedMessageSubmitRequest request);
}
