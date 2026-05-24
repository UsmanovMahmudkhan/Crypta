package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.DeliveryReceiptRequest;
import com.sovereigncomm.api.dto.CommonDtos.EncryptedMessageResponse;
import com.sovereigncomm.api.dto.CommonDtos.EncryptedMessageSubmitRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.MessageInboxRequest;

import java.util.List;

public interface DirectMessageService {
    void acceptCiphertextEnvelope(String encryptedDirectMessageEnvelope);
    IdResponse submit(EncryptedMessageSubmitRequest request);
    List<EncryptedMessageResponse> inbox(MessageInboxRequest request);
    void recordReceipt(DeliveryReceiptRequest request);
}
